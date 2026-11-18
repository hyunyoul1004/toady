import { appliesToday } from '../announcement/applyDays.js';
import { isWeekendDay } from './plannerUtils.js';

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

/**
 * '오늘의 요약' 날짜·주말 문구의 기준일. 숫자(applyToday)는 10분 캐시된 weekly-summary가 서버 날짜(today)로 계산하므로,
 * 응답에 today('YYYY-MM-DD')가 있으면 그 날짜를 써서 자정 직후에도 날짜 문구와 숫자의 기준일을 맞춘다. 없으면 기기 기준 fallback.
 */
export function summaryDay(weekly, fallback) {
  const day = weekly?.today;
  return typeof day === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(day) ? day : fallback;
}

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
/** '2026-11-13' → '11월 13일 (금)'. 기기 시간대와 상관없이 한국 날짜 문자열 그대로 계산한다. */
export function todayLabel(iso) {
  const [y, m, d] = String(iso).split('-').map(Number);
  if (!y || !m || !d) return '';
  return `${m}월 ${d}일 (${WEEKDAY[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`;
}

/**
 * '오늘 접수' 칸 글자. 숫자(openToday)는 요일과 상관없이 같은 판정(오늘 신청 가능한 공고)이다.
 * 주말(KST 토·일)에는 청약 접수가 보통 평일이라 '오늘 접수 4'가 오늘 새로 넣을 수 있는 것처럼 읽히지 않게
 * '주말 접수 기간 중'으로 바꾸고 아래 글로 평일 접수를 알린다. 평일은 예전과 같다.
 */
export function openTodayText(iso) {
  if (isWeekendDay(iso)) return { label: '주말 접수 기간 중', note: '접수는 보통 평일에 받아요' };
  return { label: '오늘 접수', note: '오늘 신청할 수 있는 공고' };
}

/**
 * 메인 검색·필터가 걸려 있는지(공급유형 일부만, 공급지역 일부만, 검색 대상 있음). 걸려 있으면 '오늘의 요약'에 '전국 기준'을 붙여
 * 요약 숫자(전국)가 필터된 달력 칸 건수와 다른 이유를 알린다. 공급유형은 rceptSe01~08 값이 'Y'인지로 본다.
 */
export function browseFiltered({ paramInfo = {}, selectedRegionCount, totalRegionCount } = {}) {
  const supplyNarrowed = Array.from({ length: 8 }, (_, index) => `rceptSe${String(index + 1).padStart(2, '0')}`)
    .some((key) => paramInfo[key] != null && paramInfo[key] !== 'Y');
  const regionNarrowed = Number.isFinite(selectedRegionCount) && Number.isFinite(totalRegionCount) && selectedRegionCount !== totalRegionCount;
  const searching = Array.isArray(paramInfo.citys) && paramInfo.citys.length > 0;
  return supplyNarrowed || regionNarrowed || searching;
}
