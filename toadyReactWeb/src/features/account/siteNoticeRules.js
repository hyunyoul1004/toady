/** 사이트 공지 노출 규칙: 첫 화면 전용(HOME)은 '/'에서만, 닫은 공지는 숨긴다. 닫은 기록은 이 브라우저에 최근 50개까지 둔다. */
const KEY = 'toady.notices.dismissed';
export function readDismissed(storage = globalThis.localStorage) {
  try { const list = JSON.parse(storage.getItem(KEY) || '[]'); return Array.isArray(list) ? list.filter(Number.isFinite) : []; } catch { return []; }
}
export function dismissNotice(seq, storage = globalThis.localStorage) {
  const next = [...new Set([Number(seq), ...readDismissed(storage)])].slice(0, 50);
  try { storage.setItem(KEY, JSON.stringify(next)); } catch { /* 저장 못 하면 이번 화면에서만 숨긴다 */ }
  return next;
}
export function visibleNotices(items, pathname, dismissed = []) {
  return (Array.isArray(items) ? items : []).filter(item => item && item.message && !dismissed.includes(Number(item.seq)) && (item.placement !== 'HOME' || pathname === '/'));
}
