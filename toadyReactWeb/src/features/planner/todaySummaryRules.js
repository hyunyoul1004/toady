/**
 * '오늘의 요약' 숫자 계산(순수 함수).
 * items: 전국 접수 중·예정 공고(자격 판정 목록), ranked: 진단 결과(rankFeed),
 * weekly: 이번 주 청약 데이터(/weekly-summary). 오늘 접수 = 이번 주 접수 중 지금 접수 중인 공고(전국·전 유형).
 */
export function summaryNumbers({ items = [], ranked = [], weekly = null } = {}) {
  const zupzup = items.filter(item => item.type !== 'apt');
  const possible = ranked.filter(({ result }) => result.best === 'ok' || result.best === 'check');
  return {
    openToday: (weekly?.current || []).filter(item => item.status === 'OPEN').length,
    winners: (weekly?.winners || []).length,
    zupzup: zupzup.length,
    zupzupOpen: zupzup.filter(item => item.status === 'OPEN').length,
    possible: possible.length,
    possibleOpen: possible.filter(({ item }) => item.status === 'OPEN').length
  };
}

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
/** '2026-11-13' → '11월 13일 (금)'. 기기 시간대와 상관없이 한국 날짜 문자열 그대로 계산한다. */
export function todayLabel(iso) {
  const [y, m, d] = String(iso).split('-').map(Number);
  if (!y || !m || !d) return '';
  return `${m}월 ${d}일 (${WEEKDAY[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`;
}
