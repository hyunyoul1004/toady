import Modal from '@mui/material/Modal';
import CloseRounded from '@mui/icons-material/CloseRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import HistoryRounded from '@mui/icons-material/HistoryRounded';
import LanguageRounded from '@mui/icons-material/LanguageRounded';
import AddCircleOutlineRounded from '@mui/icons-material/AddCircleOutlineRounded';
import GraphicEqRounded from '@mui/icons-material/GraphicEqRounded';
import ShareOutlined from '@mui/icons-material/ShareOutlined';
import AddToHomeScreenRounded from '@mui/icons-material/AddToHomeScreenRounded';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import AppsRounded from '@mui/icons-material/AppsRounded';
import TouchAppRounded from '@mui/icons-material/TouchAppRounded';
import TabRounded from '@mui/icons-material/TabRounded';
import './installCoach.css';

/**
 * 게임 튜토리얼처럼 화면 위에서 '어디를 누를지' 짚어 주는 홈 화면 추가 안내(삼성 인터넷·Chrome 안드로이드).
 * 브라우저 메뉴 버튼은 페이지 밖(주소창·아래 도구 막대)에 있어 직접 가리킬 수 없으므로, 그 쪽 화면 끝으로 통통 튀는 화살표를 둔다.
 * 메뉴 안의 항목은 실제 메뉴를 본뜬 그림에서 눌러야 할 줄을 깜빡여 보여 준다(삼성 인터넷 메뉴는 2026-11 실제 화면 기준).
 * 열고 닫기는 부모(InstallGuideDialog → Sidebar의 useHistoryOverlay)가 관리해 뒤로가기 한 번에 닫힌다.
 * MUI Modal로 감싸 열면 닫기 버튼으로 초점을 옮기고, Tab 초점을 안내 안에 가두고, 닫으면 연 버튼으로 초점을 돌려준다.
 * 뒤 화면 스크롤 잠금과 Esc 닫기도 Modal이 한다(배경 어둡게는 안내 자체 스타일이라 Modal 배경은 숨긴다).
 */
const COACH = {
  samsung: {
    arrow: 'bottom-right',
    first: <>화면 <b>맨 아래 오른쪽 끝</b>의 <span className="coach-key"><MoreVertRounded fontSize="inherit" /></span> 를 눌러요</>,
    menu: [
      [FileDownloadOutlined, '다운로드'], [HistoryRounded, '기록'], [LanguageRounded, '저장한 페이지'],
      [AddCircleOutlineRounded, '현재 페이지 추가', true], [GraphicEqRounded, '읽어주기']
    ],
    second: <>메뉴에서 <b>‘현재 페이지 추가’</b>를 눌러요</>,
    third: { text: <><b>‘홈 화면’</b>을 누른 다음, 뜨는 창에서 <b>‘추가’</b>를 눌러요</>, options: [[BookmarkBorderRounded, '북마크'], [AppsRounded, '빠른 실행'], [AddToHomeScreenRounded, '홈 화면', true]] }
  },
  chrome: {
    arrow: 'top-right',
    first: <>주소창 <b>오른쪽 끝</b>의 <span className="coach-key"><MoreVertRounded fontSize="inherit" /></span> 를 눌러요</>,
    menu: [
      [TabRounded, '새 탭'], [HistoryRounded, '방문 기록'], [FileDownloadOutlined, '다운로드'],
      [AddToHomeScreenRounded, '앱 설치', true], [ShareOutlined, '공유']
    ],
    second: <>메뉴에서 <b>‘앱 설치’</b>를 눌러요 <small>(‘홈 화면에 추가’로 보이면 그 항목)</small></>,
    third: { text: <>뜨는 창에서 <b>‘설치’</b>를 눌러요</>, options: null }
  }
};

/** 그림 속 메뉴 한 줄. hot이면 테두리가 깜빡이고 손가락 아이콘이 톡톡 누른다. */
function MenuRow({ Icon, label, hot }) {
  return <li className={hot ? 'is-hot' : undefined}>
    <Icon fontSize="small" aria-hidden="true" /><span>{label}</span>
    {hot && <TouchAppRounded className="coach-finger" aria-hidden="true" />}
  </li>;
}

export default function InstallCoach({ browser, onClose }) {
  const coach = COACH[browser] || COACH.chrome;
  // Modal의 onClose는 (event, reason)을 넘기므로 부모 onClose에는 넘기지 않는다.
  return <Modal open onClose={() => onClose()} hideBackdrop sx={{ zIndex: 1500 }}>
    <div className="install-coach" role="dialog" aria-modal="true" aria-labelledby="install-coach-title">
    {/* 브라우저 메뉴 버튼 쪽 화면 끝을 가리키는 화살표 */}
    <div className={`coach-arrow is-${coach.arrow}`} aria-hidden="true">
      <span className="coach-arrow-label">여기 <MoreVertRounded fontSize="inherit" /> 를 눌러요</span>
      <svg viewBox="0 0 48 64" width="52" height="68"><path d="M24 4v44M8 34l16 18 16-18" fill="none" stroke="currentColor" strokeWidth="7" strokeLinecap="round" strokeLinejoin="round" /></svg>
    </div>
    <div className={`coach-card is-${coach.arrow}`}>
      <div className="coach-head">
        <strong id="install-coach-title">홈 화면에 TOADY 추가하기</strong>
        <button type="button" className="coach-close" aria-label="안내 닫기" onClick={onClose} autoFocus><CloseRounded fontSize="small" /></button>
      </div>
      <ol className="coach-steps">
        <li><span className="coach-no">1</span><p>{coach.first}</p></li>
        <li>
          <span className="coach-no">2</span>
          <div><p>{coach.second}</p>
            <ul className="coach-menu" aria-label="브라우저 메뉴 예시">{coach.menu.map(([Icon, label, hot]) => <MenuRow key={label} Icon={Icon} label={label} hot={hot} />)}</ul>
          </div>
        </li>
        <li>
          <span className="coach-no">3</span>
          <div><p>{coach.third.text}</p>
            {coach.third.options && <ul className="coach-menu is-options" aria-label="추가할 곳 예시">{coach.third.options.map(([Icon, label, hot]) => <MenuRow key={label} Icon={Icon} label={label} hot={hot} />)}</ul>}
          </div>
        </li>
      </ol>
      <p className="coach-note">홈 화면에 안 보이면 화면을 아래에서 위로 쓸어 올린 앱스 화면에서 TOADY를 찾아 길게 누르고 ‘홈에 추가’를 눌러요.</p>
      <button type="button" className="coach-done" onClick={onClose}>확인했어요</button>
    </div>
    </div>
  </Modal>;
}
