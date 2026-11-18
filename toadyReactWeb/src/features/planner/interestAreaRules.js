/** 메인 '관심 지역 고르기'(InterestAreaPicker.jsx) 규칙. 화면 없이 테스트할 수 있게 순수 함수로 둔다. */
import { normalizeSearchTargets } from './searchState.js';

export const MAX_INTEREST_AREAS = 3;

/**
 * 고른 생활권 이름 → 달력 검색 조건(paramInfo.citys). 검색창에서 생활권을 고른 것과 같은 모양
 * ({ type:'living_area', label, key:'area_라벨', terms })이라 저장·복원·필터가 그대로 동작한다.
 */
export function interestAreaTargets(areas = [], labels = []) {
  const picked = labels.slice(0, MAX_INTEREST_AREAS).map(label => areas.find(area => area?.label === label)).filter(Boolean);
  return normalizeSearchTargets(picked.map(area => ({ type: 'living_area', label: area.label, key: `area_${area.label}`, terms: area.terms })));
}

/** 관심 지역 고르기는 저장한 공고도, 검색 조건도 없는 처음 상태에서만 보여 준다(이미 고른 사람에게 반복하지 않는다). */
export function showInterestPicker({ savedCount = 0, searchTargets = [], dismissed = false, areaCount = 0 } = {}) {
  return !dismissed && savedCount === 0 && areaCount > 0 && !(Array.isArray(searchTargets) && searchTargets.length);
}

/** 선택 토글: 최대 개수를 넘으면 바꾸지 않는다. */
export function toggleInterest(selected = [], label) {
  if (selected.includes(label)) return selected.filter(value => value !== label);
  return selected.length >= MAX_INTEREST_AREAS ? selected : [...selected, label];
}
