import { useSyncExternalStore } from 'react';
import { detectPlatform } from '@/features/push/pushSupport';
import { trackAction } from '@/features/analytics/trackAction';
import { installMode } from './installSupport';

/**
 * 브라우저의 설치 창(beforeinstallprompt)을 받아 두었다가 '홈 화면에 추가'를 누르면 띄운다.
 * 이 이벤트는 페이지를 연 직후 한 번만 오므로 main.jsx에서 이 모듈을 먼저 불러온다.
 * 기본 동작(Chrome 모바일의 작은 설치 배너)은 막지 않는다.
 */
let deferred = null;
let installed = false;
let version = 0;
const listeners = new Set();
function emit() { version += 1; listeners.forEach(listener => listener()); }
function subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); }

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', event => { deferred = event; emit(); });
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
    trackAction('app_install', outcome === 'accepted' ? 'tool/install-accepted' : 'tool/install-dismissed');
    return outcome;
  } catch {
    return 'unavailable';
  }
}

/** 지금 기기·브라우저에서 홈 화면 추가 방식. */
export function useInstallMode() {
  useSyncExternalStore(subscribe, () => version, () => 0);
  if (typeof window === 'undefined') return 'none';
  if (installed) return 'installed';
  return installMode(detectPlatform(window.navigator, window), {
    canPrompt: Boolean(deferred),
    android: /Android/i.test(window.navigator.userAgent || '')
  });
}
