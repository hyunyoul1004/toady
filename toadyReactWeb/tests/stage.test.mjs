import test from 'node:test';
import assert from 'node:assert/strict';
import { nextStep, resultFacts, normalizeStage, formatRate } from '../src/features/planner/stageRules.js';

const today = '2026-11-13';
const summary = { startDate: '2026-11-16', endDate: '2026-11-18', winnerDate: '2026-11-25', events: [{ label: '계약 시작', date: '2026-12-08' }] };

test('단계 값 검증', () => {
  assert.equal(normalizeStage(3), 3);
  assert.equal(normalizeStage('9'), 0);
  assert.equal(normalizeStage(undefined), 0);
  assert.equal(formatRate(12.345), '12.35 : 1');
  assert.equal(formatRate(1234.4), '1,234 : 1');
});

test('관심·신청 예정: 접수 전·중·후 안내', () => {
  const before = nextStep(0, summary, today);
  assert.match(before.text, /접수 D-3/);
  assert.equal(before.action.stage, 1);
  const open = nextStep(1, summary, '2026-11-17');
  assert.equal(open.tone, 'urgent');
  assert.equal(open.action.href, 'https://www.applyhome.co.kr');
  assert.match(nextStep(1, summary, '2026-11-18').text, /오늘 접수 마감/);
  assert.equal(nextStep(1, summary, '2026-11-19').action.stage, 2);
});

test('신청 완료: 발표 전에는 D-day, 경쟁률이 나오면 경쟁률 탭, 발표 뒤에는 결과 확인', () => {
  assert.match(nextStep(2, summary, '2026-11-20').text, /당첨자 발표 D-5 \(11.25\)/);
  const rate = nextStep(2, summary, '2026-11-20', { ratePublished: true });
  assert.equal(rate.action.tab, 'rate');
  assert.equal(nextStep(2, summary, '2026-11-25').tone, 'urgent');
});

test('당첨·미당첨·계약 완료', () => {
  assert.match(nextStep(3, summary, '2026-12-01').text, /계약 D-7 \(12.8\)/);
  assert.equal(nextStep(3, summary, '2026-12-09').action.stage, 5);
  assert.equal(nextStep(4, summary, '2026-12-01').action.to, '/match');
  assert.equal(nextStep(5, summary, '2026-12-01').tone, 'done');
});

test('결과 요약: 경쟁률·당첨가점과 내 가점 비교', () => {
  assert.deepEqual(resultFacts(null, 50), []);
  assert.deepEqual(resultFacts({ ratePublished: true, topRate: null }), ['1순위 미달']);
  const facts = resultFacts({ ratePublished: true, topRate: 12.5, scorePublished: true, scoreLow: '48', scoreHigh: '69' }, 45);
  assert.equal(facts[0], '1순위 최고 경쟁률 12.5 : 1');
  assert.equal(facts[1], '당첨가점 48~69점 · 내 가점 45점(최저보다 3점 낮음)');
  assert.match(resultFacts({ scorePublished: true, scoreLow: '48', scoreHigh: '' }, 50)[0], /최저 이상/);
});

test('가점이 오르는 다음 날짜와 캘린더 파일', async () => {
  const { nextRise, housingFromDates, accountFromDate, riseCalendarText, addMonths } = await import('../src/features/score/scoreRules.js');
  const today = '2026-11-13';
  assert.equal(addMonths('2026-08-31', 6), '2027-03-01');
  assert.equal(addMonths('2026-07-15', 6), '2027-01-15');
  // 상승일 당일에 실제로 구간이 오른다.
  assert.equal(accountFromDate('2026-08-31', '2027-03-01').index, 1);
  assert.equal(accountFromDate('2026-08-31', '2027-02-28').index, 0);
  // 통장: 가입 4개월 → 6개월 되는 날 +1점, 3년 2개월 → 4년 되는 날 +1점
  const short = { joined: '2026-07-01' };
  assert.deepEqual(nextRise('account', short, accountFromDate(short.joined, today), today), { date: '2027-01-01', plus: 1, label: '청약통장 가입기간' });
  const long = { joined: '2023-09-10' };
  assert.equal(nextRise('account', long, accountFromDate(long.joined, today), today).date, '2027-09-10');
  // 무주택: 만 30세 미만 미혼이면 30세 되는 날 +2점, 이미 기간이 있으면 다음 기념일 +2점
  const young = { birth: '2000-03-01', married: false };
  assert.deepEqual(nextRise('housing', young, housingFromDates(young, today), today), { date: '2030-03-01', plus: 2, label: '무주택 기간' });
  const adult = { birth: '1990-05-20', married: false };
  assert.equal(nextRise('housing', adult, housingFromDates(adult, today), today).date, '2027-05-20');
  // 15년 이상(최고 구간)이면 더 오르지 않는다.
  const max = { birth: '1960-01-01', married: false };
  assert.equal(nextRise('housing', max, housingFromDates(max, today), today), null);
  const ics = riseCalendarText({ date: '2027-01-01', plus: 1, label: '청약통장 가입기간' });
  assert.match(ics, /DTSTART;VALUE=DATE:20270101\r\n/);
  assert.match(ics, /SUMMARY:청약 가점 \+1점 \(청약통장 가입기간\)/);
  assert.match(ics, /TRIGGER:PT9H/);
});
