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
