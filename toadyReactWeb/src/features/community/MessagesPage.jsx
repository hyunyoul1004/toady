import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import InputBase from '@mui/material/InputBase';
import Skeleton from '@mui/material/Skeleton';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import SendRounded from '@mui/icons-material/SendRounded';
import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import FlagOutlined from '@mui/icons-material/FlagOutlined';
import WarningAmberRounded from '@mui/icons-material/WarningAmberRounded';
import ApartmentRounded from '@mui/icons-material/ApartmentRounded';
import SettingsOutlined from '@mui/icons-material/SettingsOutlined';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import ProfileAvatar from '@/features/account/ProfileAvatar';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { COMMUNITY_KEY, cardValue, communityApi, mergeMessages, messageTime, reportValue, run, uniqueThreads } from './communityApi';
import '@/features/account/account.css';
import './community.css';

const MAX = 1000;
const detailPath = contextId => `/?subscriptSeq=${encodeURIComponent(contextId)}&tabType=comment`;

/** 쪽지 입력. 보낼 수 없으면 이유를 대신 보여 준다. */
function Composer({ canSend, reason, onSend, placeholder, autoFocus }) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  if (!canSend) return <Box className="dm-composer is-closed" role="note">{reason || '지금은 쪽지를 보낼 수 없어요.'}</Box>;
  const submit = async event => {
    event.preventDefault();
    const value = text.trim();
    if (!value || busy) return;
    setBusy(true); setError('');
    const result = await onSend(value);
    setBusy(false);
    if (result.ok) setText(''); else setError(result.message);
  };
  return <Box component="form" className="dm-composer" onSubmit={submit}>
    {error && <Alert severity="error" onClose={() => setError('')} sx={{ mb: 1 }}>{error}</Alert>}
    <Box className="dm-composer-row">
      <InputBase value={text} onChange={event => setText(event.target.value.slice(0, MAX))} placeholder={placeholder} multiline maxRows={5}
        autoFocus={autoFocus} className="dm-input" inputProps={{ 'aria-label': '쪽지 내용', maxLength: MAX }}
        onKeyDown={event => { if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) submit(event); }} />
      <IconButton type="submit" color="primary" disabled={!text.trim() || busy} aria-label="보내기" className="dm-send"><SendRounded /></IconButton>
    </Box>
    {text.length > MAX * 0.8 && <small className="dm-count">{text.length} / {MAX}</small>}
  </Box>;
}

/** 대화 상대 머리글. 이름을 누르면 프로필 카드(차단·신고)가 열린다. */
function ThreadHeader({ other, contextId, houseNm, onBack, children }) {
  const [, setCard] = useHistoryOverlay('member-card');
  return <header className="dm-head">
    <IconButton onClick={onBack} aria-label="쪽지함으로"><ArrowBackRounded /></IconButton>
    <button type="button" className="dm-head-who" onClick={() => !other.withdrawn && setCard(cardValue(other.userSeq, contextId))} disabled={other.withdrawn}>
      <ProfileAvatar icon={other.profileIcon} name={other.nickname} size={36} decorative />
      <span><b>{other.nickname}</b>{houseNm && <small>{houseNm} 이야기에서 시작</small>}</span>
    </button>
    {children}
  </header>;
}

