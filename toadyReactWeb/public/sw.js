/*
 * TOADY 서비스 워커: 웹 푸시 알림 표시와 클릭 이동만 담당한다.
 * 화면·API 응답을 캐시하지 않아 배포 후에도 예전 화면이 남지 않는다(fetch 처리기 없음).
 */
const ICON = '/icons/icon-192.png';
const BADGE = '/icons/badge-96.png';

self.addEventListener('install', () => self.skipWaiting());
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));

/** 같은 출처의 경로만 연다. 외부 주소가 섞인 알림은 첫 화면으로 보낸다. */
function safeUrl(value) {
  try {
    const url = new URL(value || '/', self.location.origin);
    return url.origin === self.location.origin ? url.href : self.location.origin + '/';
  } catch { return self.location.origin + '/'; }
}

/**
 * React 앱(SPA)이 그리는 경로. /areas·/announcements·/zupzup·/weekly·/calendar 등은 서버가 HTML을 직접 그린다.
 * src/features/push/pushSupport.js의 SPA_PATHS와 같은 목록이어야 한다(테스트로 대조).
 */
const SPA_PATHS = ['/', '/my', '/score', '/match', '/match/zupzup', '/faq', '/this-week', '/regions', '/living-areas', '/certify', '/login', '/showSubscriptDetail'];
function isSpaUrl(value) {
  try {
    const url = new URL(value, self.location.origin);
    const path = url.pathname.length > 1 ? url.pathname.replace(/\/+$/, '') : url.pathname;
    return url.origin === self.location.origin && SPA_PATHS.includes(path);
  } catch { return false; }
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
    data: { url: data.url || '/', urls },
    actions: actions.map(action => ({ action: action.action, title: action.title }))
  }));
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  const data = event.notification.data || {};
  const target = safeUrl((event.action && data.urls && data.urls[event.action]) || data.url);
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
