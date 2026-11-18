/*
 * TOADY 서비스 워커: 웹 푸시 알림 표시·클릭 이동과, 연결이 끊겼을 때의 안내 화면을 담당한다.
 * 화면·API 응답은 캐시하지 않는다(늘 네트워크에서 받으므로 배포 후에도 예전 화면이 남지 않는다).
 */
const ICON = '/icons/icon-192.png';
const BADGE = '/icons/badge-96.png';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));

/**
 * 페이지 이동(주소 열기)만 네트워크로 그대로 받고, 연결이 끊겨 실패하면 안내 화면을 보여 준다.
 * 이미지·스크립트·API 요청은 손대지 않는다(respondWith를 부르지 않으면 브라우저가 평소대로 처리한다).
 * fetch 처리기가 있어야 삼성 인터넷 등이 '설치할 수 있는 앱'으로 보고 홈 화면 추가 창을 바로 띄울 수 있다.
 */
const OFFLINE_HTML = '<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
  + '<title>연결이 끊겼어요 · TOADY</title><style>body{margin:0;font-family:system-ui,sans-serif;display:grid;place-items:center;min-height:100vh;background:#f8fafc;color:#0f172a}'
  + 'main{text-align:center;padding:24px}h1{font-size:20px;margin:12px 0 8px}p{color:#475569;margin:0 0 20px}button{font:inherit;font-weight:700;padding:10px 20px;border-radius:10px;border:0;background:#2563eb;color:#fff}</style></head>'
  + '<body><main><img src="/icons/icon-192.png" width="64" height="64" alt=""><h1>인터넷 연결이 끊겼어요</h1><p>연결되면 다시 불러올게요.</p>'
  + '<button onclick="location.reload()">다시 시도</button></main></body></html>';
self.addEventListener('fetch', event => {
  const request = event.request;
  if (request.mode !== 'navigate' || request.method !== 'GET') return;
  event.respondWith(fetch(request).catch(() => new Response(OFFLINE_HTML, { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } })));
});

/** 같은 출처의 경로만 연다. 외부 주소가 섞인 알림은 첫 화면으로 보낸다. */
function safeUrl(value) {
  try {
    const url = new URL(value || '/', self.location.origin);
    return url.origin === self.location.origin ? url.href : self.location.origin + '/';
  } catch { return self.location.origin + '/'; }
}

/**
 * 앱 라우터 안에서 바로 이동하는 경로(공고 주소 /announcements/… 포함). 이 밖의 경로(/areas·/weekly·/calendar 등)는 전체 페이지 이동으로 연다.
 * src/features/push/pushSupport.js의 SPA_PATHS와 같은 목록이어야 한다(테스트로 대조).
 */
const SPA_PATHS = ['/', '/my', '/score', '/match', '/match/zupzup', '/faq', '/this-week', '/regions', '/living-areas', '/certify', '/login', '/showSubscriptDetail', '/account', '/messages'];
const ANNOUNCEMENT_PATH = /^\/announcements\/(apt|urban|remaining|public-rental|optional)\/[A-Za-z0-9_-]{1,40}\/[A-Za-z0-9_-]{1,40}$/;
const REGION_PATH = /^\/regions\/[^/]{1,80}(\/[^/]{1,80})?$/;
function isSpaUrl(value) {
  try {
    const url = new URL(value, self.location.origin);
    const path = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, '') : url.pathname;
    return url.origin === self.location.origin && (SPA_PATHS.includes(path) || ANNOUNCEMENT_PATH.test(path) || REGION_PATH.test(path));
  } catch { return false; }
}

/**
 * 어떤 알림으로 들어왔는지 알 수 있게 src 꼬리표를 붙인다(앱이 첫 기록 뒤 주소에서 지운다).
 * src/features/analytics/pageViews.js의 PUSH_SOURCES와 같은 값이어야 한다.
 */
function pushSource(tag) {
  if (tag === 'toady-digest') return 'push.digest';
  if (typeof tag === 'string' && tag.startsWith('toady-result')) return 'push.result';
  if (typeof tag === 'string' && tag.startsWith('dm-')) return 'push.message';
  if (typeof tag === 'string' && tag.startsWith('notice')) return 'push.notice';
  return 'push.other';
}
function withSource(value, tag) {
  try {
    const url = new URL(value);
    if (url.origin !== self.location.origin) return value;
    url.searchParams.set('src', pushSource(tag));
    return url.href;
  } catch { return value; }
}

self.addEventListener('push', event => {
  let data = {};
  try { data = event.data ? event.data.json() : {}; } catch { data = { body: event.data ? event.data.text() : '' }; }
  const actions = Array.isArray(data.actions) ? data.actions.slice(0, 2) : [];
  const urls = Object.fromEntries(actions.map(action => [action.action, action.url]));
  event.waitUntil(self.registration.showNotification(data.title || 'TOADY 청약 알림', {
    body: data.body || '',
    icon: ICON,
    badge: BADGE,
    tag: data.tag || 'toady',
    lang: 'ko',
    data: { url: data.url || '/', urls, tag: data.tag || '' },
    actions: actions.map(action => ({ action: action.action, title: action.title }))
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const data = event.notification.data || {};
  const target = withSource(safeUrl((event.action && data.urls && data.urls[event.action]) || data.url), data.tag);
  event.waitUntil((async () => {
    const windows = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const client = windows.find(item => new URL(item.url).origin === self.location.origin);
    if (client) {
      await client.focus();
      if (isSpaUrl(target) && isSpaUrl(client.url)) {
        // 앱 화면이 열려 있고 이동할 곳도 앱 화면이면 새로고침 없이 앱 라우터로 이동해 뒤로가기 흐름을 유지한다.
        client.postMessage({ type: 'toady-push-open', url: target });
        return;
      }
      // 서버가 그리는 페이지로 가거나, 열린 창이 서버 페이지라 앱 라우터가 없으면 창 자체를 이동시킨다.
      try {
        if (typeof client.navigate === 'function') {
          // 같은 출처 이동이면 성공 시 결과와 관계없이 이미 이동한 것이므로 새 창을 열지 않는다.
          await client.navigate(target);
          return;
        }
      } catch { /* 이 서비스 워커가 제어하지 않는 창은 이동할 수 없어 새 창으로 연다 */ }
    }
    await self.clients.openWindow(target);
  })());
});
