import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Dialog from '@mui/material/Dialog';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import CloseRounded from '@mui/icons-material/CloseRounded';
import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded';
import BlockRounded from '@mui/icons-material/BlockRounded';
import FlagOutlined from '@mui/icons-material/FlagOutlined';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import ProfileAvatar from '@/features/account/ProfileAvatar';
import { COMMUNITY_KEY, REPORT_REASONS, communityApi, parseCardValue, parseReportValue, reportValue, run } from './communityApi';
import './community.css';

/** 모바일은 아래에서 올라오는 시트, 넓은 화면은 가운데 창. */
const sheetProps = {
  fullWidth: true,
  maxWidth: 'xs',
  sx: { '& .MuiDialog-container': { alignItems: { xs: 'flex-end', sm: 'center' } } },
  PaperProps: { className: 'community-sheet', sx: { m: { xs: 0, sm: 2 }, width: { xs: '100%', sm: undefined }, maxWidth: { xs: '100%', sm: 420 } } }
};

// 'YYYY-MM' → '2024년 3월 가입'.
const joinedText = month => {
  if (!month) return '';
  const [year, value] = month.split('-');
  return `${year}년 ${Number(value)}월 가입`;
};

/** 차단·해제 뒤 카드·이야기 목록·쪽지 상태를 함께 새로 읽는다. */
const refreshCommunity = queryClient => queryClient.invalidateQueries({ queryKey: COMMUNITY_KEY });

/**
 * 프로필 카드. 이야기 작성자 이름·캐릭터를 누르면 뜬다(히스토리 층 'member-card', 뒤로가기로 닫힘).
 * 쪽지 보내기·차단·신고를 여기서 한다. 쪽지를 보낼 수 없으면 버튼을 막고 이유를 그대로 보여 준다.
 */
