import { daysUntil, isoDate, upcomingGroups } from './plannerUtils.js';
import { byStatus } from '../explore/exploreRules.js';

/** 일정 이름을 짧은 행동 문구로: '1순위 (해당·경기지역)' → '1순위 접수', '특별공급 시작' → '특별공급 접수'. */
export function eventAction(label) {
  const text = String(label || '').trim();
  const rank = text.match(/^([12])순위/);
  if (rank) return `${rank[1]}순위 접수`;
  if (/^특별공급/.test(text)) return '특별공급 접수';
  return text;
}
/** 남은 날짜를 말로: 오늘·내일·모레, 같은 달이면 '25일', 다른 달이면 '12월 3일'. */
export function dayWord(date, today) {
  const days = daysUntil(date, today);
  if (days === null || days < 0) return '';
  if (days === 0) return '오늘';
  if (days === 1) return '내일';
  if (days === 2) return '모레';
  const [, m, d] = date.split('-').map(Number);
  return date.slice(0, 7) === today.slice(0, 7) ? `${d}일` : `${m}월 ${d}일`;
}

/**
 * 저장한 공고 전체에서 가장 가까운 다가오는 일정 하나(나의 청약·메인 '오늘의 요약'이 같이 쓴다).
 * 같은 날 여러 공고가 겹치면 공고 ID 순으로 첫 공고를 보여 주고 나머지 수를 함께 돌려준다.
 */
export function nextPlannerEvent(items = [], today) {
  if (!isoDate(today)) return null;
  const groups = upcomingGroups(Array.isArray(items) ? items.filter(item => item?.summary) : [], today);
  if (!groups.length) return null;
  const { item, events } = groups[0];
  const event = events[0];
  const actions = [...new Set(event.labels.map(eventAction))];
  const action = actions.length > 2 ? `${actions.slice(0, 2).join(' · ')} 외` : actions.join(' · ');
  const sameDay = groups.filter(group => group.events[0].date === event.date).length - 1;
  return {
    id: item.id, houseNm: item.summary.houseNm || '공고', date: event.date, days: daysUntil(event.date, today),
    when: dayWord(event.date, today), action, othersSameDay: sameDay,
    text: `${dayWord(event.date, today)} ${action}`
  };
}

/** 나의 청약 빈 화면 추천: 이번 주 접수 중인 공고를 먼저, 모자라면 접수 예정으로 채운다(이미 저장한 공고 제외). */
export function weeklyPicks(weekly, savedIds = [], limit = 3) {
  const saved = new Set(savedIds);
  return byStatus(weekly?.current || []).filter(item => item?.id && item.status !== 'CLOSED' && !saved.has(item.id)).slice(0, limit);
}
