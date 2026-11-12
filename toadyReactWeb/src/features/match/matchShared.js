import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { daysUntil } from '@/features/planner/plannerUtils';
import { formatManwon } from '@/features/planner/researchUtils';

/** 전국 접수 중·예정 공고와 자격 판정용 조건. 10분 캐시(서버도 같은 주기). */
export function useEligibilityFeed(enabled = true) {
  return useQuery({ queryKey: ['eligibilityFeed'], queryFn: () => apiClient.get('/api/v1/public/eligibility/announcements'),
    staleTime: 600000, retry: 1, enabled });
}

/** 카드의 접수 상태 문구: 접수 중이면 마감까지, 예정이면 시작까지. */
export function receiptBadge(item, today) {
  if (item.status === 'OPEN') {
    const left = daysUntil(item.end, today);
    return { tone: 'open', text: left === 0 ? '오늘 마감' : left > 0 ? `접수 중 · D-${left}` : '접수 중' };
  }
  const left = daysUntil(item.start, today);
  return { tone: 'upcoming', text: left === 1 ? '내일 접수' : left > 0 ? `접수 D-${left}` : '접수 예정' };
}

export function priceRange(units = []) {
  const amounts = units.map(unit => unit.amountManwon).filter(value => Number.isFinite(value) && value > 0);
  if (!amounts.length) return null;
  const min = Math.min(...amounts), max = Math.max(...amounts);
  return min === max ? formatManwon(max) : `${formatManwon(min)} ~ ${formatManwon(max)}`;
}

export const shortDate = date => (date ? date.slice(5).replace('-', '.') : '미정');
export const TYPE_LABEL = { apt: 'APT', remaining: '무순위·잔여세대', optional: '임의공급' };
