/**
 * 행동 측정(관리자 지표용). 상세 보기·관심 저장·알림 켜기 같은 '의미 있는 행동'을 익명 방문자 ID와 함께 보낸다.
 * 여러 화면(Sidebar, pushStore, installStore, Kakaomap 등)에서 부른다. API: POST /api/v1/analytics/events
 * 정해진 action과 subject 형식만 보내고, 그 밖의 값은 조용히 버린다(서버도 같은 목록으로 검사한다).
 */
import config from 'config';
// 보낼 수 있는 행동 이름. 여기에 없는 이름은 보내지 않는다.
const ACTIONS = new Set(['detail_view','favorite_add','alert_enable','official_click','research_click','compare','share','score_save','poll_vote','push_enable','match_save','map_tool','push_open','push_denied','push_disable','app_install','signup_prompt_view','signup_prompt_click','guest_import']);
/** action: ACTIONS 중 하나, subject: '종류/번호/번호'(공고) 또는 'tool/이름'(기능). */
export function trackAction(action, subject) {
  if (!ACTIONS.has(action) || !/^((apt|urban|remaining|public-rental|optional)\/[a-zA-Z0-9_-]{1,40}\/[a-zA-Z0-9_-]{1,40}|tool\/[a-z0-9-]{1,40})$/.test(subject || '')) return;
  try {
    const visitorId = anonymousVisitorId();
    // 5초 안에 응답이 없으면 요청을 끊는다(AbortController). 끝나면 타이머를 지운다.
    const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),5000);
    void fetch(`${config.apiUrl}/api/v1/analytics/events`,{method:'POST',headers:{'Content-Type':'application/json'},credentials:'omit',keepalive:true,signal:controller.signal,
      body:JSON.stringify({eventId:crypto.randomUUID(),visitorId,action,subject})}).catch(()=>{}).finally(()=>clearTimeout(timer));
  } catch { /* 행동 측정 실패는 이용 흐름에 영향을 주지 않는다. */ }
}

/**
 * 익명 측정·투표가 함께 쓰는 브라우저 무작위 방문자 ID. 회원 정보와 연결하지 않는다.
 * 처음 부를 때 만들어 localStorage에 두고 이후 같은 값을 쓴다. 저장소가 막혀 있으면 예외가 나므로 부르는 쪽에서 try로 감싼다.
 */
export function anonymousVisitorId() {
  let visitorId = localStorage.getItem('toady.analytics.visitor');
  if (!/^[a-f0-9-]{36}$/i.test(visitorId || '')) { visitorId=crypto.randomUUID();localStorage.setItem('toady.analytics.visitor',visitorId); }
  return visitorId;
}
