const PATHS = new Set(['/', '/my', '/score', '/announcements', '/login', '/certify', '/showSubscriptDetail']);
const VISITOR_KEY = 'toady.analytics.visitor';
const SESSION_KEY = 'toady.analytics.session';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SESSION_TIMEOUT = 30 * 60 * 1000;
const HOST = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$/;
const OWN = /(^|\.)toady\.co\.kr$/;

/**
 * 외부 유입 도메인만 돌려준다. TOADY 공개 페이지(공고·생활권)를 거쳐 앱에 들어오면 그 페이지가 넘긴 src를 쓰고,
 * 공유받은 주소에 남은 src는 실제 유입처가 외부일 때 무시한다. 전체 URL·검색어는 다루지 않는다.
 */
export function externalReferrer(referrer, search, ownHost) {
  try {
    const host = referrer ? new URL(referrer).hostname.toLowerCase() : '';
    const fromSelf = host && (host === ownHost || OWN.test(host));
    if (host && !fromSelf) return HOST.test(host) && host.length <= 100 ? host : null;
    if (!fromSelf) return null;
    const source = (new URLSearchParams(search).get('src') || '').toLowerCase();
    return HOST.test(source) && source.length <= 100 && !OWN.test(source) && source !== ownHost ? source : null;
  } catch { return null; }
}

/** 저장소 차단 시 메모리 식별자로 동작하며, 조회 실패는 화면으로 전파하지 않는다. */
export function createPageViewTracker({ apiUrl, storage, fetcher, randomUUID, now = Date.now, referrerHost = () => null }) {
  let visitor;
  let session;
  let first = true;
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
      let started = false;
      if (!UUID.test(session?.id || '') || !Number.isFinite(session?.lastSeen)
          || time - session.lastSeen >= SESSION_TIMEOUT || time < session.lastSeen) {
        session = { id: randomUUID(), lastSeen: time };
        started = true;
      }
      // 유입처는 페이지를 처음 연 순간의 새 세션에만 붙인다. 같은 탭에서 세션이 만료돼도 옛 유입처를 다시 쓰지 않는다.
      const source = first && started ? referrerHost() : null;
      first = false;
      session.lastSeen = time;
      write(SESSION_KEY, JSON.stringify(session));
      const body = JSON.stringify({ eventId: randomUUID(), visitorId: visitor, sessionId: session.id, path, ...(source ? { referrerHost: source } : {}) });
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
