/**
 * 메인 청약 캘린더의 '보던 상태' 복원 도우미. 보던 달, 보기 방식(달력/목록), 공급유형 선택을 localStorage에서 읽는다.
 * 쓰는 곳: views/main/Main.jsx, MobileApplyhomeCalendar.jsx, TodayOverview.jsx(shiftMonth·uniqueInMonth).
 * 날짜는 한국 날짜 문자열(YYYY-MM-DD)로 다루고, Date는 UTC로만 계산해 기기 시간대에 흔들리지 않게 한다.
 */
import { isoDate, safeRead, koreaToday } from './plannerUtils.js';

/** 조회 기간은 달력의 렌더링 여부와 무관하게 항상 준비한다. */
// 달력에는 앞뒤 달의 날짜 칸도 보이므로, 그 달 1일의 7일 전부터 다음 달 14일까지를 조회 범위로 잡는다.
// (Date.UTC의 날짜 자리에 0 이하를 넣으면 앞 달로 넘어간다. -6이면 1일의 7일 전.)
export function monthRange(month) {
  if (!/^\d{4}-\d{2}$/.test(month) || !isoDate(`${month}-01`)) throw Error('유효하지 않은 조회 월');
  const [year, number] = month.split('-').map(Number);
  const start = new Date(Date.UTC(year, number - 1, -6));
  const end = new Date(Date.UTC(year, number, 14));
  return { startStr: start.toISOString().slice(0,10), endStr: end.toISOString().slice(0,10) };
}
/** 처음 화면에 쓸 달·보기 방식·조회 범위. storage는 localStorage(테스트에서는 가짜 저장소)를 받는다. */
export function initialBrowseState(storage, today = koreaToday(), preferredView = 'calendar') {
  const savedMonth = safeRead(storage, 'toady.calendarMonth', null);
  const valid = typeof savedMonth === 'string' && /^\d{4}-\d{2}$/.test(savedMonth) && isoDate(`${savedMonth}-01`);
  // 같은 날 메뉴를 오가면 보던 달을 그대로 복원하고, 다른 날 다시 오면 지난 달 대신 이번 달부터 보여 준다
  // ('오늘 접수'처럼 오늘 기준 정보가 예전 달 조회 범위에 묶이지 않게).
  const savedOn = safeRead(storage, 'toady.calendarMonthDay', null);
  const stale = valid && savedMonth < today.slice(0,7) && savedOn !== today;
  const month = valid && !stale ? savedMonth : today.slice(0,7);
  const savedView=safeRead(storage,'toady.viewMode',null);
  const viewMode=['calendar','list'].includes(savedView)?savedView:preferredView==='list'?'list':'calendar';
  return { month, viewMode, ...monthRange(month) };
}
/** 'YYYY-MM'에서 offset 달만큼 옮긴 달. UTC로 계산해 말일·시간대 문제를 피한다. */
export function shiftMonth(month, offset) {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0,7);
}

/** 일정이 없는 오늘을 반복 선택하지 않고, 현재 검색 결과에서 볼 수 있는 날짜를 선택한다. */
export function calendarSelection(month,eventDates,today=koreaToday(),previous='',fallback=`${month}-01`) {
  const dates=[...new Set(eventDates)].filter(date=>{
    if(!isoDate(date) || !date.startsWith(month+'-'))return false;
    // 주말 날짜는 고르지 않는다. 요일은 정오(UTC)로 만든 Date로 구해 시간대 영향을 받지 않게 한다.
    const weekday=new Date(date+'T12:00:00Z').getUTCDay();
    return weekday!==0 && weekday!==6;
  }).sort();
  if(dates.includes(previous))return previous;
  if(month===today.slice(0,7))return dates.find(date=>date>=today) || dates.at(-1) || fallback;
  return dates[0] || fallback;
}

// 'YYYY-MM-DD'(또는 'YYYYMMDD')가 토·일요일인지. 기기 시간대와 무관하게 UTC로 계산한다.
const isWeekend = value => {
  const digits = String(value || '').replace(/\D/g, '').slice(0, 8);
  if (digits.length !== 8) return false;
  const day = new Date(Date.UTC(Number(digits.slice(0, 4)), Number(digits.slice(4, 6)) - 1, Number(digits.slice(6, 8)))).getUTCDay();
  return day === 0 || day === 6;
};
/**
 * 고른 달(YYYY-MM)에 일정(inDate)이 있는 공고를 공고 단위로 한 번씩. 목록 보기의 건수·목록이 쓴다.
 * weekdaysOnly: 달력(데스크톱·모바일 모두 평일 칸만 그림)에 보이는 공고만 센다. 주말에만 일정이 있는 공고는 달력에 없으므로 빼고 센다.
 */
export function uniqueInMonth(items = [], month = '', { weekdaysOnly = false } = {}) {
  const inMonth = items.filter(x => String(x.inDate || '').startsWith(month) && !(weekdaysOnly && isWeekend(x.inDate)));
  return [...new Map(inMonth.map(x => [x.subscriptSeq, x])).values()];
}

const SUPPLY_CODES = ['01', '02', '03', '04', '05', '06', '07', '08'];
/** 공급유형 선택 저장값('YYNY…' 8자리) → { rceptSe01: 'Y', … }. 없거나 잘못됐거나 모두 꺼져 있으면 모두 선택. */
export function readSupplyTypes(storage) {
  const saved = safeRead(storage, 'toady.supplyTypes', null);
  const valid = typeof saved === 'string' && /^[YN]{8}$/.test(saved) && saved.includes('Y');
  return Object.fromEntries(SUPPLY_CODES.map((code, index) => [`rceptSe${code}`, valid ? saved[index] : 'Y']));
}
/** 공급유형 선택 → 저장용 8자리 문자열('YYNY…'). readSupplyTypes의 반대 방향이다. */
export function supplyTypesKey(paramInfo) {
  return SUPPLY_CODES.map(code => (paramInfo[`rceptSe${code}`] === 'N' ? 'N' : 'Y')).join('');
}
