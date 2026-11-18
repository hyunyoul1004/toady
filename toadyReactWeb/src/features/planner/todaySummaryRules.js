import { appliesToday } from '../announcement/applyDays.js';

/**
 * '오늘의 요약' 숫자 계산(순수 함수).
 * items: 전국 접수 중·예정 공고(자격 판정 목록), ranked: 진단 결과(rankFeed),
 * weekly: 이번 주 청약 데이터(/weekly-summary). 오늘 접수 = 이번 주 접수 중 오늘 실제로 신청할 수 있는 공고(전국·전 유형).
 *   APT는 특별공급·순위 접수일에만, 그 밖의 유형은 접수 기간 내내 센다(applyDays.js, 서버 applyToday). 달력 오늘 칸과 같은 판정이다.
 * 쓰는 곳: TodaySummary.jsx.
 */
export function summaryNumbers({ items = [], ranked = [], weekly = null } = {}) {
  // APT가 아닌 공고를 무순위·줍줍으로 센다. possible은 진단 결과가 가능(ok)·확인 필요(check)인 공고.
  const zupzup = items.filter(item => item.type !== 'apt');
  const possible = ranked.filter(({ result }) => result.best === 'ok' || result.best === 'check');
  return {
    openToday: (weekly?.current || []).filter(appliesToday).length,
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
