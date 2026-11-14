import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, IconButton, InputAdornment, Skeleton, TextField } from '@mui/material';
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded';
import LockOutlined from '@mui/icons-material/LockOutlined';
import NotificationsNoneRounded from '@mui/icons-material/NotificationsNoneRounded';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded';
import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import EditRounded from '@mui/icons-material/EditRounded';
import SubdirectoryArrowRightRounded from '@mui/icons-material/SubdirectoryArrowRightRounded';
import ProfileAvatar, { PROFILE_ICONS } from './ProfileAvatar';
import { readSeen, useMyAccount, useMyReplies } from './useMyAccount';
import ForumOutlined from '@mui/icons-material/ForumOutlined';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import PushSettingsDialog from '@/features/push/PushSettingsDialog';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { ACCOUNT_KEY, NICKNAME_RULE, PASSWORD_RULE, accountApi } from './accountApi';
import './account.css';

const count = value => (value == null ? '—' : Number(value).toLocaleString('ko-KR'));
const joined = value => {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 가입`;
};

/** 서버가 준 새 토큰으로 이 기기 로그인을 이어 간다. 토큰을 읽지 못하면 다시 로그인하라고 안내한다(조용히 실패하지 않게). */
function adoptToken(login, token) {
  if (!token) return true;
  try { login(token); return true; } catch { return false; }
}
const RELOGIN = '변경은 됐지만 이 기기의 로그인 정보를 갱신하지 못했어요. 다시 로그인해 주세요.';

/** 서버 결과(성공·실패 문구)와 요청 제한·통신 오류를 화면 안내로 바꾼다. */
async function run(request) {
  try {
    const res = await request();
    return { ok: res?.resultCode === 'SUCCESS', message: res?.resultMessage || '', data: res?.data };
  } catch (error) {
    return { ok: false, message: error.status === 429 ? '잠시 뒤에 다시 시도해 주세요. 짧은 시간에 너무 많이 시도했어요.' : (error.message || '처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.') };
  }
}

function PasswordField({ label, value, onChange, autoComplete, helperText, error }) {
  const [show, setShow] = useState(false);
  return <TextField label={label} type={show ? 'text' : 'password'} value={value} onChange={e => onChange(e.target.value)} autoComplete={autoComplete}
    fullWidth size="small" helperText={helperText} error={error} inputProps={{ maxLength: 72 }}
    InputProps={{ endAdornment: <InputAdornment position="end"><IconButton size="small" edge="end" aria-label={show ? '비밀번호 숨기기' : '비밀번호 보기'} onClick={() => setShow(on => !on)}>{show ? <VisibilityOff fontSize="small" /> : <Visibility fontSize="small" />}</IconButton></InputAdornment> }} />;
}

function NicknameForm({ current, onDone, onCancel }) {
  const login = useAuthStore(state => state.login);
  const [value, setValue] = useState(current || ''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const invalid = value.length > 0 && !NICKNAME_RULE.test(value);
  const submit = async e => {
    e.preventDefault();
    if (!NICKNAME_RULE.test(value)) { setError('한글·영문·숫자로 2~15자까지 쓸 수 있어요.'); return; }
    setBusy(true); setError('');
    const result = await run(() => accountApi.changeNickname(value));
    setBusy(false);
    if (!result.ok) { setError(result.message); return; }
    // 화면 곳곳의 닉네임(메뉴 등)이 토큰에서 오므로 새 토큰으로 바꾼다.
    if (!adoptToken(login, result.data?.accessToken)) { setError(RELOGIN); return; }
    onDone(result.message);
  };
  return <form className="account-form" onSubmit={submit}>
    <TextField label="새 닉네임" value={value} onChange={e => setValue(e.target.value.trim())} size="small" fullWidth autoFocus inputProps={{ maxLength: 15 }}
      error={invalid || Boolean(error)} helperText={error || (invalid ? '한글·영문·숫자만, 2~15자' : '이야기(댓글)에 보이는 이름이에요. 한글·영문·숫자 2~15자')} />
    <div className="account-form-actions">
      <Button onClick={onCancel} disabled={busy}>취소</Button>
      <Button type="submit" variant="contained" disableElevation disabled={busy || value === current}>{busy ? <CircularProgress size={18} color="inherit" /> : '저장'}</Button>
    </div>
  </form>;
}

function PasswordForm({ onDone, onCancel }) {
  const login = useAuthStore(state => state.login);
  const [current, setCurrent] = useState(''), [next, setNext] = useState(''), [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const weak = next.length > 0 && !PASSWORD_RULE.test(next), mismatch = confirm.length > 0 && confirm !== next;
  const submit = async e => {
    e.preventDefault();
    if (!current) { setError('현재 비밀번호를 입력해 주세요.'); return; }
    if (!PASSWORD_RULE.test(next)) { setError('새 비밀번호는 6~20자로, 영문과 숫자 또는 특수문자를 함께 써 주세요.'); return; }
    if (next !== confirm) { setError('새 비밀번호와 확인이 서로 달라요.'); return; }
    setBusy(true); setError('');
    const result = await run(() => accountApi.changePassword(current, next, confirm));
    setBusy(false);
    if (!result.ok) { setError(result.message); return; }
    // 비밀번호가 바뀌면 이전 토큰은 모두 무효가 되므로, 이 기기는 새 토큰으로 로그인을 이어 간다.
    if (!adoptToken(login, result.data?.accessToken)) { setError(RELOGIN); return; }
    onDone(result.message);
  };
  return <form className="account-form" onSubmit={submit}>
    <input type="text" name="username" autoComplete="username" hidden readOnly value="" />
    <PasswordField label="현재 비밀번호" value={current} onChange={setCurrent} autoComplete="current-password" />
    <PasswordField label="새 비밀번호" value={next} onChange={setNext} autoComplete="new-password" error={weak} helperText="6~20자, 영문과 숫자 또는 특수문자를 함께" />
    <PasswordField label="새 비밀번호 확인" value={confirm} onChange={setConfirm} autoComplete="new-password" error={mismatch} helperText={mismatch ? '위의 새 비밀번호와 달라요' : ' '} />
    {error && <Alert severity="error">{error}</Alert>}
    <p className="account-note">바꾸면 이 기기는 로그인이 유지되고, 다른 기기와 브라우저는 다시 로그인해야 해요.</p>
    <div className="account-form-actions">
      <Button onClick={onCancel} disabled={busy}>취소</Button>
      <Button type="submit" variant="contained" disableElevation disabled={busy}>{busy ? <CircularProgress size={18} color="inherit" /> : '비밀번호 바꾸기'}</Button>
    </div>
  </form>;
}

function WithdrawForm({ phrase, onDone, onCancel }) {
  const [password, setPassword] = useState(''), [typed, setTyped] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const ready = password.length > 0 && typed.trim() === phrase;
  const submit = async e => {
    e.preventDefault();
    if (!ready) return;
    setBusy(true); setError('');
    const result = await run(() => accountApi.withdraw(password, typed.trim()));
    setBusy(false);
    if (!result.ok) { setError(result.message); return; }
    onDone(result.message);
  };
  return <form className="account-form" onSubmit={submit}>
    <input type="text" name="username" autoComplete="username" hidden readOnly value="" />
    <PasswordField label="비밀번호" value={password} onChange={setPassword} autoComplete="current-password" />
    <TextField label={`확인 문구: ${phrase}`} value={typed} onChange={e => setTyped(e.target.value)} size="small" fullWidth placeholder={phrase} helperText={`탈퇴하려면 '${phrase}'를 그대로 입력하세요.`} />
    {error && <Alert severity="error">{error}</Alert>}
    <div className="account-form-actions">
      <Button onClick={onCancel} disabled={busy}>취소</Button>
      <Button type="submit" variant="contained" color="error" disableElevation disabled={!ready || busy}>{busy ? <CircularProgress size={18} color="inherit" /> : '탈퇴하기'}</Button>
    </div>
  </form>;
}

/** 준비된 캐릭터 중에서 고른다. 눌러서 미리 보고 '저장'으로 확정한다(요청 제한을 아끼고 실수로 바뀌지 않게). */
function IconPicker({ current, nickname, onDone, onCancel }) {
  const [picked, setPicked] = useState(current), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const save = async () => {
    setBusy(true); setError('');
    const result = await run(() => accountApi.setProfileIcon(picked));
    setBusy(false);
    if (!result.ok) { setError(result.message); return; }
    onDone(result.message);
  };
  const options = [{ key: null, label: '첫 글자' }, ...PROFILE_ICONS];
  return <div className="account-form">
    <h2>프로필 캐릭터</h2>
    <p className="account-note">이야기(댓글)를 쓸 때 닉네임 옆에 함께 보여요.</p>
    <div className="account-icon-grid" role="radiogroup" aria-label="프로필 캐릭터">
      {options.map(option => <button key={option.key || 'none'} type="button" role="radio" aria-checked={picked === option.key}
        className={`account-icon-option${option.key ? '' : ' is-none'}`} onClick={() => setPicked(option.key)}>
        <ProfileAvatar icon={option.key} name={nickname} size={48} decorative /><span>{option.label}</span>
      </button>)}
    </div>
    {error && <Alert severity="error">{error}</Alert>}
    <div className="account-form-actions">
      <Button onClick={onCancel} disabled={busy}>취소</Button>
      <Button variant="contained" disableElevation onClick={save} disabled={busy || picked === current}>{busy ? <CircularProgress size={18} color="inherit" /> : '저장'}</Button>
    </div>
  </div>;
}

const storyDate = value => {
  if (!value) return '';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
};

/** 받은 답글: 내 이야기에 다른 회원이 단 답글. 화면에 보이면 '봤음'으로 기억해 메뉴의 새 답글 표시를 끈다(목록의 '새 답글' 표시는 이번 화면에서는 남긴다). */
function ReceivedReplies() {
  const navigate = useNavigate();
  const replies = useMyReplies();
  const items = replies.data?.items || [];
  const latest = replies.data?.latestSeq;
  useEffect(() => { if (latest) replies.markSeen(); }, [latest]);
  if (replies.isLoading || (!items.length && !replies.isError)) return null;
  const open = item => navigate(`/?subscriptSeq=${encodeURIComponent(item.announcementId)}&tabType=comment&comment=${item.commentSeq}`, { state: { detailOverlay: true } });
  const fresh = items.filter(item => item.isNew).length;
  return <section className="account-card account-replies" aria-labelledby="account-replies-title">
    <div className="account-stories-head"><h2 id="account-replies-title"><ForumOutlined fontSize="small" />받은 답글{fresh > 0 && <span className="account-new-count">새 답글 {fresh}</span>}</h2></div>
    {replies.isError && <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => replies.refetch()}>다시 시도</Button>}>답글을 불러오지 못했어요.</Alert>}
    <ul className="account-story-list">
      {items.slice(0, 5).map(item => <li key={item.commentSeq}>
        <button type="button" className={`account-story account-reply${item.isNew ? ' is-new' : ''}`} onClick={() => open(item)}>
          <ProfileAvatar icon={item.profileIcon} name={item.nickname || '?'} size={32} decorative />
          <span className="account-reply-body">
            <span className="account-story-house">{item.houseNm || '공고 정보 없음'}{item.isNew && <i className="account-new-dot">새 답글</i>}</span>
            <span className="account-story-content"><b>{item.nickname || '회원'}</b> {item.content}</span>
            <small>내 이야기: {item.parentContent} · {storyDate(item.regDt)}</small>
          </span>
          <ChevronRightRounded className="account-story-go" />
        </button>
      </li>)}
    </ul>
  </section>;
}

/** 내가 쓴 이야기. 누르면 그 공고의 이야기 탭을 열고 해당 이야기로 이동한다(뒤로가기로 이 목록에 돌아온다). */
function MyStories({ onClose, seen }) {
  const navigate = useNavigate();
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const stories = useInfiniteQuery({
    queryKey: ['account', 'stories', userSeq],
    queryFn: ({ pageParam }) => accountApi.myComments(pageParam),
    initialPageParam: 0,
    getNextPageParam: last => (last?.hasMore ? last.page + 1 : undefined),
    enabled: userSeq != null,
    staleTime: 30000
  });
  const items = (stories.data?.pages || []).flatMap(page => page?.items || []);
  const open = item => navigate(`/?subscriptSeq=${encodeURIComponent(item.announcementId)}&tabType=comment&comment=${item.commentSeq}`, { state: { detailOverlay: true } });
  return <section className="account-card account-stories" id="account-stories" aria-labelledby="account-stories-title">
    <div className="account-stories-head"><h2 id="account-stories-title"><ChatBubbleOutlineRounded fontSize="small" />내가 쓴 이야기</h2>
      <Button size="small" onClick={onClose}>접기</Button></div>
    {stories.isLoading && <><Skeleton height={56} /><Skeleton height={56} /></>}
    {stories.isError && <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => stories.refetch()}>다시 시도</Button>}>이야기를 불러오지 못했어요.</Alert>}
    {!stories.isLoading && !stories.isError && items.length === 0 && <p className="account-note account-stories-empty">아직 쓴 이야기가 없어요. 공고 상세의 ‘이야기’ 탭에서 경험이나 질문을 남겨 보세요.</p>}
    <ul className="account-story-list">
      {items.map(item => <li key={item.commentSeq}>
        <button type="button" className="account-story" onClick={() => open(item)}>
          <span className="account-story-house">{item.reply && <SubdirectoryArrowRightRounded fontSize="inherit" aria-label="답글" />}{item.houseNm || '공고 정보 없음'}</span>
          <span className="account-story-content">{item.content}</span>
          <small>{storyDate(item.regDt)}{item.edited ? ' · 수정됨' : ''}{item.replyCount > 0 && <span className={`account-reply-count${seen && item.lastReplySeq > seen ? ' is-new' : ''}`}> · 답글 {item.replyCount}</span>}</small>
          <ChevronRightRounded className="account-story-go" />
        </button>
      </li>)}
    </ul>
    {stories.hasNextPage && <Button fullWidth onClick={() => stories.fetchNextPage()} disabled={stories.isFetchingNextPage}>{stories.isFetchingNextPage ? '불러오는 중…' : '더 보기'}</Button>}
  </section>;
}

/** 내 정보: 프로필·닉네임·비밀번호, 활동 요약, 알림, 로그아웃·탈퇴. 로그인하지 않았으면 로그인 안내만 보여 준다. */
export default function AccountPage() {
  // 이 화면에 들어온 순간의 '본 답글' 번호. 받은 답글이 보이면 곧 최신으로 바뀌므로, 내가 쓴 이야기의 '새 답글' 표시는 이 값으로 정한다.
  const enteredUser = useAuthStore(state => state.user?.userSeq ?? null);
  const [seenOnEnter] = useState(() => (enteredUser == null ? null : readSeen(enteredUser)));
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const logout = useAuthStore(state => state.logout);
  const location = useLocation(), navigate = useNavigate();
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(null), [notice, setNotice] = useState(''), [farewell, setFarewell] = useState('');
  const [emailConfirm, setEmailConfirm] = useState(false);
  const [pushOpen, setPushOpen] = useHistoryOverlay('push-settings');
  const summary = useMyAccount();
  // '내가 쓴 이야기' 펼침은 주소에 남겨, 이야기를 눌러 공고로 갔다가 뒤로 와도 그대로 열려 있게 한다.
  const [searchParams, setSearchParams] = useSearchParams();
  const storiesOpen = searchParams.get('stories') === '1';
  const toggleStories = () => { const next = new URLSearchParams(searchParams); if (storiesOpen) next.delete('stories'); else next.set('stories', '1'); setSearchParams(next, { replace: true, state: location.state }); };
  const data = summary.data;

  useEffect(() => {
    updatePageSeo({ title: '내 정보 | TOADY', description: '닉네임·비밀번호와 알림 설정을 관리합니다.', path: '/account' });
    return () => resetDefaultSeo();
  }, []);

  const openLogin = () => navigate(location.pathname + location.search, { state: { ...location.state, overlayLayers: [...(location.state?.overlayLayers || []), { id: 'layout', value: 'login' }] } });
  // 완료 안내는 화면 위쪽에 뜨므로, 아래쪽 칸에서 저장해도 보이게 맨 위로 올린다.
  const finished = message => { setEditing(null); setNotice(message); queryClient.invalidateQueries({ queryKey: ACCOUNT_KEY }); window.scrollTo({ top: 0, behavior: 'smooth' }); };
  const disableEmails = async () => {
    const result = await run(() => accountApi.disableFavoriteEmails());
    setEmailConfirm(false);
    setNotice(result.ok ? result.message : result.message || '이메일 알림을 끄지 못했어요.');
    queryClient.invalidateQueries({ queryKey: ACCOUNT_KEY });
    queryClient.invalidateQueries({ queryKey: ['planner'] });
  };

  if (farewell) return <Box className="account-page"><section className="account-card account-farewell" role="status">
    <h1>탈퇴가 끝났어요</h1><p>{farewell}</p><p className="account-note">같은 이메일로 언제든 다시 가입할 수 있어요.</p>
    <Button variant="contained" disableElevation component={Link} to="/" replace>청약 캘린더로</Button>
  </section></Box>;

  if (!isAuthenticated) return <Box className="account-page">
    <header className="account-hero"><span className="account-eyebrow">MY ACCOUNT</span><h1>내 정보</h1><p>닉네임·비밀번호와 알림을 한곳에서 관리해요.</p></header>
    <section className="account-card account-empty"><PersonOutlineRounded /><h2>로그인이 필요해요</h2><p>로그인하면 닉네임과 비밀번호를 바꾸고, 관심 공고 알림을 관리할 수 있어요.</p>
      <Button variant="contained" disableElevation onClick={openLogin}>로그인</Button></section>
  </Box>;

  return <Box className="account-page">
    <header className="account-hero"><span className="account-eyebrow">MY ACCOUNT</span><h1>내 정보</h1><p>닉네임·비밀번호와 알림을 한곳에서 관리해요.</p></header>
    {notice && <Alert severity="success" onClose={() => setNotice('')} sx={{ mb: 2 }}>{notice}</Alert>}
    {summary.isError && <Alert severity="error" sx={{ mb: 2 }} action={<Button color="inherit" size="small" onClick={() => summary.refetch()}>다시 시도</Button>}>내 정보를 불러오지 못했어요.</Alert>}

    <section className="account-card account-profile" aria-label="프로필">
      <button type="button" className="account-avatar-button" aria-label="프로필 캐릭터 바꾸기" aria-expanded={editing === 'icon'}
        onClick={() => { setEditing(editing === 'icon' ? null : 'icon'); setNotice(''); }}>
        <ProfileAvatar icon={data?.profileIcon} name={data?.nickname} size={60} decorative />
        <span className="account-avatar-edit" aria-hidden="true"><EditRounded /></span>
      </button>
      <div className="account-profile-text">
        {summary.isLoading ? <><Skeleton width={120} height={28} /><Skeleton width={200} /></> : <>
          <strong>{data?.nickname}</strong><span>{data?.email}</span><small>{joined(data?.createdAt)}</small></>}
      </div>
    </section>

    {editing === 'icon' && <section className="account-card" aria-label="프로필 캐릭터 고르기">
      <IconPicker current={data?.profileIcon || null} nickname={data?.nickname} onDone={finished} onCancel={() => setEditing(null)} />
    </section>}

    <nav className="account-stats" aria-label="내 활동">
      <Link to="/my" className="account-stat"><BookmarkBorderRounded /><span>관심 공고</span><b>{count(data?.favorites)}</b><ChevronRightRounded className="account-chevron" /></Link>
      <button type="button" className={`account-stat${storiesOpen ? ' is-open' : ''}`} aria-expanded={storiesOpen} aria-controls="account-stories" onClick={toggleStories}>
        <ChatBubbleOutlineRounded /><span>작성한 이야기</span><b>{count(data?.comments)}</b><ChevronRightRounded className="account-chevron" /></button>
      <div className="account-stat"><MailOutlineRounded /><span>이메일 알림 공고</span><b>{count(data?.emailAlerts)}</b></div>
      <div className="account-stat"><NotificationsNoneRounded /><span>알림 받는 기기</span><b>{count(data?.pushDevices)}</b></div>
    </nav>

    <ReceivedReplies />

    {storiesOpen && <MyStories onClose={toggleStories} seen={seenOnEnter} />}

    <section className="account-card" aria-labelledby="account-login-title">
      <h2 id="account-login-title"><LockOutlined fontSize="small" />로그인 정보</h2>
      <div className="account-row"><div><span className="account-label">이메일</span><span className="account-value">{data?.email || '—'}</span><small>로그인에 쓰는 주소는 바꿀 수 없어요.</small></div></div>
      <div className="account-row"><div><span className="account-label">닉네임</span><span className="account-value">{data?.nickname || '—'}</span></div>
        {editing !== 'nickname' && <Button size="small" variant="outlined" onClick={() => { setEditing('nickname'); setNotice(''); }}>변경</Button>}</div>
      {editing === 'nickname' && <NicknameForm current={data?.nickname} onDone={finished} onCancel={() => setEditing(null)} />}
      <div className="account-row"><div><span className="account-label">비밀번호</span><span className="account-value">••••••••</span></div>
        {editing !== 'password' && <Button size="small" variant="outlined" onClick={() => { setEditing('password'); setNotice(''); }}>변경</Button>}</div>
      {editing === 'password' && <PasswordForm onDone={finished} onCancel={() => setEditing(null)} />}
    </section>

    <section className="account-card" aria-labelledby="account-alert-title">
      <h2 id="account-alert-title"><NotificationsNoneRounded fontSize="small" />알림</h2>
      <button type="button" className="account-link-row" onClick={() => setPushOpen(true)}><div><span className="account-value">웹 푸시 알림</span><small>관심 공고 일정과 관심 지역의 새 공고를 이 기기로 알려 드려요.</small></div><ChevronRightRounded /></button>
      <div className="account-row"><div><span className="account-value">관심 공고 이메일 알림</span><small>{data?.emailAlerts ? `공고 ${count(data.emailAlerts)}곳의 접수 일정을 메일로 받고 있어요.` : '메일로 받는 공고가 없어요. 나의 청약에서 공고마다 켤 수 있어요.'}</small></div>
        {data?.emailAlerts > 0 && !emailConfirm && <Button size="small" variant="outlined" onClick={() => setEmailConfirm(true)}>모두 끄기</Button>}</div>
      {emailConfirm && <Alert severity="warning" action={<><Button color="inherit" size="small" onClick={() => setEmailConfirm(false)}>취소</Button><Button color="inherit" size="small" onClick={disableEmails}>끄기</Button></>}>모든 관심 공고의 이메일 알림을 끌까요? 관심 공고 목록은 그대로 남아요.</Alert>}
      <Link to="/my" className="account-link-row"><div><span className="account-value"><CalendarMonthOutlined fontSize="inherit" /> 캘린더 연결</span><small>{data?.calendarFeed ? '휴대폰 캘린더에 관심 공고 일정을 연결해 두었어요.' : '관심 공고 일정을 휴대폰 캘린더에서 볼 수 있어요. 나의 청약에서 연결해요.'}</small></div><ChevronRightRounded /></Link>
    </section>

    <section className="account-card" aria-labelledby="account-session-title">
      <h2 id="account-session-title"><PersonOutlineRounded fontSize="small" />계정</h2>
      <div className="account-row"><div><span className="account-value">로그아웃</span><small>이 기기에서만 로그아웃해요.</small></div>
        <Button size="small" variant="outlined" onClick={() => { logout(); navigate('/', { replace: true }); }}>로그아웃</Button></div>
      <div className="account-row account-danger"><div><span className="account-value">회원 탈퇴</span>
        <small>관심 공고·알림·캘린더 연결과 이메일·닉네임이 지워져요. 작성한 이야기는 '탈퇴회원'으로 남아요. 되돌릴 수 없어요.</small></div>
        {editing !== 'withdraw' && <Button size="small" color="error" onClick={() => { setEditing('withdraw'); setNotice(''); }}>탈퇴</Button>}</div>
      {editing === 'withdraw' && <WithdrawForm phrase={data?.withdrawPhrase || '탈퇴합니다'} onCancel={() => setEditing(null)}
        onDone={message => { queryClient.clear(); logout(); setFarewell(message); window.scrollTo({ top: 0 }); }} />}
    </section>

    <PushSettingsDialog open={Boolean(pushOpen)} onClose={() => setPushOpen(false)} loggedIn />
  </Box>;
}
