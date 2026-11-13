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
 * 상세를 '닫기'로 한 번에 닫을 때 돌아갈 히스토리 칸 수. 상세 위에 쌓인 레이어(예: 위치 탭 지도에서 고른 단지)는
 * 화면을 덮지 않아 닫기 단추가 계속 보이므로, 한 칸만 돌아가면 그 레이어만 닫히고 상세는 남는다.
 * 상세를 열 때 overlayLayers를 비워 두어야(이전 화면의 레이어를 물려받지 않아야) 칸 수가 맞는다.
 */
export function detailCloseSteps(state) {
  return 1 + (Array.isArray(state?.overlayLayers) ? state.overlayLayers.length : 0);
}