/** 대화 보기: 최신 30통, 위로 더 보기, 15초마다 새 쪽지 확인. 들어오면 읽음 처리된다. */
function ThreadView({ threadSeq, onBack }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [, setReport] = useHistoryOverlay('report');
  const [leaveOpen, setLeaveOpen] = useHistoryOverlay('dm-leave');
  const [older, setOlder] = useState([]);
  const [hasOlder, setHasOlder] = useState(null);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [leaveError, setLeaveError] = useState('');
  const leaving = useRef(false);
  const listRef = useRef(null);
  const stick = useRef(true);
  const thread = useQuery({
    queryKey: [...COMMUNITY_KEY, 'thread', threadSeq],
    queryFn: () => communityApi.thread(threadSeq),
    // 열리지 않는 대화(404 등)는 다시 묻지 않는다.
    refetchInterval: query => (query.state.data?.resultCode === 'SUCCESS' && document.visibilityState === 'visible' ? 15000 : false),
    refetchOnWindowFocus: true
  });
  const data = thread.data?.resultCode === 'SUCCESS' ? thread.data.data : null;
  // 이전에 불러온 쪽지와 15초마다 받는 최신 30통을 번호로 합친다(새 쪽지가 와도 사이가 비지 않게).
  const messages = useMemo(() => mergeMessages(older, data?.messages || []), [older, data?.messages]);
  const lastSeq = messages[messages.length - 1]?.messageSeq;
  // 열면 서버가 읽음으로 바꾸므로 메뉴 배지·쪽지함 숫자를 새로 읽는다.
  useEffect(() => { if (data) { queryClient.invalidateQueries({ queryKey: [...COMMUNITY_KEY, 'unread'] }); queryClient.invalidateQueries({ queryKey: [...COMMUNITY_KEY, 'threads'] }); } },
    [lastSeq]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { setOlder([]); setHasOlder(null); stick.current = true; }, [threadSeq]);
  // 지금까지 받은 쪽지를 버리지 않고 쌓아 둔다(최신 30통 범위에서 밀려난 쪽지도 화면에 남게).
  useEffect(() => { if (data?.messages?.length) setOlder(list => mergeMessages(list, data.messages)); }, [data?.messages]);
  const more = hasOlder ?? data?.hasMore;
  // 새 쪽지가 오면 맨 아래에 있을 때만 따라 내려간다(위로 읽는 중이면 그대로 둔다).
  useLayoutEffect(() => {
    const box = listRef.current;
    if (box && stick.current) box.scrollTop = box.scrollHeight;
  }, [lastSeq]);
  // 나가기: 확인 창 칸을 뒤로가기로 먼저 닫고, 대화 칸으로 돌아온 다음 그 칸을 쪽지함으로 바꾼다.
  // (확인 창 칸만 바꾸면 대화 칸이 남아, 뒤로가기 때 나간 대화가 다시 열린다.)
  useEffect(() => {
    if (!leaving.current || leaveOpen) return;
    leaving.current = false;
    navigate('/messages', { replace: true });
  }, [location.key, leaveOpen]); // eslint-disable-line react-hooks/exhaustive-deps
  const onScroll = () => { const box = listRef.current; if (box) stick.current = box.scrollHeight - box.scrollTop - box.clientHeight < 80; };

  const loadOlder = async () => {
    const first = messages[0]?.messageSeq;
    if (!first) return;
    setLoadingOlder(true);
    const box = listRef.current; const before = box ? box.scrollHeight - box.scrollTop : 0;
    const result = await run(() => communityApi.thread(threadSeq, first));
    setLoadingOlder(false);
    if (!result.ok) return;
    stick.current = false;
    setOlder(list => mergeMessages(result.data?.messages || [], list));
    setHasOlder(Boolean(result.data?.hasMore));
    requestAnimationFrame(() => { if (box) box.scrollTop = box.scrollHeight - before; });
  };
  const send = async content => {
    const result = await run(() => communityApi.send(data.other.userSeq, content, data.contextId));
    if (result.ok) { stick.current = true; await thread.refetch(); queryClient.invalidateQueries({ queryKey: [...COMMUNITY_KEY, 'threads'] }); }
    return result;
  };
  const leave = async () => {
    const result = await run(() => communityApi.leave(threadSeq));
    if (result.ok) {
      queryClient.invalidateQueries({ queryKey: COMMUNITY_KEY });
      leaving.current = true;
      setLeaveOpen(null);
    } else {
      setLeaveError(result.message || '대화에서 나가지 못했어요. 잠시 뒤에 다시 시도해 주세요.');
      setLeaveOpen(null);
    }
  };

  if (thread.isLoading) return <Box className="dm-page"><Skeleton height={56} /><Skeleton height={80} /><Skeleton height={80} /></Box>;
  if (!data) return <Box className="dm-page"><Alert severity="error" action={<Button color="inherit" size="small" onClick={onBack}>쪽지함</Button>}>
    {thread.data?.resultMessage || '대화를 불러오지 못했어요.'}</Alert></Box>;

  let lastDay = '';
  return <Box className="dm-page dm-thread">
    <ThreadHeader other={data.other} contextId={data.contextId} houseNm={data.houseNm} onBack={onBack}>
      <Button size="small" color="inherit" className="dm-leave" onClick={() => setLeaveOpen(true)}>나가기</Button>
    </ThreadHeader>
    {leaveError && <Alert severity="error" onClose={() => setLeaveError('')} sx={{ borderRadius: 0 }}>{leaveError}</Alert>}
    {data.contextId && <Link className="dm-context" to={detailPath(data.contextId)} state={{ detailOverlay: true }}>
      <ApartmentRounded fontSize="small" /><span>{data.houseNm || '공고'} 보기</span><ChevronRightRounded fontSize="small" /></Link>}
    <Box className="dm-list" ref={listRef} onScroll={onScroll} role="log" aria-live="polite" aria-busy={loadingOlder} aria-label={`${data.other.nickname}님과의 쪽지`}>
      {more && <Button size="small" onClick={loadOlder} disabled={loadingOlder} className="dm-older">{loadingOlder ? '불러오는 중…' : '이전 쪽지 더 보기'}</Button>}
      {!data.otherReplied && messages.length > 0 && messages.every(message => message.mine) &&
        <p className="dm-system">상대가 답하면 이어서 보낼 수 있어요. 답이 오면 알림과 메뉴 숫자로 알려 드려요.</p>}
      {messages.map(message => {
        const day = new Date(message.sentAt).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' });
        const showDay = day !== lastDay; lastDay = day;
        return <div key={message.messageSeq}>
          {showDay && <p className="dm-day">{day}</p>}
          <div className={`dm-row${message.mine ? ' is-mine' : ''}`}>
            <div className="dm-bubble-wrap">
              {message.flagged && <p className="dm-flag"><WarningAmberRounded fontSize="inherit" />청약통장 매매·분양권 전매 알선은 불법이고 사기 위험이 커요. 돈을 먼저 보내지 마세요.</p>}
              <p className="dm-bubble">{message.content}</p>
              <span className="dm-meta">{messageTime(message.sentAt)}
                {!message.mine && <button type="button" className="dm-report" onClick={() => setReport(reportValue('MESSAGE', message.messageSeq, data.other.userSeq))} aria-label="이 쪽지 신고"><FlagOutlined fontSize="inherit" /></button>}</span>
            </div>
          </div>
        </div>;
      })}
    </Box>
    <Composer canSend={data.canSend} reason={data.message} onSend={send} placeholder={`${data.other.nickname}님에게 쪽지 보내기`} />
    <Dialog open={Boolean(leaveOpen)} onClose={() => setLeaveOpen(null)} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 800 }}>이 대화에서 나갈까요?</DialogTitle>
      <DialogContent sx={{ color: '#475569', fontSize: '0.9rem' }}>지금까지의 쪽지가 내 쪽지함에서 사라져요. 상대 쪽지함에는 남아 있고, 상대가 새 쪽지를 보내면 그때부터 다시 보여요.</DialogContent>
      <DialogActions><Button onClick={() => setLeaveOpen(null)}>취소</Button><Button color="error" onClick={leave}>나가기</Button></DialogActions>
    </Dialog>
  </Box>;
}

