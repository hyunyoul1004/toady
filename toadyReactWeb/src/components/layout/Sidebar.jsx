/**
 * @file Sidebar.jsx
 * @description 좌측 사이드바 내비게이션 및 반응형 드로어 컴포넌트
 * - 데스크톱: 좌측 고정 사이드바 (폭 240px)
 * - 모바일/태블릿: 햄버거 메뉴 버튼 클릭 시 좌측에서 슬라이드되는 임시 Drawer
 * - 상단 로고, 주요 메뉴 리스트, 더보기 메뉴('홈 화면에 추가' 포함), 하단 로그인/회원가입/로그아웃 인증 영역 포함
 *
 * MainLayout이 그린다. 메뉴 이름·경로는 navItems.js를 모바일 하단 탭과 함께 쓴다.
 * 상태·API: useAuthStore(로그인 정보·로그아웃), 새 답글·안 읽은 쪽지 수(React Query 훅), 알림 점(usePushAttention),
 * 설치 방식(installStore), 측정(trackAction).
 *
 * 모바일 메뉴는 MainLayout의 'layout' 히스토리 레이어(값 'menu')로 열려 있다. 그래서 메뉴 안에서 다른 화면으로 갈 때는
 * 메뉴 칸을 replace로 바꾸거나 먼저 navigate(-1)로 메뉴를 닫는다. 그러지 않으면 뒤로가기 때 메뉴가 다시 열린다.
 * 메뉴 항목은 NavLink 대신 ListItemButton + navigate를 쓴다. '같은 탭 다시 누르기' 규칙(retapAction)을 적용하기 위해서다.
 */
import React, { useRef } from 'react';
import { usePushAttention } from '@/features/push/usePushAttention';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { promptInstall, useInstallMode } from '@/features/install/installStore';
import { showInstallEntry } from '@/features/install/installSupport';
import InstallGuideDialog from '@/features/install/InstallGuideDialog';
import { trackAction } from '@/features/analytics/trackAction';
import AddToHomeScreenRoundedIcon from '@mui/icons-material/AddToHomeScreenRounded';
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
import MailOutlineRoundedIcon from '@mui/icons-material/MailOutlineRounded';
import { useMessageUnread } from '@/features/community/useCommunity';
import ProfileAvatar from '@/features/account/ProfileAvatar';
import { useMyProfileIcon, useMyReplies } from '@/features/account/useMyAccount';
import CloseIcon from '@mui/icons-material/Close';
import IconButton from '@mui/material/IconButton';

import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { MAIN_NAV, MORE_NAV, isNavActive, retapAction } from './navItems';
import BrandLogo from './BrandLogo';

const MAIN_ICONS = { calendar: <CalendarMonthIcon />, match: <FactCheckOutlinedIcon />, score: <CalculateOutlinedIcon />, my: <BookmarkBorderIcon /> };
const MORE_ICONS = { weekly: <EventNoteOutlinedIcon />, areas: <PlaceOutlinedIcon />, faq: <HelpOutlineIcon />, push: <NotificationsNoneOutlinedIcon /> };

export const DRAWER_WIDTH = 240;

