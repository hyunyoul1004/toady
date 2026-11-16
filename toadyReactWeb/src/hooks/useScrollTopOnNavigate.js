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
    // 공고 상세(/announcements/…)는 목록 위에 뜨는 창이라, 열고 닫을 때 뒤의 목록을 맨 위로 올리지 않는다.
    if (type === 'POP' || hash || from.startsWith('/announcements/') || pathname.startsWith('/announcements/')) return;
    window.scrollTo(0, 0);
  }, [pathname]); // eslint-disable-line react-hooks/exhaustive-deps
}
