import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Chip, CircularProgress, Dialog, DialogContent, DialogTitle, FormControlLabel, IconButton, Switch, useMediaQuery } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import apiClient from '@/api/client';
import { usePushStore } from './pushStore';
import { PUSH_HELP } from './pushSupport';
import HomeScreenGuide from './HomeScreenGuide';
import './push.css';

/**
 * 알림 설정 창. 열고 닫기는 부모가 useHistoryOverlay로 관리해 뒤로가기 한 번에 닫힌다.
 * testAnnouncementId가 있으면 테스트 알림을 누를 때 그 공고 상세로 이동한다.
 */
export default function PushSettingsDialog({ open, onClose, testAnnouncementId, loggedIn }) {
  const mobile = useMediaQuery('(max-width:600px)');
  const push = usePushStore();
  const [busy, setBusy] = useState(false), [error, setError] = useState(null), [notice, setNotice] = useState(null);
  const areas = useQuery({ queryKey: ['livingAreas'], queryFn: () => apiClient.get('/api/v1/living-areas'), staleTime: 3600000, enabled: open });
  const [draft, setDraft] = useState(null);
  const scope = draft || push.scope;
  const mode = push.platform.mode;

  async function run(task, success) {
    setBusy(true); setError(null); setNotice(null);
    try { await task(); if (success) setNotice(success); } catch (e) { setError(e.message || '잠시 후 다시 시도해 주세요.'); } finally { setBusy(false); }
  }
  // 권한 요청이 사용자 동작 안에서 바로 시작되도록 await 없이 enable을 호출한다.
  const enable = () => run(() => push.enable(scope).then(() => setDraft(null)), '알림을 켰어요. 아래에서 테스트 알림을 보내 확인해 보세요.');
  const change = next => {
    if (!push.subscribed) { setDraft(next); return; }
    run(() => push.save(next), null);
  };
  const toggleArea = slug => change({ ...scope, areas: scope.areas.includes(slug) ? scope.areas.filter(value => value !== slug) : [...scope.areas, slug].slice(0, 6) });
  const close = () => { if (busy) return; setError(null); setNotice(null); setDraft(null); onClose(); };

  let body;
  if (push.status === 'loading') body = <div className="push-loading"><CircularProgress size={24} /></div>;
  else if (mode === 'ios-home' || mode === 'ios-safari') body = <HomeScreenGuide mode={mode} />;
  else if (mode !== 'ready') body = <Alert severity="info">{PUSH_HELP[mode] || PUSH_HELP.unsupported}</Alert>;
  else if (!push.enabled) body = <Alert severity="info">알림 기능을 준비 중이에요. 그동안 캘린더 연결로 일정을 받아 보세요.</Alert>;
  else body = <>
    {push.subscribed
      ? <div className="push-status on"><CheckCircleRounded /><div><strong>이 기기에서 알림을 받고 있어요</strong><span>하루 한 번, 저녁 6시 무렵에 모아서 보내 드려요.</span></div></div>
      : <p className="push-lead">관심 공고의 <strong>전날 일정</strong>과 고른 생활권의 <strong>새 공고</strong>를 하루 한 번 모아서 알려 드려요.</p>}
    {push.permission === 'denied' && <Alert severity="warning" sx={{ mb: 2 }}>브라우저에서 TOADY 알림이 차단되어 있어요. 주소창 왼쪽의 사이트 설정에서 알림을 ‘허용’으로 바꾼 뒤 다시 시도해 주세요.</Alert>}
    <section className="push-option">
      <FormControlLabel control={<Switch checked={scope.favorites !== false} disabled={busy} onChange={event => change({ ...scope, favorites: event.target.checked })} />}
        label={<span><strong>관심 공고 전날 일정</strong><small>{loggedIn ? '계정에 저장한 관심 공고 기준' : '이 브라우저에 저장한 관심 공고 기준'} · 접수·발표·계약일 전날</small></span>} />
    </section>
    <section className="push-option">
      <strong>새 공고를 받을 생활권</strong><small>공고가 새로 올라온 날 알려 드려요. 최대 6곳.</small>
      <div className="push-areas">{Array.isArray(areas.data) ? areas.data.map(area => <Chip key={area.slug} label={area.label} disabled={busy}
        color={scope.areas.includes(area.slug) ? 'primary' : 'default'} variant={scope.areas.includes(area.slug) ? 'filled' : 'outlined'} onClick={() => toggleArea(area.slug)} />) : areas.isLoading ? <CircularProgress size={18} /> : null}</div>
    </section>
    {push.subscribed ? <div className="push-actions">
      <Button variant="contained" disableElevation disabled={busy} onClick={() => run(() => push.sendTest(testAnnouncementId), '테스트 알림을 보냈어요. 알림을 눌러 이동해 보세요.')}>테스트 알림 보내기</Button>
      <Button color="error" disabled={busy} onClick={() => run(() => push.disable(), '이 기기의 알림을 껐어요.')}>알림 끄기</Button>
    </div> : <Button fullWidth size="large" variant="contained" disableElevation startIcon={<NotificationsActiveOutlined />} disabled={busy || push.permission === 'denied'} onClick={enable}>{busy ? '켜는 중…' : '알림 켜기'}</Button>}
    {!push.subscribed && <p className="push-help">브라우저가 알림 허용 여부를 물으면 ‘허용’을 눌러 주세요. 언제든 이곳에서 끌 수 있어요.</p>}
  </>;

  return <Dialog open={open} onClose={close} fullWidth maxWidth="xs" fullScreen={mobile} aria-labelledby="push-settings-title" className="push-dialog">
    <DialogTitle id="push-settings-title" className="push-title">알림 설정<IconButton aria-label="알림 설정 닫기" onClick={close} disabled={busy}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent className="push-content">
      {notice && <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>{notice}</Alert>}
      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}
      {body}
    </DialogContent>
  </Dialog>;
}
