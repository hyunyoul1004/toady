import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAnnouncementLocation } from '@/features/announcement/location/useAnnouncementLocation';
import { isLandLease } from './priceGapRules';

const validUnits = (list = []) => (list || []).filter(unit => Number.isFinite(unit.amountManwon) && unit.amountManwon > 0 && unit.area >= 10);

/**
 * 공고의 주택형별 분양가와 주변 실거래 요약. 상단 요약과 아래 비교 카드가 같은 조회(같은 키)를 함께 쓴다.
 * 위치가 정확한 주소로 확인된 공고만 조회한다. 일부 달만 받은 결과(partial)는 1분만 캐시해 곧 다시 받는다.
 */
export function usePriceGap(detail) {
  const units = useMemo(() => validUnits(detail.unitPrices), [detail.unitPrices]);
  const capabilities = useQuery({ queryKey: ['researchCapabilities'], queryFn: () => apiClient.get('/api/v1/research/capabilities'), staleTime: 300000, retry: 1, enabled: units.length > 0 });
  const enabled = units.length > 0 && capabilities.data?.tradesEnabled === true;
  const location = useAnnouncementLocation(detail, enabled);
  const precise = location.data?.status === 'ADDRESS' && /^\d{10}$/.test(location.data?.bcode || '') ? location.data : null;
  const result = usePriceGapFor(units, enabled ? precise : null, detail.houseNm);
  return { ...result, enabled: enabled || result.landLease, loading: enabled && !result.landLease && (location.isLoading || result.loading) };
}

/**
 * 법정동(precise: { bcode, dong })이 확정된 곳의 주택형별 분양가 비교. 상세 탭과 위치 탭(이 공고·다른 분양 공고)이
 * 같은 조회·같은 키를 쓴다. precise가 없으면 조회하지 않는다. name: 공고 이름(토지임대부면 조회하지 않고 landLease로 알린다).
 */
export function usePriceGapFor(rawUnits, precise, name = '') {
  const units = useMemo(() => validUnits(rawUnits), [rawUnits]);
  const areas = useMemo(() => [...new Set(units.map(unit => Math.round(unit.area)))].sort((a, b) => a - b).slice(0, 8), [units]);
  const landLease = isLandLease(name);
  const valid = !landLease && Boolean(precise) && /^\d{10}$/.test(precise?.bcode || '') && Boolean(precise?.dong) && areas.length > 0;
  const gap = useQuery({ queryKey: ['priceGap', precise?.bcode, precise?.dong, areas.join(',')], enabled: valid,
    queryFn: () => apiClient.get('/api/v1/research/price-gap', { bcode: precise.bcode, dong: precise.dong, areas }),
    // 제공처 실패(UNAVAILABLE)는 캐시하지 않고 잠시 뒤 몇 번만 다시 묻는다. 일부 달만 받은 결과는 1분만 캐시한다.
    staleTime: query => (query.state.data?.status === 'UNAVAILABLE' ? 0 : query.state.data?.partial ? 60000 : 1800000), retry: false,
    refetchInterval: query => (query.state.data?.status === 'UNAVAILABLE' && query.state.dataUpdateCount < 4 ? 20000 : false) });
  const failed = valid && (gap.isError || gap.data?.status === 'UNAVAILABLE');
  return { units, enabled: valid, loading: valid && gap.isLoading, ready: valid && gap.data?.status === 'READY', failed, retry: gap.refetch, landLease, precise: valid ? precise : null, data: gap.data };
}
