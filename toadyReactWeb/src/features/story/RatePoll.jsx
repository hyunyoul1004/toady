import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Skeleton } from '@mui/material';
import HowToVoteOutlined from '@mui/icons-material/HowToVoteOutlined';
import CheckRounded from '@mui/icons-material/CheckRounded';
import apiClient from '@/api/client';
import { anonymousVisitorId, trackAction } from '@/features/analytics/trackAction';
import { formatRate, pollView } from './pollRules';
import { useState } from 'react';
import './story.css';

const visitor = () => { try { return anonymousVisitorId(); } catch { return null; } };

/** 경쟁률 탭의 예상 투표. 접수 마감 전에는 고르고, 경쟁률이 공개되면 실제 구간과 맞힌 비율을 보여준다. */
export default function RatePoll({ houseManageNo, pblancNo }) {
  const [visitorId] = useState(visitor);
  const [editing, setEditing] = useState(false);
  const key = ['ratePoll', houseManageNo, pblancNo];
  const client = useQueryClient();
  const path = `/api/v1/polls/rate/apt/${encodeURIComponent(houseManageNo)}/${encodeURIComponent(pblancNo)}`;
  const query = useQuery({ queryKey: key, queryFn: () => apiClient.get(path, { visitorId }), enabled: Boolean(houseManageNo && pblancNo), staleTime: 60000, retry: false });
  const vote = useMutation({
    mutationFn: choice => apiClient.post(path, { visitorId, choice }, { auth: false }),
    onSuccess: (data, choice) => { client.setQueryData(key, data); setEditing(false); trackAction('poll_vote', `apt/${houseManageNo}/${pblancNo}`); }
  });
  if (query.isLoading) return <Skeleton variant="rounded" height={120} />;
  const view = pollView(query.data);
  // 투표 저장소가 아직 준비되지 않았거나(배포 전) 공고가 없으면 조용히 숨긴다.
  if (query.isError || !view || !visitorId) return null;
  if (!view.open && view.total === 0) return null;
  const choosing = view.open && (!view.voted || editing);
  const closes = view.closesOn ? `${Number(view.closesOn.slice(5, 7))}월 ${Number(view.closesOn.slice(8, 10))}일` : '';

  return <section className="rate-poll" aria-labelledby="rate-poll-title">
    <div className="rate-poll-head">
      <HowToVoteOutlined />
      <div>
        <h3 id="rate-poll-title">{view.actualRate !== null ? '경쟁률 예상, 맞히셨나요?' : '경쟁률 맞히기'}</h3>
        <p>{view.actualRate !== null ? `가장 인기 있는 주택형의 1순위 경쟁률은 ${formatRate(view.actualRate)}였어요.`
          : '가장 인기 있는 주택형의 1순위 경쟁률, 몇 대 1일까요?'}</p>
      </div>
      {view.total > 0 && <span className="rate-poll-count">{view.total.toLocaleString('ko-KR')}명 참여</span>}
    </div>
    {choosing ? <div className="rate-poll-options" role="group" aria-label="예상 경쟁률 구간">
      {view.options.map(option => <button type="button" key={option.index} disabled={vote.isPending} className={option.mine ? 'is-mine' : ''} aria-pressed={option.mine}
        onClick={() => vote.mutate(option.index)}>{option.label}</button>)}
    </div> : <ul className="rate-poll-results">
      {view.options.map(option => <li key={option.index} className={`${option.mine ? 'is-mine' : ''} ${option.actual ? 'is-actual' : ''}`}>
        <span className="rate-poll-bar" style={{ width: `${option.percent}%` }} />
        <span className="rate-poll-label">{option.label}{option.actual && <em>실제</em>}{option.mine && <em className="is-mine"><CheckRounded fontSize="inherit" />내 예상</em>}</span>
        <span className="rate-poll-percent">{option.percent}%</span>
      </li>)}
    </ul>}
    {vote.isError && <Alert severity="warning" sx={{ mt: 1 }}>{vote.error.message || '투표를 저장하지 못했어요.'}</Alert>}
    <div className="rate-poll-foot">
      {view.actualRate !== null ? <span>{view.hitPercent !== null ? `참여자 ${view.hitPercent}%가 맞혔어요.` : ''} 청약홈 공개 경쟁률 기준</span>
        : view.open ? <span>{closes ? `${closes} 접수 마감까지` : '접수 마감까지'} 참여할 수 있어요{view.voted ? ' · 선택을 바꿀 수 있어요' : ''}. 결과가 나오면 여기서 확인하세요.</span>
        : <span>투표가 마감됐어요. 경쟁률이 공개되면 실제 결과를 함께 보여드려요.</span>}
      {view.open && view.voted && !editing && <Button size="small" onClick={() => setEditing(true)}>다시 고르기</Button>}
    </div>
  </section>;
}
