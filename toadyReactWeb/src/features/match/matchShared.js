/**
 * 맞춤 청약(MatchPage)과 공고 상세의 자격 카드(EligibilityCard)가 함께 쓰는 조회 훅과 표시 도우미.
 */
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { daysUntil } from '@/features/planner/plannerUtils';
import { formatManwon } from '@/features/planner/researchUtils';
import { RCEPT_SE_META } from '@/views/main/calendar/calendarGroup';

/**
 * 전국 접수 중·예정 공고와 자격 판정용 조건. 10분 캐시(서버도 같은 주기).
 * React Query: queryKey가 같으면 여러 화면이 한 번 받은 결과를 나눠 쓴다. staleTime(10분) 동안은 다시 요청하지 않는다.
 * enabled가 false면 요청 자체를 하지 않는다(상세 카드에서 판정 대상이 아닌 공고일 때).
 */
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

/** 주택형별 공급금액 중 최저~최고를 '3억 ~ 4.5억'처럼 만든다. 금액이 하나뿐이면 그 값만. */
export function priceRange(units = []) {
  const amounts = units.map(unit => unit.amountManwon).filter(value => Number.isFinite(value) && value > 0);
  if (!amounts.length) return null;
  const min = Math.min(...amounts), max = Math.max(...amounts);
  return min === max ? formatManwon(max) : `${formatManwon(min)} ~ ${formatManwon(max)}`;
}

// 'YYYY-MM-DD' → 'MM.DD'. 카드 안 접수 기간 표시에 쓴다.
export const shortDate = date => (date ? date.slice(5).replace('-', '.') : '미정');
// 공급 유형 이름은 달력·공급유형 필터와 같은 곳(RCEPT_SE_META)에서 가져온다.
export const TYPE_LABEL = { apt: 'APT', remaining: RCEPT_SE_META['06'].label, optional: RCEPT_SE_META['07'].label };