/** 새 쪽지: 프로필 카드에서 '쪽지 보내기'를 누르면 온다. 이미 대화가 있으면 그 대화로 바꿔 연다. */
function ComposeView({ toUserSeq, contextId, onBack }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const card = useQuery({ queryKey: [...COMMUNITY_KEY, 'card', toUserSeq, true], queryFn: () => communityApi.card(toUserSeq), staleTime: 0 });
  const data = card.data?.resultCode === 'SUCCESS' ? card.data.data : null;
  // 이미 대화가 있으면 그 대화로. 캐시에 남은 예전 값(나가기 전 등)으로 잘못 가지 않게 새로 받아 온 값만 본다.
  const threadSeq = card.isFetching ? null : data?.dm?.threadSeq;
  useEffect(() => { if (threadSeq) navigate(`/messages?thread=${threadSeq}`, { replace: true }); }, [threadSeq]); // eslint-disable-line react-hooks/exhaustive-deps
  if (card.isLoading || card.isFetching || threadSeq) return <Box className="dm-page"><Skeleton height={56} /><Skeleton height={120} /></Box>;
  if (!data || data.withdrawn || data.me) return <Box className="dm-page"><Alert severity="error" action={<Button color="inherit" size="small" onClick={onBack}>쪽지함</Button>}>
    {data?.me ? '나에게는 쪽지를 보낼 수 없어요.' : card.data?.resultMessage || '쪽지를 보낼 회원을 찾을 수 없어요.'}</Alert></Box>;
  const send = async content => {
    const result = await run(() => communityApi.send(toUserSeq, content, contextId));
    if (result.ok) {
      queryClient.invalidateQueries({ queryKey: COMMUNITY_KEY });
      navigate(`/messages?thread=${result.data.threadSeq}`, { replace: true });
    }
    return result;
  };
  return <Box className="dm-page dm-thread">
    <ThreadHeader other={{ userSeq: toUserSeq, nickname: data.nickname, profileIcon: data.profileIcon }} contextId={contextId} onBack={onBack} />
    <Box className="dm-list dm-intro">
      <MailOutlineRounded className="dm-intro-icon" />
      <h2>{data.nickname}님에게 첫 쪽지</h2>
      <ul>
        <li>상대가 답하기 전에는 한 통만 보낼 수 있어요. 인사와 용건을 한 번에 적어 주세요.</li>
        <li>첫 쪽지에는 링크·전화번호·메신저 아이디·계좌번호를 넣을 수 없어요.</li>
        <li>청약통장 매매·분양권 전매 알선은 불법이에요. 이런 쪽지는 신고해 주세요.</li>
      </ul>
    </Box>
    <Composer canSend={Boolean(data.dm?.canSend)} reason={data.dm?.message} onSend={send} placeholder="예) 운정 ○○ 단지 이야기 보고 연락드려요. 혹시 …" autoFocus />
  </Box>;
}

