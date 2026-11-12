/**
 * '오늘의 요약' 숫자 계산(순수 함수).
 * items: 전국 접수 중·예정 공고(자격 판정 목록), ranked: 진단 결과(rankFeed), winners: 서버가 센 이번 주 APT 발표 수.
 */
export function summaryNumbers({ items = [], ranked = [], winners = 0 } = {}) {
  const zupzup = items.filter(item => item.type !== 'apt');
  const possible = ranked.filter(({ result }) => result.best === 'ok' || result.best === 'check');
  return {
    winners: Number.isFinite(winners) ? winners : 0,
    zupzup: zupzup.length,
    zupzupOpen: zupzup.filter(item => item.status === 'OPEN').length,
    possible: possible.length,
    possibleOpen: possible.filter(({ item }) => item.status === 'OPEN').length
  };
}
