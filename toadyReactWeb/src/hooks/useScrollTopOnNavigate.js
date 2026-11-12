import { useEffect } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

/**
 * 다른 화면으로 새로 이동하면 맨 위에서 보여준다.
 * - 뒤로가기·앞으로가기(POP)는 브라우저가 복원한 읽던 위치를 지킨다.
 * - 같은 화면에서 검색 조건·팝업만 바뀌는 경우(경로가 같음)와 #앵커 이동은 건드리지 않는다.
 */
export function useScrollTopOnNavigate() {
  const { pathname, hash } = useLocation();
  const type = useNavigationType();
  useEffect(() => {
    if (type === 'POP' || hash) return;
    window.scrollTo(0, 0);
  }, [pathname]); // eslint-disable-line react-hooks/exhaustive-deps
}
