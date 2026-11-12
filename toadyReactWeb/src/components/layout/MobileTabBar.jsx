import { useLocation, useNavigate } from 'react-router-dom';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import { MAIN_NAV, isMoreActive, isNavActive, retapAction } from './navItems';
import './mobileTabBar.css';

const ICONS = { calendar: <CalendarMonthOutlined />, match: <FactCheckOutlined />, score: <CalculateOutlined />, my: <BookmarkBorderRounded /> };

/** 모바일(사이드바가 숨는 폭) 하단 탭. 재방문 기능(맞춤 청약·가점·나의 청약)이 햄버거 메뉴 속에 묻히지 않게 한다. */
export default function MobileTabBar({ onOpenMenu, menuOpen = false }) {
  const location = useLocation(), navigate = useNavigate();
  const more = isMoreActive(location.pathname);
  function open(path) {
    const action = retapAction(path, location);
    const state = path === '/' ? { browseView: 'calendar' } : null;
    // 같은 탭을 다시 누르면 열린 상세를 닫거나(뒤로가기와 같게) 처음 화면으로 되돌린다.
    if (action === 'back') navigate(-1);
    else if (action === 'replace') navigate(path, { replace: true, state });
    else if (action === 'push') navigate(path, { state });
  }
  return <nav className="mobile-tabbar" aria-label="주요 메뉴">
    {MAIN_NAV.map(tab => {
      const active = isNavActive(tab.path, location.pathname);
      return <button type="button" key={tab.key} className={active ? 'is-active' : ''} aria-current={active ? 'page' : undefined} onClick={() => open(tab.path)}>
        {ICONS[tab.key]}<span>{tab.short}</span>
      </button>;
    })}
    <button type="button" className={more || menuOpen ? 'is-active' : ''} onClick={onOpenMenu} aria-haspopup="dialog" aria-expanded={menuOpen}><MoreHorizRounded /><span>더보기</span></button>
  </nav>;
}
