import { Link } from 'react-router-dom';
import { Button } from '@mui/material';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import { koreaToday } from '@/features/planner/plannerUtils';
import { evaluate, profileComplete, STATUS_TEXT } from './eligibilityRules';
import { useEligibilityProfile } from './eligibilityProfile';
import { useEligibilityFeed } from './matchShared';
import './match.css';

const TYPES = new Set(['apt', 'remaining', 'optional']);

function TrackList({ tracks }) {
  return <ul className="match-detail-tracks">{tracks.map(t => <li key={t.key} className={`is-${t.status}`}>
    <div><strong>{t.label}</strong><span className={`match-status is-${t.status}`}>{STATUS_TEXT[t.status]}</span></div>
    {t.reasons.length > 0 && <p>{t.reasons.join(' ')}</p>}
    {t.checks.length > 0 && <p className="match-checks">확인할 것: {t.checks.join(' · ')}</p>}
  </li>)}</ul>;
}

/**
 * 공고 상세의 '내 신청 가능성'. 접수 중·예정 공고만 판정한다(지난 공고는 자격 요건이 바뀌었을 수 있다).
 * 진단 전이면 진단 화면으로 안내한다.
 */
export default function EligibilityCard({ detail }) {
  const type = String(detail?.subscriptSeq || '').split('/')[0];
  const feed = useEligibilityFeed(TYPES.has(type));
  const profile = useEligibilityProfile();
  if (!TYPES.has(type)) return null;
  const item = Array.isArray(feed.data?.items) ? feed.data.items.find(entry => entry.id === detail.subscriptSeq) : null;
  if (!item) return null;
  if (!profileComplete(profile)) {
    return <section className="match-detail is-cta" aria-label="내 신청 가능성">
      <FactCheckOutlined />
      <div><strong>이 공고, 내가 넣을 수 있을까?</strong><span>사는 곳·무주택·통장·가족 조건을 고르면 일반공급 순위와 가능한 특별공급을 알려 드려요.</span></div>
      <Button size="small" variant="contained" disableElevation component={Link} to="/match" endIcon={<ArrowForwardRounded />}>30초 자격 진단</Button>
    </section>;
  }
  const result = evaluate(profile, item, koreaToday());
  const order = { ok: 0, check: 1, info: 2, no: 3 };
  const tracks = [...result.tracks].sort((a, b) => order[a.status] - order[b.status]);
  return <section className="match-detail" aria-labelledby="match-detail-heading">
    <header>
      <h3 id="match-detail-heading"><FactCheckOutlined />내 신청 가능성</h3>
      <span className={`match-verdict is-${result.best}`}>{result.headline}</span>
    </header>
    <TrackList tracks={tracks.filter(t => t.status !== 'no')} />
    {tracks.some(t => t.status === 'no') && (tracks.every(t => t.status === 'no') ? <TrackList tracks={tracks} />
      : <details className="match-detail-more"><summary>해당하지 않는 유형 {tracks.filter(t => t.status === 'no').length}개 보기</summary><TrackList tracks={tracks.filter(t => t.status === 'no')} /></details>)}
    <p className="match-detail-foot">공통 요건을 단순화한 결과예요. 세부 자격은 모집공고 원문에서 확인하세요. <a href="/faq#basics">자격 기준 자주 묻는 질문</a> · <Link to="/match">조건 수정</Link></p>
  </section>;
}
