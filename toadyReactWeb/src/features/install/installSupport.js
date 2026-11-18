/**
 * 홈 화면 추가(앱처럼 설치) 안내 방식 판정(순수 함수). platform은 push/pushSupport의 detectPlatform 결과다.
 * installed: 이미 홈 화면 앱으로 열었음(메뉴 숨김) / prompt: 브라우저 설치 창을 바로 띄울 수 있음(안드로이드 Chrome·PC Chrome·Edge)
 * ios: 사파리 공유 메뉴로 추가 / ios-safari: 사파리로 열어야 함 / in-app: 카카오톡 등 앱 안 브라우저
 * android: 설치 창을 못 받는 안드로이드 브라우저(삼성 인터넷 등, 메뉴에서 직접 추가) / none: 안내할 방법이 없는 PC 브라우저(메뉴 숨김)
 */
export function installMode(platform, { canPrompt = false, android = false } = {}) {
  if (platform.standalone) return 'installed';
  if (canPrompt) return 'prompt';
  if (platform.ios) return platform.safari ? 'ios' : 'ios-safari';
  if (platform.inApp) return 'in-app';
  if (android) return 'android';
  return 'none';
}

/** 메뉴에 '홈 화면에 추가'를 보여 줄지. */
export function showInstallEntry(mode) {
  return mode !== 'installed' && mode !== 'none';
}
