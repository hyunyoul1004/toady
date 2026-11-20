/**
 * @file SignupPrompts.jsx
 * @description 가입 권유 화면 두 가지(기획: 회원 가입 유도 1단계). MainLayout이 한 번 그린다.
 * - SignupPromptHost: 비회원이 관심 공고를 3개째 저장하면 화면 아래에 정보 안내 카드를 한 번 띄운다(usePlanner가 보내는 신호).
 * - GuestImportPrompt: 로그인했는데 이 기기에 비회원 관심 공고가 남아 있으면 계정으로 옮길지 한 번 묻는다.
 *   옮기기는 '나의 청약' 메뉴의 기기 관심 공고 가져오기(usePlanner.importGuest)와 같은 함수다. 같은 공고 묶음은 다시 묻지 않는다.
 * - ResultSoonNotice: 비회원 관심 공고의 당첨자 발표가 오늘·내일이면 한 줄 안내(나의 청약, 공고 상세의 관심 저장 칸).
 * 규칙·문구·측정은 signupPrompt.js 한 곳에 있다.
 */
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Snackbar from '@mui/material/Snackbar';
import Typography from '@mui/material/Typography';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { trackAction } from '@/features/analytics/trackAction';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useGuestFavoriteIds, useGuestFavorites, usePlanner } from '@/features/planner/usePlanner';
import { koreaToday } from '@/features/planner/plannerUtils';
import {
  SIGNUP_PROMPT_EVENT, FAVORITE_PROMPT_COUNT, favoritePromptText, importAsked, importCandidates, importBucket, markImportAsked, promptSnoozed, resultSoon, resultSoonText, snoozePrompt,
  trackPromptClick, trackPromptView
} from './signupPrompt';

// 발표 임박 안내를 닫았음을 같은 화면의 다른 안내에 알리는 신호.
const SIGNUP_SNOOZED_EVENT = 'toady:signup-snoozed';
// 모바일은 하단 탭 위로 띄운다(MainLayout의 로그인 만료 안내와 같은 위치).
const BOTTOM = { bottom: { xs: 'calc(76px + env(safe-area-inset-bottom))', md: 24 } };

