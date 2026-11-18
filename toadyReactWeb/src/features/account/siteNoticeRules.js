/**
 * 사이트 공지 노출 규칙: 첫 화면 전용(HOME)은 '/'에서만, 닫은 공지는 숨긴다. 닫은 기록은 이 브라우저에 최근 50개까지 둔다.
 * 쓰는 곳: SiteNotices. storage를 인자로 받는 것은 테스트에서 가짜 저장소를 넣기 위해서다.
 */
const KEY = 'toady.notices.dismissed';
export function readDismissed(storage = globalThis.localStorage) {
  try { const list = JSON.parse(storage.getItem(KEY) || '[]'); return Array.isArray(list) ? list.filter(Number.isFinite) : []; } catch { return []; }
}
/** 공지를 닫은 것으로 기록하고 새 목록을 돌려준다. 최신 것을 앞에 두고 50개를 넘으면 오래된 것부터 버린다. */
export function dismissNotice(seq, storage = globalThis.localStorage) {
  const next = [...new Set([Number(seq), ...readDismissed(storage)])].slice(0, 50);
  try { storage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 못 하면 이번 화면에서만 숨긴다 */ }
  return next;
}
/** 지금 주소에서 보여 줄 공지만 거른다. */
export function visibleNotices(items, pathname, dismissed = []) {
  return (Array.isArray(items) ? items : []).filter(item => item && item.message && !dismissed.includes(Number(item.seq)) && (item.placement !== 'HOME' || pathname === '/'));
}
