import { isoDate, safeRead, koreaToday } from './plannerUtils.js';

/** 조회 기간은 달력의 렌더링 여부와 무관하게 항상 준비한다. */
export function monthRange(month) {
  if (!/^\d{4}-\d{2}$/.test(month) || !isoDate(`${month}-01`)) throw Error('유효하지 않은 조회 월');
  const [year, number] = month.split('-').map(Number);
  const start = new Date(Date.UTC(year, number - 1, -6));
  const end = new Date(Date.UTC(year, number, 14));
  return { startStr: start.toISOString().slice(0,10), endStr: end.toISOString().slice(0,10) };
}
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
export function shiftMonth(month, offset) {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0,7);
}

/** 일정이 없는 오늘을 반복 선택하지 않고, 현재 검색 결과에서 볼 수 있는 날짜를 선택한다. */
export function calendarSelection(month,eventDates,today=koreaToday(),previous='',fallback=`${month}-01`) {
  const dates=[...new Set(eventDates)].filter(date=>{
    if(!isoDate(date) || !date.startsWith(month+'-'))return false;
    const weekday=new Date(date+'T12:00:00Z').getUTCDay();
    return weekday!==0 && weekday!==6;
  }).sort();
  if(dates.includes(previous))return previous;
  if(month===today.slice(0,7))return dates.find(date=>date>=today) || dates.at(-1) || fallback;
  return dates[0] || fallback;
}

/** 고른 달(YYYY-MM)에 일정(inDate)이 있는 공고를 공고 단위로 한 번씩. 목록 보기의 건수·목록이 쓴다. */
export function uniqueInMonth(items = [], month = '') {
  return [...new Map(items.filter(x => String(x.inDate || '').startsWith(month)).map(x => [x.subscriptSeq, x])).values()];
}

const SUPPLY_CODES = ['01', '02', '03', '04', '05', '06', '07', '08'];
/** 공급유형 선택 저장값('YYNY…' 8자리) → { rceptSe01: 'Y', … }. 없거나 잘못됐거나 모두 꺼져 있으면 모두 선택. */
export function readSupplyTypes(storage) {
  const saved = safeRead(storage, 'toady.supplyTypes', null);
  const valid = typeof saved === 'string' && /^[YN]{8}$/.test(saved) && saved.includes('Y');
  return Object.fromEntries(SUPPLY_CODES.map((code, index) => [`rceptSe${code}`, valid ? saved[index] : 'Y']));
}
export function supplyTypesKey(paramInfo) {
  return SUPPLY_CODES.map(code => (paramInfo[`rceptSe${code}`] === 'N' ? 'N' : 'Y')).join('');
}
