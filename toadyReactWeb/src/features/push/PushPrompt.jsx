import { useState } from 'react';
import { Button, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import AddBoxOutlined from '@mui/icons-material/AddBoxOutlined';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { usePushStore } from './pushStore';
import PushSettingsDialog from './PushSettingsDialog';
import './push.css';

const DISMISSED_KEY = 'toady.push.promptDismissed';
function dismissed() { try { return localStorage.getItem(DISMISSED_KEY) === '1'; } catch { return false; } }

/**
 * 관심 공고 저장 직후와 나의 청약에서 보여 주는 알림 안내 카드. 닫으면 이 브라우저에서는 다시 보이지 않는다.
 * 권한은 '알림 켜기'를 눌렀을 때만 요청하고, 아이폰 사파리 탭에서는 홈 화면 추가 방법을 연다.
 */
export default function PushPrompt({ overlayId, announcementId, loggedIn, compact = false }) {
  const push = usePushStore();
  const [hidden, setHidden] = useState(dismissed);
  const [justEnabled, setJustEnabled] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const [open, setOpen] = useHistoryOverlay(overlayId);
  const mode = push.platform.mode;
  const offerable = mode === 'ready' ? push.enabled && push.permission !== 'denied' : mode === 'ios-home' || mode === 'ios-safari';
  const dialog = <PushSettingsDialog open={Boolean(open)} onClose={() => setOpen(false)} testAnnouncementId={announcementId} loggedIn={loggedIn} />;
  if (hidden || push.status !== 'ready' || (!offerable && !justEnabled) || (push.subscribed && !justEnabled)) return dialog;

  const dismiss = () => { try { localStorage.setItem(DISMISSED_KEY, '1'); } catch { /* 이번 화면에서만 숨김 */ } setHidden(true); };
  function enable() {
    setBusy(true); setError(null);
    push.enable().then(() => setJustEnabled(true)).catch(e => setError(e.message)).finally(() => setBusy(false));
  }
  async function test() {
    setBusy(true); setError(null);
    try { await push.sendTest(announcementId); } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  let content;
  if (justEnabled) content = <><CheckCircleRounded className="push-card-icon ok" /><div className="push-card-text"><strong>알림을 켰어요</strong><span>전날 일정과 새 공고를 하루 한 번 모아서 알려 드릴게요.</span></div>
    <div className="push-card-actions"><Button size="small" disabled={busy} onClick={test}>테스트 알림</Button><Button size="small" onClick={() => setOpen(true)}>설정</Button></div></>;
  else if (mode === 'ready') content = <><NotificationsActiveOutlined className="push-card-icon" /><div className="push-card-text"><strong>{compact ? '이 공고 일정, 전날 알려 드릴까요?' : '청약 일정을 놓치지 않게 알려 드릴까요?'}</strong><span>관심 공고의 전날 일정을 하루 한 번 모아서 보내 드려요.</span></div>
    <div className="push-card-actions"><Button size="small" variant="contained" disableElevation disabled={busy} onClick={enable}>{busy ? '켜는 중…' : '알림 켜기'}</Button><Button size="small" onClick={() => setOpen(true)}>자세히</Button></div></>;
  else content = <><AddBoxOutlined className="push-card-icon" /><div className="push-card-text"><strong>아이폰은 홈 화면에 추가하면 알림을 받을 수 있어요</strong><span>앱 설치 없이 10초면 돼요. 추가한 아이콘으로 열고 알림을 켜 주세요.</span></div>
    <div className="push-card-actions"><Button size="small" variant="contained" disableElevation onClick={() => setOpen(true)}>방법 보기</Button></div></>;

  return <>
    <aside className={`push-card${compact ? ' compact' : ''}`} aria-label="알림 안내">
      {content}
      <IconButton className="push-card-close" size="small" aria-label="알림 안내 닫기(다시 보지 않기)" onClick={dismiss}><CloseRounded fontSize="small" /></IconButton>
      {error && <p className="push-card-error" role="alert">{error}</p>}
    </aside>
    {dialog}
  </>;
}
