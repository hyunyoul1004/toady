import { useSyncExternalStore } from 'react';

/**
 * 서버로 보내지 않고 이 브라우저(localStorage)에만 두는 값 하나를 다룬다.
 * parse는 저장된 JSON(없으면 null)을 검증해 화면에서 쓸 값으로 바꾼다. 읽기 실패는 null로 본다.
 * 다른 탭에서 바꾸거나 저장소 전체를 비운 경우(storage 이벤트 key가 null)도 다시 읽는다.
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
