import test from 'node:test';
import assert from 'node:assert/strict';
import { HOUSING_OPTIONS, DEPENDENT_OPTIONS, ACCOUNT_OPTIONS, scoreOf, normalizeSelection, publicRange, compareToRange, selectionFromParams, selectionToParams } from '../src/features/score/scoreRules.js';
import { storyStage, splitTag, withTag, STAGE_PROMPTS } from '../src/features/story/storyRules.js';
import { pollView, formatRate } from '../src/features/story/pollRules.js';
import { tradeComparison } from '../src/features/planner/researchUtils.js';

test('가점표는 주택공급 규칙의 항목별 만점과 구간을 따른다', () => {
  assert.equal(HOUSING_OPTIONS.length, 17); assert.equal(HOUSING_OPTIONS.at(-1).points, 32); assert.equal(HOUSING_OPTIONS[1].points, 2); assert.equal(HOUSING_OPTIONS[2].points, 4);
  assert.equal(DEPENDENT_OPTIONS[0].points, 5); assert.equal(DEPENDENT_OPTIONS[6].points, 35);
  assert.equal(ACCOUNT_OPTIONS[0].points, 1); assert.equal(ACCOUNT_OPTIONS[2].points, 3); assert.equal(ACCOUNT_OPTIONS.at(-1).points, 17);
  assert.deepEqual(scoreOf({ housing: 16, dependents: 6, account: 16 }), { housing: 32, dependents: 35, account: 17, total: 84 });
  assert.equal(scoreOf({ housing: 0, dependents: 0, account: 0 }).total, 6);
  assert.equal(scoreOf({ housing: 17, dependents: 0, account: 0 }), null, '범위를 벗어난 값은 계산하지 않는다');
  assert.equal(normalizeSelection({ housing: '3', dependents: 2 }), null, '빈 항목이 있으면 계산하지 않는다');
});

test('공개 당첨가점 비교는 지난 결과 기준 문구만 쓰고 미공개 값은 무시한다', () => {
  const range = publicRange([{ lwetScore: '52', topScore: '69' }, { lwetScore: '47', topScore: '74' }, { lwetScore: '-', topScore: '0' }, { lwetScore: '90', topScore: '' }]);
  assert.deepEqual(range, { low: 47, high: 74 });
  assert.equal(publicRange([{ lwetScore: '', topScore: '-' }]), null);
  assert.equal(compareToRange(80, range).status, 'above');
  assert.equal(compareToRange(50, range).text, '공개 최저 당첨가점보다 3점 높아요');
  assert.equal(compareToRange(47, range).text, '공개 최저 당첨가점과 같아요');
  assert.deepEqual([compareToRange(40, range).status, compareToRange(40, range).gap], ['below', 7]);
  assert.equal(compareToRange(undefined, range).status, 'unknown');
  for (const total of [10, 50, 80]) assert.doesNotMatch(compareToRange(total, range).text, /당첨(돼|될|가능|확률)/);
});

test('가점 공유 주소는 검증된 순번만 주고받는다', () => {
  const params = selectionToParams({ housing: 5, dependents: 2, account: 9 });
  assert.equal(params.toString(), 'h=5&d=2&a=9');
  assert.deepEqual(selectionFromParams(params), { housing: 5, dependents: 2, account: 9 });
  assert.equal(selectionFromParams(new URLSearchParams('h=99&d=2&a=1')), null);
  assert.equal(selectionToParams({}).toString(), '');
  assert.equal(selectionFromParams(new URLSearchParams('')), null, '값이 없는 주소를 0점 선택으로 읽지 않는다');
  assert.equal(normalizeSelection({ housing: null, dependents: null, account: null }), null);
});

test('이야기 질문은 공고 일정 단계에 맞추고 말머리를 본문과 분리한다', () => {
  const dates = { startDate: '2026-11-20', endDate: '2026-11-22', winnerDate: '2026-11-28' };
  assert.equal(storyStage(dates, '2026-11-12'), 'before');
  assert.equal(storyStage(dates, '2026-11-21'), 'open');
  assert.equal(storyStage(dates, '2026-11-25'), 'waiting');
  assert.equal(storyStage(dates, '2026-11-28'), 'after');
  assert.equal(storyStage({ startDate: '2026-11-01', endDate: '2026-11-02' }, '2026-11-12'), 'after', '발표일이 없으면 마감 후를 이후 단계로 본다');
  assert.ok(Object.values(STAGE_PROMPTS).every(list => list.length >= 3 && new Set(list.map(p => p.tag)).size === list.length));
  assert.deepEqual(splitTag('[모델하우스] 평면이 좋아요'), { tag: '모델하우스', body: '평면이 좋아요' });
  assert.deepEqual(splitTag('그냥 글'), { tag: null, body: '그냥 글' });
  assert.equal(withTag('[분양가] 비싸요', '가점'), '[가점] 비싸요');
  assert.equal(withTag('[분양가] 비싸요', null), '비싸요');
  assert.equal(splitTag('[이건 열두 글자를 훌쩍 넘는 말머리] 본문').tag, null, '긴 대괄호는 말머리로 보지 않는다');
});

