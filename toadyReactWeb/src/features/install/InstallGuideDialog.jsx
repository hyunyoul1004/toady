import { useState } from 'react';
import { Button, Dialog, DialogContent, DialogTitle, IconButton } from '@mui/material';
import CloseRounded from '@mui/icons-material/CloseRounded';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import AddToHomeScreenRounded from '@mui/icons-material/AddToHomeScreenRounded';
import HomeScreenGuide from '@/features/push/HomeScreenGuide';
import { androidBrowser } from './installSupport';
import InstallCoach from './InstallCoach';
import '@/features/push/push.css';

// 화면 속 버튼 모양을 흉내 낸 작은 표시(아이콘 + 이름)와 번호 붙은 단계 한 줄.
const Key = ({ icon, children }) => <span className="push-key">{icon}{children}</span>;
const Step = ({ no, title, note }) => <li><span className="push-step-no">{no}</span><div><strong>{title}</strong>{note && <small>{note}</small>}</div></li>;

/** 갤럭시 등에서는 설치한 아이콘이 홈 화면이 아니라 앱스 화면(앱 목록)에 먼저 생긴다. */
const APP_DRAWER_NOTE = '홈 화면에 안 보이면 화면을 아래에서 위로 쓸어 올린 앱스 화면에서 TOADY를 찾아 길게 누르고 ‘홈에 추가’를 눌러요.';

/** 브라우저별 홈 화면 추가 순서(안드로이드). 메뉴 위치와 이름은 각 브라우저 최신 버전 기준이다. */
const ANDROID_STEPS = {
  chrome: [
    { title: <>주소창 오른쪽 끝의 <Key icon={<MoreVertRounded fontSize="inherit" />}>점 세 개</Key>를 눌러요</> },
    { title: <>메뉴에서 ‘앱 설치’를 누르고, 뜨는 창에서 ‘설치’를 눌러요</>, note: '‘앱 설치’ 대신 ‘홈 화면에 추가’가 보이면 그 항목을 누르고 ‘설치’를 눌러요.' },
    { title: '홈 화면의 TOADY 아이콘으로 열어요', note: APP_DRAWER_NOTE }
  ],
  samsung: [
    { title: <>화면 맨 아래 오른쪽 끝의 <Key icon={<MoreVertRounded fontSize="inherit" />}>점 세 개</Key>를 눌러요</> },
    { title: '‘현재 페이지 추가’를 누른 다음 ‘홈 화면’을 눌러요', note: '메뉴 첫 화면에 없으면 ‘도구’ 안에 있어요.' },
    { title: '‘추가’를 눌러요', note: APP_DRAWER_NOTE }
  ],
  whale: [
    { title: <>화면 아래 오른쪽의 <Key icon={<MoreHorizRounded fontSize="inherit" />}>점 세 개</Key>를 눌러요</> },
    { title: '‘홈 화면에 추가’를 누르고 ‘추가’를 눌러요' },
    { title: '홈 화면의 TOADY 아이콘으로 열어요', note: APP_DRAWER_NOTE }
  ],
  firefox: [
    { title: <>주소창 오른쪽의 <Key icon={<MoreVertRounded fontSize="inherit" />}>점 세 개</Key>를 눌러요</> },
    { title: '‘설치’를 누르고 ‘추가’를 눌러요' },
    { title: '홈 화면의 TOADY 아이콘으로 열어요', note: APP_DRAWER_NOTE }
  ],
  edge: [
    { title: <>화면 아래 가운데의 <Key icon={<MoreHorizRounded fontSize="inherit" />}>점 세 개</Key>를 눌러요</> },
    { title: '‘휴대폰에 추가’를 누르고 ‘설치’를 눌러요' },
    { title: '홈 화면의 TOADY 아이콘으로 열어요', note: APP_DRAWER_NOTE }
  ]
};

/**
 * 홈 화면 추가 안내. 설치 창을 바로 띄울 수 없는 브라우저에서는 기기·브라우저별 순서를, 설치를 마친 뒤(mode='done')에는 아이콘 위치를 알려 준다.
 * 열고 닫기는 부모가 useHistoryOverlay로 관리한다.
 * 쓰는 곳: Sidebar의 '홈 화면에 추가'. mode는 installSupport.installMode 결과이거나, 설치를 마친 뒤의 'done'이다.
 * 'prompt'(설치 창을 바로 띄울 수 있음)일 때는 이 창 대신 브라우저 설치 창이 뜬다(installStore.promptInstall).
 * 'android'와 그 밖의 값은 맨 아래 else에서 안드로이드 브라우저별 순서를 보여 준다.
 */
