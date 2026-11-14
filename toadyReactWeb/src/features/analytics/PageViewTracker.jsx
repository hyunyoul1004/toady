import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
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
function logicalPath(pathname, search) {
  if (pathname.startsWith('/announcements/')) return '/announcements';
  if (new URLSearchParams(search).has('subscriptSeq')) return '/announcements';
  return pathname;
}

/** 첫 진입과 경로 이동을 기록한다. 상세 열기 외의 쿼리·해시 변경 및 StrictMode의 effect 재실행은 제외한다. */
export default function PageViewTracker() {
  const location = useLocation();
  const navigate = useNavigate();
  const { pathname, search, key } = location;
  const previous = useRef(null);
  const path = logicalPath(pathname, search);
  useEffect(() => {
    if (previous.current?.key === key || previous.current?.path === path) return;
    previous.current = { key, path };
    track(path);
  }, [path, key]);
  // 유입 꼬리표(src)는 첫 기록에만 쓰고 주소에서 지운다(기록은 위 track이 동기로 먼저 읽는다).
  // 남겨 두면 이 주소를 복사해 공유했을 때 받은 사람의 방문까지 같은 출처로 잡힌다.
  useEffect(() => {
    const params = new URLSearchParams(search);
    if (!params.has('src')) return;
    params.delete('src');
    const rest = params.toString();
    navigate(`${pathname}${rest ? `?${rest}` : ''}${location.hash}`, { replace: true, state: location.state });
  }, [search]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}
