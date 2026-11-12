import { RCEPT_SE_META } from '../../views/main/calendar/calendarGroup.js';

/** 공고 유형 이름: 달력·공급유형 필터와 같은 이름(RCEPT_SE_META)을 쓴다. */
export const TYPE_NAME = {
  apt: 'APT',
  urban: RCEPT_SE_META['05'].label,
  remaining: RCEPT_SE_META['06'].label,
  'public-rental': RCEPT_SE_META['04'].label,
  optional: RCEPT_SE_META['07'].label
};

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];
/** '2026-11-13' → '11.13 (금)'. 날짜가 없으면 '미정'. */
export function dayLabel(iso) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(iso || '')) return '미정';
  const [y, m, d] = iso.split('-').map(Number);
  return `${m}.${String(d).padStart(2, '0')} (${WEEKDAY[new Date(Date.UTC(y, m - 1, d)).getUTCDay()]})`;
}
/** 접수 기간 문구: 하루면 한 날짜만. */
export function periodLabel(start, end) {
  return !end || end === start ? dayLabel(start) : `${dayLabel(start)} ~ ${dayLabel(end)}`;
}
export const STATUS_TEXT = { OPEN: '접수 중', UPCOMING: '접수 예정', CLOSED: '마감' };
/** 이번 주 접수 목록 나누기: 접수 중 → 예정 → 이번 주 마감 순. */
export function byStatus(items = []) {
  const order = { OPEN: 0, UPCOMING: 1, CLOSED: 2 };
  return [...items].sort((a, b) => (order[a.status] ?? 3) - (order[b.status] ?? 3) || String(a.start).localeCompare(String(b.start)));
}
