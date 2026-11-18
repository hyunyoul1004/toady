/**
 * @file AppRoutes.jsx
 * @description 애플리케이션 라우팅 정의 컴포넌트. App.jsx의 BrowserRouter 안에서 그린다.
 * 주소(경로)마다 어떤 화면을 보여 줄지 정한다. 대부분의 화면은 MainLayout(메뉴·하단 탭) 안의 <Outlet> 자리에 그려진다.
 * :type 처럼 콜론으로 시작하는 부분은 경로 변수다. 화면에서 useParams()로 꺼내 쓴다.
 */
import React, { Suspense, lazy, useEffect, useRef } from 'react';
import { Routes, Route, Navigate, useNavigate, useLocation, useParams } from 'react-router-dom';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import MainLayout from '@/components/layout/MainLayout';
import { Main } from '@/views/main/Main';
import { safeReturnPath } from '@/features/auth/signupReturn';
import { openLoginOver } from '@/features/push/pushSupport';

// 첫 화면(청약 캘린더)만 바로 싣고, 나머지 화면은 들어갈 때 내려받는다(첫 접속 JS 크기를 줄인다).
// lazy(() => import(...))는 화면 코드를 별도 파일로 나눈다. 파일을 받는 동안은 가장 가까운 <Suspense>의 fallback이 보인다.
// lazy는 default export만 받으므로, 이름 있는 export(SignUpCertify)는 then으로 { default } 모양을 만들어 준다.
const MyPlanner = lazy(() => import('@/features/planner/MyPlanner'));
const AccountPage = lazy(() => import('@/features/account/AccountPage'));
const MessagesPage = lazy(() => import('@/features/community/MessagesPage'));
const ScoreCalculator = lazy(() => import('@/features/score/ScoreCalculator'));
const MatchPage = lazy(() => import('@/features/match/MatchPage'));
const FaqPage = lazy(() => import('@/features/faq/FaqPage'));
const ThisWeekPage = lazy(() => import('@/features/explore/ThisWeekPage'));
const RegionsPage = lazy(() => import('@/features/explore/RegionsPage'));
const LegalPage = lazy(() => import('@/features/legal/LegalPage'));
// 운영자용 방문자 ID 확인 화면. 메뉴에 없고 주소로만 들어온다(색인·조회 기록 제외).
const MyVisitorIdPage = lazy(() => import('@/features/analytics/MyVisitorIdPage'));
const SignUpCertify = lazy(() => import('@/views/signup/SignUpCertify').then(module => ({ default: module.SignUpCertify })));

/** 화면 파일을 내려받는 동안: 레이아웃(메뉴·하단 탭)은 그대로 두고 본문 자리에만 작은 표시를 둔다. */
function PageLoading() {
  return <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }} role="status" aria-label="화면을 불러오는 중"><CircularProgress size={28} /></Box>;
}
/**
 * 화면 파일을 받지 못했을 때(배포 직후 이전 파일 이름, 끊긴 모바일 네트워크) 빈 화면 대신 다시 시도할 길을 준다.
 * 메뉴·하단 탭은 레이아웃이라 그대로 남는다.
 */
// 에러 경계(Error Boundary)는 아직 클래스 컴포넌트로만 만들 수 있다.
// 자식이 그리다가 오류를 던지면 getDerivedStateFromError가 failed를 켜고, render가 대신 안내 화면을 그린다.
// lazy 파일 내려받기 실패도 여기서 잡힌다.
class PageErrorBoundary extends React.Component {
  constructor(props) { super(props); this.state = { failed: false }; }
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch(error, info) { console.error('[PageErrorBoundary]', error, info?.componentStack); }
  // 주소가 바뀌면(다른 메뉴·공고로 이동) 실패 표시를 지우고 다시 그린다.
  componentDidUpdate(previousProps) { if (this.state.failed && previousProps.resetKey !== this.props.resetKey) this.setState({ failed: false }); }
  render() {
    if (!this.state.failed) return this.props.children;
    return <Box sx={{ textAlign: 'center', py: 8, px: 2 }} role="alert">
      <Typography sx={{ fontWeight: 800, mb: 1 }}>화면을 불러오지 못했어요</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>새 버전이 배포됐거나 연결이 잠시 끊겼을 수 있어요.</Typography>
      <Button variant="contained" disableElevation onClick={() => window.location.reload()}>새로고침</Button>
    </Box>;
  }
}
// 라우터가 화면이 바뀌어도 같은 경계 인스턴스를 다시 쓰므로, 주소마다 새 경계를 둬 한 화면의 실패가 다른 메뉴로 번지지 않게 한다.
function Page({ children }) {
  const { pathname } = useLocation();
  return <PageErrorBoundary key={pathname}><Suspense fallback={<PageLoading />}>{children}</Suspense></PageErrorBoundary>;
}
const page = element => <Page>{element}</Page>;
/**
 * 홈(청약 캘린더)과 공고 상세는 같은 Main을 계속 쓴다(상세를 열고 닫아도 달력·검색 상태가 그대로 남게).
 * 그래서 주소마다 key를 바꾸지 않고 같은 경계를 유지하되, 주소가 바뀌면 실패 표시만 지운다.
 */
