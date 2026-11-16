import { useEffect, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

/**
 * 다른 화면으로 새로 이동하면 맨 위에서 보여준다.
 * - 뒤로가기·앞으로가기(POP)는 브라우저가 복원한 읽던 위치를 지킨다.
 * - 같은 화면에서 검색 조건·팝업만 바뀌는 경우(경로가 같음)와 #앵커 이동은 건드리지 않는다.
 */
export function useScrollTopOnNavigate() {
  const { pathname, hash } = useLocation();
  const type = useNavigationType();
  const previous = useRef(pathname);
  useEffect(() => {
    const from = previous.current;
    previous.current = pathname;
    // 공고 상세(/announcements/…)는 첫 화면 목록 위에 뜨는 창이라, 목록과 상세 사이(또는 상세끼리) 오갈 때만 위치를 그대로 둔다.
    // 상세에서 다른 메뉴(나의 청약 등)로 가거나 다른 메뉴에서 상세로 올 때는 새 화면이므로 맨 위에서 보여 준다.
    const list = path => path === '/' || path === '/showSubscriptDetail';
    const detail = path => path.startsWith('/announcements/');
    const overList = (detail(from) && (list(pathname) || detail(pathname))) || (detail(pathname) && list(from));
    if (type === 'POP' || hash || overList) return;
    window.scrollTo(0, 0);
  }, [pathname]); // eslint-disable-line react-hooks/exhaustive-deps
}
