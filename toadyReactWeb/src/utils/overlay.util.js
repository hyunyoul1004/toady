/**
 * @file overlay.util.js
 * @description 팝업·상세 창을 브라우저 히스토리와 묶는 '히스토리 레이어' 규칙. useHistoryOverlay와 지도·상세 화면이 쓴다.
 *
 * 왜 필요한가: 휴대폰에서 팝업을 연 채 뒤로가기를 누르면 사용자는 팝업이 닫히길 기대한다.
 * 그런데 팝업을 useState로만 열면 히스토리에 칸이 없어서 뒤로가기가 이전 화면(또는 사이트 밖)으로 가 버린다.
 * 그래서 팝업을 열 때 같은 주소로 navigate 하면서 location.state에 '어떤 팝업이 열렸는지'를 적어 히스토리 칸을 하나 만든다.
 *
 * 데이터 모양: location.state.overlayLayers = [{ id: 'login', value: true }, { id: 'complex', value: {...} }, ...]
 * - 배열 순서가 쌓인 순서다. 마지막 항목이 맨 위 팝업이다.
 * - 열기 = 새 칸 추가(push), 값만 바꾸기 = 지금 칸 교체(replace), 맨 위 팝업 닫기 = 뒤로가기(navigate(-1)).
 * - 뒤로가기·앞으로가기를 누르면 브라우저가 그 칸의 state를 되살리므로 팝업이 저절로 닫히고 다시 열린다.
 * 이 파일의 함수는 모두 순수 함수(입력만 보고 결과를 돌려줌)라 테스트하기 쉽다. 실제 이동은 호출한 쪽이 한다.
 */

/**
 * 팝업 레이어 변경을 계산하는 순수 함수. 히스토리 state 의 overlayLayers 를 기준으로
 * 아무것도 하지 않을지(none), 한 단계 뒤로 갈지(back), 새 state 로 이동할지(navigate) 정한다.
 */
export function resolveOverlayUpdate(locationState, id, next) {
  const layers = locationState?.overlayLayers || [];
  const layer = layers.find(item => item.id === id);
  const value = layer?.value ?? null;
  // useState의 setter처럼 함수(이전 값 => 새 값)도 받는다.
  const resolved = typeof next === 'function' ? next(value) : next;
  // 이미 같은 값이거나, 닫으려는 레이어가 이미 사라졌으면(뒤로가기로 닫힌 뒤 늦게 온 닫기 호출) 아무것도 하지 않는다.
  if (resolved === value || (!resolved && !layer)) return { type: 'none' };
  // 맨 위 레이어를 닫을 때는 새 칸을 만들지 않고 뒤로 한 칸 간다. 그래야 앞으로가기로 다시 열 수 있고 칸이 쌓이지 않는다.
  if (!resolved && layers.at(-1)?.id === id) return { type: 'back' };
  // 그 밖(새로 열기, 값 바꾸기, 아래쪽 레이어 닫기)은 같은 주소로 이동하며 state만 바꾼다.
  // 이미 있던 레이어를 바꾸는 경우만 replace(지금 칸 교체), 새로 여는 경우는 push(새 칸).
  const remaining = layers.filter(item => item.id !== id);
  return {
    type: 'navigate',
    state: { ...locationState, overlayLayers: resolved ? [...remaining, { id, value: resolved }] : remaining },
    replace: Boolean(layer)
  };
}

/**
 * 이 레이어와 그 위에 쌓인 레이어를 모두 닫으려면 돌아갈 히스토리 칸 수(없으면 0).
 * 아래 레이어를 replace로만 빼면 그 레이어가 열려 있던 이전 칸이 남아, 뒤로가기 때 닫은 화면이 다시 열린다.
 * 예: [전체 화면, 고른 단지]에서 전체 화면을 닫으면 2칸 돌아가 둘 다 닫는다.
 */
export function closeStackSteps(state, id) {
  const layers = Array.isArray(state?.overlayLayers) ? state.overlayLayers : [];
  const index = layers.findIndex(item => item.id === id);
  return index < 0 ? 0 : layers.length - index;
}

/**
 * 새 레이어를 열 때 이미 열린 레이어(lift)를 그 위로 옮긴 히스토리 칸들: [현재 칸을 새 레이어로 교체, 새 레이어+옮긴 레이어 추가].
 * 현재 칸(옮길 레이어가 열린 칸)을 교체하므로 같은 화면이 두 번 쌓이는 빈 칸이 생기지 않는다.
 * 고른 단지가 있는 채로 전체 화면을 열 때 쓴다. 단지가 전체 화면 아래에 깔리면 단지만 닫을 때 뒤로가기로 처리할 수 없다.
 */
export function liftedOverlayStates(state, id, value, liftIds) {
  const layers = Array.isArray(state?.overlayLayers) ? state.overlayLayers : [];
  const lifted = layers.filter(item => liftIds.includes(item.id));
  const base = layers.filter(item => !liftIds.includes(item.id) && item.id !== id);
  const opened = [...base, { id, value }];
  return lifted.length ? [{ ...state, overlayLayers: opened }, { ...state, overlayLayers: [...opened, ...lifted] }] : null;
}

/**
 * 상세를 '닫기'로 한 번에 닫을 때 돌아갈 히스토리 칸 수. 상세 위에 쌓인 레이어(예: 위치 탭 지도에서 고른 단지)는
 * 화면을 덮지 않아 닫기 단추가 계속 보이므로, 한 칸만 돌아가면 그 레이어만 닫히고 상세는 남는다.
 * 상세를 열 때 overlayLayers를 비워 두어야(이전 화면의 레이어를 물려받지 않아야) 칸 수가 맞는다.
 * 상세 위에 다른 공고 상세를 쌓았으면(stackedDetailState) 그 아래 상세들을 닫는 칸 수(detailStackSteps)도 더해 한 번에 닫는다.
 */
export function detailCloseSteps(state) {
  const below = Number.isInteger(state?.detailStackSteps) && state.detailStackSteps > 0 ? state.detailStackSteps : 0;
  return 1 + (Array.isArray(state?.overlayLayers) ? state.overlayLayers.length : 0) + below;
}

/**
 * 상세 위에서 다른 공고 상세를 열 때(위치 탭 지도의 다른 분양 공고) 새 칸의 state.
 * 이전 공고의 레이어(고른 단지·전체 화면 등)는 새 공고로 따라가지 않게 dropIds로 빼고,
 * detailStackSteps에 '이전 상세를 닫는 데 필요한 칸 수'를 적어 둔다. 그래서 새 공고의 '닫기'는 이전 상세까지 한 번에 닫고
 * (detailCloseSteps가 더한다), 브라우저 뒤로가기는 한 칸씩 이전 공고로 돌아간다. 쌓인 공고가 여럿이어도 칸 수가 이어서 더해진다.
 */
export function stackedDetailState(state, dropIds = []) {
  if (!state) return state;
  return {
    ...state,
    overlayLayers: (state.overlayLayers || []).filter(layer => !dropIds.includes(layer.id)),
    detailStackSteps: detailCloseSteps(state)
  };
}
