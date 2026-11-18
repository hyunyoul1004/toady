import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// 운영 웹 nginx(nginx.conf의 $toady_bot)와 같은 기준: 검색·공유 봇만 공개 서버 HTML을 받고, 사람은 앱 화면을 받는다.
// 개발 중 서버 HTML을 직접 보려면 /api/v1/public/... 주소를 열면 된다.
const BOT_UA = /(googlebot|google-inspectiontool|adsbot-google|mediapartners-google|storebot-google|bingbot|yeti|daumoa|kakaotalk-scrap|facebookexternalhit|facebot|twitterbot|slackbot|discordbot|telegrambot|linkedinbot|whatsapp|line-poker|applebot|duckduckbot|baiduspider|yandex|petalbot|skypeuripreview|embedly|redditbot|crawler|spider)/i;
const isBot = req => BOT_UA.test(req.headers['user-agent'] || '');
const humansGetApp = req => (isBot(req) ? undefined : '/index.html');
const API = 'http://localhost:8081';
// nginx의 $toady_app_path와 같은 목록 + 회원 전용 화면. 봇이 이 목록에도, 아래 공개 경로에도 없는 주소를 요청하면 404.
const APP_ONLY_PATH = /^\/(showSubscriptDetail|privacy|terms|living-areas|my|account|messages|certify|login)\/?(\?.*)?$/;
// nginx의 $toady_seq_path와 같은 규칙: 예전 앱 주소(/?subscriptSeq=apt%2F번호%2F번호)로 온 봇은 공고 서버 HTML을 받는다.
const SEQ = /^(apt|urban|remaining|public-rental|optional)\/([a-zA-Z0-9_-]{1,40})\/([a-zA-Z0-9_-]{1,40})$/;
const botSeqPath = url => {
  const seq = new URLSearchParams((url || '').split('?')[1] || '').get('subscriptSeq') || '';
  return SEQ.test(seq) ? `/api/v1/public/announcements/${seq}` : null;
};

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
          // FullCalendar와 그 내부 렌더러(preact)는 데스크톱 달력을 열 때만 받는 묶음으로 둔다(모바일 첫 화면에서 받지 않음).
          if (id.includes('@fullcalendar') || /node_modules[\\/]preact[\\/]/.test(id)) return 'vendor-calendar';
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
      // 봇 홈: 이번 주 공고·지역 링크가 담긴 서버 HTML.
      '^/(\\?.*)?$': {
        bypass: humansGetApp,
        target: API,
        changeOrigin: true,
        // 봇: 예전 상세 주소(?subscriptSeq=)면 그 공고 HTML, 아니면 홈 HTML(nginx location = /와 같은 규칙).
        rewrite: path => botSeqPath(path) || path.replace(/^\//, '/api/v1/public/home')
      },
      '^/regions/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: API,
        changeOrigin: true,
        rewrite: path => botSeqPath(path) || path.replace(/^\/regions\/?/, '/api/v1/public/regions')
      },
      // 사람은 같은 주소에서 앱의 지역 캘린더(앱 경로 /regions/:sido/:district)를 받는다.
      '^/regions/[^/?]+(/[^/?]+)?/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: API,
        changeOrigin: true,
        rewrite: path => botSeqPath(path) || path.replace(/^\/regions\//, '/api/v1/public/regions/').replace(/\/(\?|$)/, '$1')
      },
      '^/(score|match)/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: API,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/(score|match)\/?/, '/api/v1/public/$1')
      },
      '^/(match/zupzup|this-week)/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: API,
        changeOrigin: true,
        rewrite: path => path.replace(/^\/match\/zupzup\/?/, '/api/v1/public/zupzup').replace(/^\/this-week\/?/, '/api/v1/public/weekly')
      },
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
      '^/(help/)?faq/?(\\?.*)?$': {
        bypass: humansGetApp,
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/(help\/)?faq\/?/, '/api/v1/public/faq')
      },
      '^/announcements/(apt|urban|remaining|public-rental|optional)/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/?(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        bypass: humansGetApp,
        rewrite: path => path.replace(/^\/announcements\//, '/api/v1/public/announcements/')
      },
      '^/(sitemap|announcement-sitemap)\\.xml(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: () => '/api/v1/public/sitemap.xml'
      },
      '^/sitemaps/.+\\.xml(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: path => path.replace(/^\/sitemaps\//, '/api/v1/public/sitemaps/')
      },
      '^/rss\\.xml(\\?.*)?$': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: () => '/api/v1/public/rss.xml'
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
      },
      // 위 공개 경로·앱 경로가 아닌 주소(파일·Vite 내부 경로 제외)를 봇이 요청하면 404(nginx @app과 같은 규칙). 사람은 그대로 앱.
      '^/(?!@|src/|node_modules/|api/|webApi/|announcements/|areas|zupzup|weekly|help/|calendar/|sitemaps/)[^.?]+(\\?.*)?$': {
        bypass: req => (!isBot(req) || APP_ONLY_PATH.test(req.url) ? req.url : false),
        target: API
      }
    }
  },
});
