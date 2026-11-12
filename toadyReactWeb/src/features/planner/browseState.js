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
  const month = typeof savedMonth === 'string' && /^\d{4}-\d{2}$/.test(savedMonth) && isoDate(`${savedMonth}-01`)
    ? savedMonth : today.slice(0,7);
  const savedView=safeRead(storage,'toady.viewMode',null);
  const viewMode=['calendar','list'].includes(savedView)?savedView:preferredView==='list'?'list':'calendar';
  return { month, viewMode, ...monthRange(month) };
}
export function shiftMonth(month, offset) {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0,7);
}