export function Sidebar({ mobileOpen, onMobileClose, onOpenLogin, onOpenSignUp }) {
  const location = useLocation();
  const pushDot = usePushAttention();
  const navigate = useNavigate();
  const installMode = useInstallMode();
  // 값: 안내 종류('guide' 또는 설치를 마친 뒤 'done'). 히스토리 한 단계로 열어 뒤로가기 한 번에 닫힌다.
  const [installGuide, setInstallGuide] = useHistoryOverlay('install-guide');
  // 모바일 메뉴(히스토리 한 단계)를 먼저 닫고 안내를 연다. 메뉴 위에 겹치지 않고, 뒤로가기 한 번에 안내만 닫힌다.
  // popstate: 뒤로가기(navigate(-1))가 실제로 끝났을 때 브라우저가 보내는 이벤트. once: 한 번만 듣고 저절로 떨어진다.
  // setTimeout 0으로 한 박자 늦춰 React Router가 바뀐 location을 먼저 반영하게 한다.
  const openInstallGuide = (kind) => {
    if (!mobileOpen) { setInstallGuide(kind); return; }
    window.addEventListener('popstate', () => setTimeout(() => setInstallGuide(kind), 0), { once: true });
    navigate(-1);
  };
  // 설치 창을 띄울 수 있으면 누른 순간 바로 띄우고(사용자 동작 안), 설치하면 아이콘이 어디에 생기는지 알려 준다.
  // 띄울 수 없으면 기기·브라우저별 추가 방법을 보여 준다.
  const addToHome = () => {
    if (installMode === 'prompt') {
      promptInstall().then(outcome => { if (outcome === 'accepted') openInstallGuide('done'); });
      return;
    }
    trackAction('app_install', 'tool/install-guide');
    openInstallGuide('guide');
  };
  // 안내 창 안의 '홈 화면에 설치' 버튼(설치 창을 띄울 수 있게 된 뒤). 누른 순간 띄워야 브라우저가 막지 않는다.
  const installFromGuide = () => {
    promptInstall().then(outcome => { if (outcome === 'accepted') setInstallGuide('done'); });
  };

  // zustand selector: 필요한 값만 하나씩 꺼낸다. 그 값이 바뀔 때만 사이드바가 다시 그려진다.
  const user = useAuthStore(state => state.user);
  const isAuthenticated = useAuthStore(state => state.isAuthenticated);
  const logout = useAuthStore(state => state.logout);

  const nickname = user?.userNicName || user?.sub || '회원';
  const myProfileIcon = useMyProfileIcon();
  const { unread: replyUnread } = useMyReplies();
  const messageUnread = useMessageUnread();

  const menuItems = MAIN_NAV.map(item => ({ ...item, text: item.label, icon: MAIN_ICONS[item.key] }));
  /**
   * 하단 탭과 같은 '다시 누르기' 규칙. 모바일 메뉴(드로어)는 히스토리 한 단계(layout 레이어)로 열려 있으므로
   * 판정에서는 그 레이어를 빼고, 같은 화면이면 먼저 메뉴를 닫은 뒤(뒤로 한 단계) 상세 닫기·처음 화면·맨 위로를 적용한다.
   * 다른 화면이면 메뉴가 열린 칸을 replace로 바꿔 새 화면으로 간다(메뉴 칸이 히스토리에 남지 않게).
   */
  // going: 메뉴를 닫는 중인지. 화면에 보일 값이 아니라서 state 대신 useRef로 둔다(바꿔도 다시 그리지 않는다).
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
  /** 더보기 항목. overlay가 있는 항목(알림 설정)은 그 화면으로 간 뒤 팝업 레이어를 열린 채로 쌓는다. */
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
          <BrandLogo size={30} />
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
            // 지역 주소(/regions/경기도/파주시)도 '지역별 청약'으로 표시한다.
            const selected = !item.overlay && (location.pathname === item.path || (item.path === '/regions' && location.pathname.startsWith('/regions/')));
            return <ListItem key={item.key} disablePadding sx={{ mb: 0.25 }}>
              <ListItemButton aria-current={selected ? 'page' : undefined} selected={selected} onClick={() => openMore(item)}
                sx={{ borderRadius: 1.5, py: 0.75, px: 1.5, bgcolor: selected ? '#eff6ff' : 'transparent', color: selected ? '#2563eb' : '#475569', '&:hover': { bgcolor: selected ? '#eff6ff' : '#f8fafc' } }}>
                <ListItemIcon sx={{ minWidth: 36, color: selected ? '#2563eb' : '#64748b' }}>{MORE_ICONS[item.key]}</ListItemIcon>
                <ListItemText primary={item.label} primaryTypographyProps={{ fontWeight: selected ? 800 : 600, fontSize: '0.86rem' }} />
                {item.key === 'push' && pushDot && <Box component="span" sx={{ fontSize: '0.7rem', fontWeight: 800, color: '#dc2626', bgcolor: '#fef2f2', borderRadius: 999, px: 0.9, py: 0.2 }}>꺼짐</Box>}
              </ListItemButton>
            </ListItem>;
          })}
          {showInstallEntry(installMode) && <ListItem disablePadding sx={{ mb: 0.25 }}>
            <ListItemButton onClick={addToHome} sx={{ borderRadius: 1.5, py: 0.75, px: 1.5, color: '#475569', '&:hover': { bgcolor: '#f8fafc' } }}>
              <ListItemIcon sx={{ minWidth: 36, color: '#64748b' }}><AddToHomeScreenRoundedIcon /></ListItemIcon>
              <ListItemText primary="홈 화면에 추가" secondary="앱처럼 바로 열기" primaryTypographyProps={{ fontWeight: 600, fontSize: '0.86rem' }} secondaryTypographyProps={{ fontSize: '0.72rem' }} />
            </ListItemButton>
          </ListItem>}
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
            <Box sx={{ px: 0.5, display: 'flex', alignItems: 'center', gap: 1.2 }}>
              <ProfileAvatar icon={myProfileIcon} name={nickname} size={36} decorative />
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="caption" sx={{ color: '#64748b' }}>
                  접속 중인 계정
                </Typography>
                <Typography sx={{ fontWeight: 800, fontSize: '0.95rem', color: '#1e293b', overflowWrap: 'anywhere' }}>
                  {nickname}님
                </Typography>
              </Box>
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
              {replyUnread > 0 && <Box component="span" sx={{ ml: 0.75, px: 0.75, minWidth: 20, borderRadius: 999, bgcolor: '#ef4444', color: '#fff', fontSize: 11, fontWeight: 800, lineHeight: '18px' }} aria-label={`새 답글 ${replyUnread}개`}>{replyUnread > 99 ? '99+' : replyUnread}</Box>}
            </Button>
            <Button
              fullWidth
              variant={location.pathname === '/messages' ? 'contained' : 'outlined'}
              disableElevation
              size="small"
              startIcon={<MailOutlineRoundedIcon />}
              onClick={() => go('/messages')}
              aria-current={location.pathname === '/messages' ? 'page' : undefined}
              sx={{ fontWeight: 700, borderRadius: 1.5 }}
            >
              쪽지함
              {messageUnread > 0 && <Box component="span" sx={{ ml: 0.75, px: 0.75, minWidth: 20, borderRadius: 999, bgcolor: '#ef4444', color: '#fff', fontSize: 11, fontWeight: 800, lineHeight: '18px' }} aria-label={`안 읽은 쪽지 ${messageUnread}개`}>{messageUnread > 99 ? '99+' : messageUnread}</Box>}
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
              title="이 계정으로 로그인한 모든 기기에서 로그아웃해요"
              sx={{ fontWeight: 700, borderRadius: 1.5, mt: 0.5, whiteSpace: 'nowrap' }}
            >
              {/* 서버가 토큰 버전을 올려 다른 기기의 로그인도 함께 끊는다(useAuthStore.logout). */}
              모든 기기에서 로그아웃
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
        // keepMounted: 닫혀 있어도 내용을 DOM에 남겨 둔다(모바일에서 열 때 더 빨리 뜬다. MUI 권장 설정).
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
      {/* 홈 화면 추가 안내 창. 사이드바가 두 벌(모바일·PC)이라 창은 Drawer 밖에 한 번만 둔다. */}
      <InstallGuideDialog open={Boolean(installGuide)} onClose={() => setInstallGuide(false)} mode={installGuide === 'done' ? 'done' : installMode} onInstall={installFromGuide} />
    </Box>
  );
}

export default Sidebar;

