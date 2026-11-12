import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAnnouncementLocation } from '@/features/announcement/location/useAnnouncementLocation';

/**
 * 공고의 주택형별 분양가와 주변 실거래 요약. 상단 요약과 아래 비교 카드가 같은 조회(같은 키)를 함께 쓴다.
 * 위치가 정확한 주소로 확인된 공고만 조회한다. 일부 달만 받은 결과(partial)는 1분만 캐시해 곧 다시 받는다.
 */
export function usePriceGap(detail) {
  const units = useMemo(() => (detail.unitPrices || []).filter(unit => Number.isFinite(unit.amountManwon) && unit.amountManwon > 0 && unit.area >= 10), [detail.unitPrices]);
  const areas = useMemo(() => [...new Set(units.map(unit => Math.round(unit.area)))].sort((a, b) => a - b).slice(0, 8), [units]);
  const capabilities = useQuery({ queryKey: ['researchCapabilities'], queryFn: () => apiClient.get('/api/v1/research/capabilities'), staleTime: 300000, retry: 1, enabled: areas.length > 0 });
  const enabled = areas.length > 0 && capabilities.data?.tradesEnabled === true;
  const location = useAnnouncementLocation(detail, enabled);
  const precise = location.data?.status === 'ADDRESS' && /^\d{10}$/.test(location.data?.bcode || '') ? location.data : null;
  const gap = useQuery({ queryKey: ['priceGap', precise?.bcode, precise?.dong, areas.join(',')], enabled: Boolean(precise),
    queryFn: () => apiClient.get('/api/v1/research/price-gap', { bcode: precise.bcode, dong: precise.dong, areas }),
    staleTime: query => (query.state.data?.partial ? 60000 : 1800000), retry: false });
  const loading = enabled && (location.isLoading || (Boolean(precise) && gap.isLoading));
  const ready = Boolean(precise) && gap.data?.status === 'READY';
  return { units, enabled, loading, ready, precise, data: gap.data };
}
