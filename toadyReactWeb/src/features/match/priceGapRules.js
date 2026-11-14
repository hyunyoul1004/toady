/**
 * 분양가와 주변 실거래 비교 규칙. 금액 단위는 만원.
 * - 기준: 새 아파트는 구축보다 비싸게 거래되므로, 같은 면적의 준공 10년 이내 거래가 3건 이상이면 그 값을 기본 기준으로 쓴다.
 * - 범위: 기준 거래의 가운데 절반(1~3사분위)과 분양 최고가의 차이. 단정하지 않도록 한 숫자 대신 범위로 보여준다.
 * - 신뢰도: 높음(같은 동·신축 5건 이상) · 보통(3건 이상) · 참고(그 밖). '참고'는 요약·목록에 쓰지 않는다.
 * diff = 시세 − 분양가. 양수면 분양가가 시세보다 낮다.
 */
const MIN_NEW_TRADES = 3;
/**
 * 토지임대부 분양은 건물만 분양하고 토지는 임대(월 임대료 별도)라 일반 아파트 매매가와 직접 비교하지 않는다.
 * 서버 PriceGapRules.landLease와 같은 규칙: 주택명에 '토지임대부'(띄어쓰기 무관)가 있으면 제외.
 */
export const isLandLease = name => /토지\s*임대\s*부/.test(String(name || ''));
export const LAND_LEASE_TEXT = '토지임대부 공고라 주변 아파트 매매가와 직접 비교하지 않아요. 토지는 임대(월 임대료 별도)라 같은 기준이 아니에요.';

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

/**
 * 비교 결과의 방향. below: 시세 범위 전체가 분양가보다 높음(분양가가 낮음), above: 전체가 낮음,
 * same: 범위가 분양가에 걸치거나 차이가 1천만원 미만으로 반올림됨. 서버 PriceGapRules.tone과 같은 규칙이다.
 */
export function gapTone(row) {
  if (!row) return 'same';
  const low = Math.min(row.diffLow, row.diffHigh), high = Math.max(row.diffLow, row.diffHigh);
  if (Math.round(Math.max(Math.abs(low), Math.abs(high)) / 1000) === 0) return 'same';
  if (low > 0) return 'below';
  if (high < 0) return 'above';
  return 'same';
}

/** "약 1.0억~1.4억 낮아요" 같은 범위 문구. 범위가 0을 가로지르면 '주변 시세와 비슷해요'. 가까운 쪽이 1천만원 미만이면 "최대 약 …". */
export function gapText(row) {
  if (!row) return '';
  const tone = gapTone(row);
  if (tone === 'same') return '주변 시세와 비슷해요';
  const low = Math.min(row.diffLow, row.diffHigh), high = Math.max(row.diffLow, row.diffHigh);
  const below = tone === 'below';
  const a = roughEok(Math.abs(below ? low : high)), b = roughEok(Math.abs(below ? high : low));
  const word = below ? '낮아요' : '높아요';
  if (a === '0원') return `최대 약 ${b} ${word}`;
  return `${a === b ? `약 ${a}` : `약 ${a}~${b}`} ${word}`;
}

/**
 * 미리 계산한 주변 시세 비교 뱃지 문구. 서버 text: '약 1.0억~1.4억 낮음' 또는 '비슷'.
 * 신뢰도 '참고'는 서버가 내려주지 않는다.
 */
export function gapBadge(gap) {
  if (!gap || !gap.text) return null;
  const label = `주변 ${gap.basis === 'new' ? '신축 ' : ''}시세`;
  return { tone: gap.tone, text: gap.text === '비슷' ? `${label}와 비슷` : `${label} 대비 ${gap.text}` };
}
/** 줍줍 정렬: 'gap'이면 시세보다 많이 낮은 순(비교가 없는 공고는 뒤), 아니면 접수 상태·날짜 순. */
export function sortZupzup(items = [], sort = 'date') {
  const byDate = (a, b) => (a.status === 'OPEN' ? 0 : 1) - (b.status === 'OPEN' ? 0 : 1)
    || String(a.status === 'OPEN' ? a.end : a.start).localeCompare(String(b.status === 'OPEN' ? b.end : b.start));
  if (sort !== 'gap') return [...items].sort(byDate);
  const saving = item => (item.gap ? Math.min(item.gap.diffLow, item.gap.diffHigh) : Number.NEGATIVE_INFINITY);
  return [...items].sort((a, b) => saving(b) - saving(a) || byDate(a, b));
}
