import { createLocalStore } from '../../utils/localStore.js';
import { SIDO } from './eligibilityRules.js';

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
/** 자격 진단 입력은 서버로 보내지 않고 이 브라우저에만 둔다(가점 계산기와 같은 방식). */
const store = createLocalStore('toady.eligibility', saved => (saved ? normalizeProfile(saved) : null));

export function saveProfile(profile) {
  return store.save(normalizeProfile(profile));
}
export function clearProfile() {
  store.clear();
}
export function useEligibilityProfile() {
  return store.useValue();
}
