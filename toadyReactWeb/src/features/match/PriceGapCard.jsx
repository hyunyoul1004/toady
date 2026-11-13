import { useState } from 'react';
import { Button, Skeleton, ToggleButton, ToggleButtonGroup } from '@mui/material';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import TrendingDownRounded from '@mui/icons-material/TrendingDownRounded';
import { formatManwon } from '@/features/planner/researchUtils';
import { CONFIDENCE_TEXT, LAND_LEASE_TEXT, gapRows, gapText, gapTone, representative } from './priceGapRules';
import { usePriceGap } from './usePriceGap';
import './match.css';

const periodText = data => `최근 ${data.months || 6}개월(${data.fromMonth.replace('-', '.')} ~ ${data.toMonth.replace('-', '.')})`;
const basisText = row => (row.basis === 'new' ? '준공 10년 이내' : '전체 연식');
const scopeText = (row, dong) => (row.scope === 'DONG' ? dong : '같은 시·군·구');

/**
 * 상세 화면 맨 위의 한 줄 요약: "주변 신축 시세 대비 약 1.0억~1.4억 낮아요 · 전용 84㎡ · 신뢰도 높음".
 * 신뢰도가 '참고'뿐이거나 비교할 수 없으면 아무것도 보여주지 않는다. 누르면 아래 면적별 비교로 이동한다.
 */
export function PriceGapSummary({ detail, onMore }) {
  const gap = usePriceGap(detail);
  if (!gap.enabled) return null;
  if (gap.landLease) return <div className="match-gap-summary is-same" role="note"><span><b>주변 시세 비교 제외</b><small>{LAND_LEASE_TEXT}</small></span></div>;
  if (gap.loading) return <div className="match-gap-summary is-loading" aria-busy="true"><Skeleton variant="text" width="70%" /></div>;
  if (!gap.ready) return null;
  const row = representative(gapRows(gap.units, gap.data.areas));
  if (!row) return null;
  const tone = gapTone(row);
  return <button type="button" className={`match-gap-summary is-${tone}`} onClick={onMore}>
    <TrendingDownRounded fontSize="small" />
    <span><b>{tone === 'same' ? `주변 ${row.basis === 'new' ? '신축 ' : ''}시세와 비슷해요` : `주변 ${row.basis === 'new' ? '신축 ' : ''}시세 대비 ${gapText(row)}`}</b>
      <small>전용 {row.area}㎡ 기준 · {scopeText(row, gap.precise.dong)} {basisText(row)} {row.basisCount}건 · {CONFIDENCE_TEXT[row.confidence]}</small></span>
    <em>자세히</em>
  </button>;
}

/**
 * 주택형별 최고 공급금액과 주변 실거래(최근 최대 6개월, 전용 ±5㎡)를 나란히 놓고 차이를 범위로 보여준다.
 * 기준은 준공 10년 이내 거래(3건 이상일 때)가 기본이며 전체 연식으로 바꿔 볼 수 있다.
 */
export default function PriceGapCard({ detail, onShowTrades, id }) {
  const gap = usePriceGap(detail);
  const [basis, setBasis] = useState('auto');
  if (!gap.enabled) return null;
  if (gap.loading) return <section className="match-gap" aria-busy="true"><Skeleton variant="text" width="40%" /><Skeleton variant="rounded" height={90} /></section>;
  if (gap.landLease) return <section className="match-gap" id={id} aria-labelledby="match-gap-heading"><h3 id="match-gap-heading"><CompareArrowsRounded />분양가 vs 주변 시세</h3><p className="match-gap-note">{LAND_LEASE_TEXT}</p></section>;
  if (gap.failed) return <section className="match-gap" id={id} aria-labelledby="match-gap-heading"><h3 id="match-gap-heading"><CompareArrowsRounded />분양가 vs 주변 시세</h3><p className="match-gap-note">주변 실거래를 지금 불러오지 못했어요. <Button size="small" onClick={() => gap.retry()}>다시 시도</Button></p></section>;
  if (!gap.ready) return null;
  const rows = gapRows(gap.units, gap.data.areas, basis);
  if (!rows.length) return null;
  const anyNew = rows.some(row => row.hasNew);
  return <section className="match-gap" id={id} aria-labelledby="match-gap-heading">
    <h3 id="match-gap-heading"><CompareArrowsRounded />분양가 vs 주변 시세</h3>
    <p className="match-gap-sub">{periodText(gap.data)} · 같은 면적(전용 ±5㎡) 매매의 가운데 절반 범위와 비교{gap.data.partial ? ' · 일부 기간만 반영' : ''}</p>
    {anyNew && <ToggleButtonGroup exclusive size="small" value={basis === 'all' ? 'all' : 'auto'} onChange={(_, value) => value && setBasis(value)} className="match-gap-basis" aria-label="비교 기준">
      <ToggleButton value="auto">신축 기준</ToggleButton><ToggleButton value="all">전체 연식</ToggleButton>
    </ToggleButtonGroup>}
    <div className="match-gap-rows">{rows.map(row => <div key={row.area} className="match-gap-row">
      <div className="match-gap-area"><strong>전용 {row.area}㎡</strong><span>{scopeText(row, gap.precise.dong)} · {basisText(row)} {row.basisCount}건</span></div>
      <div className="match-gap-values">
        <span>분양 최고 <b>{formatManwon(row.supply)}</b></span>
        <span>시세 범위 <b>{formatManwon(row.q1)}{row.q1 !== row.q3 ? ` ~ ${formatManwon(row.q3)}` : ''}</b></span>
        <span className="is-new">{CONFIDENCE_TEXT[row.confidence]}</span>
      </div>
      <span className={`match-gap-diff is-${gapTone(row)}`}>{gapText(row)}</span>
    </div>)}</div>
    <p className="match-gap-note">실제 차익을 보장하지 않는 참고 비교예요. 발코니 확장·옵션·중도금 이자와 전매제한·실거주 의무는 반영하지 않았어요.{onShowTrades && <> <Button size="small" onClick={onShowTrades}>거래 목록 보기</Button></>}</p>
  </section>;
}
