import { useState } from 'react';
import { Button, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import AddBoxOutlined from '@mui/icons-material/AddBoxOutlined';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { usePushStore } from './pushStore';
import PushSettingsDialog from './PushSettingsDialog';
import { promptSnoozed } from './pushSupport';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import './push.css';

const DISMISSED_KEY = 'toady.push.promptDismissed';
/** 닫으면 30일 동안 보이지 않는다(닫은 시각을 저장). 관심 공고를 새로 저장하는 사람에게 영영 묻지 않게 되는 일을 막는다. */
function dismissed() { try { return promptSnoozed(localStorage.getItem(DISMISSED_KEY)); } catch { return false; } }

/**
 * 관심 공고 저장 직후와 나의 청약에서 보여 주는 알림 안내 카드. 닫으면 30일 동안 다시 보이지 않는다.
 * 권한은 '알림 켜기'를 눌렀을 때만 요청하고(브라우저 권한 창은 한 번 거절하면 되돌리기 어려워 앱 안에서 먼저 묻는다),
 * 아이폰 사파리 탭에서는 홈 화면 추가 방법을 연다.
 * onCalendar가 있으면(공고 상세) 푸시 대신 캘린더에 넣는 길도 준다. 카카오톡 앱 안 브라우저처럼 푸시를 못 받는 곳에서는 캘린더만 권한다.
 * 쓰는 곳: 나의 청약(MyPlanner), 공고 상세의 조사 패널(ResearchPanel, compact).
 * overlayId: 알림 설정 창을 여는 히스토리 레이어 id. 화면마다 달라야 같은 화면의 다른 카드와 섞이지 않는다.
 * useHistoryOverlay: 팝업을 히스토리 한 칸으로 연다(location.state.overlayLayers에 기록). 그래서 브라우저 뒤로가기로 창이 닫힌다.
 */
export default function PushPrompt({ overlayId, announcementId, loggedIn, compact = false, onCalendar }) {
  const push = usePushStore();
  // useState에 함수를 넘기면 첫 렌더에서만 한 번 불러 초깃값을 정한다(매 렌더마다 localStorage를 읽지 않게).
  const [hidden, setHidden] = useState(dismissed);
  const [justEnabled, setJustEnabled] = useState(false), [busy, setBusy] = useState(false), [error, setError] = useState(null);
  const [calendarDone, setCalendarDone] = useState(false);
  const [open, setOpen] = useHistoryOverlay(overlayId);
  const mode = push.platform.mode;
  // 지금 이 기기에서 알림을 권할 수 있는지: 바로 켤 수 있거나(ready), 아이폰이라 홈 화면 추가를 안내할 수 있을 때.
  const offerable = mode === 'ready' ? push.enabled && push.permission !== 'denied' : mode === 'ios-home' || mode === 'ios-safari';
  const dialog = <PushSettingsDialog open={Boolean(open)} onClose={() => setOpen(false)} testAnnouncementId={announcementId} loggedIn={loggedIn} />;
  const calendarOnly = Boolean(onCalendar) && compact && push.status === 'ready' && !offerable && !push.subscribed;
  // 카드를 보일 이유가 없으면 창만 남긴다. 창을 빼면 뒤로가기·앞으로가기로 창 레이어가 돌아왔을 때 열 컴포넌트가 없다.
  if (hidden || push.status !== 'ready' || (!offerable && !justEnabled && !calendarOnly && !calendarDone) || (push.subscribed && !justEnabled)) return dialog;

  const dismiss = () => { try { localStorage.setItem(DISMISSED_KEY, String(Date.now())); } catch { /* 이번 화면에서만 숨김 */ } setHidden(true); };
  /** '알림 켜기'. 클릭 처리기 안에서 바로 push.enable()을 불러야 브라우저(특히 사파리)가 권한 창을 띄워 준다. */
  function enable() {
    setBusy(true); setError(null);
    push.enable().then(() => setJustEnabled(true)).catch(e => setError(e.message)).finally(() => setBusy(false));
  }
  function addCalendar() { onCalendar?.(); setCalendarDone(true); }
  async function test() {
    setBusy(true); setError(null);
    try { await push.sendTest(announcementId); } catch (e) { setError(e.message); } finally { setBusy(false); }
  }

  const calendarButton = onCalendar && compact ? <Button size="small" disabled={busy} onClick={addCalendar}>캘린더에 넣기</Button> : null;
  // 상황별 카드 내용: 캘린더 준비됨 → 방금 켬 → 캘린더만 권함 → 알림 켜기 권함 → 아이폰 홈 화면 추가 안내.
  let content;
  if (calendarDone) content = <><CheckCircleRounded className="push-card-icon ok" /><div className="push-card-text"><strong>캘린더 파일을 준비했어요</strong><span>내려받은 파일을 열어 캘린더에 추가하면 접수·발표 전날 알려 줘요. 받아지지 않으면 기본 브라우저에서 열어 주세요.</span></div></>;
  else if (justEnabled) content = <><CheckCircleRounded className="push-card-icon ok" /><div className="push-card-text"><strong>알림을 켰어요</strong><span>{compact ? '경쟁률·당첨가점이 나오면 바로, 전날 일정은 저녁에 한 번 모아서 알려 드릴게요.' : '관심 공고 경쟁률·당첨가점은 나오면 바로, 전날 일정과 새 공고는 저녁에 한 번 모아서 알려 드릴게요.'}</span></div>
    <div className="push-card-actions"><Button size="small" disabled={busy} onClick={test}>테스트 알림</Button><Button size="small" onClick={() => setOpen(true)}>설정</Button></div></>;
  else if (calendarOnly) content = <><EventAvailableOutlined className="push-card-icon" /><div className="push-card-text"><strong>이 공고 일정, 캘린더에 넣어 둘까요?</strong><span>{push.permission === 'denied' ? '이 브라우저는 알림이 차단돼 있어요.' : '지금 브라우저에서는 알림을 받을 수 없어요.'} 캘린더에 넣으면 접수·발표 전날 캘린더가 알려 줘요.</span></div>
    <div className="push-card-actions"><Button size="small" variant="contained" disableElevation onClick={addCalendar}>캘린더에 넣기</Button></div></>;
  else if (mode === 'ready') content = <><NotificationsActiveOutlined className="push-card-icon" /><div className="push-card-text"><strong>{compact ? '경쟁률·당첨가점 나오면 바로 알려 드릴까요?' : '청약 일정을 놓치지 않게 알려 드릴까요?'}</strong><span>{compact ? '결과가 공개되면 바로, 전날 일정은 저녁에 한 번 모아서 보내 드려요.' : '관심 공고의 전날 일정은 저녁에 한 번 모아서, 경쟁률·당첨가점은 공개되면 바로 보내 드려요.'}</span></div>
    <div className="push-card-actions"><Button size="small" variant="contained" disableElevation disabled={busy} onClick={enable}>{busy ? '켜는 중…' : '알림 켜기'}</Button>{calendarButton || <Button size="small" onClick={() => setOpen(true)}>자세히</Button>}</div></>;
  else content = <><AddBoxOutlined className="push-card-icon" /><div className="push-card-text"><strong>아이폰은 홈 화면에 추가하면 알림을 받을 수 있어요</strong><span>앱 설치 없이 10초면 돼요. 추가한 아이콘으로 열고 알림을 켜 주세요.</span></div>
    <div className="push-card-actions"><Button size="small" variant="contained" disableElevation onClick={() => setOpen(true)}>방법 보기</Button>{calendarButton}</div></>;

  return <>
    <aside className={`push-card${compact ? ' compact' : ''}`} aria-label="알림 안내">
      {content}
      <IconButton className="push-card-close" size="small" aria-label="알림 안내 닫기(30일 동안 보이지 않음)" onClick={dismiss}><CloseRounded fontSize="small" /></IconButton>
      {error && <p className="push-card-error" role="alert">{error}</p>}
    </aside>
    {dialog}
  </>;
}