/** 쪽지함 목록. 최근 대화 순. */
function ThreadList() {
  const navigate = useNavigate();
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const threads = useInfiniteQuery({
    queryKey: [...COMMUNITY_KEY, 'threads', userSeq],
    queryFn: ({ pageParam }) => communityApi.threads(pageParam),
    initialPageParam: 0,
    getNextPageParam: last => (last?.hasMore ? last.page + 1 : undefined),
    refetchInterval: 60000,
    refetchOnWindowFocus: true
  });
  const pages = threads.data?.pages || [];
  // 페이지 사이에 새 대화가 생겨 순서가 밀려도 같은 대화가 두 번 보이지 않게 한다.
  const items = uniqueThreads(pages);
  const ready = pages[0]?.ready !== false;
  return <Box className="account-page dm-page">
    <header className="account-hero dm-hero"><span className="account-eyebrow">MESSAGES</span><h1>쪽지함</h1>
      <p>이야기에서 만난 회원과 1:1로 이야기해요. 이야기에서 닉네임을 누르면 쪽지를 보낼 수 있어요.</p>
      <Button size="small" startIcon={<SettingsOutlined />} component={Link} to="/account#account-messages">받는 범위·차단 관리</Button></header>
    {!ready && <Alert severity="info">쪽지 기능을 준비하고 있어요. 잠시 뒤에 다시 와 주세요.</Alert>}
    {threads.isLoading && <><Skeleton height={72} /><Skeleton height={72} /></>}
    {threads.isError && <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => threads.refetch()}>다시 시도</Button>}>쪽지함을 불러오지 못했어요.</Alert>}
    {ready && !threads.isLoading && !threads.isError && items.length === 0 && <section className="account-card dm-empty">
      <MailOutlineRounded /><h2>아직 주고받은 쪽지가 없어요</h2>
      <p>공고 상세의 ‘이야기’에서 궁금한 점을 나눈 회원의 닉네임을 누르면 쪽지를 보낼 수 있어요.</p>
      <Button variant="outlined" component={Link} to="/">청약 캘린더로</Button></section>}
    {items.length > 0 && <ul className="dm-threads">
      {items.map(item => <li key={item.threadSeq}>
        <button type="button" className={`dm-thread-item${item.unread > 0 ? ' is-unread' : ''}`} onClick={() => navigate(`/messages?thread=${item.threadSeq}`)}>
          <ProfileAvatar icon={item.other.profileIcon} name={item.other.nickname} size={44} decorative />
          <span className="dm-thread-text">
            <span className="dm-thread-top"><b>{item.other.nickname}</b><small>{messageTime(item.lastAt)}</small></span>
            <span className="dm-thread-preview">{item.lastMine ? '나: ' : ''}{item.lastPreview}</span>
            {item.houseNm && <small className="dm-thread-house">{item.houseNm}</small>}
          </span>
          {item.unread > 0 && <span className="dm-unread" aria-label={`안 읽은 쪽지 ${item.unread}개`}>{item.unread > 99 ? '99+' : item.unread}</span>}
        </button>
      </li>)}
    </ul>}
    {threads.hasNextPage && <Button fullWidth onClick={() => threads.fetchNextPage()} disabled={threads.isFetchingNextPage}>{threads.isFetchingNextPage ? '불러오는 중…' : '더 보기'}</Button>}
  </Box>;
}

