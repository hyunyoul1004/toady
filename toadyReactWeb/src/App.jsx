/**
 * @file App.jsx
 * @description 애플리케이션 루트 컴포넌트. main.jsx가 이 컴포넌트 하나를 화면에 그린다.
 * - React Query Provider: 서버 데이터를 캐시하는 queryClient를 앱 전체에 나눠 준다.
 * - MUI 전역 테마 및 CssBaseline(브라우저 기본 여백·글꼴 초기화) 적용
 * - BrowserRouter: 주소창 경로에 맞는 화면을 고르는 라우터. 실제 경로 목록은 AppRoutes에 있다.
 * - PageViewTracker·PushBootstrap은 화면을 그리지 않고 라우터 안에서 방문 기록·알림 구독만 챙긴다.
 */
import React from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import AppRoutes from '@/routes/AppRoutes';
import { bindAuthQueryCache } from '@/features/auth/store/bindAuthQueryCache';
import PageViewTracker from '@/features/analytics/PageViewTracker';
import PushBootstrap from '@/features/push/PushBootstrap';

// 앱 전체가 함께 쓰는 React Query 캐시. 컴포넌트 밖에서 한 번만 만들어야 렌더마다 캐시가 새로 생기지 않는다.
// staleTime 3분: 받은 데이터를 3분 동안은 '신선'하다고 보고, 같은 queryKey로 다시 써도 서버에 다시 묻지 않는다.
// refetchOnWindowFocus false: 탭을 다시 눌렀다고 매번 다시 불러오지 않는다. retry 1: 실패하면 한 번만 다시 시도한다.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 1000 * 60 * 3, // 3분
    },
  },
});

// 로그인 사용자가 바뀌면 이전 사용자의 캐시를 비우도록 인증 스토어와 queryClient를 묶는다.
// 개발 중 파일을 고쳐 모듈이 다시 실행될 때(HMR) 연결이 두 번 생기지 않게 이전 연결을 푼다.
const unbindAuthCache = bindAuthQueryCache(queryClient);
if (import.meta.hot) import.meta.hot.dispose(unbindAuthCache);

const theme = createTheme({
  palette: {
    primary: {
      main: '#1976d2',
    },
    secondary: {
      main: '#dc004e',
    },
    background: {
      default: '#fafafa',
    },
  },
  typography: {
    fontFamily: [
      '-apple-system',
      'BlinkMacSystemFont',
      '"Segoe UI"',
      'Roboto',
      '"Helvetica Neue"',
      'Arial',
      'sans-serif',
    ].join(','),
  },
});

export const App = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <BrowserRouter>
          <PageViewTracker />
          <PushBootstrap />
          <AppRoutes />
        </BrowserRouter>
      </ThemeProvider>
      {/* 개발 서버에서만 보이는 React Query 캐시 확인 도구. 운영 빌드에는 들어가지 않는다. */}
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
};

export default App;
