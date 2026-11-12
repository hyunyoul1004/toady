import { MAX_SCORE } from './scoreRules';

/** 0~84점 막대 위에 공개 최저~최고 범위와 내 점수 위치를 함께 보여준다. 수치는 옆 문구로 따로 읽어 준다. */
export default function ScoreRangeBar({ low, high, mine }) {
  const pct = value => `${Math.max(0, Math.min(100, (value / MAX_SCORE) * 100))}%`;
  const from = low ?? high, to = high ?? low;
  return <div className="score-range" aria-hidden="true">
    {from !== null && from !== undefined && <span className="score-range-fill" style={{ left: pct(from), width: `calc(${pct(to)} - ${pct(from)} + 4px)` }} />}
    {Number.isFinite(mine) && <span className="score-range-mine" style={{ left: pct(mine) }} />}
  </div>;
}
