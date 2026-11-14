import { createLocalStore } from '@/utils/localStore';
import { normalizeSelection, scoreOf } from './scoreRules';

/** 내 가점은 서버로 보내지 않고 이 브라우저에만 둔다. 공고 상세의 비교 카드가 같은 값을 읽는다. */
const store = createLocalStore('toady.myScore', saved => {
  const selection = normalizeSelection(saved || {});
  return selection ? { ...selection, savedAt: typeof saved.savedAt === 'string' ? saved.savedAt : null } : null;
});

export function saveMyScore(selection) {
  const valid = normalizeSelection(selection);
  if (!valid) return null;
  return store.save({ ...valid, savedAt: new Date().toISOString() });
}
export function clearMyScore() {
  store.clear();
}
/** { selection, score } — 저장된 값이 없으면 둘 다 null */
export function useMyScore() {
  const selection = store.useValue();
  return { selection, score: selection ? scoreOf(selection) : null };
}
