/** 이번 주 청약·지역별 청약 화면이 같이 쓰는 표시 규칙(유형 이름, 날짜 문구, 접수 상태 정렬). 쓰는 곳: ThisWeekPage, WeeklyPicks, nextEventRules. */
import { RCEPT_SE_META } from '../../views/main/calendar/calendarGroup.js';
import { appliesToday } from '../announcement/applyDays.js';

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
/**
 * 이번 주 접수 카드의 상태 표시. 접수 기간 중(OPEN)이면 오늘 신청할 수 있는지로 한 번 더 나눈다.
 * - 오늘 신청: APT 특별공급·순위 접수일이거나, 그 밖의 유형 접수 기간 중
 * - 접수 기간 중: APT 순위 접수일 사이(예: 1순위 끝, 2순위 전). note에 다음 신청일을 담는다.
 * 반환: { key: CSS 이름(is-…), text: 뱃지 문구, note: 보조 문구(없으면 '') }
 */
export function weeklyStatus(item = {}) {
  if (item.status !== 'OPEN') return { key: String(item.status || '').toLowerCase(), text: STATUS_TEXT[item.status] || '', note: '' };
  if (appliesToday(item)) return { key: 'open', text: '오늘 신청', note: '' };
  return { key: 'between', text: '접수 기간 중', note: item.nextApplyDay ? `다음 신청일 ${dayLabel(item.nextApplyDay)}` : '오늘은 신청일이 아니에요' };
}
/** 이번 주 접수 목록 나누기: 오늘 신청 → 접수 기간 중 → 예정 → 이번 주 마감 순. */
export function byStatus(items = []) {
  const order = { OPEN: 0, UPCOMING: 2, CLOSED: 3 };
  const rank = item => (order[item.status] ?? 4) + (item.status === 'OPEN' && !appliesToday(item) ? 1 : 0);
  return [...items].sort((a, b) => rank(a) - rank(b) || String(a.start).localeCompare(String(b.start)));
}
