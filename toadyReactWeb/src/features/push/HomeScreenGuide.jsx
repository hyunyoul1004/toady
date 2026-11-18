import IosShareRounded from '@mui/icons-material/IosShareRounded';
import AddBoxOutlined from '@mui/icons-material/AddBoxOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';

/**
 * 아이폰 사파리에서 홈 화면에 추가하는 세 단계. 실제 화면의 버튼 모양을 아이콘으로 보여 준다.
 * 알림 설정 창(PushSettingsDialog)과 홈 화면 추가 안내(InstallGuideDialog)가 함께 쓴다.
 * 아이폰은 설치 창(beforeinstallprompt)이 없어서, 사용자가 직접 사파리 공유 메뉴에서 추가해야 한다.
 * mode: 'ios-safari'면 '사파리로 먼저 열기' 단계를 앞에 붙인다. 그 밖의 값('ios-home' 등)은 바로 세 단계를 보여 준다.
 * purpose: 'push'(알림 받으려고) 또는 'install'(앱처럼 쓰려고). 문구만 달라진다.
 */
export default function HomeScreenGuide({ mode, purpose = 'push' }) {
  const install = purpose === 'install';
  if (mode === 'ios-safari') return <div className="push-guide">
    <p className="push-guide-lead">아이폰은 <strong>Safari</strong>에서만 홈 화면에 추가할 수 있어요.</p>
    <ol className="push-steps">
      <li><span className="push-step-no">1</span><div><strong>주소를 복사해 Safari에서 열어 주세요</strong><small>지금 브라우저의 공유 메뉴에서 ‘Safari로 열기’를 눌러도 돼요.</small></div></li>
      <li><span className="push-step-no">2</span><div><strong>Safari에서 아래 방법으로 홈 화면에 추가해 주세요</strong></div></li>
    </ol>
    <Steps install={install} />
  </div>;
  return <div className="push-guide">
    <p className="push-guide-lead">{install
      ? <>TOADY를 <strong>홈 화면에 추가</strong>하면 앱처럼 바로 열 수 있어요. 앱 설치 없이 10초면 돼요.</>
      : <>아이폰은 TOADY를 <strong>홈 화면에 추가</strong>해야 알림을 받을 수 있어요. 앱 설치 없이 10초면 돼요.</>}</p>
    <Steps install={install} />
  </div>;
}

/** 공유 → 홈 화면에 추가 → 아이콘으로 열기. 마지막 단계 문구만 purpose에 따라 바뀐다. */
function Steps({ install }) {
  return <ol className="push-steps">
    <li><span className="push-step-no">1</span><div><strong>화면 아래 <span className="push-key"><IosShareRounded fontSize="inherit" />공유</span> 버튼을 눌러요</strong><small>아이패드는 주소창 오른쪽에 있어요.</small></div></li>
    <li><span className="push-step-no">2</span><div><strong>목록을 올려 <span className="push-key"><AddBoxOutlined fontSize="inherit" />홈 화면에 추가</span>를 눌러요</strong><small>보이지 않으면 목록 맨 아래 ‘작업 편집’에서 추가할 수 있어요.</small></div></li>
    {install
      ? <li><span className="push-step-no">3</span><div><strong>홈 화면의 TOADY 아이콘으로 열어요</strong><small>홈 화면에서 열면 알림도 받을 수 있어요(더보기 › 알림 설정).</small></div></li>
      : <li><span className="push-step-no">3</span><div><strong>홈 화면의 TOADY 아이콘으로 열고 <span className="push-key"><NotificationsActiveOutlined fontSize="inherit" />알림 켜기</span>를 눌러요</strong><small>나의 청약 › 더보기 › 알림 설정에서 켤 수 있어요.</small></div></li>}
  </ol>;
}
