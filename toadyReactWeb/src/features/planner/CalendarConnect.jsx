import { useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, CircularProgress, Dialog, DialogContent, DialogTitle, IconButton, useMediaQuery } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import SyncRounded from '@mui/icons-material/SyncRounded';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import apiClient from '@/api/client';
import { downloadCalendar } from './plannerUtils';

const BENEFITS = [
  [SyncRounded, '저장한 공고 일정이 캘린더에 자동으로 추가되고, 일정이 바뀌면 함께 갱신돼요.'],
  [NotificationsActiveOutlined, '일정 전날 오전 9시에 캘린더 앱이 알려 줘요.'],
  [EventAvailableOutlined, '메일 알림과 별개로 동작해요. 둘 다 사용해도 괜찮아요.']
];

/** 회원은 구독 주소로 자동 갱신, 비회원은 로그인 안내와 일회성 파일을 제공한다. 확인 단계도 같은 창 안에서 처리해 뒤로가기 한 번에 닫힌다. */
export default function CalendarConnect({ open, onClose, loggedIn, summaries, onLogin }) {
  const client = useQueryClient();
  const mobile = useMediaQuery('(max-width:600px)');
  const feed = useQuery({ queryKey: ['calendarFeed'], enabled: open && loggedIn, queryFn: async () => (await apiClient.get('/api/v1/me/calendar-feed')).data, staleTime: 60000 });
  const [busy, setBusy] = useState(false), [error, setError] = useState(null), [notice, setNotice] = useState(null), [confirm, setConfirm] = useState(null);
  const data = feed.data;
  async function change(kind) {
    setBusy(true); setError(null); setNotice(null);
    try {
      const response = kind === 'revoke' ? await apiClient.delete('/api/v1/me/calendar-feed') : await apiClient.post('/api/v1/me/calendar-feed', {});
      client.setQueryData(['calendarFeed'], response.data);
      setNotice(kind === 'revoke' ? '구독을 해지했어요. 캘린더 앱에 남은 캘린더는 직접 삭제해 주세요.' : kind === 'reset' ? '새 주소를 만들었어요. 이전 주소로 연결한 캘린더는 더 이상 갱신되지 않아요.' : null);
      setConfirm(null);
    } catch (e) { setError(e.message || '잠시 후 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  }
  async function copy() {
    try { await navigator.clipboard.writeText(data.url); setNotice('구독 주소를 복사했어요. 캘린더 앱의 ‘URL로 구독’에 붙여 넣으세요.'); }
    catch { setError('주소를 복사하지 못했어요. 아래 주소를 길게 눌러 복사해 주세요.'); }
  }
  const close = () => { if (busy) return; setConfirm(null); setNotice(null); setError(null); onClose(); };
  // 뒤로가기로 닫힌 경우 close()를 거치지 않으므로, 다시 열릴 때 지난 확인창·안내를 비운다.
  useEffect(() => { if (open) { setConfirm(null); setNotice(null); setError(null); } }, [open]);

  let body;
  if (!loggedIn) body = <>
    <p className="calendar-lead">로그인하면 저장한 공고 일정이 휴대폰 캘린더에 <strong>자동으로</strong> 반영돼요.</p>
    <Benefits />
    <Button fullWidth variant="contained" disableElevation size="large" onClick={onLogin}>로그인하고 연결하기</Button>
    <div className="calendar-divider"><span>또는</span></div>
    <Button fullWidth variant="outlined" startIcon={<FileDownloadOutlined />} disabled={!summaries.length} onClick={() => downloadCalendar(summaries)}>지금 일정만 파일로 받기</Button>
    <p className="calendar-help">{summaries.length ? '파일은 한 번만 추가되며 이후 저장하는 공고는 반영되지 않아요.' : '저장한 공고가 없어 받을 일정이 없어요.'}</p>
  </>;
  else if (feed.isLoading) body = <div className="calendar-loading"><CircularProgress size={24} /></div>;
  else if (feed.error) body = <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => feed.refetch()}>재시도</Button>}>구독 상태를 확인하지 못했어요.</Alert>;
  else if (!data?.subscribed) body = <>
    <p className="calendar-lead">구독 주소를 한 번 연결해 두면 앞으로 저장하는 공고도 캘린더에 자동으로 추가돼요.</p>
    <Benefits />
    <Button fullWidth variant="contained" disableElevation size="large" disabled={busy} onClick={() => change('issue')}>{busy ? '만드는 중…' : '구독 주소 만들기'}</Button>
    {!summaries.length && <p className="calendar-help">아직 저장한 공고가 없어요. 지금 연결해 두면 저장하는 대로 반영돼요.</p>}
  </>;
  else if (confirm) body = <>
    <p className="calendar-lead"><strong>{confirm === 'reset' ? '새 구독 주소를 만들까요?' : '캘린더 구독을 해지할까요?'}</strong></p>
    <p className="calendar-help">{confirm === 'reset' ? '이전 주소로 연결한 캘린더는 더 이상 갱신되지 않아요. 주소가 다른 사람에게 알려졌을 때 사용하세요.' : '연결한 캘린더가 더 이상 갱신되지 않아요. 캘린더 앱에 남은 일정은 직접 삭제해야 해요.'}</p>
    <Box className="calendar-confirm"><Button disabled={busy} onClick={() => setConfirm(null)}>취소</Button><Button variant="contained" disableElevation color={confirm === 'revoke' ? 'error' : 'primary'} disabled={busy} onClick={() => change(confirm)}>{confirm === 'reset' ? '새 주소 만들기' : '구독 해지'}</Button></Box>
  </>;
  else body = <>
    <p className="calendar-lead">사용하는 캘린더를 선택하세요. 한 번만 연결하면 돼요.</p>
    <div className="calendar-actions">
      <Button fullWidth variant="contained" disableElevation size="large" component="a" href={data.googleUrl} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded />}>구글 캘린더에 추가</Button>
      <Button fullWidth variant="outlined" size="large" component="a" href={data.webcalUrl}>아이폰·맥 캘린더에 추가</Button>
      <Button fullWidth variant="text" startIcon={<ContentCopyRounded />} onClick={copy}>구독 주소 복사</Button>
    </div>
    <input className="calendar-url" readOnly value={data.url} aria-label="캘린더 구독 주소" onFocus={e => e.target.select()} />
    <ul className="calendar-tips">
      <li>삼성 캘린더는 구글 캘린더에 추가한 뒤 구글 계정을 동기화하면 함께 보여요.</li>
      <li>구글 캘린더는 새로 저장한 공고가 반영되기까지 몇 시간이 걸릴 수 있어요.</li>
      <li>이 주소를 아는 사람은 저장한 공고 일정을 볼 수 있으니 공유하지 마세요.</li>
    </ul>
    <div className="calendar-manage"><Button size="small" disabled={busy} onClick={() => setConfirm('reset')}>주소 다시 만들기</Button><Button size="small" color="error" disabled={busy} onClick={() => setConfirm('revoke')}>구독 해지</Button></div>
  </>;

  return <Dialog open={open} onClose={close} fullWidth maxWidth="xs" fullScreen={mobile} className="calendar-dialog" aria-labelledby="calendar-connect-title">
    <DialogTitle id="calendar-connect-title" className="calendar-title">휴대폰 캘린더에 연결<IconButton aria-label="캘린더 연결 닫기" onClick={close} disabled={busy}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent className="calendar-content">
      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}
      {body}
    </DialogContent>
  </Dialog>;
}

function Benefits() {
  return <ul className="calendar-benefits">{BENEFITS.map(([Icon, text]) => <li key={text}><Icon fontSize="small" /><span>{text}</span></li>)}</ul>;
}
