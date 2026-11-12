import { useSyncExternalStore } from 'react';
import { SIDO } from './eligibilityRules.js';

/** 자격 진단 입력은 서버로 보내지 않고 이 브라우저에만 둔다(가점 계산기와 같은 방식). */
const KEY = 'toady.eligibility';
const listeners = new Set();
let cache;

const bool = value => (value === true || value === false ? value : null);
const int = (value, min, max) => { const n = Number(value); return value === null || value === '' || !Number.isInteger(n) || n < min || n > max ? null : n; };
/** 저장값을 검증한다. 모르는 키·잘못된 값은 버린다. */
export function normalizeProfile(raw = {}) {
  if (!raw || typeof raw !== 'object') return {};
  return {
    sido: SIDO.includes(raw.sido) ? raw.sido : null,
    birthYear: int(raw.birthYear, 1920, 2015),
    householder: bool(raw.householder),
    homeless: bool(raw.homeless),
    homes: int(raw.homes, 1, 2),
    everOwned: bool(raw.everOwned),
    marriage: ['single', 'married', 'engaged'].includes(raw.marriage) ? raw.marriage : null,
    marriedWithin7: bool(raw.marriedWithin7),
    children: int(raw.children, 0, 9),
    newborn: bool(raw.newborn),
    parents: bool(raw.parents),
    accountMonths: int(raw.accountMonths, -1, 600),
    won5y: bool(raw.won5y),
    usedSpecial: bool(raw.usedSpecial)
  };
}
function read() {
  try { const saved = JSON.parse(localStorage.getItem(KEY) || 'null'); return saved ? normalizeProfile(saved) : null; } catch { return null; }
}
function snapshot() { if (cache === undefined) cache = read(); return cache; }
function emit() { listeners.forEach(listener => listener()); }
if (typeof window !== 'undefined') window.addEventListener('storage', event => { if (event.key === KEY || event.key === null) { cache = read(); emit(); } });

export function saveProfile(profile) {
  const value = normalizeProfile(profile);
  try { localStorage.setItem(KEY, JSON.stringify(value)); } catch { /* 저장소가 막혀도 이번 화면에서는 쓴다 */ }
  cache = value; emit();
  return value;
}
export function clearProfile() {
  try { localStorage.removeItem(KEY); } catch { /* 무시 */ }
  cache = null; emit();
}
export function useEligibilityProfile() {
  return useSyncExternalStore(callback => { listeners.add(callback); return () => listeners.delete(callback); }, snapshot, () => null);
}
