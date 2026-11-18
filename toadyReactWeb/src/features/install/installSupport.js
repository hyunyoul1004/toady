/**
 * 홈 화면 추가(앱처럼 설치) 안내 방식 판정(순수 함수). platform은 push/pushSupport의 detectPlatform 결과다.
 * installed: 이미 홈 화면 앱으로 열었음(메뉴 숨김) / prompt: 브라우저 설치 창을 바로 띄울 수 있음(안드로이드 Chrome·PC Chrome·Edge)
 * ios: 사파리 공유 메뉴로 추가 / ios-safari: 사파리로 열어야 함 / in-app: 카카오톡 등 앱 안 브라우저
 * android: 설치 창을 못 받는 안드로이드 브라우저(삼성 인터넷 등, 메뉴에서 직접 추가)
 * desktop: PC에서 설치 창을 한 번 닫은 뒤(주소창의 설치 아이콘을 안내) / none: 안내할 방법이 없는 PC 브라우저(메뉴 숨김)
 * installStore의 useInstallMode가 이 함수를 부르고, 결과는 Sidebar의 '홈 화면에 추가' 메뉴와 InstallGuideDialog가 쓴다.
 * 순서가 중요하다: 이미 설치됨 → 설치 창 가능 → 아이폰 → 앱 안 브라우저 → 안드로이드 → PC 순으로 먼저 맞는 것을 고른다.
 */
export function installMode(platform, { canPrompt = false, android = false, promptDismissed = false } = {}) {
  if (platform.standalone) return 'installed';
  if (canPrompt) return 'prompt';
  if (platform.ios) return platform.safari ? 'ios' : 'ios-safari';
  if (platform.inApp) return 'in-app';
  if (android) return 'android';
  // PC에서 설치 창을 한 번 닫았으면 브라우저가 같은 창을 다시 주지 않으므로, 주소창의 설치 아이콘 안내로 바꾼다.
  if (promptDismissed) return 'desktop';
  return 'none';
}

/** 메뉴에 '홈 화면에 추가'를 보여 줄지. 이미 설치했거나 안내할 방법이 없으면 숨긴다. */
export function showInstallEntry(mode) {
  return mode !== 'installed' && mode !== 'none';
}

/** 안드로이드 브라우저 구분(브라우저마다 홈 화면 추가 메뉴 위치·이름이 달라 안내를 나눈다). */
export function androidBrowser(ua = '') {
  if (/SamsungBrowser/i.test(ua)) return 'samsung';
  if (/Whale/i.test(ua)) return 'whale';
  if (/Firefox/i.test(ua)) return 'firefox';
  if (/EdgA/i.test(ua)) return 'edge';
  return 'chrome';
}

/**
 * 안드로이드에서 지금 주소를 Chrome으로 여는 intent 주소. 삼성 인터넷·카카오톡 안 브라우저 등은 사이트가 설치 창을 띄울 수 없어,
 * Chrome으로 옮겨 '설치' 한 번으로 끝나게 한다. install=1을 붙여 Chrome에서 열리자마자 설치 안내를 띄운다.
 * Chrome이 없으면 browser_fallback_url(같은 주소)로 그냥 열린다.
 */
export function chromeIntentUrl(href) {
  try {
    const url = new URL(href);
    url.searchParams.set('install', '1');
    const fallback = encodeURIComponent(url.href);
    return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=https;package=com.android.chrome;S.browser_fallback_url=${fallback};end`;
  } catch { return href; }
}
