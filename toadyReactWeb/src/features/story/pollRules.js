/** 투표 응답을 화면용으로 정리한다. 비율은 반올림해도 합이 100을 넘지 않게 표시용으로만 쓴다. */
export function pollView(data) {
  if (!data || !Array.isArray(data.counts)) return null;
  const total = Number(data.total) || 0;
  const options = data.labels.map((label, index) => {
    const count = Number(data.counts[index]) || 0;
    return { index, label, count, percent: total ? Math.round((count / total) * 100) : 0, mine: data.myChoice === index, actual: data.actualChoice === index };
  });
  const top = options.reduce((best, option) => option.count > (best?.count ?? 0) ? option : best, null);
  const hitPercent = data.actualChoice === null || data.actualChoice === undefined || !total ? null : Math.round(((Number(data.counts[data.actualChoice]) || 0) / total) * 100);
  return { open: Boolean(data.open), total, voted: data.myChoice !== null && data.myChoice !== undefined, closesOn: data.closesOn, actualRate: data.actualRate ?? null, actualChoice: data.actualChoice ?? null, options, top, hitPercent };
}
export const formatRate = rate => `${Number(rate).toLocaleString('ko-KR', { maximumFractionDigits: 2 })} : 1`;
