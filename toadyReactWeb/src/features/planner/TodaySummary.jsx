import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { koreaToday } from './plannerUtils';
import { profileComplete, rankFeed } from '@/features/match/eligibilityRules';
import { useEligibilityProfile } from '@/features/match/eligibilityProfile';
import { useEligibilityFeed } from '@/features/match/matchShared';
import { summaryNumbers } from './todaySummaryRules';
import './todaySummary.css';

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토'];

/**
 * 메인 '오늘의 요약': 오늘 접수 · 이번 주 발표 · 무순위·줍줍 · 내가 넣을 수 있는 공고(진단 전이면 진단 안내).
 * 숫자마다 그 목록으로 바로 간다. 맞춤 안내 줄·오늘 접수 칩·나의 청약 개수를 한 카드로 모았다.
 */
export default function TodaySummary({ todayCount, loading, savedCount, onShowToday }) {
  const feed = useEligibilityFeed();
  const profile = useEligibilityProfile();
  const complete = profileComplete(profile);
  const today = koreaToday();
  const items = useMemo(() => (Array.isArray(feed.data?.items) ? feed.data.items : []), [feed.data]);
  const ranked = useMemo(() => (complete ? rankFeed(items.filter(item => item.type === 'apt'), profile, today) : []), [complete, items, profile, today]);
  const numbers = summaryNumbers({ items, ranked, winners: feed.data?.winnersThisWeek });
  const feedReady = !feed.isLoading && !feed.isError;
  const value = (number, ready) => (ready ? number : '–');
  const date = new Date(`${today}T00:00:00+09:00`);
  const dateText = `${date.getMonth() + 1}월 ${date.getDate()}일 (${WEEKDAY[date.getDay()]})`;

  return <section className="today-summary" aria-labelledby="today-summary-title">
    <div className="today-summary-head">
      <h2 id="today-summary-title">오늘의 요약 <span>{dateText}</span></h2>
      <Link to="/my" className="today-summary-saved">나의 청약 <b>{savedCount}</b><ChevronRightRounded fontSize="inherit" /></Link>
    </div>
    <div className="today-summary-grid">
      <button type="button" className="today-tile" onClick={onShowToday} disabled={loading}>
        <span>오늘 접수</span><strong>{value(todayCount, !loading)}<em>건</em></strong><small>접수 중인 공고 보기</small>
      </button>
      <a className="today-tile" href="/weekly">
        <span>이번 주 발표</span><strong>{value(numbers.winners, feedReady)}<em>건</em></strong><small>이번 주 청약 일정</small>
      </a>
      <Link className="today-tile is-zupzup" to="/match/zupzup">
        <span>무순위·줍줍</span><strong>{value(numbers.zupzup, feedReady)}<em>건</em></strong><small>{feedReady && numbers.zupzupOpen > 0 ? `접수 중 ${numbers.zupzupOpen}건` : '접수 중·예정'}</small>
      </Link>
      {complete
        ? <Link className="today-tile is-match" to="/match">
          <span>내가 넣을 수 있는</span><strong>{value(numbers.possible, feedReady)}<em>건</em></strong><small>{feedReady && numbers.possibleOpen > 0 ? `접수 중 ${numbers.possibleOpen}건` : '맞춤 청약 보기'}</small>
        </Link>
        : <Link className="today-tile is-match is-cta" to="/match">
          <span>내가 넣을 수 있는</span><strong className="is-text">30초 진단</strong><small>가능한 공고만 골라 보기</small>
        </Link>}
    </div>
  </section>;
}
