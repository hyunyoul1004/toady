/** 검색어 통계로 보낼지: 확정된 검색어만, 숫자만 긴 값(전화번호·계좌 등)은 빼고, 같은 검색어는 이 탭에서 10분에 한 번만. */
const sent = new Map();
export function shouldTrackSearch(term, now = Date.now()) {
  const text = String(term || '').trim().replace(/\s+/g, ' ').toLowerCase();
  if (text.length < 2 || text.length > 60) return null;
  if (/^[\d\s-]{6,}$/.test(text)) return null;
  const last = sent.get(text);
  if (last !== undefined && now - last < 600000) return null;
  sent.set(text, now);
  return text;
}
