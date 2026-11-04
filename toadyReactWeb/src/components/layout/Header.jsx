/**
 * @file Header.jsx
 * @description 상단 내비게이션 바 및 인증(로그인, 회원가입, 비밀번호 찾기) 모달 관리 컴포넌트
 * - Zustand(useAuthStore) 기반의 로그인 상태 관리
 * - MUI v5 컴포넌트 적용
 */
import React, { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Menu from '@mui/material/Menu';
import Container from '@mui/material/Container';
import Button from '@mui/material/Button';
import MenuItem from '@mui/material/MenuItem';
import LoginIcon from '@mui/icons-material/Login';
import FollowTheSignsIcon from '@mui/icons-material/FollowTheSigns';
import ExitToAppIcon from '@mui/icons-material/ExitToApp';
import DisabledByDefaultIcon from '@mui/icons-material/DisabledByDefault';
import Modal from '@mui/material/Modal';
import useScrollTrigger from '@mui/material/useScrollTrigger';
import Slide from '@mui/material/Slide';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { LoginPage } from '@/views/login/LoginPage';
import { SignUp, FindPassword } from '@/views/signup';

const loginModalstyle = {
  position: 'absolute',
  top: '50%',
  left: '50%',
  transform: 'translate(-50%, -50%)',
  width: { xs: '90%', sm: 500 },
  bgcolor: 'background.paper',
  border: '2px solid #000',
  boxShadow: 24,
  p: 2,
};

function HideOnScroll(props) {
  const { children, window } = props;
  const trigger = useScrollTrigger({
    target: window ? window() : undefined,
  });

  return (
    <Slide appear={false} direction="down" in={!trigger}>
      {children}
    </Slide>
  );
}

export const Header = (props) => {
  const { user, isAuthenticated, logout } = useAuthStore();

  const [loginModalOpen, setLoginModalOpen] = useState(false);
  const handleLoginModalOpen = () => setLoginModalOpen(true);
  const handleLoginModalClose = () => setLoginModalOpen(false);

  const [signUpModalOpen, setSignUpModalOpen] = useState(false);
  const handleSignUpModalOpen = () => setSignUpModalOpen(true);
  const handleSignUpModalClose = () => setSignUpModalOpen(false);

  const [findPwModalOpen, setFindPwModalOpen] = useState(false);
  const handleFindPwModalOpen = () => setFindPwModalOpen(true);
  const handleFindPwModalClose = () => setFindPwModalOpen(false);

  const [anchorEl, setAnchorEl] = useState(null);
  const handleMenu = (event) => setAnchorEl(event.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleLogout = (e) => {
    e.preventDefault();
    logout();
  };

  const nickname = user?.userNicName || user?.sub || '회원';

  return (
    <>
      <HideOnScroll {...props}>
        <AppBar sx={{ backgroundColor: 'white', color: 'black' }} position="fixed">
          <Container maxWidth="xl">
            <Toolbar disableGutters>
              <Box component="a" href="/" sx={{ display: 'flex', alignItems: 'center', textDecoration: 'none', color: 'inherit' }}>
                <img
                  alt="TOADY Logo"
                  src="https://www.toady.co.kr/cont/images/logo/logo.png"
                  style={{ width: '45px', marginRight: '8px' }}
                />
                <Typography
                  variant="h6"
                  noWrap
                  sx={{
                    fontFamily: 'monospace',
                    fontWeight: 700,
                    letterSpacing: '.2rem',
                    color: 'inherit',
                  }}
                >
                  TOADY
                </Typography>
              </Box>

              <Box sx={{ flexGrow: 1 }} />

              <Box sx={{ flexGrow: 0 }}>
                {!isAuthenticated ? (
                  <div>
                    <Button
                      color="inherit"
                      startIcon={<LoginIcon />}
                      onClick={(e) => {
                        e.preventDefault();
                        handleLoginModalOpen();
                      }}
                    >
                      로그인
                    </Button>
                    {' | '}
                    <Button
                      color="inherit"
                      startIcon={<FollowTheSignsIcon />}
                      onClick={(e) => {
                        e.preventDefault();
                        handleSignUpModalOpen();
                      }}
                    >
                      회원가입
                    </Button>
                  </div>
                ) : (
                  <div>
                    <Menu
                      id="menu-appbar"
                      anchorEl={anchorEl}
                      anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                      keepMounted
                      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
                      open={Boolean(anchorEl)}
                      onClose={handleClose}
                    >
                      <MenuItem onClick={handleClose}>{nickname}님 반갑습니다.</MenuItem>
                    </Menu>

                    <Button color="inherit" onClick={handleMenu}>
                      {nickname}
                    </Button>
                    {' | '}
                    <Button
                      sx={{ color: 'error.main' }}
                      startIcon={<ExitToAppIcon />}
                      onClick={handleLogout}
                    >
                      로그아웃
                    </Button>
                  </div>
                )}
              </Box>
            </Toolbar>
          </Container>
        </AppBar>
      </HideOnScroll>
      <Toolbar />

      {/* 로그인 모달 */}
      <Modal open={loginModalOpen} onClose={handleLoginModalClose}>
        <Box sx={loginModalstyle}>
          <Button sx={{ float: 'right' }} onClick={handleLoginModalClose}>
            <DisabledByDefaultIcon />
          </Button>
          <LoginPage
            handleLoginModalClose={handleLoginModalClose}
            handleSignUpModalOpen={handleSignUpModalOpen}
            handleFindPwModalOpen={handleFindPwModalOpen}
          />
        </Box>
      </Modal>

      {/* 회원가입 모달 */}
      <Modal open={signUpModalOpen} onClose={handleSignUpModalClose}>
        <Box sx={loginModalstyle}>
          <Button sx={{ float: 'right' }} onClick={handleSignUpModalClose}>
            <DisabledByDefaultIcon />
          </Button>
          <SignUp handleSignUpModalClose={handleSignUpModalClose} />
        </Box>
      </Modal>

      {/* 비밀번호 찾기 모달 */}
      <Modal open={findPwModalOpen} onClose={handleFindPwModalClose}>
        <Box sx={loginModalstyle}>
          <Button sx={{ float: 'right' }} onClick={handleFindPwModalClose}>
            <DisabledByDefaultIcon />
          </Button>
          <FindPassword handleFindPwModalClose={handleFindPwModalClose} />
        </Box>
      </Modal>
    </>
  );
};

export default Header;
