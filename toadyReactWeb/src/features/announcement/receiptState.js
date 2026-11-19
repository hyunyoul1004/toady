/**
 * @file receiptState.js
 * @description 공고 한 건의 접수 상태(예정·접수 중·종료)와 마감까지 남은 날을 정하는 공통 규칙(순수 함수, node 테스트 가능).
 * 목록·달력과 같은 기준을 쓴다: 접수 기간은 시작일부터 MAX_PERIOD_DAYS(서버 ReceiptWindow.MAX_OPEN_DAYS)까지만 인정해,
 * 마감일이 9999-12-31처럼 들어온 공고가 상세·관심 공고·맞춤 청약에서만 끝없이 '접수 중'으로 남지 않게 한다.
 * 마감일이 ALWAYS_OPEN_FROM 이후면 실제 마감일이 아니라 '상시 접수' 표기로 보고 D-day를 보이지 않는다.
 * 쓰는 곳: ResearchPanel(상세 상태 칩), FavoriteCard(관심 공고 카드), matchShared.receiptBadge(맞춤 청약 카드 배지),
 * priceMapRules.saleStatusOf(지도 '이 공고' 상태), applyDays(달력 '상시 접수' 줄).
 * applyDays와 서로 가져다 쓰지만 함수 안에서만 쓰므로(모듈을 읽는 순간에는 쓰지 않음) 순서 문제가 없다.
 */
import { MAX_PERIOD_DAYS, addDays } from './applyDays.js';

/** 이 날 이후의 마감일은 '상시 접수'로 본다(청약홈이 마감 없음 대신 9999-12-31 등을 넣는다). */
export const ALWAYS_OPEN_FROM = '2100-01-01';

/** 'YYYY-MM-DD'·'YYYYMMDD'·'YYYY.MM.DD' → 'YYYY-MM-DD'. 읽을 수 없으면 null. */
function day(value) {
  const text = String(value ?? '').trim().slice(0, 10).replaceAll('-', '').replaceAll('.', '');
  return /^\d{8}$/.test(text) ? `${text.slice(0, 4)}-${text.slice(4, 6)}-${text.slice(6, 8)}` : null;
}
const between = (from, to) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86400000);

/** 마감일이 '상시 접수' 표기(ALWAYS_OPEN_FROM 이후)인지. 달력 일정 행(applyDays)도 이 함수로 상한일을 마감일처럼 보이지 않게 한다. */
export function isAlwaysOpenEnd(endValue) {
  const end = day(endValue);
  return Boolean(end && end >= ALWAYS_OPEN_FROM);
}

/** 인정하는 마지막 접수일: 마감일(없거나 시작일보다 앞서면 시작일)과 시작일+MAX_PERIOD_DAYS 중 이른 날. 서버 ReceiptWindow.lastDay와 같다. */
export function receiptLastDay(startValue, endValue) {
  const start = day(startValue);
  if (!start) return day(endValue);
  const end = day(endValue);
  const last = end && end > start ? end : start;
  const cap = addDays(start, MAX_PERIOD_DAYS);
  return last > cap ? cap : last;
}

/**
 * 접수 상태.
 * @returns {{state:'upcoming'|'open'|'closed'|'unknown', daysToStart:number|null, daysLeft:number|null, always:boolean}}
 * - upcoming: 오늘 < 시작일. daysToStart는 시작까지 남은 날
 * - open: 시작일 ≤ 오늘 ≤ 인정 마지막 접수일. daysLeft는 그날까지 남은 날(always면 null)
 * - closed: 인정 마지막 접수일이 지남(시작 후 MAX_PERIOD_DAYS가 지난 상시 공고 포함)
 * - unknown: 시작일·마감일이 모두 없음
 * 시작일이 없으면 마감일만으로 판단한다(상한은 시작일이 있어야 걸 수 있다).
 */
export function receiptState(startValue, endValue, todayValue) {
  const start = day(startValue), end = day(endValue), today = day(todayValue);
  const always = isAlwaysOpenEnd(end);
  if (!today || (!start && !end)) return { state: 'unknown', daysToStart: null, daysLeft: null, always };
  if (start && start > today) return { state: 'upcoming', daysToStart: between(today, start), daysLeft: null, always };
  const last = receiptLastDay(start, end);
  if (last < today) return { state: 'closed', daysToStart: null, daysLeft: null, always };
  return { state: 'open', daysToStart: null, daysLeft: always ? null : between(today, last), always };
}

/** 접수 중 문구: 상시 접수 / 오늘 마감 / 접수 중 · D-n. 접수 중이 아니면 null. */
export function openReceiptText(status) {
  if (status?.state !== 'open') return null;
  if (status.always) return '상시 접수';
  if (status.daysLeft === 0) return '오늘 마감';
  return status.daysLeft > 0 ? `접수 중 · D-${status.daysLeft}` : '접수 중';
}
