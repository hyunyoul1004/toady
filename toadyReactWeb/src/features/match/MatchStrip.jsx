import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import BoltRounded from '@mui/icons-material/BoltRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { koreaToday } from '@/features/planner/plannerUtils';
import { profileComplete, rankFeed } from './eligibilityRules';
import { useEligibilityProfile } from './eligibilityProfile';
import { useEligibilityFeed } from './matchShared';
import './match.css';

/**
 * 메인 화면의 맞춤 안내 두 줄: 내가 넣을 수 있는 공고 수(진단 전이면 진단 안내)와 접수 중·예정 줍줍 수.
 * 숫자를 보여줘 재방문 동기를 만든다. 자료를 불러오지 못하면 조용히 숨긴다.
 */
export default function MatchStrip() {
  const feed = useEligibilityFeed();
  const profile = useEligibilityProfile();
  const complete = profileComplete(profile);
  const today = koreaToday();
  const items = useMemo(() => (Array.isArray(feed.data?.items) ? feed.data.items : []), [feed.data]);
  const ranked = useMemo(() => (complete ? rankFeed(items.filter(item => item.type === 'apt'), profile, today) : []), [complete, items, profile, today]);
  if (feed.isError || (feed.isLoading && !complete)) return null;
  const possible = ranked.filter(({ result }) => result.best === 'ok' || result.best === 'check');
  const open = possible.filter(({ item }) => item.status === 'OPEN').length;
  const zupzup = items.filter(item => item.type !== 'apt');
  const zupzupOpen = zupzup.filter(item => item.status === 'OPEN').length;
  return <div className="match-strips">
    <Link to="/match" className="match-strip">
      <FactCheckOutlined />
      {complete ? <div><strong>내가 넣을 수 있는 공고 <b>{possible.length}건</b>{open > 0 ? ` · 접수 중 ${open}건` : ''}</strong>
        <span>{possible[0] ? `${possible[0].item.houseNm} · ${possible[0].result.headline}` : '조건에 맞는 새 공고가 올라오면 여기에 보여 드려요.'}</span></div>
        : <div><strong>이번 달 내가 넣을 수 있는 청약은?</strong><span>30초 자격 진단으로 가능한 공고와 특별공급만 골라 보세요.</span></div>}
      <ChevronRightRounded />
    </Link>
    {zupzup.length > 0 && <Link to="/match/zupzup" className="match-strip is-zupzup">
      <BoltRounded />
      <div><strong>무순위·줍줍 <b>{zupzup.length}건</b>{zupzupOpen > 0 ? ` · 접수 중 ${zupzupOpen}건` : ''}</strong><span>{zupzup[0].houseNm}</span></div>
      <ChevronRightRounded />
    </Link>}
  </div>;
}
