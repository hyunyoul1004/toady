import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import config from 'config';
import { createPageViewTracker } from './pageViews';

let tracker;
function track(path) {
  tracker ||= createPageViewTracker({
    apiUrl: config.apiUrl,
    // localStorage 접근 자체가 금지된 브라우저에서도 추적 실패가 렌더링을 막지 않는다.
    storage: { getItem: key => window.localStorage.getItem(key), setItem: (key, value) => window.localStorage.setItem(key, value) },
    fetcher: (...args) => fetch(...args), randomUUID: () => crypto.randomUUID()
  });
  void tracker(path);
}

/** 첫 진입과 경로 이동을 기록한다. 쿼리·해시 변경 및 StrictMode의 effect 재실행은 제외한다. */
export default function PageViewTracker() {
  const { pathname, key } = useLocation();
  const previous = useRef(null);
  useEffect(() => {
    if (previous.current?.key === key || previous.current?.pathname === pathname) return;
    previous.current = { key, pathname };
    track(pathname.startsWith('/announcements/') ? '/announcements' : pathname);
  }, [pathname, key]);
  return null;
}
