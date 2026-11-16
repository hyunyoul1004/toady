import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// 운영 웹 nginx(nginx.conf의 $toady_bot)와 같은 기준: 검색·공유 봇만 공개 서버 HTML을 받고, 사람은 앱 화면을 받는다.
// 개발 중 서버 HTML을 직접 보려면 /api/v1/public/... 주소를 열면 된다.
const BOT_UA = /(googlebot|google-inspectiontool|adsbot-google|mediapartners-google|storebot-google|bingbot|yeti|daumoa|kakaotalk-scrap|facebookexternalhit|facebot|twitterbot|slackbot|discordbot|telegrambot|linkedinbot|whatsapp|line-poker|applebot|duckduckbot|baiduspider|yandex|petalbot|skypeuripreview|embedly|redditbot|crawler|spider)/i;
const humansGetApp = req => (BOT_UA.test(req.headers['user-agent'] || '') ? undefined : '/index.html');

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // 자주 바뀌지 않는 큰 라이브러리를 따로 묶어, 앱 코드만 바뀐 배포에서는 다시 받지 않게 한다.
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('@fullcalendar')) return 'vendor-calendar';
          if (id.includes('@mui') || id.includes('@emotion')) return 'vendor-mui';
          return 'vendor';
        }
      }
    }
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
      config: path.resolve(__dirname, './src/config')
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
      // 운영 nginx와 같은 공개 경로를 개발 서버에서도 API로 연결한다(봇만. 사람은 앱이 같은 내용의 앱 화면으로 옮긴다).
      '^/areas(/[a-z0-9-]+)?/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/areas\/?([a-z0-9-]*)\/?/, (_, slug) => `/api/v1/public/areas${slug ? `/${slug}` : ''}`)
      },
      '^/(zupzup|weekly)/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/(zupzup|weekly)\/?/, '/api/v1/public/$1')
      },
      '^/help/faq/?(\\?.*)?$': {
        bypass: humansGetApp,
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
});
