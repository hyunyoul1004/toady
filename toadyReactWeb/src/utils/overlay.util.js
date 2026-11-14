/**
 * 팝업 레이어 변경을 계산하는 순수 함수. 히스토리 state 의 overlayLayers 를 기준으로
 * 아무것도 하지 않을지(none), 한 단계 뒤로 갈지(back), 새 state 로 이동할지(navigate) 정한다.
 */
export function resolveOverlayUpdate(locationState, id, next) {
  const layers = locationState?.overlayLayers || [];
  const layer = layers.find(item => item.id === id);
  const value = layer?.value ?? null;
  const resolved = typeof next === 'function' ? next(value) : next;
  // 이미 같은 값이거나, 닫으려는 레이어가 이미 사라졌으면(뒤로가기로 닫힌 뒤 늦게 온 닫기 호출) 아무것도 하지 않는다.
  if (resolved === value || (!resolved && !layer)) return { type: 'none' };
  if (!resolved && layers.at(-1)?.id === id) return { type: 'back' };
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
 */
export function detailCloseSteps(state) {
  return 1 + (Array.isArray(state?.overlayLayers) ? state.overlayLayers.length : 0);
}