/**
 * 쪽지 화면(/messages). 주소의 쿼리로 목록·대화(?thread=)·새 쪽지(?to=&context=)를 나눈다.
 * 푸시 알림도 /messages?thread=로 열리며, 서비스 워커는 경로(/messages)만 보고 앱 안에서 이동한다.
 */
export default function MessagesPage() {
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const [params] = useSearchParams();
  const location = useLocation(), navigate = useNavigate();
  const threadSeq = Number(params.get('thread')) || null;
  const toUserSeq = Number(params.get('to')) || null;
  const contextId = params.get('context');
  useEffect(() => {
    updatePageSeo({ title: '쪽지함 | TOADY', description: '이야기에서 만난 회원과 주고받은 쪽지', path: '/messages' });
    return () => resetDefaultSeo();
  }, []);
  // 앱 안에서 들어왔으면 뒤로, 알림·주소로 바로 들어왔으면 쪽지함으로.
  const back = () => { if ((window.history.state?.idx ?? 0) > 0) navigate(-1); else navigate('/messages', { replace: true }); };

  if (!isAuthenticated) {
    const openLogin = () => navigate(location.pathname + location.search, { state: { ...location.state, overlayLayers: [...(location.state?.overlayLayers || []), { id: 'layout', value: 'login' }] } });
    return <Box className="account-page dm-page"><section className="account-card account-empty"><MailOutlineRounded /><h2>로그인이 필요해요</h2>
      <p>로그인하면 이야기에서 만난 회원과 쪽지를 주고받을 수 있어요.</p>
      <Button variant="contained" disableElevation onClick={openLogin}>로그인</Button></section></Box>;
  }
  if (threadSeq) return <ThreadView key={threadSeq} threadSeq={threadSeq} onBack={back} />;
  if (toUserSeq) return <ComposeView key={toUserSeq} toUserSeq={toUserSeq} contextId={contextId} onBack={back} />;
  return <ThreadList />;
}
