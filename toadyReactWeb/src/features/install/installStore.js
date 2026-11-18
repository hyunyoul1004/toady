import { useSyncExternalStore } from 'react';
import { detectPlatform } from '@/features/push/pushSupport';
import { trackAction } from '@/features/analytics/trackAction';
import { installMode } from './installSupport';

/**
 * 브라우저의 설치 창(beforeinstallprompt)을 받아 두었다가 '홈 화면에 추가'를 누르면 띄운다.
 * 이 이벤트는 페이지를 연 직후 한 번만 오므로 main.jsx에서 이 모듈을 먼저 불러온다.
 * 기본 동작(Chrome 모바일의 작은 설치 배너)은 막지 않는다.
 *
 * beforeinstallprompt: Chrome·Edge·안드로이드가 '이 사이트는 앱처럼 설치할 수 있다'고 판단하면(manifest·서비스 워커 조건) 보내는 이벤트다.
 * 이벤트 객체를 들고 있다가 나중에 event.prompt()를 부르면 설치 창이 뜬다. 아이폰 사파리는 이 이벤트가 없어서
 * 공유 메뉴 › 홈 화면에 추가를 직접 안내한다(HomeScreenGuide).
 *
 * 상태는 React 밖 모듈 변수에 두고, useSyncExternalStore로 화면에 연결한다(작은 직접 만든 스토어).
 * emit()이 version을 올리고 구독자에게 알리면, useInstallMode를 쓰는 컴포넌트가 다시 그려진다.
 * 쓰는 곳: Sidebar('홈 화면에 추가' 메뉴). 측정: trackAction('app_install').
 */
let deferred = null;
let installed = false;
/** 이번 방문에서 설치 창을 띄웠다가 닫았는지(브라우저는 같은 창을 다시 주지 않는다). */
let promptDismissed = false;
let version = 0;
const listeners = new Set();
function emit() { version += 1; listeners.forEach(listener => listener()); }
function subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); }

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', event => { deferred = event; emit(); });
  // 이미 이 기기에 설치돼 있으면(브라우저 탭으로 다시 연 경우) 메뉴를 숨긴다. manifest의 related_applications(webapp)와 짝이다.
  try {
    window.navigator.getInstalledRelatedApps?.().then(apps => { if (apps?.length) { installed = true; emit(); } }).catch(() => {});
  } catch { /* 지원하지 않는 브라우저 */ }
  window.addEventListener('appinstalled', () => { deferred = null; installed = true; trackAction('app_install', 'tool/install-done'); emit(); });
}

/** 받아 둔 설치 창을 띄운다. 사용자 동작(클릭) 안에서 바로 불러야 한다. 결과: 'accepted' | 'dismissed' | 'unavailable'. */
export async function promptInstall() {
  const event = deferred;
  if (!event) return 'unavailable';
  deferred = null;
  emit();
  try {
    event.prompt();
    const { outcome } = await event.userChoice;
    if (outcome !== 'accepted') { promptDismissed = true; emit(); }
    trackAction('app_install', outcome === 'accepted' ? 'tool/install-accepted' : 'tool/install-dismissed');
    return outcome;
  } catch {
    return 'unavailable';
  }
}

/** 지금 기기·브라우저에서 홈 화면 추가 방식. */
export function useInstallMode() {
  // 반환값은 쓰지 않는다. version이 바뀔 때 다시 그려지게 구독만 하고, 실제 값은 아래에서 매번 계산한다.
  useSyncExternalStore(subscribe, () => version, () => 0);
  if (typeof window === 'undefined') return 'none';
  if (installed) return 'installed';
  return installMode(detectPlatform(window.navigator, window), {
    canPrompt: Boolean(deferred),
    promptDismissed,
    android: /Android/i.test(window.navigator.userAgent || '')
  });
}
