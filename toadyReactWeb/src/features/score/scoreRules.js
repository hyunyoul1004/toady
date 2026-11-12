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

/* ---------- 날짜로 구간 고르기 ---------- */
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const validDate = value => DATE.test(value || '') && !Number.isNaN(Date.parse(`${value}T00:00:00Z`)) && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
/** YYYY-MM-DD에 n년을 더한다. 2월 29일은 다음 해 3월 1일로 넘긴다(만 나이 계산과 같다). */
export function addYears(value, years) {
  const [y, m, d] = value.split('-').map(Number);
  const date = new Date(Date.UTC(y + years, m - 1, d));
  return date.toISOString().slice(0, 10);
}
/** from부터 to까지 꽉 찬 개월 수. */
export function fullMonths(from, to) {
  const [fy, fm, fd] = from.split('-').map(Number), [ty, tm, td] = to.split('-').map(Number);
  let months = (ty - fy) * 12 + (tm - fm);
  if (td < fd) months -= 1;
  return Math.max(0, months);
}
export const periodText = months => `${Math.floor(months / 12) ? `${Math.floor(months / 12)}년` : ''}${Math.floor(months / 12) && months % 12 ? ' ' : ''}${months % 12 || !Math.floor(months / 12) ? `${months % 12}개월` : ''}`;
const dot = value => value.replaceAll('-', '.');

/**
 * 무주택 기간 구간을 날짜로 정한다. 기준: 만 30세가 된 날부터(30세 전에 혼인했다면 혼인신고일부터),
 * 집을 가진 적이 있으면 마지막으로 무주택이 된 날 중 늦은 날부터 오늘까지.
 * 반환: { index, start, months, reason } 또는 입력이 부족하면 { error }.
 */
export function housingFromDates({ birth, married, marriageDate, owned, soldDate }, today) {
  if (!validDate(birth) || birth > today) return { error: '생년월일을 입력해 주세요.' };
  if (married && (!validDate(marriageDate) || marriageDate > today)) return { error: '혼인신고일을 입력해 주세요.' };
  if (owned && (!validDate(soldDate) || soldDate > today)) return { error: '집을 처분한(무주택이 된) 날을 입력해 주세요.' };
  const age30 = addYears(birth, 30);
  const marriedEarly = married && marriageDate < age30;
  let start = marriedEarly ? marriageDate : age30, reason = marriedEarly ? `만 30세 전에 혼인해 혼인신고일(${dot(marriageDate)})부터` : `만 30세가 된 날(${dot(age30)})부터`;
  if (start > today) return { index: 0, start: null, months: 0, reason: '만 30세 미만 미혼이라 무주택 기간 점수가 없어요(0점).' };
  if (owned && soldDate > start) { start = soldDate; reason = `마지막으로 무주택이 된 날(${dot(soldDate)})부터`; }
  const months = fullMonths(start, today), years = Math.floor(months / 12);
  return { index: years < 1 ? 1 : Math.min(16, years + 1), start, months, reason: `${reason} ${periodText(months)}` };
}
/** 청약통장 가입기간 구간을 가입일로 정한다. */
export function accountFromDate(joined, today) {
  if (!validDate(joined) || joined > today) return { error: '청약통장 가입일을 입력해 주세요.' };
  const months = fullMonths(joined, today), years = Math.floor(months / 12);
  const index = months < 6 ? 0 : years < 1 ? 1 : Math.min(16, years + 1);
  return { index, months, reason: `가입일(${dot(joined)})부터 ${periodText(months)}` };
}

/** 숫자만 받아 YYYY.MM.DD로 보여 준다. 8자리가 되면 YYYY-MM-DD로 넘기고, 아니면 빈 값을 넘긴다. */
export function formatDigits(raw) {
  const digits = String(raw || '').replace(/\D/g, '').slice(0, 8);
  return [digits.slice(0, 4), digits.slice(4, 6), digits.slice(6, 8)].filter(Boolean).join('.');
}
export function toIsoDate(display) {
  const digits = String(display || '').replace(/\D/g, '');
  if (digits.length !== 8) return '';
  const iso = `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
  const parsed = new Date(`${iso}T00:00:00Z`);
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === iso ? iso : '';
}


/** 입력칸 글자를 부모에게 넘길 값으로 바꾼다. 올바른 날짜이고 max 이하일 때만 YYYY-MM-DD, 아니면 빈 값. */
export function normalizeDateInput(display, max) {
  const iso = toIsoDate(display);
  return iso && (!max || iso <= max) ? iso : '';
}

/**
 * 날짜 입력 결과를 구간 선택에 어떻게 반영할지 정한다.
 * - 계산된 구간이 있으면 그 값으로(이미 같으면 그대로)
 * - 날짜를 입력한 적이 있는데 지금 입력이 부족·잘못되면, 이 날짜 입력이 골랐던 값(lastPicked)일 때만 지운다(null)
 * 반환값이 undefined 이면 아무것도 바꾸지 않는다.
 */
export function periodPickUpdate(result, value, dirty, lastPicked) {
  if (result.index !== undefined) return result.index !== value ? result.index : undefined;
  // 날짜 입력이 직접 자동으로 골랐던 값일 때만 지운다. 직접 고른 값이나 예전에 저장된 값은 건드리지 않는다.
  if (dirty && result.error && value !== undefined && value !== null && value !== '' && value === lastPicked) return null;
  return undefined;
}
