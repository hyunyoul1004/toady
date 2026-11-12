import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import config from 'config';
import { createPageViewTracker, externalReferrer } from './pageViews';

let tracker;
function track(path) {
  tracker ||= createPageViewTracker({
    apiUrl: config.apiUrl,
    // localStorage 접근 자체가 금지된 브라우저에서도 추적 실패가 렌더링을 막지 않는다.
    storage: { getItem: key => window.localStorage.getItem(key), setItem: (key, value) => window.localStorage.setItem(key, value) },
    fetcher: (...args) => fetch(...args), randomUUID: () => crypto.randomUUID(),
    referrerHost: () => externalReferrer(document.referrer, window.location.search, window.location.hostname)
  });
  void tracker(path);
}

/** 공고 상세는 공유 주소(/announcements/…)든 앱 안 주소(?subscriptSeq=)든 같은 '/announcements'로 센다. */
export function logicalPath(pathname, search) {
  if (pathname.startsWith('/announcements/')) return '/announcements';
  if (new URLSearchParams(search).has('subscriptSeq')) return '/announcements';
  return pathname;
}

/** 첫 진입과 경로 이동을 기록한다. 상세 열기 외의 쿼리·해시 변경 및 StrictMode의 effect 재실행은 제외한다. */
export default function PageViewTracker() {
  const { pathname, search, key } = useLocation();
  const previous = useRef(null);
  const path = logicalPath(pathname, search);
  useEffect(() => {
    if (previous.current?.key === key || previous.current?.path === path) return;
    previous.current = { key, path };
    track(path);
  }, [path, key]);
  return null;
}
