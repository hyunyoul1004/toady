import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Button, Skeleton } from '@mui/material';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import BookmarkRounded from '@mui/icons-material/BookmarkRounded';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import apiClient from '@/api/client';
import { STATUS_TEXT, TYPE_NAME, periodLabel } from '@/features/explore/exploreRules';
import { weeklyPicks } from './nextEventRules';

/**
 * 나의 청약 빈 화면: 이번 주 접수 공고 3개를 바로 저장할 수 있게 보여 준다.
 * 이번 주 청약·오늘의 요약과 같은 데이터(weekly-summary)를 쓴다. 저장하면 화면이 일반 상태로 바뀐다.
 * 데이터: /api/v1/public/weekly-summary(추천 목록), /api/v1/planner/summaries(저장할 공고 요약).
 */
export default function WeeklyPicks({ planner, report }) {
  const weekly = useQuery({ queryKey: ['weeklySummary'], queryFn: () => apiClient.get('/api/v1/public/weekly-summary'), staleTime: 600000, retry: 1 });
  const [pending, setPending] = useState(null);
  const picks = weeklyPicks(weekly.data, planner.items.map(item => item.id));
  async function save(pick) {
    setPending(pick.id);
    try {
      // 저장에는 일정이 담긴 공고 요약이 필요하다. 추천 항목 ID로 요약을 한 번 받아 온 뒤 저장한다.
      const [summary] = await apiClient.post('/api/v1/planner/summaries', { ids: [pick.id] }, { auth: false });
      if (!summary || summary.unavailable) throw Error('공고 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
      await planner.save({ id: pick.id, summary, note: '', checklist: 0, emailEnabled: false });
      report('관심 공고에 저장했어요. 다가오는 일정을 이곳에 모아 드릴게요.', false);
    } catch (error) { report(error.message, true); } finally { setPending(null); }
  }
  return <div className="planner-empty planner-empty-picks">
    <div><BookmarkBorderRounded /></div>
    <h3>어떤 집이 마음에 드세요?</h3>
    <p>공고를 저장하면 접수일·발표일을 모아<br />다음 일정을 먼저 알려 드려요.</p>
    {weekly.isLoading
      ? <ul className="planner-picks" aria-busy="true">{[0, 1, 2].map(index => <li key={index}><Skeleton variant="rounded" height={64} /></li>)}</ul>
      : picks.length > 0 && <>
        <h4 className="planner-picks-title">이번 주 접수하는 공고</h4>
        <ul className="planner-picks">{picks.map(pick => <li key={pick.id} className="planner-pick">
          <Link className="planner-pick-main" to={`/my?subscriptSeq=${encodeURIComponent(pick.id)}`} state={{ plannerDetail: true }}>
            <span className="planner-pick-meta"><span className={`planner-pick-status is-${String(pick.status).toLowerCase()}`}>{STATUS_TEXT[pick.status] || ''}</span>{TYPE_NAME[pick.type] || pick.houseKind || '공고'}</span>
            <strong>{pick.houseNm}</strong>
            <span className="planner-pick-period">접수 {periodLabel(pick.start, pick.end)}{pick.address ? ` · ${pick.address}` : ''}</span>
          </Link>
          <Button size="small" variant="outlined" className="planner-pick-save" disabled={Boolean(pending) || planner.isLoading} aria-label={`${pick.houseNm} 관심 공고 저장`}
            startIcon={pending === pick.id ? <BookmarkRounded /> : <BookmarkBorderRounded />} onClick={() => save(pick)}>{pending === pick.id ? '저장 중' : '저장'}</Button>
        </li>)}</ul>
      </>}
    <Button variant={picks.length ? 'text' : 'contained'} disableElevation component={Link} to={picks.length ? '/this-week' : '/'} endIcon={<ArrowForwardRounded />}>{picks.length ? '이번 주 청약 전체 보기' : '청약 찾아보기'}</Button>
  </div>;
}