function MemberCard({ value, onClose }) {
  const parsed = parseCardValue(value);
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  // 로그인 창('layout')과 신고 창('report')도 히스토리 층이다. 값만 넘기면 레이아웃이 그 창을 연다. 값은 안 쓰므로 setter만 꺼낸다.
  const [, setLayout] = useHistoryOverlay('layout');
  const [, setReport] = useHistoryOverlay('report');
  const [confirmBlock, setConfirmBlock] = useState(false);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);
  const card = useQuery({
    // 로그인 여부도 키에 넣는다. 로그인 전후로 쪽지 가능 여부(dm)·차단 여부가 달라지기 때문이다.
    queryKey: [...COMMUNITY_KEY, 'card', parsed?.userSeq, isAuthenticated],
    queryFn: () => communityApi.card(parsed.userSeq),
    enabled: Boolean(parsed),
    staleTime: 15000
  });
  // 다른 회원 카드로 바뀌면(value 변경) 차단 확인·안내를 처음 상태로 돌린다.
  useEffect(() => { setConfirmBlock(false); setNotice(null); }, [value]);
  // 주소 값이 잘못돼 회원 번호를 읽지 못하면 카드를 바로 닫는다.
  useEffect(() => { if (!parsed) onClose(); }, [parsed == null]); // eslint-disable-line react-hooks/exhaustive-deps
  const data = card.data?.resultCode === 'SUCCESS' ? card.data.data : null;
  const failed = card.isError || (card.data && card.data.resultCode !== 'SUCCESS');
  const dm = data?.dm;

  // 쪽지 화면으로 갈 때는 카드 칸을 쪽지 화면으로 바꿔(replace), 뒤로가기가 카드를 다시 열지 않고 원래 화면으로 가게 한다.
  const openMessages = () => {
    if (!isAuthenticated) { setLayout('login'); return; }
    const params = dm?.threadSeq ? `thread=${dm.threadSeq}` : `to=${parsed.userSeq}${parsed.contextId ? `&context=${encodeURIComponent(parsed.contextId)}` : ''}`;
    // 이미 그 대화를 보고 있으면(대화 화면에서 상대 이름을 눌러 연 카드) 카드만 닫는다. 같은 주소가 두 칸 쌓이지 않게.
    if (location.pathname === '/messages' && new URLSearchParams(location.search).get('thread') === String(dm?.threadSeq)) { onClose(); return; }
    navigate(`/messages?${params}`, { replace: true });
  };
  // 차단은 한 번 더 눌러 확인한 뒤 실행하고(confirmBlock), 풀기는 바로 실행한다.
  const toggleBlock = async () => {
    if (!isAuthenticated) { setLayout('login'); return; }
    if (!data.blocked && !confirmBlock) { setConfirmBlock(true); return; }
    setBusy(true);
    const result = await run(() => (data.blocked ? communityApi.unblock(parsed.userSeq) : communityApi.block(parsed.userSeq)));
    setBusy(false); setConfirmBlock(false);
    setNotice({ severity: result.ok ? 'success' : 'error', text: result.message || (result.ok ? '바꿨어요.' : '처리하지 못했어요.') });
    if (result.ok) refreshCommunity(queryClient);
  };
  const openReport = () => {
    if (!isAuthenticated) { setLayout('login'); return; }
    setReport(reportValue('USER', parsed.userSeq, parsed.userSeq));
  };

  return <Dialog open onClose={onClose} {...sheetProps} aria-labelledby="member-card-title">
    <Box className="community-sheet-body">
      <IconButton className="community-sheet-close" aria-label="닫기" onClick={onClose}><CloseRounded /></IconButton>
      {card.isLoading && <Box className="member-card-loading"><CircularProgress size={28} /></Box>}
      {failed && <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => card.refetch()}>다시 시도</Button>}>
        {card.data?.resultMessage || '회원 정보를 불러오지 못했어요.'}</Alert>}
      {data && <>
        <Box className="member-card-head">
          <ProfileAvatar icon={data.profileIcon} name={data.nickname} size={64} decorative />
          <Box sx={{ minWidth: 0 }}>
            <Typography id="member-card-title" component="h2" className="member-card-name">{data.nickname}</Typography>
            {!data.withdrawn && <Typography className="member-card-meta">{[joinedText(data.joinedMonth), `이야기 ${data.stories}개`].filter(Boolean).join(' · ')}</Typography>}
          </Box>
        </Box>
        {data.withdrawn && <Alert severity="info">탈퇴한 회원이에요.</Alert>}
        {data.me && <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => navigate('/account', { replace: true })}>내 정보</Button>}>내 프로필이에요.</Alert>}
        {!data.withdrawn && !data.me && <>
          {data.blocked && <Alert severity="warning" className="member-card-note">차단한 회원이에요. 이 회원의 이야기는 접어서 보이고, 서로 쪽지를 보낼 수 없어요.</Alert>}
          <Button fullWidth variant="contained" disableElevation size="large" startIcon={<MailOutlineRounded />} onClick={openMessages}
            disabled={isAuthenticated && dm != null && !dm.canSend && !dm.threadSeq}>
            {dm?.threadSeq ? '대화 이어 가기' : '쪽지 보내기'}
          </Button>
          {!isAuthenticated && <Typography className="member-card-hint">로그인하면 쪽지를 보낼 수 있어요.</Typography>}
          {isAuthenticated && dm && !dm.canSend && dm.message && <Typography className="member-card-hint" role="note">{dm.message}</Typography>}
          {confirmBlock && <Alert severity="warning" className="member-card-note">차단하면 서로 쪽지를 보낼 수 없고, 이 회원의 이야기는 접어서 보여요. 상대에게는 알리지 않아요.</Alert>}
          <Box className="member-card-actions">
            <Button color={confirmBlock ? 'error' : 'inherit'} variant={confirmBlock ? 'contained' : 'text'} disableElevation startIcon={<BlockRounded />} onClick={toggleBlock} disabled={busy}>
              {data.blocked ? '차단 풀기' : confirmBlock ? '차단하기' : '차단'}
            </Button>
            {confirmBlock && <Button color="inherit" onClick={() => setConfirmBlock(false)}>취소</Button>}
            {!confirmBlock && <Button color="inherit" startIcon={<FlagOutlined />} onClick={openReport}>신고</Button>}
          </Box>
          {data.ready === false && <Typography className="member-card-hint">쪽지·차단·신고 기능을 준비하고 있어요.</Typography>}
        </>}
        {notice && <Alert severity={notice.severity} className="member-card-note" onClose={() => setNotice(null)}>{notice.text}</Alert>}
      </>}
    </Box>
  </Dialog>;
}

/**
 * 신고 창(히스토리 층 'report'). 이유를 고르고 필요하면 설명을 적는다.
 * 신고를 마치면, 아직 차단하지 않은 회원이면 바로 차단할 수 있게 한다.
 */
