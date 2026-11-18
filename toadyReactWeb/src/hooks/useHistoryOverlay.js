import { useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { resolveOverlayUpdate } from '@/utils/overlay.util';

/**
 * 팝업을 실제 히스토리 한 단계로 표현해 뒤로가기·앞으로가기와 닫기를 일치시킨다.
 * 사용법은 useState와 같다: const [open, setOpen] = useHistoryOverlay('login');
 * - value: location.state.overlayLayers에서 id가 같은 레이어의 값(없으면 null). 주소의 state에서 읽으므로
 *   뒤로가기·앞으로가기를 누르면 컴포넌트가 다시 렌더되며 값이 저절로 바뀐다.
 * - setValue(값 또는 이전값 => 새값): 열기는 새 히스토리 칸, 맨 위 팝업 닫기는 뒤로가기, 그 밖은 지금 칸 교체.
 *   어떻게 움직일지는 utils/overlay.util.js의 resolveOverlayUpdate가 정한다(규칙 설명도 그 파일 맨 위).
 * 로그인 팝업, 공유 메뉴, 지도에서 고른 단지, 커뮤니티 창 등 화면을 덮는 팝업 대부분이 이 훅을 쓴다.
 * id는 화면 안에서 겹치지 않는 이름이어야 한다.
 */
export function useHistoryOverlay(id) {
  const location = useLocation();
  const navigate = useNavigate();
  // 비동기 작업 뒤에 호출되는 setValue(오래된 클로저)도 최신 히스토리 기준으로 판단하도록 매 렌더마다 갱신한다.
  // useRef는 값을 바꿔도 다시 렌더하지 않는 상자다. 여기서는 '지금 주소'를 늘 최신으로 들고 있는 용도로 쓴다.
  const locationRef = useRef(location);
  locationRef.current = location;
  const value = (location.state?.overlayLayers || []).find(item => item.id === id)?.value ?? null;
  const setValue = next => {
    const current = locationRef.current;
    const update = resolveOverlayUpdate(current.state, id, next);
    if (update.type === 'back') {
      navigate(-1);
    } else if (update.type === 'navigate') {
      // 주소(경로·검색어·#)는 그대로 두고 state만 바꿔 이동한다. 화면은 그대로이고 히스토리 칸만 생기거나 바뀐다.
      navigate(current.pathname + current.search + current.hash, { state: update.state, replace: update.replace });
    }
  };
  return [value, setValue];
}

// 상세 화면이 '닫기' 단추로 몇 칸 돌아갈지 계산할 때 쓰도록 함께 내보낸다.
export { detailCloseSteps } from '@/utils/overlay.util';
