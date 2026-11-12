import config from 'config';
const ACTIONS = new Set(['detail_view','favorite_add','alert_enable','official_click','research_click','compare','share','score_save','poll_vote','push_enable']);
export function trackAction(action, subject) {
  if (!ACTIONS.has(action) || !/^((apt|urban|remaining|public-rental|optional)\/[a-zA-Z0-9_-]{1,40}\/[a-zA-Z0-9_-]{1,40}|tool\/[a-z0-9-]{1,40})$/.test(subject || '')) return;
  try {
    const visitorId = anonymousVisitorId();
    const controller=new AbortController(), timer=setTimeout(()=>controller.abort(),5000);
    void fetch(`${config.apiUrl}/api/v1/analytics/events`,{method:'POST',headers:{'Content-Type':'application/json'},credentials:'omit',keepalive:true,signal:controller.signal,
      body:JSON.stringify({eventId:crypto.randomUUID(),visitorId,action,subject})}).catch(()=>{}).finally(()=>clearTimeout(timer));
  } catch { /* 행동 측정 실패는 이용 흐름에 영향을 주지 않는다. */ }
}

/** 익명 측정·투표가 함께 쓰는 브라우저 무작위 방문자 ID. 회원 정보와 연결하지 않는다. */
export function anonymousVisitorId() {
  let visitorId = localStorage.getItem('toady.analytics.visitor');
  if (!/^[a-f0-9-]{36}$/i.test(visitorId || '')) { visitorId=crypto.randomUUID();localStorage.setItem('toady.analytics.visitor',visitorId); }
  return visitorId;
}
