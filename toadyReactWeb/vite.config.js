import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      api: path.resolve(__dirname, './src/api'),
      components: path.resolve(__dirname, './src/components'),
      features: path.resolve(__dirname, './src/features'),
      routes: path.resolve(__dirname, './src/routes'),
      utils: path.resolve(__dirname, './src/utils'),
      config: path.resolve(__dirname, './src/config'),
      // 레거시 호환 경로
      _actions: path.resolve(__dirname, './src/features/legacy-compat'),
      _constants: path.resolve(__dirname, './src/features/legacy-compat'),
      _reducers: path.resolve(__dirname, './src/features/legacy-compat'),
      _services: path.resolve(__dirname, './src/api'),
      _helpers: path.resolve(__dirname, './src/utils'),
      _utils: path.resolve(__dirname, './src/utils'),
      views: path.resolve(__dirname, './src/features')
    }
  },
  server: {
    host: '0.0.0.0', // 외부 접속 및 공유기 DDNS 접속 허용
    port: 3000,
    open: true,
    cors: true,
    allowedHosts: [
      'hyunyoul2.iptime.org',
      '.iptime.org',
      'localhost',
      '127.0.0.1'
    ],
    proxy: {
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      '/webApi': {
        target: 'http://localhost:8081',
        changeOrigin: true
      },
      // 운영 nginx와 같은 공개 경로를 개발 서버에서도 API로 연결한다.
      '^/areas(/[a-z0-9-]+)?/?(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/areas\/?([a-z0-9-]*)\/?/, (_, slug) => `/api/v1/public/areas${slug ? `/${slug}` : ''}`)
      },
      '^/(zupzup|weekly)/?(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/(zupzup|weekly)\/?/, '/api/v1/public/$1')
      },
      '^/help/faq/?(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/help\/faq\/?/, '/api/v1/public/faq')
      },
      '^/calendar/areas/[a-z0-9-]+\\.ics(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/calendar\/areas\/([a-z0-9-]+)\.ics/, '/api/v1/public/areas/$1/calendar.ics')
      },
      '^/calendar/[A-Za-z0-9_-]{43}\\.ics(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/calendar\//, '/api/v1/public/calendar/')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  }
});
