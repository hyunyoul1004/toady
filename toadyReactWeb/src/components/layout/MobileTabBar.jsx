import { useLocation, useNavigate } from 'react-router-dom';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import CalculateOutlined from '@mui/icons-material/CalculateOutlined';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import MenuRounded from '@mui/icons-material/MenuRounded';
import './mobileTabBar.css';

const TABS = [
  { path: '/', label: '캘린더', icon: <CalendarMonthOutlined /> },
  { path: '/score', label: '가점 계산', icon: <CalculateOutlined /> },
  { path: '/my', label: '나의 청약', icon: <BookmarkBorderRounded /> }
];

/** 모바일(사이드바가 숨는 폭) 하단 탭. 재방문 기능(가점·나의 청약)이 햄버거 메뉴 속에 묻히지 않게 한다. */
export default function MobileTabBar({ onOpenMenu, menuOpen = false }) {
  const location = useLocation(), navigate = useNavigate();
  const active = path => path === '/' ? location.pathname === '/' : location.pathname.startsWith(path);
  return <nav className="mobile-tabbar" aria-label="주요 메뉴">
    {TABS.map(tab => <button type="button" key={tab.path} className={active(tab.path) ? 'is-active' : ''} aria-current={active(tab.path) ? 'page' : undefined}
      onClick={() => { if (!active(tab.path) || location.search) navigate(tab.path, { state: tab.path === '/' ? { browseView: 'calendar' } : null }); }}>
      {tab.icon}<span>{tab.label}</span>
    </button>)}
    <button type="button" onClick={onOpenMenu} aria-haspopup="dialog" aria-expanded={menuOpen}><MenuRounded /><span>메뉴</span></button>
  </nav>;
}
