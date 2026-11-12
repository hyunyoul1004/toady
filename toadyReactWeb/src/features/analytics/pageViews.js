const PATHS = new Set(['/', '/my', '/announcements', '/login', '/certify', '/showSubscriptDetail']);
const VISITOR_KEY = 'toady.analytics.visitor';
const SESSION_KEY = 'toady.analytics.session';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SESSION_TIMEOUT = 30 * 60 * 1000;

/** 저장소 차단 시 메모리 식별자로 동작하며, 조회 실패는 화면으로 전파하지 않는다. */
export function createPageViewTracker({ apiUrl, storage, fetcher, randomUUID, now = Date.now }) {
  let visitor;
  let session;
  const read = key => { try { return storage.getItem(key); } catch { return null; } };
  const write = (key, value) => { try { storage.setItem(key, value); } catch { /* 비공개 모드 등 */ } };
  return async function track(path) {
    if (!PATHS.has(path)) return;
    try {
      visitor ||= read(VISITOR_KEY);
      if (!UUID.test(visitor || '')) visitor = randomUUID();
      write(VISITOR_KEY, visitor);
      try { session = JSON.parse(read(SESSION_KEY)) || session; } catch { /* 저장 값 손상 */ }
      const time = now();
      if (!UUID.test(session?.id || '') || !Number.isFinite(session?.lastSeen)
          || time - session.lastSeen >= SESSION_TIMEOUT || time < session.lastSeen) {
        session = { id: randomUUID(), lastSeen: time };
      }
      session.lastSeen = time;
      write(SESSION_KEY, JSON.stringify(session));
      const body = JSON.stringify({ eventId: randomUUID(), visitorId: visitor, sessionId: session.id, path });
      // JWT를 보내지 않으며, 재시도에서도 같은 이벤트 ID를 유지한다.
      for (let attempt = 0; attempt < 2; attempt++) {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        try {
          const response = await fetcher(`${apiUrl}/api/v1/analytics/page-views`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' }, body,
            credentials: 'omit', keepalive: true, signal: controller.signal
          });
          if (response.ok || response.status < 500) return;
        } catch { /* 네트워크 오류는 한 번만 재시도한다. */ }
        finally { clearTimeout(timeout); }
      }
    } catch { /* 통계 기능이 서비스 이용을 방해하지 않도록 한다. */ }
  };
}
