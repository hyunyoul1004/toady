/**
 * @file Sidebar.jsx
 * @description 좌측 사이드바 내비게이션 및 반응형 드로어 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바 (폭 240px)
 * - 모바일/태블릿: 햄버거 메뉴 버튼 클릭 시 좌측에서 슬라이드되는 임시 Drawer
 * - 상단 로고, 주요 메뉴 리스트, 하단 로그인/회원가입/로그아웃 인증 영역 포함
 */
import React, { useRef } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import BookmarkBorderIcon from '@mui/icons-material/BookmarkBorder';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import CalculateOutlinedIcon from '@mui/icons-material/CalculateOutlined';
import FactCheckOutlinedIcon from '@mui/icons-material/FactCheckOutlined';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import EventNoteOutlinedIcon from '@mui/icons-material/EventNoteOutlined';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import NotificationsNoneOutlinedIcon from '@mui/icons-material/NotificationsNoneOutlined';
import LoginIcon from '@mui/icons-material/Login';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import ExitToAppIcon from '@mui/icons-material/ExitToApp';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import CloseIcon from '@mui/icons-material/Close';
import IconButton from '@mui/material/IconButton';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { MAIN_NAV, MORE_NAV, isNavActive, retapAction } from './navItems';

const MAIN_ICONS = { calendar: <CalendarMonthIcon />, match: <FactCheckOutlinedIcon />, score: <CalculateOutlinedIcon />, my: <BookmarkBorderIcon /> };
const MORE_ICONS = { weekly: <EventNoteOutlinedIcon />, areas: <PlaceOutlinedIcon />, faq: <HelpOutlineIcon />, push: <NotificationsNoneOutlinedIcon /> };

export const DRAWER_WIDTH = 240;

