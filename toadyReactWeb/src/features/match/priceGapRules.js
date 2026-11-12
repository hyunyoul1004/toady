/**
 * 주택형(전용면적 반올림)별 최고 공급금액과 실거래 요약을 묶는다. diff = 실거래 중간값 − 분양 최고가(만원).
 * 양수면 분양가가 실거래보다 낮다. 거래가 없는 면적은 뺀다.
 */
export function gapRows(units = [], stats = []) {
  return stats.filter(stat => stat && stat.count > 0).map(stat => {
    const area = Math.round(Number(stat.area));
    const supply = Math.max(0, ...units.filter(unit => Math.round(unit.area) === area).map(unit => unit.amountManwon).filter(value => Number.isFinite(value) && value > 0));
    if (!supply) return null;
    return { area, supply, scope: stat.scope, count: stat.count, median: stat.median, newMedian: stat.newCount >= 2 ? stat.newMedian : 0, newCount: stat.newCount, diff: stat.median - supply };
  }).filter(Boolean);
}
