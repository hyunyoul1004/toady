import { Link } from 'react-router-dom';
import { Button } from '@mui/material';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import { compareToRange, fmt, publicRange } from './scoreRules';
import { useMyScore } from './myScore';
import ScoreRangeBar from './ScoreRangeBar';
import './score.css';

/** 경쟁률 탭 상단: 이 공고의 공개 당첨가점 범위와 내 가점(이 브라우저 저장값)을 한 줄로 비교한다. */
export default function ScoreCompareCard({ rows, loading }) {
  const { score } = useMyScore();
  if (loading) return null;
  const range = publicRange(rows);
  const total = score?.total;
  const result = compareToRange(total, range);
  const rangeText = range ? [range.low !== null && `최저 ${fmt(range.low)}점`, range.high !== null && `최고 ${fmt(range.high)}점`].filter(Boolean).join(' ~ ') : '';
  return <section className={`score-compare is-${score ? result.status : 'empty'}`} aria-label="내 가점 비교">
    <div className="score-compare-main">
      <div className="score-compare-mine">
        <span>내 가점</span>
        <strong>{score ? <>{total}<small>점</small></> : '—'}</strong>
      </div>
      <div className="score-compare-body">
        <p className="score-compare-title">{!range ? '아직 공개된 당첨가점이 없어요' : score ? result.text : `공개 당첨가점 ${rangeText}`}</p>
        <p className="score-compare-sub">{!range ? (score ? '청약홈이 당첨가점을 공개하면 내 점수와 바로 비교해 드려요.' : '내 가점을 미리 계산해 두면 공개되는 대로 비교해 드려요.')
          : score ? `공개 ${rangeText} · 주택형·거주지역 전체 기준` : '내 가점을 계산하면 이 공고와 바로 비교해 드려요.'}</p>
        {range && <ScoreRangeBar low={range.low} high={range.high} mine={total} />}
      </div>
    </div>
    <Button size="small" variant={score ? 'text' : 'contained'} disableElevation component={Link} to="/score" startIcon={<CalculateOutlined />}>{score ? '가점 다시 계산' : '내 가점 계산하기'}</Button>
    {range && score && <p className="score-compare-note">지난 결과와의 비교이며 당첨을 보장하지 않아요. 주택형별 가점은 아래 표에서 확인하세요.</p>}
  </section>;
}
