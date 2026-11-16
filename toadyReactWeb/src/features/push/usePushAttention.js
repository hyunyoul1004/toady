import { useSyncExternalStore } from 'react';
import { usePushStore } from './pushStore';
import { needsPushAttention } from './pushSupport';

const SEEN_KEY = 'toady.push.settingsSeen';
const listeners = new Set();
function readSeen() { try { return localStorage.getItem(SEEN_KEY) === '1'; } catch { return true; } }
function subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); }

/** 알림 설정 창을 열면 부른다. 이후 '더보기'·메뉴의 알림 점이 사라진다. */
export function markPushSettingsSeen() {
  try { localStorage.setItem(SEEN_KEY, '1'); } catch { /* 저장 못 해도 이번 화면에서만 다시 보임 */ }
  listeners.forEach(listener => listener());
}

/** 알림을 켤 수 있는데 아직 켜지 않았고 설정을 열어 본 적도 없으면 true. */
export function usePushAttention() {
  const push = usePushStore();
  const seen = useSyncExternalStore(subscribe, readSeen, () => true);
  return needsPushAttention({ status: push.status, mode: push.platform?.mode, enabled: push.enabled, permission: push.permission, subscribed: push.subscribed }, seen);
}
