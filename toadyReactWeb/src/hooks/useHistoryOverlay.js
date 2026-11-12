import { useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { resolveOverlayUpdate } from '@/utils/overlay.util';

/** 팝업을 실제 히스토리 한 단계로 표현해 뒤로가기·앞으로가기와 닫기를 일치시킨다. */
export function useHistoryOverlay(id) {
  const location = useLocation();
  const navigate = useNavigate();
  // 비동기 작업 뒤에 호출되는 setValue(오래된 클로저)도 최신 히스토리 기준으로 판단하도록 매 렌더마다 갱신한다.
  const locationRef = useRef(location);
  locationRef.current = location;
  const value = (location.state?.overlayLayers || []).find(item => item.id === id)?.value ?? null;
  const setValue = next => {
    const current = locationRef.current;
    const update = resolveOverlayUpdate(current.state, id, next);
    if (update.type === 'back') {
      navigate(-1);
    } else if (update.type === 'navigate') {
      navigate(current.pathname + current.search + current.hash, { state: update.state, replace: update.replace });
    }
  };
  return [value, setValue];
}
