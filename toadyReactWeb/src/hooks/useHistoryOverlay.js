import { useLocation, useNavigate } from 'react-router-dom';

/** 팝업을 실제 히스토리 한 단계로 표현해 뒤로가기·앞으로가기와 닫기를 일치시킨다. */
export function useHistoryOverlay(id) {
  const location = useLocation();
  const navigate = useNavigate();
  const layers = location.state?.overlayLayers || [];
  const layer = layers.find(item => item.id === id);
  const value = layer?.value ?? null;
  const setValue = next => {
    const resolved = typeof next === 'function' ? next(value) : next;
    if (resolved === value || (!resolved && !layer)) return;
    if (!resolved && layers.at(-1)?.id === id) {
      navigate(-1);
      return;
    }
    const remaining = layers.filter(item => item.id !== id);
    const state = { ...location.state, overlayLayers: resolved ? [...remaining, { id, value: resolved }] : remaining };
    navigate(location.pathname + location.search + location.hash, { state, replace: Boolean(layer) });
  };
  return [value, setValue];
}