function MainPage() {
  const { pathname } = useLocation();
  return <PageErrorBoundary resetKey={pathname}><Main /></PageErrorBoundary>;
}

/**
 * 예전 단독 /login 주소: 메인 화면 위에 로그인 팝업을 히스토리 층으로 연다.
 * 먼저 /login 기록을 /로 바꾼 뒤 팝업 층을 쌓아, 닫기·뒤로가기가 메인 화면에 머물게 한다.
 */
function LoginRedirect() {
  const navigate = useNavigate();
  const { search } = useLocation();
  const done = useRef(false);
  useEffect(() => {
    if (done.current) return;
    done.current = true;
    // /login?next=/my 처럼 돌아갈 앱 안 주소가 있으면 그 화면 위에 로그인 창을 연다(외부 주소는 받지 않는다).
    // done(useRef): 개발 모드 StrictMode 등으로 effect가 두 번 돌아도 팝업을 한 번만 열게 막는다.
    const next = safeReturnPath(new URLSearchParams(search).get('next')) || '/';
    openLoginOver(navigate, next);
  }, [navigate, search]);
  return null;
}

/**
 * 검색·공유용 공개 주소(/areas, /weekly, /zupzup, /help/faq)로 들어온 사람은 같은 내용의 앱 화면으로 옮긴다.
 * 봇은 웹 nginx가 서버 HTML을 주므로 이 화면까지 오지 않는다. 유입 꼬리표(src 등) 쿼리는 그대로 넘긴다.
 */
// <Navigate replace>는 지금 히스토리 칸을 새 주소로 바꾼다. 뒤로가기를 눌러도 옛 주소로 돌아와 다시 튕기지 않는다.
function PublicPathRedirect({ to }) {
  const { search, hash, state } = useLocation();
  return <Navigate to={to + search + hash} replace state={state} />;
}
/** /areas/{생활권} → 그 생활권으로 거른 청약 캘린더(/?area=). 생활권 이름 확인은 캘린더가 한다. */
function AreaRedirect() {
  const { slug } = useParams();
  const { search, hash, state } = useLocation();
  const next = new URLSearchParams(search);
  if (/^[a-z0-9-]{1,40}$/.test(slug || '')) next.set('area', slug);
  return <Navigate to={`/?${next}${hash}`} replace state={state} />;
}

export const AppRoutes = () => {
  return (
    <Routes>
      {/* 기본 레이아웃 하위 페이지 */}
      <Route path="/" element={<MainLayout />}>
        {/* 홈·옛 상세 주소·공고 상세 주소가 모두 같은 Main을 쓴다. Main이 주소를 보고 상세 창을 열지 정한다. */}
        <Route index element={<MainPage />} />
        <Route path="showSubscriptDetail" element={<MainPage />} />
        <Route path="announcements/:type/:house/:notice" element={<MainPage />} />
        <Route path="my" element={page(<MyPlanner />)} />
        <Route path="account" element={page(<AccountPage />)} />
        <Route path="messages" element={page(<MessagesPage />)} />
        <Route path="score" element={page(<ScoreCalculator />)} />
        {/* 같은 MatchPage를 두 주소에서 쓰므로 key를 달리 줘서 탭을 옮길 때 상태를 새로 시작하게 한다. */}
        <Route path="match" element={page(<MatchPage key="match" tab="match" />)} />
        <Route path="match/zupzup" element={page(<MatchPage key="zupzup" tab="zupzup" />)} />
        <Route path="faq" element={page(<FaqPage />)} />
        <Route path="this-week" element={page(<ThisWeekPage />)} />
        <Route path="regions" element={page(<RegionsPage />)} />
        {/* 지역별 청약 공개 주소: 사람에게도 같은 주소에서 그 지역으로 거른 청약 캘린더를 보여 준다(봇은 nginx가 서버 HTML). */}
        <Route path="regions/:sido" element={<MainPage />} />
        <Route path="regions/:sido/:district" element={<MainPage />} />
        {/* 이전 주소(생활권별 청약)는 지역별 청약으로 옮긴다. */}
        <Route path="living-areas" element={<Navigate to="/regions" replace />} />
        <Route path="areas" element={<PublicPathRedirect to="/regions" />} />
        <Route path="areas/:slug" element={<AreaRedirect />} />
        <Route path="weekly" element={<PublicPathRedirect to="/this-week" />} />
        <Route path="zupzup" element={<PublicPathRedirect to="/match/zupzup" />} />
        <Route path="help/faq" element={<PublicPathRedirect to="/faq" />} />
        <Route path="privacy" element={page(<LegalPage kind="privacy" />)} />
        <Route path="terms" element={page(<LegalPage kind="terms" />)} />
        <Route path="certify" element={page(<SignUpCertify />)} />
        <Route path="my-visitor-id" element={page(<MyVisitorIdPage />)} />
      </Route>

      {/* 로그인은 메인 위 팝업으로 연다(회원가입·비밀번호 찾기·로그인 후 처리가 팝업에 연결되어 있음) */}
      <Route path="/login" element={<LoginRedirect />} />

      {/* 정의되지 않은 경로는 메인으로 리다이렉트 */}
      <Route path="*" element={<PublicPathRedirect to="/" />} />
    </Routes>
  );
};

export default AppRoutes;
