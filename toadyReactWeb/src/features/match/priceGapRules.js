/**
 * 분양가와 주변 실거래 비교 규칙. 금액 단위는 만원.
 * - 기준: 새 아파트는 구축보다 비싸게 거래되므로, 같은 면적의 준공 10년 이내 거래가 3건 이상이면 그 값을 기본 기준으로 쓴다.
 * - 범위: 기준 거래의 가운데 절반(1~3사분위)과 분양 최고가의 차이. 단정하지 않도록 한 숫자 대신 범위로 보여준다.
 * - 신뢰도: 높음(같은 동·신축 5건 이상) · 보통(3건 이상) · 참고(그 밖). '참고'는 요약·목록에 쓰지 않는다.
 * diff = 시세 − 분양가. 양수면 분양가가 시세보다 낮다.
 */
export const MIN_NEW_TRADES = 3;

export function confidenceOf(row) {
  if (!row) return 'low';
  if (row.basis === 'new' && row.scope === 'DONG' && row.basisCount >= 5) return 'high';
  if (row.basisCount >= 3) return 'medium';
  return 'low';
}
export const CONFIDENCE_TEXT = { high: '신뢰도 높음', medium: '신뢰도 보통', low: '참고용' };

/** 주택형(전용면적 반올림)별 비교 행. basis: 'new'(10년 이내 준공) 또는 'all'. 거래가 없는 면적은 뺀다. */
export function gapRows(units = [], stats = [], preferred = 'auto') {
  return stats.filter(stat => stat && stat.count > 0).map(stat => {
    const area = Math.round(Number(stat.area));
    const supply = Math.max(0, ...units.filter(unit => Math.round(unit.area) === area).map(unit => unit.amountManwon).filter(value => Number.isFinite(value) && value > 0));
    if (!supply) return null;
    const hasNew = stat.newCount >= MIN_NEW_TRADES;
    const basis = preferred === 'all' || !hasNew ? 'all' : 'new';
    const median = basis === 'new' ? stat.newMedian : stat.median;
    const q1 = (basis === 'new' ? stat.newQ1 : stat.q1) || median;
    const q3 = (basis === 'new' ? stat.newQ3 : stat.q3) || median;
    const row = {
      area, supply, scope: stat.scope, count: stat.count, basis, hasNew, basisCount: basis === 'new' ? stat.newCount : stat.count,
      median, q1, q3, diff: median - supply, diffLow: q1 - supply, diffHigh: q3 - supply,
      newMedian: stat.newCount >= 2 ? stat.newMedian : 0, newCount: stat.newCount
    };
    return { ...row, confidence: confidenceOf(row) };
  }).filter(Boolean);
}

/** 요약에 쓸 대표 주택형: 신뢰도가 높은 순, 같으면 거래가 많은 순, 같으면 국민평형(84㎡)에 가까운 순. '참고'만 있으면 null. */
export function representative(rows = []) {
  const rank = { high: 2, medium: 1, low: 0 };
  const usable = rows.filter(row => row.confidence !== 'low');
  if (!usable.length) return null;
  return [...usable].sort((a, b) => rank[b.confidence] - rank[a.confidence] || b.basisCount - a.basisCount || Math.abs(a.area - 84) - Math.abs(b.area - 84))[0];
}

/** 1천만원 단위로 반올림한 억 표기: 12,345 → '1.2억', 4,800 → '4,800만원'(1억 미만은 천만 단위 만원). */
export function roughEok(manwon) {
  const value = Math.abs(Math.round(manwon / 1000) * 1000);
  if (value < 10000) return value === 0 ? '0원' : `${value.toLocaleString('ko-KR')}만원`;
  return `${(value / 10000).toFixed(1).replace(/\.0$/, '')}억`;
}

/** "약 1.0억~1.4억 낮음" 같은 범위 문구. 범위가 0을 가로지르면 '비슷해요'. */
export function gapText(row) {
  if (!row) return '';
  const low = Math.min(row.diffLow, row.diffHigh), high = Math.max(row.diffLow, row.diffHigh);
  if (low < 0 && high > 0) return '주변 시세와 비슷해요';
  const below = high > 0; // 시세 − 분양가가 양수 → 분양가가 낮다
  const a = roughEok(Math.abs(below ? low : high)), b = roughEok(Math.abs(below ? high : low));
  return `${a === b ? `약 ${a}` : `약 ${a}~${b}`} ${below ? '낮아요' : '높아요'}`;
}
