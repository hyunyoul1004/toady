/**
 * 자격 진단 입력값(사는 곳·무주택·통장·가족 등)을 이 브라우저(localStorage)에 저장하고 읽는 모듈.
 * 맞춤 청약 화면(MatchPage)과 공고 상세의 '내 신청 가능성'(EligibilityCard)이 같은 값을 함께 쓴다.
 * 서버 API는 쓰지 않는다.
 */
import { createLocalStore } from '../../utils/localStore.js';
import { SIDO } from './eligibilityRules.js';

// 저장된 값이 꼭 true/false인지, 정해진 범위의 정수인지 확인하는 작은 도우미. 아니면 null(모름)로 바꾼다.
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

/** 진단 결과를 저장한다. 저장하면 useEligibilityProfile을 쓰는 화면이 모두 새 값으로 다시 그려진다. */
export function saveProfile(profile) {
  return store.save(normalizeProfile(profile));
}
/** '진단 기록 지우기'에서 쓴다. 저장값을 지우면 화면은 다시 진단 양식으로 돌아간다. */
export function clearProfile() {
  store.clear();
}
/**
 * 저장된 진단 값을 읽는 훅. 내부는 useSyncExternalStore라서 다른 화면이나 다른 탭에서 저장해도 바로 반영된다.
 * 진단 전이면 null.
 */
export function useEligibilityProfile() {
  return store.useValue();
}
