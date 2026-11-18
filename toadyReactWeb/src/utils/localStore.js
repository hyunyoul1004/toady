import { useSyncExternalStore } from 'react';

/**
 * 서버로 보내지 않고 이 브라우저(localStorage)에만 두는 값 하나를 다룬다.
 * parse는 저장된 JSON(없으면 null)을 검증해 화면에서 쓸 값으로 바꾼다. 읽기 실패는 null로 본다.
 * 다른 탭에서 바꾸거나 저장소 전체를 비운 경우(storage 이벤트 key가 null)도 다시 읽는다.
 *
 * useSyncExternalStore: React 바깥의 저장소(여기서는 localStorage + cache)를 컴포넌트와 이어 주는 React 18 훅.
 * - subscribe: 값이 바뀌면 부를 콜백을 등록한다. save·clear·storage 이벤트가 emit으로 알린다.
 * - snapshot: 지금 값을 돌려준다. 같은 값이면 같은 객체를 돌려줘야 하므로 cache에 담아 두고 바뀔 때만 새로 읽는다.
 * - 세 번째 인자(() => null): 서버 렌더링용 값. 이 앱은 쓰지 않지만 자리를 채운다.
 * 사용 예: 내 가점(myScore), 자격 정보(eligibilityProfile) 같은 로그인 없이 쓰는 값.
 */
export function createLocalStore(key, parse) {
  const listeners = new Set();
  let cache;

  const read = () => {
    try { return parse(JSON.parse(localStorage.getItem(key) || 'null')); } catch { return null; }
  };
  const snapshot = () => {
    if (cache === undefined) cache = read();
    return cache;
  };
  const emit = () => listeners.forEach(listener => listener());
  const subscribe = callback => { listeners.add(callback); return () => listeners.delete(callback); };
  if (typeof window !== 'undefined') window.addEventListener('storage', event => { if (event.key === key || event.key === null) { cache = read(); emit(); } });

  return {
    save(value) {
      try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 저장소가 막힌 환경에서도 이번 화면에서는 계속 쓴다 */ }
      cache = value; emit();
      return value;
    },
    clear() {
      try { localStorage.removeItem(key); } catch { /* 무시 */ }
      cache = null; emit();
    },
    useValue() {
      return useSyncExternalStore(subscribe, snapshot, () => null);
    }
  };
}