/** 관심 공고 3개째 저장 권유. openSignup: MainLayout의 회원가입 창 열기. */
export function SignupPromptHost({ openSignup }) {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const location = useLocation();
  const [prompt, setPrompt] = useState(null);
  // 저장 직후 상세에 알림 안내 카드(PushPrompt)가 떠 있으면 두 권유가 겹치므로 미뤄 두었다가, 화면이 바뀌고(상세 닫기 등) 그 카드가 없을 때 띄운다.
  const deferredRef = useRef(null);
  // 지금 이 기기의 비회원 관심 공고 수. 미뤄 둔 권유를 띄울 때 그사이 해제했는지 다시 본다.
  const guestCount = useGuestFavoriteIds().length;
  const guestCountRef = useRef(guestCount);
  guestCountRef.current = guestCount;
  const show = next => {
    deferredRef.current = null;
    if (useAuthStore.getState().isAuthenticated || promptSnoozed(next.moment)) return;
    if (guestCountRef.current < FAVORITE_PROMPT_COUNT) return;
    next = { ...next, count: guestCountRef.current };
    // 띄운 순간 30일 미룬다: 한 순간은 한 번만 권한다.
    snoozePrompt(next.moment);
    trackPromptView(trackAction, next.moment);
    setPrompt(next);
  };
  // 저장 직후 상세에 뜨는 작은 알림 카드(compact)만 기다린다. 나의 청약의 상시 알림 카드는 기준이 아니다.
  const pushCardVisible = () => Boolean(document.querySelector('aside.push-card.compact'));
  useEffect(() => {
    let timer;
    const onPrompt = event => {
      const { moment, count } = event.detail || {};
      if (moment !== 'favorite' || useAuthStore.getState().isAuthenticated || promptSnoozed(moment)) return;
      clearTimeout(timer);
      timer = setTimeout(() => { if (pushCardVisible()) deferredRef.current = { moment, count }; else show({ moment, count }); }, 500);
    };
    window.addEventListener(SIGNUP_PROMPT_EVENT, onPrompt);
    return () => { clearTimeout(timer); window.removeEventListener(SIGNUP_PROMPT_EVENT, onPrompt); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!deferredRef.current) return undefined;
    const timer = setTimeout(() => { if (deferredRef.current && !pushCardVisible()) show(deferredRef.current); }, 600);
    return () => clearTimeout(timer);
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps
  // 다른 탭에서 로그인하면 카드를 닫는다.
  // 로그인하면 떠 있는 카드와 미뤄 둔 권유를 모두 지운다(로그아웃 뒤 지난 권유가 뜨지 않게).
  useEffect(() => { if (isAuthenticated) { setPrompt(null); deferredRef.current = null; } }, [isAuthenticated]);
  const close = () => setPrompt(null);
  // 닫히는 동안 두 번 눌러도(prompt가 이미 null) 오류 없이 넘긴다.
  const join = () => { if (!prompt) return; trackPromptClick(trackAction, prompt.moment); close(); openSignup(); };
  return (
    <Snackbar open={Boolean(prompt)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} sx={BOTTOM}
      onClose={(_, reason) => { if (reason !== 'clickaway') close(); }}>
      <Box role="status" sx={{ maxWidth: 440, p: 2, borderRadius: 2, bgcolor: '#ffffff', border: '1px solid #e2e8f0', boxShadow: '0 8px 24px rgba(15,23,42,0.14)' }}>
        <Typography sx={{ fontSize: '0.9rem', fontWeight: 600, color: '#1e293b', lineHeight: 1.5 }}>
          {prompt ? favoritePromptText(prompt.count) : ''}
        </Typography>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', gap: 1, mt: 1.5 }}>
          <Button size="small" onClick={close} sx={{ color: '#64748b', fontWeight: 700 }}>나중에</Button>
          <Button size="small" variant="contained" disableElevation onClick={join} sx={{ fontWeight: 700 }}>가입하고 이어 보기</Button>
        </Box>
      </Box>
    </Snackbar>
  );
}

/**
 * 로그인했고, 이 기기에 비회원 관심 공고가 있고, 그 묶음을 아직 묻지 않았을 때만 계정 공고를 읽는다.
 * (기기 공고가 없는 대부분의 회원은 화면마다 관심 공고·설정 요청이 늘지 않게. 창이 열려 있는 동안은 계속 그린다.)
 */
export function GuestImportPrompt() {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  const userSeq = useAuthStore(s => s.user?.userSeq);
  const guestIds = useGuestFavoriteIds();
  const [open, setOpen] = useHistoryOverlay('guest-import');
  const [message, setMessage] = useState(null);
  // 옮기는 중에는 창을 뒤로가기로 닫아도 컴포넌트를 남겨 둔다(끝난 뒤 히스토리를 한 칸 더 되돌리지 않게, 결과 안내를 보이게).
  const [moving, setMoving] = useState(false);
  // 옮길 공고가 없다고 확인돼 '물어봄'을 적었을 때 다시 그려 GuestImportAsk(관심 공고 조회)를 떼어 내는 신호.
  const [, setSettled] = useState(0);
  const eligible = isAuthenticated && Boolean(userSeq) && guestIds.length > 0 && !askedThisSession.has(userSeq) && !importAsked(userSeq, guestIds);
  // 다른 탭에서 로그아웃하면 열려 있던 창을 닫는다(비회원에게 '계정으로 옮기기'가 남지 않게).
  // 옮기는 중에 로그아웃되면 여기서는 닫지 않는다: 옮기기가 끝나면(성공·실패 모두) GuestImportAsk의 closeIfOpen이 닫는다.
  // (끝난 뒤에 여기서도 닫으면 같은 칸을 두 번 되돌려 창 아래 화면까지 빠져나간다)
  useEffect(() => { if (!isAuthenticated && open === true && !moving) setOpen(null); }, [isAuthenticated]); // eslint-disable-line react-hooks/exhaustive-deps
  return <>
    {(eligible || moving || (open === true && isAuthenticated)) && <GuestImportAsk onDone={setMessage} onMoving={setMoving} onSettled={() => setSettled(n => n + 1)} />}
    <Snackbar open={Boolean(message)} autoHideDuration={6000} onClose={() => setMessage(null)} message={message} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} sx={BOTTOM} />
  </>;
}

// 이번 페이지 실행에서 이미 물어본 계정. 저장소 쓰기가 막혀 '물어봄'을 못 적어도, 닫을 때마다 다시 뜨지 않게 한다(컴포넌트가 다시 붙어도 유지).
const askedThisSession = new Set();
// MyPlanner가 캘린더 연결을 위해 로그인 창을 열 때 남기는 표시. 있으면 캘린더 창이 먼저 열리므로 이어받기는 다음 기회에 묻는다.
const CALENDAR_INTENT = 'toady.calendarIntent';
const calendarPending = () => { try { return sessionStorage.getItem(CALENDAR_INTENT) === '1'; } catch { return false; } };

function GuestImportAsk({ onDone, onMoving, onSettled }) {
  const planner = usePlanner();
  const guestItems = useGuestFavorites();
  const guestIds = guestItems.map(item => item.id);
  const userSeq = useAuthStore(s => s.user?.userSeq);
  const location = useLocation();
  const locationRef = useRef(location);
  locationRef.current = location;
  const [open, setOpen] = useHistoryOverlay('guest-import');
  const [busy, setBusy] = useState(false);
  const pending = importCandidates(guestItems, planner.items);
  const openLayers = location.state?.overlayLayers?.length || 0;
  // 로그인 창 같은 다른 창이 모두 닫히고 계정 공고를 다 읽은 뒤에 한 번 묻는다. 같은 공고 묶음을 이미 물었으면 묻지 않는다.
  const ready = Boolean(userSeq) && !planner.isLoading && !planner.error && pending.length > 0 && openLayers === 0
    && !calendarPending() && !importAsked(userSeq, guestIds);
  useEffect(() => {
    if (!ready || open || askedThisSession.has(userSeq)) return undefined;
    // 로그인 창이 닫히는 같은 순간에 다른 창(캘린더 연결 등)이 열릴 수 있어, 한 박자 뒤 최신 주소에서 열린 창이 없을 때만 연다.
    const timer = setTimeout(() => {
      if ((locationRef.current.state?.overlayLayers?.length || 0) > 0 || calendarPending()) return;
      askedThisSession.add(userSeq);
      // 묻는 순간 '물어봄'으로 적어 둔다. 뒤로가기로 닫아도 같은 묶음을 다시 묻지 않는다(메뉴의 가져오기는 그대로 있다).
      markImportAsked(userSeq, guestIds);
      setOpen(true);
    }, 400);
    return () => clearTimeout(timer);
  }, [ready]); // eslint-disable-line react-hooks/exhaustive-deps
  // 기기 공고가 모두 계정에 이미 있으면(메뉴로 가져온 뒤 등) 묻지 않고 '물어봄'으로 적어, 화면마다 관심 공고를 다시 읽지 않게 한다.
  useEffect(() => {
    if (userSeq && !planner.isLoading && !planner.error && pending.length === 0 && open !== true) { markImportAsked(userSeq, guestIds); onSettled(); }
  }, [userSeq, planner.isLoading, planner.error, pending.length]); // eslint-disable-line react-hooks/exhaustive-deps
  const later = () => setOpen(null);
  // 옮기는 동안 뒤로가기로 이미 닫혔으면 다시 되돌리지 않는다(최신 주소에 창 레이어가 남아 있을 때만 닫는다).
  const closeIfOpen = () => { if ((locationRef.current.state?.overlayLayers || []).some(layer => layer.id === 'guest-import')) setOpen(null); };
  const move = async () => {
    setBusy(true); onMoving(true);
    try {
      const result = await planner.importGuest();
      const bucket = importBucket(result.imported);
      if (bucket) trackAction('guest_import', bucket);
      const missed = result.failed + result.skipped;
      onDone(missed ? `${result.imported}개를 옮겼고 ${missed}개는 옮기지 못했어요.${result.skipped ? ' 원본이 사라진 공고는 뺐어요.' : ''}` : `관심 공고 ${result.imported}개를 계정으로 옮겼어요.`);
      closeIfOpen();
    } catch (error) {
      onDone(error.message);
      closeIfOpen();
    } finally { setBusy(false); onMoving(false); }
  };
  return (
    <Dialog open={open === true} onClose={busy ? undefined : later} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 800 }}>기기에 저장한 관심 공고</DialogTitle>
      <DialogContent>
        <Typography sx={{ color: '#334155', lineHeight: 1.6 }}>
          이 기기에 저장한 관심 공고 {pending.length}개를 계정으로 옮길까요? 진행 단계와 메모도 함께 옮겨요. 계정에 이미 있는 공고는 그대로 둬요.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={later} disabled={busy} sx={{ color: '#64748b', fontWeight: 700 }}>나중에</Button>
        <Button onClick={move} disabled={busy} variant="contained" disableElevation sx={{ fontWeight: 700 }}>{busy ? '옮기는 중…' : '계정으로 옮기기'}</Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * 발표 임박 가입 안내(비회원). items: 비회원 관심 공고(상세에서는 그 공고 하나). 닫거나 가입을 누르면 30일 동안 다시 띄우지 않는다.
 * 가입 창은 MainLayout의 공통 회원가입 창(layout 레이어 'signup')을 지금 화면 위에 연다(뒤로가기로 닫힘).
 */
export function ResultSoonNotice({ items }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [hidden, setHidden] = useState(() => promptSnoozed('result'));
  // 같은 안내가 두 곳(나의 청약, 그 위에 연 상세)에 있을 수 있어, 한쪽에서 닫으면 다른 쪽도 숨긴다.
  useEffect(() => {
    const onSnoozed = event => { if (event.detail?.moment === 'result') setHidden(true); };
    window.addEventListener(SIGNUP_SNOOZED_EVENT, onSnoozed);
    return () => window.removeEventListener(SIGNUP_SNOOZED_EVENT, onSnoozed);
  }, []);
  const found = hidden ? null : resultSoon(items, koreaToday());
  useEffect(() => { if (found) trackPromptView(trackAction, 'result'); }, [Boolean(found)]); // eslint-disable-line react-hooks/exhaustive-deps
  if (!found) return null;
  const dismiss = () => {
    snoozePrompt('result'); setHidden(true);
    try { window.dispatchEvent(new CustomEvent(SIGNUP_SNOOZED_EVENT, { detail: { moment: 'result' } })); } catch { /* 이 안내만 숨김 */ }
  };
  const join = () => {
    trackPromptClick(trackAction, 'result');
    dismiss();
    navigate(location.pathname + location.search + location.hash, { state: { ...location.state, overlayLayers: [...(location.state?.overlayLayers || []), { id: 'layout', value: 'signup' }] } });
  };
  return (
    <Box role="note" sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1, p: '12px 16px', mb: 2, borderRadius: '10px', bgcolor: '#fff7ed', border: '1px solid #fed7aa' }}>
      <Typography sx={{ flex: '1 1 240px', fontSize: '0.82rem', color: '#7c2d12', lineHeight: 1.6 }}>{resultSoonText(found)}</Typography>
      <Box sx={{ display: 'flex', gap: 0.5, ml: 'auto' }}>
        <Button size="small" onClick={dismiss} sx={{ color: '#9a3412', fontWeight: 700 }}>닫기</Button>
        <Button size="small" variant="contained" disableElevation onClick={join} sx={{ fontWeight: 700, bgcolor: '#c2410c', '&:hover': { bgcolor: '#9a3412' } }}>가입하고 일정 받기</Button>
      </Box>
    </Box>
  );
}

/** 이미 있는 로그인 안내(이야기 쓰기 등)가 보였음을 센다. 화면에는 아무것도 그리지 않는다. */
export function PromptViewMark({ moment }) {
  useEffect(() => { trackPromptView(trackAction, moment); }, [moment]);
  return null;
}
