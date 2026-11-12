/**
 * 웹 푸시 지원 환경 판정(순수 함수). 아이폰·아이패드는 iOS 16.4 이상에서 '홈 화면에 추가'한 앱으로 열었을 때만
 * 푸시를 받을 수 있어, 브라우저 탭에서는 알림 켜기 대신 홈 화면 추가 방법을 안내한다.
 */
const IN_APP = /KAKAOTALK|NAVER\(inapp|Instagram|FBAN|FBAV|Line\/|DaumApps|everytimeApp/i;

export function detectPlatform(nav = {}, win = {}) {
  const ua = nav.userAgent || '';
  const ios = /iPhone|iPad|iPod/.test(ua) || (nav.platform === 'MacIntel' && nav.maxTouchPoints > 1);
  const version = ua.match(/(?:iPhone|CPU) OS (\d+)_(\d+)/);
  let standalone = nav.standalone === true;
  try { standalone ||= Boolean(win.matchMedia?.('(display-mode: standalone)').matches); } catch { /* 지원하지 않는 브라우저 */ }
  const inApp = IN_APP.test(ua);
  return {
    ios,
    iosVersion: version ? [Number(version[1]), Number(version[2])] : null,
    standalone,
    inApp,
    safari: ios && !inApp && !/CriOS|FxiOS|EdgiOS|OPiOS|Whale/.test(ua),
    api: Boolean(win.isSecureContext && nav.serviceWorker && win.PushManager && win.Notification)
  };
}

/**
 * ready: 바로 알림을 켤 수 있음 / ios-home: 사파리에서 홈 화면에 추가해야 함 / ios-safari: 사파리로 열어야 함
 * ios-old: iOS 16.4 미만 / in-app: 카카오톡 등 앱 안 브라우저 / unsupported: 그 밖의 미지원 브라우저
 */
export function pushMode(p) {
  if (p.ios && p.iosVersion && (p.iosVersion[0] < 16 || (p.iosVersion[0] === 16 && p.iosVersion[1] < 4))) return 'ios-old';
  if (p.api && (!p.ios || p.standalone)) return 'ready';
  if (p.ios && !p.standalone) return p.safari ? 'ios-home' : 'ios-safari';
  if (p.inApp) return 'in-app';
  return 'unsupported';
}

/** VAPID 공개키(base64url)를 PushManager.subscribe가 받는 바이트 배열로 바꾼다. */
export function base64UrlToBytes(value) {
  const base64 = (value + '='.repeat((4 - (value.length % 4)) % 4)).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);
  return Uint8Array.from(raw, char => char.charCodeAt(0));
}

/** 서비스 워커가 보낸 이동 주소가 같은 출처의 앱 경로일 때만 라우터 경로로 돌려준다. */
export function appPath(url, origin) {
  try {
    const parsed = new URL(url, origin);
    return parsed.origin === origin ? parsed.pathname + parsed.search + parsed.hash : null;
  } catch { return null; }
}

/**
 * React 앱(SPA)이 그리는 경로. 나머지(/areas, /announcements, /zupzup, /weekly, /calendar 등)는 서버가 HTML을 직접 그려
 * 앱 라우터로 이동하면 첫 화면으로 떨어지므로 전체 페이지 이동이 필요하다. public/sw.js의 목록과 같아야 한다.
 */
export const SPA_PATHS = ['/', '/my', '/score', '/match', '/match/zupzup', '/faq', '/this-week', '/regions', '/living-areas', '/certify', '/login', '/showSubscriptDetail'];

/** 같은 출처의 SPA 경로인지(끝 슬래시는 무시). */
export function isSpaPath(url, origin) {
  try {
    const parsed = new URL(url, origin);
    const path = parsed.pathname.length > 1 ? parsed.pathname.replace(/\/+$/, '') : parsed.pathname;
    return parsed.origin === origin && SPA_PATHS.includes(path);
  } catch { return false; }
}

/** 구독 범위가 바뀌었을 때만 서버와 다시 맞추기 위한 비교 키. */
export function scopeKey({ endpoint, userSeq, favorites, areas = [], favoriteIds = [] }) {
  return JSON.stringify([endpoint || '', userSeq ?? null, favorites !== false, [...areas].sort(), userSeq ? [] : [...favoriteIds].sort()]);
}

export const PUSH_HELP = {
  'ios-home': '아이폰에서는 TOADY를 홈 화면에 추가한 뒤, 홈 화면 아이콘으로 열어야 알림을 받을 수 있어요.',
  'ios-safari': '아이폰은 Safari에서 TOADY를 열어 홈 화면에 추가해야 알림을 받을 수 있어요.',
  'ios-old': 'iOS 16.4 이상에서 알림을 받을 수 있어요. 설정 › 일반 › 소프트웨어 업데이트를 확인해 주세요.',
  'in-app': '카카오톡·네이버 등 앱 안의 브라우저에서는 알림을 받을 수 없어요. Chrome이나 Safari로 열어 주세요.',
  unsupported: '이 브라우저는 알림을 지원하지 않아요. Chrome, Edge, Safari 최신 버전에서 이용해 주세요.'
};

/** 알림으로 연 공고 상세만 '앱 안 상세'로 표시한다(그 화면의 표식 하나). 상세가 없는 화면에 표식을 붙이면 탭 다시 누르기가 이전 화면으로 빠져나간다. */
export function detailMarker(path) {
  const url = new URL(path, 'https://toady.invalid');
  if (!url.searchParams.get('subscriptSeq')) return null;
  const marker = { '/': 'detailOverlay', '/my': 'plannerDetail', '/score': 'scoreDetail', '/match': 'matchDetail', '/match/zupzup': 'matchDetail', '/this-week': 'weeklyDetail' }[url.pathname];
  return marker ? { [marker]: true } : null;
}
