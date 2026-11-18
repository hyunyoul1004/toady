import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { koreaToday } from './plannerUtils';
import { profileComplete, rankFeed } from '@/features/match/eligibilityRules';
import { useEligibilityProfile } from '@/features/match/eligibilityProfile';
import { useEligibilityFeed } from '@/features/match/matchShared';
import { summaryNumbers, summaryDay, todayLabel, openTodayText } from './todaySummaryRules';
import './todaySummary.css';

/**
 * 메인 '오늘의 요약': 오늘 접수 · 이번 주 발표 · 무순위·줍줍 · 내가 넣을 수 있는 공고(진단 전이면 진단 안내).
 * 숫자마다 그 목록으로 바로 간다. 모든 숫자는 달력의 보고 있는 달·검색 조건과 상관없는 전국 기준이다.
 * '오늘 접수'와 '이번 주 발표'는 이번 주 청약 화면과 같은 데이터를 써서 눌렀을 때 건수가 맞는다.
 * 주말(KST)에는 '오늘 접수' 칸을 '주말 접수 기간 중'으로 바꿔 부른다(openTodayText). 검색·필터가 걸려 있으면(filtered)
 * 제목 옆에 작게 '전국 기준'을 붙여 필터된 달력 칸 건수와 다른 이유를 알린다.
 * 쓰는 곳: TodayOverview.jsx. 데이터: /api/v1/public/weekly-summary, 자격 진단 피드(useEligibilityFeed), 내 진단 정보(useEligibilityProfile).
 */
export default function TodaySummary({ savedCount, filtered = false }) {
  // queryKey ['weeklySummary']는 이번 주 청약·나의 청약 빈 화면과 같아서 한 번 받은 캐시를 같이 쓴다(10분).
  const weekly = useQuery({ queryKey: ['weeklySummary'], queryFn: () => apiClient.get('/api/v1/public/weekly-summary'), staleTime: 600000, retry: 1 });
  const feed = useEligibilityFeed();
  const profile = useEligibilityProfile();
  const complete = profileComplete(profile);
  const today = koreaToday();
  // useMemo: 의존 값이 그대로면 지난 계산 결과를 다시 쓴다. items가 렌더마다 새 배열이 되지 않아 아래 rankFeed 계산도 아낀다.
  const items = useMemo(() => (Array.isArray(feed.data?.items) ? feed.data.items : []), [feed.data]);
  const ranked = useMemo(() => (complete ? rankFeed(items.filter(item => item.type === 'apt'), profile, today) : []), [complete, items, profile, today]);
  const numbers = summaryNumbers({ items, ranked, weekly: weekly.data });
  const feedReady = !feed.isLoading && !feed.isError;
  const weeklyReady = !weekly.isLoading && !weekly.isError;
  // 불러오는 중이거나 오류면 0 대신 '–'를 보여 준다.
  const value = (number, ready) => (ready ? number : '–');
  // 날짜·주말 문구는 숫자를 계산한 weekly-summary의 기준일(today)을 따른다(자정 직후 캐시와 기기 날짜가 달라도 맞게).
  const labelDay = summaryDay(weekly.data, today);
  const dateText = todayLabel(labelDay);
  const openText = openTodayText(labelDay);

  return <section className="today-summary" aria-labelledby="today-summary-title">
    <div className="today-summary-head">
      <h2 id="today-summary-title">오늘의 요약 <span>{dateText}</span>{filtered && <span className="today-summary-scope" title="검색·필터와 상관없이 전국 공고 기준 숫자예요">전국 기준</span>}</h2>
      <Link to="/my" className="today-summary-saved">나의 청약 <b>{savedCount}</b><ChevronRightRounded fontSize="inherit" /></Link>
    </div>
    <div className="today-summary-grid">
      <Link className="today-tile" to="/this-week?filter=open">
        <span>{openText.label}</span><strong>{value(numbers.openToday, weeklyReady)}<em>건</em></strong><small>{openText.note}</small>
      </Link>
      <Link className="today-tile" to="/this-week#week-winners">
        <span>이번 주 발표</span><strong>{value(numbers.winners, weeklyReady)}<em>건</em></strong><small>이번 주 청약 일정</small>
      </Link>
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
