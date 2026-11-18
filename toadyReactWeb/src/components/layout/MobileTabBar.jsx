import { useLocation, useNavigate } from 'react-router-dom';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import { MAIN_NAV, isMoreActive, isNavActive, retapAction } from './navItems';
import { usePushAttention } from '@/features/push/usePushAttention';
import './mobileTabBar.css';

const ICONS = { calendar: <CalendarMonthOutlined />, match: <FactCheckOutlined />, score: <CalculateOutlined />, my: <BookmarkBorderRounded /> };

/**
 * 모바일(사이드바가 숨는 폭) 하단 탭. 재방문 기능(맞춤 청약·가점·나의 청약)이 햄버거 메뉴 속에 묻히지 않게 한다.
 * MainLayout이 그린다. 메뉴 목록은 navItems.js를 사이드바와 함께 쓴다. PC 폭에서는 CSS로 숨긴다.
 * onOpenMenu: '더보기'를 누르면 MainLayout이 모바일 메뉴(사이드바 드로어)를 연다.
 * menuOpen: 메뉴가 열려 있으면 '더보기'를 켜진 상태로 보여 준다.
 * pushDot: 알림을 아직 안 켰으면 '더보기'에 빨간 점(usePushAttention).
 */
export default function MobileTabBar({ onOpenMenu, menuOpen = false }) {
  const location = useLocation(), navigate = useNavigate();
  const more = isMoreActive(location.pathname);
  const pushDot = usePushAttention();
  /**
   * 탭을 눌렀을 때. navigate(경로, { state })는 히스토리를 한 칸 쌓고, { replace: true }는 지금 칸을 바꿔 쌓지 않는다.
   * state는 주소에 보이지 않는 값으로 history에 함께 저장된다(location.state로 읽음). 뒤로가기하면 그 칸의 state도 돌아온다.
   */
  function open(path) {
    const action = retapAction(path, location);
    const state = path === '/' ? { browseView: 'calendar' } : null;
    // 같은 탭을 다시 누르면 열린 상세를 닫거나(뒤로가기와 같게) 처음 화면으로 되돌린다.
    if (action === 'back') navigate(-1);
    else if (action === 'push') navigate(path, { state });
    else {
      // 이미 그 탭의 처음 화면이면(또는 조건만 지우면) 맨 위로 올린다(앱의 탭 다시 누르기와 같게).
      // 캘린더는 목록 보기였어도 달력 보기로 돌아가게 state를 다시 준다(히스토리는 쌓지 않음).
      if (action === 'replace' || state) navigate(path, { replace: true, state });
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }
  return <nav className="mobile-tabbar" aria-label="주요 메뉴">
    {MAIN_NAV.map(tab => {
      const active = isNavActive(tab.path, location.pathname);
      return <button type="button" key={tab.key} className={active ? 'is-active' : ''} aria-current={active ? 'page' : undefined} onClick={() => open(tab.path)}>
        {ICONS[tab.key]}<span>{tab.short}</span>
      </button>;
    })}
    <button type="button" className={more || menuOpen ? 'is-active' : ''} onClick={onOpenMenu} aria-haspopup="dialog" aria-expanded={menuOpen} aria-label={pushDot ? '더보기, 알림이 꺼져 있어요' : undefined}><span className="mobile-tabbar-icon"><MoreHorizRounded />{pushDot && <i className="mobile-tabbar-dot" aria-hidden="true" />}</span><span>더보기</span></button>
  </nav>;
}
