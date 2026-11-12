import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Skeleton } from '@mui/material';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import apiClient from '@/api/client';
import { useAnnouncementLocation } from '@/features/announcement/location/useAnnouncementLocation';
import { formatManwon } from '@/features/planner/researchUtils';
import { gapRows } from './priceGapRules';
import './match.css';

/**
 * 주택형별 최고 공급금액과 주변 실거래 중간값(최근 6개월, 전용 ±5㎡)을 나란히 놓고 차이를 보여준다.
 * 위치가 정확한 주소로 확인된 공고만 조회한다(지구·블록 후보 위치로는 비교하지 않는다).
 */
export default function PriceGapCard({ detail, onShowTrades }) {
  const units = useMemo(() => (detail.unitPrices || []).filter(unit => Number.isFinite(unit.amountManwon) && unit.amountManwon > 0 && unit.area >= 10), [detail.unitPrices]);
  const areas = useMemo(() => [...new Set(units.map(unit => Math.round(unit.area)))].sort((a, b) => a - b).slice(0, 8), [units]);
  const capabilities = useQuery({ queryKey: ['researchCapabilities'], queryFn: () => apiClient.get('/api/v1/research/capabilities'), staleTime: 300000, retry: 1, enabled: areas.length > 0 });
  const enabled = areas.length > 0 && capabilities.data?.tradesEnabled === true;
  const location = useAnnouncementLocation(detail, enabled);
  const precise = location.data?.status === 'ADDRESS' && /^\d{10}$/.test(location.data?.bcode || '') ? location.data : null;
  const gap = useQuery({ queryKey: ['priceGap', precise?.bcode, precise?.dong, areas.join(',')], enabled: Boolean(precise),
    queryFn: () => apiClient.get('/api/v1/research/price-gap', { bcode: precise.bcode, dong: precise.dong, areas }), staleTime: 1800000, retry: false });
  if (!enabled) return null;
  if (location.isLoading || gap.isLoading) return <section className="match-gap" aria-busy="true"><Skeleton variant="text" width="40%" /><Skeleton variant="rounded" height={90} /></section>;
  // 정확한 위치를 확인하지 못했거나 거래 자료가 없으면 비교 카드를 숨긴다(동네 실거래 탭에서 이유를 안내한다).
  if (!precise || gap.data?.status !== 'READY') return null;
  const rows = gapRows(units, gap.data.areas);
  if (!rows.length) return null;
  return <section className="match-gap" aria-labelledby="match-gap-heading">
    <h3 id="match-gap-heading"><CompareArrowsRounded />분양가 vs 주변 실거래</h3>
    <p className="match-gap-sub">최근 6개월 {gap.data.fromMonth.replace('-', '.')} ~ {gap.data.toMonth.replace('-', '.')} · 같은 면적(전용 ±5㎡) 매매 중간값</p>
    <div className="match-gap-rows">{rows.map(row => <div key={row.area} className="match-gap-row">
      <div className="match-gap-area"><strong>전용 {row.area}㎡</strong><span>{row.scope === 'DONG' ? precise.dong : '같은 시·군·구'} {row.count}건</span></div>
      <div className="match-gap-values">
        <span>분양 최고 <b>{formatManwon(row.supply)}</b></span>
        <span>실거래 중간 <b>{formatManwon(row.median)}</b></span>
        {row.newMedian > 0 && <span className="is-new">10년 이내 준공 <b>{formatManwon(row.newMedian)}</b> ({row.newCount}건)</span>}
      </div>
      <span className={`match-gap-diff is-${row.diff > 0 ? 'below' : row.diff < 0 ? 'above' : 'same'}`}>
        {row.diff === 0 ? '비슷해요' : `실거래보다 ${formatManwon(Math.abs(row.diff))} ${row.diff > 0 ? '낮아요' : '높아요'}`}
      </span>
    </div>)}</div>
    <p className="match-gap-note">기존 아파트 거래라 연식·입지·옵션이 달라요. 시세 차익을 보장하지 않는 참고 비교예요.{onShowTrades && <> <Button size="small" onClick={onShowTrades}>거래 목록 보기</Button></>}</p>
  </section>;
}
