/**
 * 가점 계산기에서 고른 값(무주택 기간·부양가족·통장 기간의 선택 순번)을 이 브라우저에 저장하고 읽는다.
 * 쓰는 곳: ScoreCalculator(저장), ScoreCompareCard(공고 상세 비교), MatchPage(통장 기간 미리 채우기).
 */
import { createLocalStore } from '@/utils/localStore';
import { normalizeSelection, scoreOf } from './scoreRules';

/** 내 가점은 서버로 보내지 않고 이 브라우저에만 둔다. 공고 상세의 비교 카드가 같은 값을 읽는다. */
const store = createLocalStore('toady.myScore', saved => {
  const selection = normalizeSelection(saved || {});
  return selection ? { ...selection, savedAt: typeof saved.savedAt === 'string' ? saved.savedAt : null } : null;
});

/** 세 항목이 모두 올바를 때만 저장하고, 저장 시각(savedAt)을 함께 남긴다. 하나라도 비면 저장하지 않고 null. */
export function saveMyScore(selection) {
  const valid = normalizeSelection(selection);
  if (!valid) return null;
  return store.save({ ...valid, savedAt: new Date().toISOString() });
}
export function clearMyScore() {
  store.clear();
}
/**
 * { selection, score } — 저장된 값이 없으면 둘 다 null
 * 내부는 useSyncExternalStore라서 계산기에서 저장하면 공고 상세의 비교 카드도 곧바로 새 점수로 바뀐다.
 */
export function useMyScore() {
  const selection = store.useValue();
  return { selection, score: selection ? scoreOf(selection) : null };
}
