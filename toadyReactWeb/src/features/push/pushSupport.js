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
 * 앱 라우터 안에서 바로 이동하는 경로(공고 주소는 ANNOUNCEMENT_PATH). 이 밖의 경로(/areas, /weekly, /calendar 등)는 전체 페이지 이동으로 연다.
 * 공개 주소는 웹 nginx가 사람에게 앱을 주므로 전체 이동해도 앱 화면이 열린다(.ics 등은 서버 응답). public/sw.js의 목록과 같아야 한다.
 */
export const SPA_PATHS = ['/', '/my', '/score', '/match', '/match/zupzup', '/faq', '/this-week', '/regions', '/living-areas', '/certify', '/login', '/showSubscriptDetail', '/account', '/messages'];

/** 공고 주소(/announcements/종류/번호/번호)도 앱 안에서 바로 연다. public/sw.js와 같은 식이어야 한다(테스트로 대조). */
export const ANNOUNCEMENT_PATH = /^\/announcements\/(apt|urban|remaining|public-rental|optional)\/[A-Za-z0-9_-]{1,40}\/[A-Za-z0-9_-]{1,40}$/;
/** 지역별 청약 주소(/regions/시·도[/시·군·구])도 앱 안의 지역 캘린더다. public/sw.js와 같은 식이어야 한다(테스트로 대조). */
export const REGION_PATH = /^\/regions\/[^/]{1,80}(\/[^/]{1,80})?$/;
/** 같은 출처의 SPA 경로인지(끝 슬래시는 무시). */
export function isSpaPath(url, origin) {
  try {
    const parsed = new URL(url, origin);
    const path = parsed.pathname.length > 1 ? parsed.pathname.replace(/\/+$/, '') : parsed.pathname;
    return parsed.origin === origin && (SPA_PATHS.includes(path) || ANNOUNCEMENT_PATH.test(path) || REGION_PATH.test(path));
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
  // 공유 링크와 같은 공고 주소(/announcements/종류/번호/번호)는 메인 화면의 상세다.
  if (/^\/announcements\/[a-z-]+\/[A-Za-z0-9_-]+\/[A-Za-z0-9_-]+\/?$/.test(url.pathname)) return { detailOverlay: true };
  if (!url.searchParams.get('subscriptSeq')) return null;
  const marker = { '/': 'detailOverlay', '/showSubscriptDetail': 'detailOverlay', '/my': 'plannerDetail', '/score': 'scoreDetail', '/match': 'matchDetail', '/match/zupzup': 'matchDetail', '/this-week': 'weeklyDetail' }[url.pathname];
  return marker ? { [marker]: true } : null;
}

/** 상세 주소를 닫았을 때 돌아갈 목록 주소(상세 값만 뺀다). /announcements/…는 첫 화면이다. */
export function detailListBase(path) {
  const url = new URL(path, 'https://toady.invalid');
  if (url.pathname.startsWith('/announcements/') || url.pathname === '/showSubscriptDetail') return '/';
  url.searchParams.delete('subscriptSeq'); url.searchParams.delete('tabType'); url.searchParams.delete('comment');
  return url.pathname + (url.searchParams.toString() ? `?${url.searchParams}` : '');
}

/**
 * 어떤 화면 위에 로그인 창을 연다(인증 완료 후, 예전 /login?next=). 지금 기록은 바꾸고 새 기록을 쌓는다.
 * 상세 주소면 [목록, 상세(표식), 상세+로그인] 순으로 쌓아, 로그인 창을 닫으면 상세가, 상세를 닫으면 목록이 나오게 한다.
 * (표식 없이 상세를 열면 useDirectDetailHistory가 목록 단계를 다시 끼우면서 로그인을 닫을 때 상세까지 닫혔다.)
 */
export function openLoginOver(navigate, path) {
  const login = { overlayLayers: [{ id: 'layout', value: 'login' }] };
  const marker = detailMarker(path);
  if (!marker) { navigate(path, { replace: true }); navigate(path, { state: login }); return; }
  navigate(detailListBase(path), { replace: true });
  navigate(path, { state: marker });
  navigate(path, { state: { ...marker, ...login } });
}


/**
 * 알림을 아직 켜지 않았고 켤 수 있는 환경인데 알림 설정을 한 번도 열어 보지 않았으면 true.
 * 하단 '더보기' 탭과 메뉴의 알림 설정에 점을 찍어 알림 진입점을 알린다(한 번 열어 보면 사라진다).
 */
export function needsPushAttention({ status, mode, enabled, permission, subscribed }, seen) {
  if (status !== 'ready' || subscribed || seen) return false;
  if (mode === 'ready') return Boolean(enabled) && permission !== 'denied';
  return mode === 'ios-home';
}

/** 알림 안내 카드를 닫은 뒤 다시 보여 주기까지의 기간. 닫은 시각(ms)이 이보다 오래됐거나 예전 형식('1')이면 false. */
export const PROMPT_SNOOZE_MS = 30 * 24 * 60 * 60 * 1000;
export function promptSnoozed(value, now = Date.now()) {
  const at = Number(value);
  return Number.isFinite(at) && at > 1 && now - at < PROMPT_SNOOZE_MS;
}
