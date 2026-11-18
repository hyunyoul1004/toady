import { useState } from 'react';
import { Alert, Button, Dialog, DialogContent, DialogTitle, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import AddToHomeScreenRounded from '@mui/icons-material/AddToHomeScreenRounded';
import HomeScreenGuide from '@/features/push/HomeScreenGuide';
import '@/features/push/push.css';

/** 설치 창을 바로 띄울 수 없는 브라우저에서 홈 화면 추가 방법을 보여 준다. 열고 닫기는 부모가 useHistoryOverlay로 관리한다. */
export default function InstallGuideDialog({ open, onClose, mode }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try { await navigator.clipboard.writeText(window.location.origin + '/'); setCopied(true); } catch { setCopied(false); }
  };
  let body;
  if (mode === 'ios' || mode === 'ios-safari') body = <HomeScreenGuide mode={mode === 'ios' ? 'ios-home' : 'ios-safari'} purpose="install" />;
  else if (mode === 'in-app') body = <div className="push-guide">
    <p className="push-guide-lead">카카오톡·네이버 같은 <strong>앱 안의 브라우저</strong>에서는 홈 화면에 추가할 수 없어요.</p>
    <ol className="push-steps">
      <li><span className="push-step-no">1</span><div><strong>화면 위나 아래의 메뉴에서 ‘다른 브라우저로 열기’를 눌러요</strong><small>메뉴가 없으면 아래 버튼으로 주소를 복사해 Chrome·Safari에 붙여 넣어 주세요.</small></div></li>
      <li><span className="push-step-no">2</span><div><strong>열린 브라우저에서 메뉴의 ‘홈 화면에 추가’를 눌러요</strong></div></li>
    </ol>
    <Button fullWidth variant="outlined" startIcon={<ContentCopyRounded />} onClick={copy}>{copied ? '주소를 복사했어요' : '주소 복사'}</Button>
  </div>;
  else body = <div className="push-guide">
    <p className="push-guide-lead">TOADY를 홈 화면에 추가하면 <strong>앱처럼 바로</strong> 열 수 있어요. 설치할 앱은 없어요.</p>
    <ol className="push-steps">
      <li><span className="push-step-no">1</span><div><strong>브라우저 오른쪽 위나 아래의 <span className="push-key"><MoreVertRounded fontSize="inherit" />메뉴</span>를 눌러요</strong><small>삼성 인터넷은 아래쪽 ≡ 메뉴예요.</small></div></li>
      <li><span className="push-step-no">2</span><div><strong><span className="push-key"><AddToHomeScreenRounded fontSize="inherit" />홈 화면에 추가</span> 또는 ‘앱 설치’를 눌러요</strong><small>이미 추가했다면 홈 화면의 TOADY 아이콘으로 열어 주세요.</small></div></li>
    </ol>
  </div>;
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="install-guide-title" className="push-dialog">
    <DialogTitle id="install-guide-title" className="push-title">홈 화면에 추가<IconButton aria-label="닫기" onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>{body}{mode === 'prompt' && <Alert severity="info">브라우저 설치 창이 뜨지 않았다면 잠시 뒤 다시 눌러 주세요.</Alert>}</DialogContent>
  </Dialog>;
}
