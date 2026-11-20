/**
 * @file MainLayout.jsx
 * @description 애플리케이션 공통 레이아웃 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바
 * - 모바일: 상단 앱바 및 슬라이드 내비게이션 드로어
 * - 모바일: 하단 탭(MobileTabBar)
 * - 계정 관련 모달(로그인, 회원가입, 비밀번호 찾기) 관리
 * - 로그인 만료 안내(Snackbar), 사이트·운영 공지, 프로필 카드·신고 창(CommunityLayers)
 *
 * 라우터(routes/AppRoutes.jsx)에서 대부분 화면의 부모 레이아웃으로 쓴다. 각 화면은 <Outlet /> 자리에 그려진다.
 *
 * 팝업과 히스토리: 모바일 메뉴·로그인·회원가입·비밀번호 찾기는 모두 useHistoryOverlay('layout') 레이어 하나의 값
 * ('menu' | 'login' | 'signup' | 'password')이다. 열면 히스토리 한 칸이 쌓이고(location.state.overlayLayers),
 * 브라우저 뒤로가기를 누르면 그 칸이 빠지면서 창이 닫힌다. 같은 레이어라 로그인 → 회원가입처럼 바꾸면 칸을 더 쌓지 않고 값만 바꾼다.
 */
import React from 'react';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useScrollTopOnNavigate } from '@/hooks/useScrollTopOnNavigate';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { replaceHomeLink } from './navItems';
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
import SiteNotices from '@/features/account/SiteNotices';
import Dialog from '@mui/material/Dialog';
import CloseIcon from '@mui/icons-material/Close';
import Snackbar from '@mui/material/Snackbar';
import { GuestImportPrompt, SignupPromptHost } from '@/features/account/SignupPrompts';
import Button from '@mui/material/Button';

import Sidebar, { DRAWER_WIDTH } from './Sidebar';
import Copyright from './Copyright';
import MobileTabBar from './MobileTabBar';
import BrandLogo from './BrandLogo';
import Skeleton from '@mui/material/Skeleton';
import SectionErrorBoundary from '@/components/error/SectionErrorBoundary';

// 로그인·회원가입·비밀번호 찾기 창은 열 때 내려받는다(대부분의 방문은 로그인 없이 둘러보므로 첫 화면 JS에서 뺀다).
// React.lazy: 처음 그릴 때 import()로 파일을 받는다. 받는 동안은 바깥 Suspense의 fallback이 보인다.
// .then(...)은 이름 있는 내보내기(export function LoginPage)를 lazy가 원하는 { default } 모양으로 바꾸는 것이다.
const LoginPage = React.lazy(() => import('@/views/login/LoginPage').then(module => ({ default: module.LoginPage })));
const SignUp = React.lazy(() => import('@/views/signup/SignUp').then(module => ({ default: module.SignUp })));
const FindPassword = React.lazy(() => import('@/views/signup/FindPassword').then(module => ({ default: module.FindPassword })));

/** 계정 창 내용을 내려받는 동안: 제목·입력칸·버튼 자리를 잡아 창 크기가 덜컥 바뀌지 않게 한다. */
function AccountDialogSkeleton() {
  return (
    <Box role="status" aria-label="불러오는 중" aria-busy="true" sx={{ pt: 1 }}>
      <Skeleton variant="text" width="45%" height={36} sx={{ mb: 2 }} />
      <Skeleton variant="rounded" height={48} sx={{ mb: 1.5, borderRadius: 2 }} />
      <Skeleton variant="rounded" height={48} sx={{ mb: 2.5, borderRadius: 2 }} />
      <Skeleton variant="rounded" height={44} sx={{ borderRadius: 2 }} />
    </Box>
  );
}
/** 계정 창 내용: 파일을 받지 못하거나 오류가 나도 창 안에서만 안내한다. */
function AccountDialogBody({ children }) {
  return <SectionErrorBoundary compact title="창을 열지 못했어요"><React.Suspense fallback={<AccountDialogSkeleton />}>{children}</React.Suspense></SectionErrorBoundary>;
}

