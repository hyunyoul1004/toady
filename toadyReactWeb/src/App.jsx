/**
 * @file App.jsx
 * @description 애플리케이션 루트 컴포넌트
 * - React Query Provider 설정
 * - MUI 전역 테마 및 CssBaseline 적용
 * - 라우터 Provider 구성
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

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
      staleTime: 1000 * 60 * 3, // 3분
    },
  },
});

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
          <AppRoutes />
        </BrowserRouter>
      </ThemeProvider>
      {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
    </QueryClientProvider>
  );
};

export default App;
