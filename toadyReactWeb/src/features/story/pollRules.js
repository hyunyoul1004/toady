/** 경쟁률 예상 투표(RatePoll) 화면 계산. 쓰는 곳: RatePoll.jsx. */
import { formatCompetitionRate } from '../../utils/rate.util.js';
/** 투표 응답을 화면용으로 정리한다. 비율은 반올림해도 합이 100을 넘지 않게 표시용으로만 쓴다. */
export function pollView(data) {
  if (!data || !Array.isArray(data.counts)) return null;
  const total = Number(data.total) || 0;
  const options = data.labels.map((label, index) => {
    const count = Number(data.counts[index]) || 0;
    return { index, label, count, percent: total ? Math.round((count / total) * 100) : 0, mine: data.myChoice === index, actual: data.actualChoice === index };
  });
  // 가장 많이 고른 구간(동률이면 앞의 것). 아무도 고르지 않았으면 null.
  const top = options.reduce((best, option) => option.count > (best?.count ?? 0) ? option : best, null);
  // 실제 결과 구간을 고른 사람 비율 = 맞힌 비율.
  const hitPercent = data.actualChoice === null || data.actualChoice === undefined || !total ? null : Math.round(((Number(data.counts[data.actualChoice]) || 0) / total) * 100);
  return { open: Boolean(data.open), total, voted: data.myChoice !== null && data.myChoice !== undefined, closesOn: data.closesOn, actualRate: data.actualRate ?? null, actualChoice: data.actualChoice ?? null, options, top, hitPercent };
}
/** 경쟁률 표기는 공통 규칙(utils/rate.util)을 따른다. */
export const formatRate = rate => formatCompetitionRate(rate);
