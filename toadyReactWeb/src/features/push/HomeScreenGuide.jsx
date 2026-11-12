import IosShareRounded from '@mui/icons-material/IosShareRounded';
import AddBoxOutlined from '@mui/icons-material/AddBoxOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';

/** 아이폰 사파리에서 홈 화면에 추가하는 세 단계. 실제 화면의 버튼 모양을 아이콘으로 보여 준다. */
export default function HomeScreenGuide({ mode }) {
  if (mode === 'ios-safari') return <div className="push-guide">
    <p className="push-guide-lead">아이폰은 <strong>Safari</strong>에서만 홈 화면에 추가할 수 있어요.</p>
    <ol className="push-steps">
      <li><span className="push-step-no">1</span><div><strong>주소를 복사해 Safari에서 열어 주세요</strong><small>지금 브라우저의 공유 메뉴에서 ‘Safari로 열기’를 눌러도 돼요.</small></div></li>
      <li><span className="push-step-no">2</span><div><strong>Safari에서 아래 방법으로 홈 화면에 추가해 주세요</strong></div></li>
    </ol>
    <Steps />
  </div>;
  return <div className="push-guide">
    <p className="push-guide-lead">아이폰은 TOADY를 <strong>홈 화면에 추가</strong>해야 알림을 받을 수 있어요. 앱 설치 없이 10초면 돼요.</p>
    <Steps />
  </div>;
}

function Steps() {
  return <ol className="push-steps">
    <li><span className="push-step-no">1</span><div><strong>화면 아래 <span className="push-key"><IosShareRounded fontSize="inherit" />공유</span> 버튼을 눌러요</strong><small>아이패드는 주소창 오른쪽에 있어요.</small></div></li>
    <li><span className="push-step-no">2</span><div><strong>목록을 올려 <span className="push-key"><AddBoxOutlined fontSize="inherit" />홈 화면에 추가</span>를 눌러요</strong><small>보이지 않으면 목록 맨 아래 ‘작업 편집’에서 추가할 수 있어요.</small></div></li>
    <li><span className="push-step-no">3</span><div><strong>홈 화면의 TOADY 아이콘으로 열고 <span className="push-key"><NotificationsActiveOutlined fontSize="inherit" />알림 켜기</span>를 눌러요</strong><small>나의 청약 › 더보기 › 알림 설정에서 켤 수 있어요.</small></div></li>
  </ol>;
}
