import { useEffect, useRef } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import config from 'config';
import { createPageViewTracker, externalReferrer, pushOpenSubject } from './pageViews';
import { trackAction } from './trackAction';

// 기록기는 처음 쓸 때 한 번만 만든다(||=: 비어 있을 때만 대입). 모듈 변수라 화면이 다시 그려져도 유지된다.
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
  // 지역 캘린더(/regions/경기도/파주시)는 지역별 청약 화면으로 센다.
  if (pathname.startsWith('/regions/')) return '/regions';
  if (new URLSearchParams(search).has('subscriptSeq')) return '/announcements';
  return pathname;
}

/**
 * 첫 진입과 경로 이동을 기록한다. 상세 열기 외의 쿼리·해시 변경 및 StrictMode의 effect 재실행은 제외한다.
 * App.jsx의 라우터 안에 한 번 둔다. 화면에는 아무것도 그리지 않는다(return null).
 * API: POST /api/v1/analytics/page-views(pageViews.js), 알림 클릭은 trackAction('push_open').
 *
 * useRef: 값이 바뀌어도 다시 그리지 않는 '상자'다. 지난번에 센 주소처럼 화면과 상관없이 기억만 할 값에 쓴다.
 * (useState였다면 값을 바꿀 때마다 다시 그려진다.)
 * location.key: React Router가 히스토리 칸마다 붙이는 고유값. 같은 주소라도 새로 이동하면 바뀐다.
 */
export default function PageViewTracker() {
  const location = useLocation();
  const navigate = useNavigate();
  const { pathname, search, key } = location;
  const previous = useRef(null);
  // 같은 주소의 알림 열기를 두 번 세지 않는다(개발 모드 StrictMode의 effect 재실행 등).
  const pushCounted = useRef(null);
  const path = logicalPath(pathname, search);
  // useEffect의 두 번째 인자(의존성 배열): 이 값들 중 하나가 바뀐 렌더 뒤에만 안쪽을 다시 실행한다.
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
    // 알림을 눌러 들어온 방문은 세션과 무관하게 알림 종류별로 한 번 센다(서비스 워커가 src=push.* 를 붙인다).
    const pushSubject = pushOpenSubject(params.get('src'));
    if (pushSubject && pushCounted.current !== key) { pushCounted.current = key; trackAction('push_open', pushSubject); }
    params.delete('src');
    const rest = params.toString();
    // replace: 히스토리를 한 칸 더 쌓지 않고 지금 칸의 주소만 바꾼다(뒤로가기 한 번에 이전 화면으로). state는 그대로 넘겨 열린 팝업을 지킨다.
    navigate(`${pathname}${rest ? `?${rest}` : ''}${location.hash}`, { replace: true, state: location.state });
    // 의존성은 search 하나만 둔다. navigate·location.state가 바뀔 때마다 다시 돌 필요가 없어서 일부러 뺐다.
  }, [search]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}
