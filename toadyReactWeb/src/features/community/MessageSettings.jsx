import { useEffect, useRef, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import ProfileAvatar from '@/features/account/ProfileAvatar';
import { COMMUNITY_KEY, RECEIVE_POLICIES, communityApi, run } from './communityApi';
import { useMessageUnread } from './useCommunity';

/** 내 정보의 '쪽지·차단' 칸: 쪽지함 바로가기, 받는 범위, 차단한 회원 관리. */
export default function MessageSettings() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const queryClient = useQueryClient();
  const unread = useMessageUnread();
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const settings = useQuery({ queryKey: [...COMMUNITY_KEY, 'settings', userSeq], queryFn: communityApi.settings, enabled: userSeq != null });
  const blocks = useQuery({ queryKey: [...COMMUNITY_KEY, 'blocks', userSeq], queryFn: communityApi.blocks, enabled: userSeq != null, staleTime: 300000 });
  const ready = settings.data?.ready !== false;
  const policy = settings.data?.policy || 'ALL';
  const items = blocks.data?.items || [];
  // 쪽지함의 '받는 범위·차단 관리'(#account-messages)로 들어오면 이 칸으로 내려 준다.
  const location = useLocation();
  const sectionRef = useRef(null);
  // 이 주소(#account-messages)로 들어온 처음 한 번만 옮긴다. 같은 화면에서 푸시 설정 창을 열고 닫거나(히스토리 칸이 바뀜)
  // 읽은 답글 기록을 고쳐 써도 다시 튀지 않게 한다.
  const scrolledFor = useRef(null);
  useEffect(() => {
    if (location.hash !== '#account-messages') { scrolledFor.current = null; return undefined; }
    if (settings.isLoading || scrolledFor.current === location.hash) return undefined;
    scrolledFor.current = location.hash;
    // 위쪽 칸(프로필·요약)이 그려진 뒤 바로 옮긴다. 부드러운 스크롤은 이어지는 다시 그리기에 끊길 수 있어 즉시 이동한다.
    const timer = setTimeout(() => sectionRef.current?.scrollIntoView({ block: 'start' }), 120);
    return () => clearTimeout(timer);
  }, [location.hash, settings.isLoading]);

  const changePolicy = async value => {
    setBusy(true);
    const result = await run(() => communityApi.setPolicy(value));
    setBusy(false);
    setNotice({ severity: result.ok ? 'success' : 'error', text: result.message });
    if (result.ok) queryClient.invalidateQueries({ queryKey: [...COMMUNITY_KEY, 'settings'] });
  };
  const unblock = async item => {
    setBusy(true);
    const result = await run(() => communityApi.unblock(item.userSeq));
    setBusy(false);
    setNotice({ severity: result.ok ? 'success' : 'error', text: result.ok ? `${item.nickname}님 차단을 풀었어요.` : result.message });
    if (result.ok) queryClient.invalidateQueries({ queryKey: COMMUNITY_KEY });
  };

  return <section className="account-card" id="account-messages" ref={sectionRef} style={{ scrollMarginTop: 72 }} aria-labelledby="account-messages-title">
    <h2 id="account-messages-title"><MailOutlineRounded fontSize="small" />쪽지·차단</h2>
    <Link to="/messages" className="account-link-row"><div><span className="account-value">쪽지함{unread > 0 && <span className="account-new-count">새 쪽지 {unread}</span>}</span>
      <small>이야기에서 만난 회원과 주고받은 쪽지를 봐요.</small></div><ChevronRightRounded /></Link>
    {!ready && <Alert severity="info" sx={{ mt: 1 }}>쪽지·차단 기능을 준비하고 있어요.</Alert>}
    {settings.data?.suspendedUntil && <Alert severity="warning" sx={{ mt: 1 }}>운영 정책 위반으로 {String(settings.data.suspendedUntil).slice(0, 10)}까지 쪽지를 보낼 수 없어요.</Alert>}
    {notice && <Alert severity={notice.severity} onClose={() => setNotice(null)} sx={{ mt: 1 }}>{notice.text}</Alert>}
    {ready && <div className="account-row account-row-stack">
      <div><span className="account-value">쪽지 받는 범위</span><small>누가 나에게 첫 쪽지를 보낼 수 있는지 정해요.</small></div>
      <RadioGroup value={policy} onChange={event => changePolicy(event.target.value)} aria-label="쪽지 받는 범위" className="report-reasons">
        {RECEIVE_POLICIES.map(item => <FormControlLabel key={item.value} value={item.value} disabled={busy || settings.isLoading} control={<Radio size="small" />}
          label={<span className="report-reason"><b>{item.label}</b><small>{item.hint}</small></span>} />)}
      </RadioGroup>
    </div>}
    {ready && <div className="account-row account-row-stack">
      <div><span className="account-value">차단한 회원 {items.length > 0 ? items.length : ''}</span>
        <small>{items.length ? '차단을 풀면 다시 쪽지를 주고받고 이야기가 펼쳐져 보여요.' : '이야기에서 닉네임을 누르면 차단할 수 있어요.'}</small></div>
      {items.length > 0 && <ul className="account-block-list">
        {items.map(item => <li key={item.userSeq}><ProfileAvatar icon={item.profileIcon} name={item.nickname} size={30} decorative /><span>{item.nickname}</span>
          <Button size="small" variant="outlined" onClick={() => unblock(item)} disabled={busy}>차단 풀기</Button></li>)}
      </ul>}
    </div>}
  </section>;
}