function ReportDialog({ value, onClose }) {
  const target = parseReportValue(value);
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [detail, setDetail] = useState('');
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState(null);
  const [blocked, setBlocked] = useState(false);
  const [blockError, setBlockError] = useState('');
  // 신고 대상이 바뀌면 입력을 모두 비운다.
  useEffect(() => { setReason(''); setDetail(''); setResult(null); setBlocked(false); setBlockError(''); }, [value]);
  useEffect(() => { if (!target) onClose(); }, [target == null]); // eslint-disable-line react-hooks/exhaustive-deps
  if (!target) return null;
  const label = target.type === 'COMMENT' ? '이야기' : target.type === 'MESSAGE' ? '쪽지' : '회원';
  const submit = async event => {
    event.preventDefault();
    if (!reason) { setResult({ ok: false, message: '신고 이유를 골라 주세요.' }); return; }
    if (reason === 'ETC' && !detail.trim()) { setResult({ ok: false, message: '\'기타\'를 고르면 어떤 문제인지 짧게 적어 주세요.' }); return; }
    setBusy(true);
    const outcome = await run(() => communityApi.report(target.type, target.seq, reason, detail.trim()));
    setBusy(false);
    setResult(outcome);
  };
  const blockAuthor = async () => {
    setBusy(true);
    const outcome = await run(() => communityApi.block(target.author));
    setBusy(false);
    if (outcome.ok) { setBlocked(true); setBlockError(''); queryClient.invalidateQueries({ queryKey: COMMUNITY_KEY }); }
    else setBlockError(outcome.message || '차단하지 못했어요. 잠시 뒤에 다시 시도해 주세요.');
  };
  const done = result?.ok;

  return <Dialog open onClose={() => { if (!busy) onClose(); }} {...sheetProps} aria-labelledby="report-title">
    <Box component="form" className="community-sheet-body" onSubmit={submit}>
      <IconButton className="community-sheet-close" aria-label="닫기" onClick={onClose} disabled={busy}><CloseRounded /></IconButton>
      <Typography id="report-title" component="h2" className="report-title">{label} 신고</Typography>
      {!done && <>
        <Typography className="member-card-hint" sx={{ mt: 0 }}>운영자가 확인한 뒤 숨기거나 쪽지·이용을 제한해요. 신고한 사실은 상대에게 알리지 않아요.</Typography>
        <RadioGroup value={reason} onChange={event => setReason(event.target.value)} aria-label="신고 이유" className="report-reasons">
          {REPORT_REASONS.map(item => <FormControlLabel key={item.value} value={item.value} control={<Radio size="small" />}
            label={<span className="report-reason"><b>{item.label}</b><small>{item.hint}</small></span>} />)}
        </RadioGroup>
        <TextField label={reason === 'ETC' ? '어떤 문제인가요? (필수)' : '설명 (선택)'} value={detail} onChange={event => setDetail(event.target.value.slice(0, 500))}
          multiline minRows={2} maxRows={5} fullWidth size="small" helperText={`${detail.length} / 500`} />
        {result && !result.ok && <Alert severity="error" sx={{ mt: 1.5 }}>{result.message}</Alert>}
        <Box className="member-card-actions" sx={{ justifyContent: 'flex-end' }}>
          <Button color="inherit" onClick={onClose} disabled={busy}>취소</Button>
          <Button type="submit" variant="contained" color="error" disableElevation disabled={busy}>{busy ? '보내는 중…' : '신고하기'}</Button>
        </Box>
      </>}
      {done && <>
        <Alert severity="success">{result.message}</Alert>
        {target.author && !blocked && <Box className="report-follow">
          <Typography>이 회원의 글과 쪽지를 더 보고 싶지 않다면 차단할 수 있어요.</Typography>
          <Button variant="outlined" color="inherit" startIcon={<BlockRounded />} onClick={blockAuthor} disabled={busy}>이 회원 차단하기</Button>
        </Box>}
        {blocked && <Alert severity="info" sx={{ mt: 1.5 }}>차단했어요. 내 정보에서 언제든 풀 수 있어요.</Alert>}
        {blockError && <Alert severity="error" sx={{ mt: 1.5 }}>{blockError}</Alert>}
        <Box className="member-card-actions" sx={{ justifyContent: 'flex-end' }}><Button variant="contained" disableElevation onClick={onClose}>확인</Button></Box>
      </>}
    </Box>
  </Dialog>;
}

/**
 * 레이아웃에 한 번 두는 프로필 카드·신고 창. 어느 화면에서든 히스토리 층 값만 바꾸면 열린다.
 * useHistoryOverlay: 열린 창을 location.state.overlayLayers에 적고 히스토리를 한 단계 쌓는다.
 * 그래서 창 하나가 뒤로가기 한 번이고, 휴대폰에서 뒤로가기를 누르면 페이지 대신 창이 닫힌다.
 * 프로필 카드 위에서 신고를 열면 층이 두 개 쌓이고, 뒤로가기마다 하나씩 닫힌다.
 */
export default function CommunityLayers() {
  const [cardOpen, setCardOpen] = useHistoryOverlay('member-card');
  const [reportOpen, setReportOpen] = useHistoryOverlay('report');
  return <>
    {cardOpen && <MemberCard value={cardOpen} onClose={() => setCardOpen(null)} />}
    {reportOpen && <ReportDialog value={reportOpen} onClose={() => setReportOpen(null)} />}
  </>;
}
