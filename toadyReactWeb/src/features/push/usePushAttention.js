import { useSyncExternalStore } from 'react';
import { usePushStore } from './pushStore';
import { needsPushAttention } from './pushSupport';

/**
 * '알림을 아직 안 켰어요' 점 표시. 모바일 하단 탭 '더보기'(MobileTabBar)와 사이드바 '알림 설정'(Sidebar)이 쓴다.
 * 판정은 pushStore(zustand)의 상태 + localStorage의 '설정 창을 열어 봤는지' 값을 합쳐서 한다.
 *
 * useSyncExternalStore: React 밖에 있는 값(여기서는 localStorage)을 화면에 연결하는 훅이다.
 * subscribe(바뀌면 알려 줄 함수 등록)와 getSnapshot(지금 값 읽기)을 넘기면, 알림이 올 때 값을 다시 읽어 다시 그린다.
 * localStorage는 바뀌어도 스스로 알려 주지 않으므로, 값을 쓰는 markPushSettingsSeen이 listeners를 직접 부른다.
 */
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
  // selector 없이 부르면 스토어 전체를 받는다. 스토어 값이 하나라도 바뀌면 이 훅을 쓰는 컴포넌트가 다시 그려진다.
  const push = usePushStore();
  // 세 번째 인자는 서버 렌더링용 값이다. true(이미 봄)로 두어 점이 잠깐 깜빡이지 않게 한다.
  const seen = useSyncExternalStore(subscribe, readSeen, () => true);
  return needsPushAttention({ status: push.status, mode: push.platform?.mode, enabled: push.enabled, permission: push.permission, subscribed: push.subscribed }, seen);
}
