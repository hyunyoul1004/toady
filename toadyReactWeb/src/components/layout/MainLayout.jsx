/**
 * @file MainLayout.jsx
 * @description 애플리케이션 공통 레이아웃 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바
 * - 모바일: 상단 앱바 및 슬라이드 내비게이션 드로어
 * - 계정 관련 모달(로그인, 회원가입, 비밀번호 찾기) 관리
 */
import React from 'react';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useScrollTopOnNavigate } from '@/hooks/useScrollTopOnNavigate';
import { Outlet, Link } from 'react-router-dom';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import IconButton from '@mui/material/IconButton';
import MenuIcon from '@mui/icons-material/Menu';
import Badge from '@mui/material/Badge';
import { useMyReplies } from '@/features/account/useMyAccount';
import { useMessageUnread } from '@/features/community/useCommunity';
import CommunityLayers from '@/features/community/CommunityLayers';
import OperationNotices from '@/features/account/OperationNotices';
import Dialog from '@mui/material/Dialog';
import CloseIcon from '@mui/icons-material/Close';
import Snackbar from '@mui/material/Snackbar';
import Button from '@mui/material/Button';

import Sidebar, { DRAWER_WIDTH } from './Sidebar';
import Copyright from './Copyright';
import MobileTabBar from './MobileTabBar';
import BrandLogo from './BrandLogo';
import { LoginPage } from '@/views/login/LoginPage';
import { SignUp, FindPassword } from '@/views/signup';

export function MainLayout() {
  const [overlay, setOverlay] = useHistoryOverlay('layout');
  useScrollTopOnNavigate();
  const mobileOpen = overlay === 'menu';
  const loginModalOpen = overlay === 'login';
  const signUpModalOpen = overlay === 'signup';
  const findPwModalOpen = overlay === 'password';
  // 닫기는 최신 레이어 값이 자기 자신일 때만 적용한다(비동기 로그인 뒤 늦게 온 닫기가 다른 창을 닫지 않게).
  const toggleLayer = name => open => setOverlay(current => open ? name : current === name ? null : current);
  const setMobileOpen = toggleLayer('menu');
  const setLoginModalOpen = toggleLayer('login');
  const setSignUpModalOpen = toggleLayer('signup');
  const setFindPwModalOpen = toggleLayer('password');

  // 내 이야기의 새 답글(로그인했을 때만). 모바일 메뉴 단추에 점으로 알린다.

  const { unread: replyUnread } = useMyReplies();
  const messageUnread = useMessageUnread();
  const newCount = replyUnread + messageUnread;

  // 로그인이 만료돼 API가 401을 돌려주면(api/client.js가 auth-logout을 보낸다) 말없이 로그아웃되지 않게 알리고 다시 로그인할 길을 준다.
  const [sessionExpired, setSessionExpired] = React.useState(false);
  React.useEffect(() => {
    const onExpired = () => setSessionExpired(true);
    window.addEventListener('auth-logout', onExpired);
    return () => window.removeEventListener('auth-logout', onExpired);
  }, []);

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
        <Toolbar sx={{ minHeight: '56px !important', px: 2, pl: 'max(16px, env(safe-area-inset-left))', pr: 'max(16px, env(safe-area-inset-right))' }}>
          <IconButton
            color="inherit"
            aria-label={newCount > 0 ? `메뉴 열기 (새 답글·쪽지 ${newCount}개)` : '메뉴 열기'}
            edge="start"
            onClick={handleDrawerToggle}
            sx={{ mr: 1.5 }}
          >
            <Badge color="error" variant="dot" invisible={!newCount}><MenuIcon /></Badge>
          </IconButton>
          <Box component={Link} to="/" state={{browseView:'calendar'}} aria-label="TOADY 홈으로" sx={{ display: 'flex', alignItems: 'center', gap: 1,color:'inherit',textDecoration:'none' }}>
            <BrandLogo size={26} />
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
          minWidth: 0,
          maxWidth:'100%',
          p: { xs: 1.5, sm: 2.5 },
          // 아이폰 가로 모드의 노치 영역(viewport-fit=cover)을 피한다.
          pl: { xs: 'max(12px, env(safe-area-inset-left))', sm: 'max(20px, env(safe-area-inset-left))' },
          pr: { xs: 'max(12px, env(safe-area-inset-right))', sm: 'max(20px, env(safe-area-inset-right))' },
          width: { xs:'100%',md: `calc(100% - ${DRAWER_WIDTH}px)` },
          mt: { xs: '56px', md: 0 }, // 모바일 상단 바 높이만큼 여백 확보
          display: 'flex',
          flexDirection: 'column',
          minHeight: '100vh'
        }}
      >
        <Box sx={{ flexGrow: 1,minWidth:0,maxWidth:'100%' }}>
          <OperationNotices />
          <Outlet />
        </Box>
        <Box component="footer" sx={{ py: 3, mt: 4, mb: { xs: 'calc(64px + env(safe-area-inset-bottom))', md: 0 }, borderTop: '1px solid #f1f5f9' }}>
          <Copyright />
        </Box>
      </Box>
      <MobileTabBar onOpenMenu={() => setMobileOpen(true)} menuOpen={mobileOpen} />

      {/* 프로필 카드·신고 창(히스토리 층). 이야기·쪽지 어디서든 연다. */}
      <CommunityLayers />

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
          aria-label="로그인 닫기"
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
            setSignUpModalOpen(true);
          }}
          handleFindPwModalOpen={() => {
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
          aria-label="회원가입 닫기"
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
          aria-label="비밀번호 찾기 닫기"
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
          open={findPwModalOpen}
          handleCloseModal={() => setFindPwModalOpen(false)}
          handleOpenLogin={() => {
            setLoginModalOpen(true);
          }}
        />
      </Dialog>
      <Snackbar open={sessionExpired} autoHideDuration={8000} onClose={(_, reason) => { if (reason !== 'clickaway') setSessionExpired(false); }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} sx={{ bottom: { xs: 'calc(76px + env(safe-area-inset-bottom))', md: 24 } }}
        message="로그인이 만료됐어요. 다시 로그인해 주세요."
        action={<Button color="inherit" size="small" sx={{ fontWeight: 800 }} onClick={() => { setSessionExpired(false); setLoginModalOpen(true); }}>다시 로그인</Button>} />
    </Box>
  );
}

export default MainLayout;