export default function InstallGuideDialog({ open, onClose, mode, onInstall }) {
  const [copied, setCopied] = useState(false);
  // 주소 복사. 클립보드 API는 https와 사용자 동작 안에서만 되고, 일부 앱 안 브라우저는 막아 둔다(실패하면 조용히 넘어간다).
  const copy = async () => {
    try { await navigator.clipboard.writeText(window.location.origin + '/'); setCopied(true); } catch { setCopied(false); }
  };
  let body;
  const onAndroid = typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent || '');
  const browser = androidBrowser(typeof navigator === 'undefined' ? '' : navigator.userAgent);
  if (mode === 'prompt') body = <div className="push-guide">
    <p className="push-guide-lead">TOADY를 홈 화면에 추가하면 <strong>앱처럼 바로</strong> 열 수 있어요. 따로 받을 앱은 없어요.</p>
    <Button fullWidth size="large" variant="contained" disableElevation startIcon={<AddToHomeScreenRounded />} onClick={onInstall}>홈 화면에 설치</Button>
  </div>;
  else if (mode === 'done') body = <div className="push-guide">
    <div className="push-status on"><CheckCircleRounded /><div><strong>설치했어요</strong><span>이제 TOADY 아이콘으로 앱처럼 바로 열 수 있어요.</span></div></div>
    <p className="push-help">{onAndroid ? APP_DRAWER_NOTE : '바탕화면이나 시작 메뉴(맥은 응용 프로그램 폴더·Dock)의 TOADY로 열 수 있어요.'}</p>
  </div>;
  else if (mode === 'desktop') body = <div className="push-guide">
    <p className="push-guide-lead">TOADY를 PC에 설치하면 <strong>창 하나로 바로</strong> 열 수 있어요.</p>
    <ol className="push-steps">
      <Step no={1} title="주소창 오른쪽 끝의 설치 아이콘(모니터에 아래 화살표 모양)을 눌러요" note="Chrome·Edge에서 보여요. 아이콘이 없으면 새로고침한 뒤 다시 눌러 주세요." />
      <Step no={2} title="뜨는 창에서 ‘설치’를 눌러요" />
    </ol>
  </div>;
  else if (mode === 'ios' || mode === 'ios-safari') body = <HomeScreenGuide mode={mode === 'ios' ? 'ios-home' : 'ios-safari'} purpose="install" />;
  else if (mode === 'in-app') body = <div className="push-guide">
    <p className="push-guide-lead">카카오톡·네이버 같은 <strong>앱 안의 브라우저</strong>에서는 홈 화면에 추가할 수 없어요. Chrome이나 Safari에서 열어 주세요.</p>
    <ol className="push-steps">
      <Step no={1} title="아래 버튼으로 주소를 복사해요" />
      <Step no={2} title="Chrome(아이폰은 Safari)을 열고 주소창에 붙여 넣어요" />
      <Step no={3} title="TOADY 메뉴의 ‘더보기 › 홈 화면에 추가’를 다시 눌러요" />
    </ol>
    <Button fullWidth variant="outlined" startIcon={<ContentCopyRounded />} onClick={copy}>{copied ? '주소를 복사했어요' : 'TOADY 주소 복사'}</Button>
  </div>;
  else {
    const steps = ANDROID_STEPS[browser];
    body = <div className="push-guide">
      <p className="push-guide-lead">TOADY를 홈 화면에 추가하면 <strong>앱처럼 바로</strong> 열 수 있어요. 따로 받을 앱은 없어요.</p>
      <ol className="push-steps">{steps.map((step, index) => <Step key={index} no={index + 1} {...step} />)}</ol>
    </div>;
  }
  // 삼성 인터넷·Chrome(안드로이드)은 메뉴 위치를 화살표로 짚어 주는 화면 안내로 보여 준다.
  if (open && onAndroid && mode === 'android' && (browser === 'samsung' || browser === 'chrome')) return <InstallCoach browser={browser} onClose={onClose} />;
  return <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs" aria-labelledby="install-guide-title" className="push-dialog">
    <DialogTitle id="install-guide-title" className="push-title">{mode === 'done' ? '홈 화면에 추가 완료' : '홈 화면에 추가'}<IconButton aria-label="닫기" onClick={onClose}><CloseRounded /></IconButton></DialogTitle>
    <DialogContent>{body}</DialogContent>
  </Dialog>;
}