test('경쟁률 투표 결과는 비율·내 선택·실제 구간을 함께 보여준다', () => {
  const view = pollView({ open: false, labels: ['a', 'b', 'c'], counts: [1, 3, 0], total: 4, myChoice: 1, actualRate: 7.5, actualChoice: 1, closesOn: '2026-11-22' });
  assert.deepEqual(view.options.map(o => o.percent), [25, 75, 0]);
  assert.equal(view.options[1].mine && view.options[1].actual, true);
  assert.equal(view.hitPercent, 75); assert.equal(view.voted, true);
  assert.equal(pollView(null), null);
  assert.equal(pollView({ open: true, labels: ['a'], counts: [0], total: 0, myChoice: null, actualChoice: null }).hitPercent, null);
  assert.equal(formatRate(1234.5), '1,234.5 : 1');
});

test('실거래 비교는 같은 면적 주택형과 화면의 실거래만 쓴다', () => {
  const units = [{ area: 84.97, amountManwon: 90000 }, { area: 84.5, amountManwon: 95000 }, { area: 59.9, amountManwon: 70000 }];
  const trades = [{ amountManwon: 80000 }, { amountManwon: 100000 }, { amountManwon: 85000 }, { amountManwon: 'x' }];
  assert.deepEqual(tradeComparison(units, trades, '85'), { area: 85, supplyMin: 90000, supplyMax: 95000, count: 3, min: 80000, max: 100000, median: 85000 });
  assert.equal(tradeComparison(units, trades, ''), null);
  assert.equal(tradeComparison(units, [], '85'), null);
  assert.equal(tradeComparison(units, trades, '74'), null, '같은 면적 주택형이 없으면 비교하지 않는다');
  assert.equal(tradeComparison(units, [{ amountManwon: 1 }, { amountManwon: 3 }], '60').median, 2);
});

import { housingFromDates, accountFromDate, fullMonths, addYears, HOUSING_OPTIONS as H, ACCOUNT_OPTIONS as A } from '../src/features/score/scoreRules.js';
test('날짜로 무주택 기간과 통장 가입기간 구간을 정한다', () => {
  const today = '2026-11-12';
  assert.equal(fullMonths('2019-03-02', today), 92);
  assert.equal(addYears('1996-02-29', 30), '2026-03-01');
  // 미혼: 만 30세부터
  let r = housingFromDates({ birth: '1989-03-02', married: false, owned: false }, today);
  assert.equal(r.start, '2019-03-02'); assert.equal(H[r.index].label, '7년 이상 ~ 8년 미만'); assert.equal(H[r.index].points, 16);
  // 30세 전 혼인: 혼인신고일부터
  r = housingFromDates({ birth: '1995-01-01', married: true, marriageDate: '2021-05-01', owned: false }, today);
  assert.equal(r.start, '2021-05-01'); assert.equal(H[r.index].points, 12);
  // 30세 이후 혼인: 만 30세부터
  r = housingFromDates({ birth: '1985-01-01', married: true, marriageDate: '2018-01-01', owned: false }, today);
  assert.equal(r.start, '2015-01-01');
  // 집을 처분한 날이 더 늦으면 그날부터
  r = housingFromDates({ birth: '1985-01-01', married: false, owned: true, soldDate: '2026-02-01' }, today);
  assert.equal(r.start, '2026-02-01'); assert.equal(H[r.index].label, '1년 미만');
  // 만 30세 미만 미혼
  r = housingFromDates({ birth: '2000-01-01', married: false, owned: false }, today);
  assert.equal(r.index, 0); assert.equal(H[r.index].points, 0);
  // 15년 이상
  assert.equal(H[housingFromDates({ birth: '1960-01-01', married: false, owned: false }, today).index].points, 32);
  assert.ok(housingFromDates({ birth: '', married: false }, today).error);
  assert.ok(housingFromDates({ birth: '1990-01-01', married: true, marriageDate: '' }, today).error);
  assert.ok(housingFromDates({ birth: '2027-01-01' }, today).error, '미래 날짜는 받지 않는다');
  assert.equal(A[accountFromDate('2026-08-01', today).index].label, '6개월 미만');
  assert.equal(A[accountFromDate('2026-03-01', today).index].label, '6개월 이상 ~ 1년 미만');
  assert.equal(A[accountFromDate('2016-11-12', today).index].points, 12, '정확히 10년이면 10년 이상 구간');
  assert.equal(A[accountFromDate('2000-01-01', today).index].points, 17);
  assert.ok(accountFromDate('2026-02-30', today).error, '없는 날짜는 받지 않는다');
});