export function MainLayout() {
  const [overlay, setOverlay] = useHistoryOverlay('layout');
  // 다른 화면으로 새로 이동하면 맨 위로(뒤로가기는 읽던 위치 유지).
  useScrollTopOnNavigate();
  const mobileOpen = overlay === 'menu';
  const atHome = replaceHomeLink(useLocation());
  const loginModalOpen = overlay === 'login';
  const signUpModalOpen = overlay === 'signup';
  const findPwModalOpen = overlay === 'password';
  // 닫기는 최신 레이어 값이 자기 자신일 때만 적용한다(비동기 로그인 뒤 늦게 온 닫기가 다른 창을 닫지 않게).
  // toggleLayer('login')(true)는 로그인 창 열기, (false)는 닫기. 각 창의 setXxxOpen을 이걸로 만든다.
  const toggleLayer = name => open => setOverlay(current => open ? name : current === name ? null : current);
  const setMobileOpen = toggleLayer('menu');
  const setLoginModalOpen = toggleLayer('login');
  const setSignUpModalOpen = toggleLayer('signup');
  const setFindPwModalOpen = toggleLayer('password');

  // 내 이야기의 새 답글과 안 읽은 쪽지(로그인했을 때만, React Query 조회). 모바일 메뉴 단추에 점으로 알린다.

  const { unread: replyUnread } = useMyReplies();
  const messageUnread = useMessageUnread();
  const newCount = replyUnread + messageUnread;

  // 로그인이 만료돼 API가 401을 돌려주면(api/client.js가 auth-logout을 보낸다) 말없이 로그아웃되지 않게 알리고 다시 로그인할 길을 준다.
  const [sessionExpired, setSessionExpired] = React.useState(false);
  // 빈 의존성 배열([]): 처음 그릴 때 한 번만 리스너를 붙이고, 레이아웃이 사라질 때 return의 정리 함수로 뗀다.
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
          {/* Sidebar 로고와 같은 규칙: 이미 첫 화면(쿼리 없는 '/')이면 바꿔 써서 누를 때마다 히스토리가 쌓이지 않게 한다. */}
          <Box component={Link} to="/" replace={atHome} state={{browseView:'calendar'}} aria-label="TOADY 홈으로" sx={{ display: 'flex', alignItems: 'center', gap: 1,color:'inherit',textDecoration:'none' }}>
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
          <SiteNotices />
          <OperationNotices />
          <Outlet />
        </Box>
        <Box component="footer" sx={{ py: 3, mt: 4, mb: { xs: 'calc(64px + env(safe-area-inset-bottom))', md: 0 }, borderTop: '1px solid #f1f5f9' }}>
          <Copyright />
        </Box>
      </Box>
      {/* 모바일 하단 탭('더보기'는 위 메뉴 드로어를 연다). PC 폭에서는 CSS로 숨는다. */}
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
            color: '#64748b',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        {/* 창 안쪽만 오류 경계 + Suspense로 감싼다. 파일을 받는 동안 뼈대를, 실패하면 창 안에 안내를 보여 준다. */}
        <AccountDialogBody>
          <LoginPage
            handleCloseModal={() => setLoginModalOpen(false)}
            handleSignUpModalOpen={() => {
              setSignUpModalOpen(true);
            }}
            handleFindPwModalOpen={() => {
              setFindPwModalOpen(true);
            }}
          />
        </AccountDialogBody>
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
            color: '#64748b',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        <AccountDialogBody>
          <SignUp
            handleCloseModal={() => setSignUpModalOpen(false)}
            handleOpenLogin={() => {
              setLoginModalOpen(true);
            }}
          />
        </AccountDialogBody>
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
            color: '#64748b',
            bgcolor: '#f8fafc',
            border: '1px solid #e2e8f0',
            transition: 'all 0.15s ease',
            '&:hover': { color: '#0f172a', bgcolor: '#f1f5f9', transform: 'rotate(90deg)' }
          }}
        >
          <CloseIcon fontSize="small" />
        </IconButton>
        <AccountDialogBody>
          <FindPassword
            open={findPwModalOpen}
            handleCloseModal={() => setFindPwModalOpen(false)}
            handleOpenLogin={() => {
              setLoginModalOpen(true);
            }}
          />
        </AccountDialogBody>
      </Dialog>
      {/* 가입 권유: 관심 공고 3개째 저장 카드(비회원), 로그인 뒤 기기 관심 공고 옮기기 확인(회원). features/account/SignupPrompts.jsx */}
      <SignupPromptHost openSignup={() => setSignUpModalOpen(true)} />
      <GuestImportPrompt />
      {/* 로그인 만료 안내. 바깥을 눌러 닫히지 않게(clickaway 무시) 하고, 모바일은 하단 탭 위로 띄운다. */}
      <Snackbar open={sessionExpired} autoHideDuration={8000} onClose={(_, reason) => { if (reason !== 'clickaway') setSessionExpired(false); }}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} sx={{ bottom: { xs: 'calc(76px + env(safe-area-inset-bottom))', md: 24 } }}
        message="로그인이 만료됐어요. 다시 로그인해 주세요."
        action={<Button color="inherit" size="small" sx={{ fontWeight: 800 }} onClick={() => { setSessionExpired(false); setLoginModalOpen(true); }}>다시 로그인</Button>} />
    </Box>
  );
}

export default MainLayout;
