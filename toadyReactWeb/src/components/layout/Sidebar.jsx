/**
 * @file Sidebar.jsx
 * @description 좌측 사이드바 내비게이션 및 반응형 드로어 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바 (폭 240px)
 * - 모바일/태블릿: 햄버거 메뉴 버튼 클릭 시 좌측에서 슬라이드되는 임시 Drawer
 * - 상단 로고, 주요 메뉴 리스트, 하단 로그인/회원가입/로그아웃 인증 영역 포함
 */
import React from 'react';
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
import LoginIcon from '@mui/icons-material/Login';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import ExitToAppIcon from '@mui/icons-material/ExitToApp';
import CloseIcon from '@mui/icons-material/Close';
import IconButton from '@mui/material/IconButton';

import { useAuthStore } from '@/features/auth/store/useAuthStore';

export const DRAWER_WIDTH = 240;

export function Sidebar({ mobileOpen, onMobileClose, onOpenLogin, onOpenSignUp }) {
  const location = useLocation();
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuthStore();

  const nickname = user?.userNicName || user?.sub || '회원';

  const menuItems = [
    { text: '청약캘린더', icon: <CalendarMonthIcon />, path: '/' },
    { text: '가점 계산기', icon: <CalculateOutlinedIcon />, path: '/score' },
    { text: '나의 청약', icon: <BookmarkBorderIcon />, path: '/my' }
  ];

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
            alt="TOADY Logo"
            src="https://www.toady.co.kr/cont/images/logo/logo.png"
            style={{ width: '36px', height: '36px', objectFit: 'contain' }}
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
            const isSelected = item.path==='/'?location.pathname==='/' || location.pathname.startsWith('/announcements/') || location.pathname==='/showSubscriptDetail':location.pathname===item.path;
            return (
              <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
                <ListItemButton
                  aria-current={isSelected?'page':undefined}
                  selected={isSelected}
                  onClick={() => {
                    navigate(item.path, { replace: mobileOpen, state: item.path === '/' ? { browseView: 'calendar' } : null });
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

