import { useSyncExternalStore } from 'react';
import { normalizeSelection, scoreOf } from './scoreRules';

/** 내 가점은 서버로 보내지 않고 이 브라우저에만 둔다. 공고 상세의 비교 카드가 같은 값을 읽는다. */
const KEY = 'toady.myScore';
const listeners = new Set();
let cache;

function read() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null');
    const selection = normalizeSelection(saved || {});
    return selection ? { ...selection, savedAt: typeof saved.savedAt === 'string' ? saved.savedAt : null } : null;
  } catch { return null; }
}
function snapshot() {
  if (cache === undefined) cache = read();
  return cache;
}
function emit() { listeners.forEach(listener => listener()); }
if (typeof window !== 'undefined') window.addEventListener('storage', event => { if (event.key === KEY) { cache = read(); emit(); } });

export function saveMyScore(selection) {
  const valid = normalizeSelection(selection);
  if (!valid) return null;
  const value = { ...valid, savedAt: new Date().toISOString() };
  try { localStorage.setItem(KEY, JSON.stringify(value)); } catch { /* 저장소가 막힌 환경에서도 이번 화면에서는 계속 쓴다 */ }
  cache = value; emit();
  return value;
}
export function clearMyScore() {
  try { localStorage.removeItem(KEY); } catch { /* 무시 */ }
  cache = null; emit();
}
/** { selection, score } — 저장된 값이 없으면 둘 다 null */
export function useMyScore() {
  const selection = useSyncExternalStore(callback => { listeners.add(callback); return () => listeners.delete(callback); }, snapshot, () => null);
  return { selection, score: selection ? scoreOf(selection) : null };
}
