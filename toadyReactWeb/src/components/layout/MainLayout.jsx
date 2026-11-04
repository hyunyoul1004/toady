/**
 * @file MainLayout.jsx
 * @description 애플리케이션 공통 레이아웃 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바
 * - 모바일: 상단 앱바 및 슬라이드 내비게이션 드로어
 * - 계정 관련 모달(로그인, 회원가입, 비밀번호 찾기) 관리
 */
import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import MenuIcon from '@mui/icons-material/Menu';
import Dialog from '@mui/material/Dialog';
import CloseIcon from '@mui/icons-material/Close';

import Sidebar, { DRAWER_WIDTH } from './Sidebar';
import Copyright from './Copyright';
import { LoginPage } from '@/views/login/LoginPage';
import { SignUp, FindPassword } from '@/views/signup';

export function MainLayout() {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const [signUpModalOpen, setSignUpModalOpen] = useState(false);
  const [findPwModalOpen, setFindPwModalOpen] = useState(false);

  const handleDrawerToggle = () => {
    setMobileOpen(!mobileOpen);
  };

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#f8fafc' }}>
      <CssBaseline />

      {/* 모바일 화면에서만 노출되는 상단 슬림 네비게이션 바 (햄버거 메뉴 포함) */}
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          display: { md: 'none' },
          bgcolor: '#ffffff',
          color: '#1e293b',
          borderBottom: '1px solid #e2e8f0'
        }}
      >
        <Toolbar sx={{ minHeight: '56px !important', px: 2 }}>
          <IconButton
            color="inherit"
            aria-label="open drawer"
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 1.5 }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <img
              alt="TOADY Logo"
              src="https://www.toady.co.kr/cont/images/logo/logo.png"
              style={{ width: '28px', height: '28px', objectFit: 'contain' }}
            />
            <Typography variant="h6" sx={{ fontWeight: 800, fontSize: '1.1rem', color: '#1e293b' }}>
              TOADY
            </Typography>
          </Box>
        </Toolbar>
      </AppBar>

      {/* 좌측 사이드바 (데스크톱: 고정, 모바일: 슬라이드 드로어) */}
      <Sidebar
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
        onOpenLogin={() => setLoginModalOpen(true)}
        onOpenSignUp={() => setSignUpModalOpen(true)}
      />

      {/* 메인 콘텐츠 영역 */}
      <Box
        component="main"
        sx={{
          flexGrow: 1,
          p: { xs: 1.5, sm: 2.5 },
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: { xs: '56px', md: 0 }, // 모바일 상단 바 높이만큼 여백 확보
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100vh'
        }}
      >
        <Box sx={{ flexGrow: 1 }}>
          <Outlet />
        </Box>
        <Box component="footer" sx={{ py: 3, mt: 4, borderTop: '1px solid #f1f5f9' }}>
          <Copyright />
        </Box>
      </Box>

      {/* 로그인 다이얼로그 */}
      <Dialog
        open={loginModalOpen}
        onClose={() => setLoginModalOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: 3.5,
            p: { xs: 2.5, sm: 3.5 },
            bgcolor: '#ffffff',
            boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.25)',
            position: 'relative'
          }
        }}
        BackdropProps={{
          sx: {
            backdropFilter: 'blur(6px)',
            bgcolor: 'rgba(15, 23, 42, 0.45)'
          }
        }}
      >
        <IconButton
          onClick={() => setLoginModalOpen(false)}
          size="small"
          sx={{
            position: 'absolute',
            top: 16,
            right: 16,
            color: '#94a3b8',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        <LoginPage
          handleCloseModal={() => setLoginModalOpen(false)}
          handleSignUpModalOpen={() => {
            setLoginModalOpen(false);
            setSignUpModalOpen(true);
          }}
          handleFindPwModalOpen={() => {
            setLoginModalOpen(false);
            setFindPwModalOpen(true);
          }}
        />
      </Dialog>

      {/* 회원가입 다이얼로그 */}
      <Dialog
        open={signUpModalOpen}
        onClose={() => setSignUpModalOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: 3.5,
            p: { xs: 2.5, sm: 3.5 },
            bgcolor: '#ffffff',
            boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.25)',
            position: 'relative'
          }
        }}
        BackdropProps={{
          sx: {
            backdropFilter: 'blur(6px)',
            bgcolor: 'rgba(15, 23, 42, 0.45)'
          }
        }}
      >
        <IconButton
          onClick={() => setSignUpModalOpen(false)}
          size="small"
          sx={{
            position: 'absolute',
            top: 16,
            right: 16,
            color: '#94a3b8',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        <SignUp
          handleCloseModal={() => setSignUpModalOpen(false)}
          handleOpenLogin={() => {
            setSignUpModalOpen(false);
            setLoginModalOpen(true);
          }}
        />
      </Dialog>

      {/* 비밀번호 찾기 다이얼로그 */}
      <Dialog
        open={findPwModalOpen}
        onClose={() => setFindPwModalOpen(false)}
        maxWidth="xs"
        fullWidth
        PaperProps={{
          sx: {
            borderRadius: 3.5,
            p: { xs: 2.5, sm: 3.5 },
            bgcolor: '#ffffff',
            boxShadow: '0 25px 50px -12px rgba(15, 23, 42, 0.25)',
            position: 'relative'
          }
        }}
        BackdropProps={{
          sx: {
            backdropFilter: 'blur(6px)',
            bgcolor: 'rgba(15, 23, 42, 0.45)'
          }
        }}
      >
        <IconButton
          onClick={() => setFindPwModalOpen(false)}
          size="small"
          sx={{
            position: 'absolute',
            top: 16,
            right: 16,
            color: '#94a3b8',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        <FindPassword
          handleCloseModal={() => setFindPwModalOpen(false)}
          handleOpenLogin={() => {
            setFindPwModalOpen(false);
            setLoginModalOpen(true);
          }}
        />
      </Dialog>
    </Box>
  );
}

export default MainLayout;