export function Sidebar({ mobileOpen, onMobileClose, onOpenLogin, onOpenSignUp }) {
  const location = useLocation();
  const navigate = useNavigate();
  const user = useAuthStore(state => state.user);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const logout = useAuthStore(state => state.logout);

  const nickname = user?.userNicName || user?.sub || '회원';

  const menuItems = MAIN_NAV.map(item => ({ ...item, text: item.label, icon: MAIN_ICONS[item.key] }));
  /**
   * 하단 탭과 같은 '다시 누르기' 규칙. 모바일 메뉴(드로어)는 히스토리 한 단계(layout 레이어)로 열려 있으므로
   * 판정에서는 그 레이어를 빼고, 같은 화면이면 먼저 메뉴를 닫은 뒤(뒤로 한 단계) 상세 닫기·처음 화면·맨 위로를 적용한다.
   */
  const going = useRef(false);
  function go(path, state = null) {
    // 메뉴가 닫히는 동안 다시 눌러 두 단계 뒤로 가지 않게 한 번만 처리한다.
    if (going.current) return;
    const layers = (location.state?.overlayLayers || []).filter(layer => layer.id !== 'layout');
    const action = retapAction(path, { ...location, state: { ...location.state, overlayLayers: layers } });
    if (action === 'push') { navigate(path, { replace: mobileOpen, state }); return; }
    const apply = () => {
      if (action === 'back') navigate(-1);
      else { if (action === 'replace' || state) navigate(path, { replace: true, state }); window.scrollTo({ top: 0, behavior: 'smooth' }); }
    };
    if (!mobileOpen) { apply(); return; }
    going.current = true;
    window.addEventListener('popstate', () => setTimeout(() => { going.current = false; apply(); }, 0), { once: true });
    navigate(-1);
  }
  function openMore(item) {
    if (item.overlay) {
      // 나의 청약을 한 단계 쌓은 뒤 알림 설정 창을 연다(닫으면 나의 청약에 머문다).
      if (location.pathname !== item.path) navigate(item.path, { replace: mobileOpen });
      navigate(item.path, { replace: mobileOpen && location.pathname === item.path, state: { overlayLayers: [{ id: item.overlay, value: true }] } });
      return;
    }
    go(item.path);
  }

  const drawerContent = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', bgcolor: '#ffffff' }}>
      {/* 1. 상단 로고 영역 */}
      <Box
        sx={{
          height: 64,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          px: 2.5,
          borderBottom: '1px solid #f1f5f9'
        }}
      >
        <Box component={Link} to="/" replace={mobileOpen} state={{browseView:'calendar'}} aria-label="TOADY 홈으로"
          sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer', gap: 1,color:'inherit',textDecoration:'none' }}
        >
          <img
            alt=""
            src="/icons/icon-192.png"
            style={{ width: '34px', height: '34px', objectFit: 'contain', background: '#eef7f0', borderRadius: '10px', padding: '3px', boxSizing: 'border-box' }}
          />
          <Typography
            variant="h6"
            sx={{
              fontWeight: 800,
              fontSize: '1.25rem',
              color: '#1e293b',
              letterSpacing: '-0.5px'
            }}
          >
            TOADY
          </Typography>
        </Box>
        {/* 모바일 화면에서만 닫기 버튼 노출 */}
        <IconButton
          onClick={onMobileClose}
          aria-label="메뉴 닫기"
          sx={{ display: { xs: 'flex', md: 'none' }, color: '#64748b' }}
          size="small"
        >
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>

      {/* 2. 내비게이션 메뉴 리스트 */}
      <Box sx={{ flexGrow: 1, py: 2, px: 1.5 }}>
        <Typography
          variant="caption"
          sx={{ px: 1.5, mb: 1, display: 'block', fontWeight: 700, color: '#94a3b8', fontSize: '0.72rem' }}
        >
          MENU
        </Typography>
        <List disablePadding>
          {menuItems.map((item) => {
            const isSelected = isNavActive(item.path, location.pathname);
            return (
              <ListItem key={item.key} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  aria-current={isSelected?'page':undefined}
                  selected={isSelected}
                  onClick={() => {
                    go(item.path, item.path === '/' ? { browseView: 'calendar' } : null);
                  }}
                  sx={{
                    borderRadius: 1.5,
                    py: 1,
                    px: 1.5,
                    bgcolor: isSelected ? '#eff6ff' : 'transparent',
                    color: isSelected ? '#2563eb' : '#475569',
                    '&:hover': {
                      bgcolor: isSelected ? '#eff6ff' : '#f8fafc'
                    }
                  }}
                >
                  <ListItemIcon sx={{ minWidth: 36, color: isSelected ? '#2563eb' : '#64748b' }}>
                    {item.icon}
                  </ListItemIcon>
                  <ListItemText
                    primary={item.text}
                    primaryTypographyProps={{
                      fontWeight: isSelected ? 800 : 600,
                      fontSize: '0.9rem'
                    }}
                  />
                </ListItemButton>
              </ListItem>
            );
          })}
        </List>
        {/* 더보기: 주요 메뉴와 구분해 아래에 둔다. 하단 탭의 '더보기'도 이 목록을 연다. */}
        <Typography variant="caption" sx={{ px: 1.5, mt: 2, mb: 1, display: 'block', fontWeight: 700, color: '#94a3b8', fontSize: '0.72rem' }}>더보기</Typography>
        <List disablePadding>
          {MORE_NAV.map(item => {
            const selected = !item.overlay && location.pathname === item.path;
            return <ListItem key={item.key} disablePadding sx={{ mb: 0.25 }}>
              <ListItemButton aria-current={selected ? 'page' : undefined} selected={selected} onClick={() => openMore(item)}
                sx={{ borderRadius: 1.5, py: 0.75, px: 1.5, bgcolor: selected ? '#eff6ff' : 'transparent', color: selected ? '#2563eb' : '#475569', '&:hover': { bgcolor: selected ? '#eff6ff' : '#f8fafc' } }}>
                <ListItemIcon sx={{ minWidth: 36, color: selected ? '#2563eb' : '#64748b' }}>{MORE_ICONS[item.key]}</ListItemIcon>
                <ListItemText primary={item.label} primaryTypographyProps={{ fontWeight: selected ? 800 : 600, fontSize: '0.86rem' }} />
              </ListItemButton>
            </ListItem>;
          })}
        </List>
      </Box>

      <Divider />

      {/* 3. 하단 인증(로그인/회원가입/사용자 정보) 영역 */}
      <Box sx={{ p: 2, bgcolor: '#f8fafc' }}>
        {!isAuthenticated ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            <Button
              fullWidth
              variant="contained"
              disableElevation
              startIcon={<LoginIcon />}
              onClick={() => {
                onOpenLogin();
              }}
              sx={{
                bgcolor: '#2563eb',
                fontWeight: 700,
                fontSize: '0.85rem',
                py: 0.8,
                borderRadius: 1.5,
                '&:hover': { bgcolor: '#1d4ed8' }
              }}
            >
              로그인
            </Button>
            <Button
              fullWidth
              variant="outlined"
              startIcon={<PersonAddIcon />}
              onClick={() => {
                onOpenSignUp();
              }}
              sx={{
                color: '#475569',
                borderColor: '#cbd5e1',
                fontWeight: 700,
                fontSize: '0.85rem',
                py: 0.8,
                borderRadius: 1.5,
                '&:hover': { borderColor: '#94a3b8', bgcolor: '#fff' }
              }}
            >
              회원가입
            </Button>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            <Box sx={{ px: 0.5 }}>
              <Typography variant="caption" sx={{ color: '#64748b' }}>
                접속 중인 계정
              </Typography>
              <Typography sx={{ fontWeight: 800, fontSize: '0.95rem', color: '#1e293b' }}>
                {nickname}님
              </Typography>
            </Box>
            <Button
              fullWidth
              variant={location.pathname === '/account' ? 'contained' : 'outlined'}
              disableElevation
              size="small"
              startIcon={<ManageAccountsOutlinedIcon />}
              onClick={() => go('/account')}
              aria-current={location.pathname === '/account' ? 'page' : undefined}
              sx={{ fontWeight: 700, borderRadius: 1.5, mt: 0.5 }}
            >
              내 정보
            </Button>
            <Button
              fullWidth
              variant="outlined"
              color="error"
              size="small"
              startIcon={<ExitToAppIcon />}
              onClick={() => {
                logout();
                if (mobileOpen) onMobileClose();
              }}
              sx={{ fontWeight: 700, borderRadius: 1.5, mt: 0.5 }}
            >
              로그아웃
            </Button>
          </Box>
        )}
      </Box>
    </Box>
  );

  return (
    <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
      {/* 모바일 화면용 임시 Drawer */}
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onMobileClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          display: { xs: 'block', md: 'none' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: DRAWER_WIDTH,
            borderRight: '1px solid #e2e8f0',
            boxShadow: '4px 0 24px rgba(0,0,0,0.1)'
          }
        }}
      >
        {drawerContent}
      </Drawer>

      {/* 데스크톱 화면용 고정 Drawer */}
      <Drawer
        variant="permanent"
        sx={{
          display: { xs: 'none', md: 'block' },
          '& .MuiDrawer-paper': {
            boxSizing: 'border-box',
            width: DRAWER_WIDTH,
            borderRight: '1px solid #e2e8f0',
            bgcolor: '#ffffff'
          }
        }}
        open
      >
        {drawerContent}
      </Drawer>
    </Box>
  );
}

export default Sidebar;

