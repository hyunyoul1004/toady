/**
 * 주택공급에 관한 규칙 별표 1의 일반공급 가점 기준(84점 만점).
 * 화면·저장·비교가 같은 표를 쓰도록 한곳에 둔다. 값은 선택지의 순번(index)으로 저장한다.
 */
export const MAX_SCORE = 84;

const years = (from, to) => `${from}년 이상 ~ ${to}년 미만`;
export const HOUSING_OPTIONS = [
  { label: '해당 없음', hint: '유주택자이거나 만 30세 미만 미혼', points: 0 },
  { label: '1년 미만', points: 2 },
  ...Array.from({ length: 14 }, (_, i) => ({ label: years(i + 1, i + 2), points: (i + 2) * 2 })),
  { label: '15년 이상', points: 32 }
];
export const DEPENDENT_OPTIONS = Array.from({ length: 7 }, (_, n) => ({ label: n === 6 ? '6명 이상' : `${n}명`, points: 5 + n * 5 }));
export const ACCOUNT_OPTIONS = [
  { label: '6개월 미만', points: 1 },
  { label: '6개월 이상 ~ 1년 미만', points: 2 },
  ...Array.from({ length: 14 }, (_, i) => ({ label: years(i + 1, i + 2), points: i + 3 })),
  { label: '15년 이상', points: 17 }
];
export const SCORE_PARTS = [
  { key: 'housing', title: '무주택 기간', max: 32, options: HOUSING_OPTIONS },
  { key: 'dependents', title: '부양가족 수', max: 35, options: DEPENDENT_OPTIONS },
  { key: 'account', title: '청약통장 가입기간', max: 17, options: ACCOUNT_OPTIONS }
];

const pick = (options, index) => {
  if (index === null || index === undefined || index === '' || typeof index === 'boolean') return null;
  const value = Number(index);
  return Number.isInteger(value) && value >= 0 && value < options.length ? value : null;
};
/** 입력 순번을 검증한다. 하나라도 비었거나 범위를 벗어나면 null. */
export function normalizeSelection(selection = {}) {
  const housing = pick(HOUSING_OPTIONS, selection.housing), dependents = pick(DEPENDENT_OPTIONS, selection.dependents), account = pick(ACCOUNT_OPTIONS, selection.account);
  return housing === null || dependents === null || account === null ? null : { housing, dependents, account };
}
export function scoreOf(selection) {
  const valid = normalizeSelection(selection);
  if (!valid) return null;
  const parts = { housing: HOUSING_OPTIONS[valid.housing].points, dependents: DEPENDENT_OPTIONS[valid.dependents].points, account: ACCOUNT_OPTIONS[valid.account].points };
  return { ...parts, total: parts.housing + parts.dependents + parts.account };
}

const scoreNumber = value => {
  const text = String(value ?? '').trim();
  if (!/^\d+(?:\.\d+)?$/.test(text)) return null;
  const number = Number(text);
  return number > 0 && number <= MAX_SCORE ? number : null;
};
/** 청약홈 당첨가점 행(lwetScore/topScore)에서 공개된 최저·최고만 모은다. 공유 문구와 같은 기준이다. */
export function publicRange(rows = []) {
  const lows = rows.map(row => scoreNumber(row.lwetScore)).filter(v => v !== null);
  const highs = rows.map(row => scoreNumber(row.topScore)).filter(v => v !== null);
  if (!lows.length && !highs.length) return null;
  return { low: lows.length ? Math.min(...lows) : null, high: highs.length ? Math.max(...highs) : null };
}

/**
 * 내 점수와 공개 당첨가점 범위를 비교한다. 지난 결과와의 비교일 뿐 당첨 가능성을 단정하지 않는 문구를 돌려준다.
 * status: above(최고 이상) · within(최저~최고 사이) · below(최저 미만) · unknown
 */
export function compareToRange(total, range) {
  if (!Number.isFinite(total) || !range) return { status: 'unknown', gap: null, text: '공개된 당첨가점이 없어요' };
  const { low, high } = range;
  if (high !== null && total >= high) return { status: 'above', gap: total - high, text: '공개 최고 당첨가점 이상이에요' };
  if (low !== null && total >= low) return { status: 'within', gap: total - low, text: low === total ? '공개 최저 당첨가점과 같아요' : `공개 최저 당첨가점보다 ${fmt(total - low)}점 높아요` };
  if (low !== null) return { status: 'below', gap: low - total, text: `공개 최저 당첨가점보다 ${fmt(low - total)}점 낮아요` };
  return { status: 'unknown', gap: null, text: '공개 최저 당첨가점이 없어요' };
}
export const fmt = value => Number.isInteger(value) ? String(value) : value.toFixed(1).replace(/\.0$/, '');

/** 공유 주소(?h=&d=&a=)와 저장값이 같은 형식을 쓴다. */
export function selectionFromParams(params) {
  return normalizeSelection({ housing: params.get('h'), dependents: params.get('d'), account: params.get('a') });
}
export function selectionToParams(selection) {
  const valid = normalizeSelection(selection);
  return valid ? new URLSearchParams({ h: String(valid.housing), d: String(valid.dependents), a: String(valid.account) }) : new URLSearchParams();
}
