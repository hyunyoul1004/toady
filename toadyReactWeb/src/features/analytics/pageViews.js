/**
 * 페이지 조회 기록(관리자 '유입' 지표). PageViewTracker가 createPageViewTracker로 기록기를 만들어 쓴다.
 * 방문자 ID(브라우저마다 무작위, localStorage)와 세션 ID(30분 동안 움직임이 없으면 새로)를 함께 보낸다.
 * API: POST /api/v1/analytics/page-views. 로그인 토큰은 싣지 않는다(회원과 연결하지 않음).
 */
// 기록할 화면 경로. 여기에 없는 경로는 보내지 않는다.
const PATHS = new Set(['/', '/my', '/score', '/match', '/match/zupzup', '/faq', '/this-week', '/regions', '/announcements', '/login', '/certify', '/showSubscriptDetail']);
const VISITOR_KEY = 'toady.analytics.visitor';
const SESSION_KEY = 'toady.analytics.session';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const SESSION_TIMEOUT = 30 * 60 * 1000;
const HOST = /^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$/;
const OWN = /(^|\.)toady\.co\.kr$/;

/**
 * 외부 유입 도메인만 돌려준다. 전체 URL·검색어는 다루지 않는다.
 * - 외부 사이트에서 왔으면(referrer) 그 도메인. 주소에 남은 src보다 실제 유입처가 우선이다.
 * - TOADY 공개 페이지(공고·생활권·주간)를 거쳐 왔으면 그 페이지가 넘긴 src.
 * - 유입 정보가 없으면(카카오톡 등 앱 안 브라우저·직접 입력) 링크에 붙인 꼬리표 src(예: ?src=kakao.com).
 *   앱은 첫 기록 뒤 주소에서 src를 지워, 그 주소를 다시 공유해도 출처가 따라가지 않게 한다.
 */
export function externalReferrer(referrer, search, ownHost) {
  try {
    const host = referrer ? new URL(referrer).hostname.toLowerCase() : '';
    const fromSelf = host && (host === ownHost || OWN.test(host));
    if (host && !fromSelf) return HOST.test(host) && host.length <= 100 ? host : null;
    const source = (new URLSearchParams(search).get('src') || '').toLowerCase();
    return HOST.test(source) && source.length <= 100 && !OWN.test(source) && source !== ownHost ? source : null;
  } catch { return null; }
}

/** 알림 클릭 꼬리표(src=push.digest 등)를 행동 기록 이름으로 바꾼다. 정해진 값이 아니면 null. */
const PUSH_SOURCES = { 'push.digest': 'tool/push-digest', 'push.result': 'tool/push-result', 'push.message': 'tool/push-message', 'push.notice': 'tool/push-notice', 'push.other': 'tool/push-other' };
export function pushOpenSubject(source) { return PUSH_SOURCES[(source || '').toLowerCase()] || null; }

/**
 * 저장소 차단 시 메모리 식별자로 동작하며, 조회 실패는 화면으로 전파하지 않는다.
 * storage·fetcher·randomUUID·now를 밖에서 받는 것은 테스트에서 가짜를 넣기 위해서다.
 * 돌려주는 track(path)는 클로저로 visitor·session·first를 기억한다(이 함수가 끝나도 값이 남는다).
 */
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
          // 4xx는 다시 보내도 같은 결과라 멈춘다. 5xx(서버 오류)만 한 번 더 보낸다.
          if (response.ok || response.status < 500) return;
        } catch { /* 네트워크 오류는 한 번만 재시도한다. */ }
        finally { clearTimeout(timeout); }
      }
    } catch { /* 통계 기능이 서비스 이용을 방해하지 않도록 한다. */ }
  };
}
