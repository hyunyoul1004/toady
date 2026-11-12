import test from 'node:test';
import assert from 'node:assert/strict';
import { evaluate, regionFit, rank1Months, shortSido, monthsFromAccountIndex, profileComplete, rankFeed } from '../src/features/match/eligibilityRules.js';
import { normalizeProfile } from '../src/features/match/eligibilityProfile.js';
import { fundPlan, acquisitionTax } from '../src/features/match/fundRules.js';

const today = '2026-11-13';
const base = { sido: '서울', birthYear: 1990, householder: true, homeless: true, everOwned: false, marriage: 'married', marriedWithin7: true, children: 1, newborn: false, parents: false, accountMonths: 60, won5y: false, usedSpecial: false };
const apt = (patch = {}) => ({ id: 'apt/1/1', type: 'apt', region: '서울', status: 'OPEN', start: '2026-11-10', national: false, speculative: false, adjusted: false,
  rank1Gg: null, rank1Etc: null, special: { newlywed: 10, firstLife: 5, multiChild: 3, oldParents: 1, youth: 0, newborn: 4, institution: 2 }, ...patch });
const track = (result, key) => result.tracks.find(t => t.key === key);

test('지역명·통장 기간 변환', () => {
  assert.equal(shortSido('경기도'), '경기');
  assert.equal(shortSido('전북특별자치도 전주시'), '전북');
  assert.equal(shortSido('충청남도'), '충남');
  assert.equal(monthsFromAccountIndex(0), 0);
  assert.equal(monthsFromAccountIndex(1), 6);
  assert.equal(monthsFromAccountIndex(3), 24);
  assert.equal(rank1Months(apt()), 12);
  assert.equal(rank1Months(apt({ region: '부산' })), 6);
  assert.equal(rank1Months(apt({ adjusted: true })), 24);
});

test('필수 입력이 없으면 판정하지 않는다', () => {
  assert.equal(profileComplete({ ...base, sido: null }), false);
  assert.equal(evaluate({ ...base, sido: null }, apt(), today).best, 'unknown');
  assert.equal(profileComplete(base), true);
});

test('거주지역: 같은 시·도, 수도권 기타지역, 지방 공고', () => {
  assert.equal(regionFit(base, apt()).status, 'ok');
  assert.equal(regionFit({ ...base, sido: '인천' }, apt({ region: '경기', rank1Etc: '2026-11-12' })).status, 'ok');
  assert.equal(regionFit({ ...base, sido: '인천' }, apt({ region: '경기' })).status, 'no');
  // 경기 거주자 접수(rank1Gg)만 있는 경기 공고는 서울·인천 거주자가 신청할 수 없다.
  assert.equal(regionFit({ ...base, sido: '서울' }, apt({ region: '경기', rank1Gg: '2026-11-12' })).status, 'no');
  assert.equal(regionFit({ ...base, sido: '경기' }, apt({ region: '경기', rank1Gg: '2026-11-12' })).status, 'ok');
  assert.equal(regionFit(base, apt({ region: '부산', rank1Etc: '2026-11-12' })).status, 'check');
  assert.equal(evaluate(base, apt({ region: '부산' }), today).best, 'no');
});

test('일반공급 1순위·2순위와 규제지역 요건', () => {
  assert.equal(track(evaluate(base, apt(), today), 'general').label, '일반공급 1순위');
  const short = evaluate({ ...base, accountMonths: 6 }, apt(), today);
  assert.equal(track(short, 'general').label, '일반공급 2순위');
  assert.equal(track(short, 'general').status, 'check');
  const tenant = evaluate({ ...base, householder: false }, apt({ speculative: true }), today);
  assert.equal(track(tenant, 'general').label, '일반공급 2순위');
  assert.equal(track(evaluate({ ...base, homeless: false, homes: 1 }, apt({ national: true }), today), 'general').status, 'no');
  assert.equal(track(evaluate({ ...base, accountMonths: -1 }, apt(), today), 'general').status, 'no');
});

test('특별공급 유형별 요건', () => {
  const result = evaluate(base, apt(), today);
  assert.equal(track(result, 'newlywed').status, 'ok');
  assert.equal(track(result, 'firstLife').status, 'ok');
  assert.equal(track(result, 'multiChild').status, 'no');
  assert.equal(track(result, 'newborn').status, 'no');
  assert.equal(track(result, 'oldParents').status, 'no');
  assert.equal(track(result, 'youth'), undefined, '물량이 없는 유형은 보여주지 않는다');
  assert.equal(track(result, 'institution').status, 'info');
  assert.match(result.headline, /신혼부부·생애최초/);
  const owner = evaluate({ ...base, homeless: false, homes: 1 }, apt(), today);
  assert.ok(owner.tracks.filter(t => !['general', 'institution'].includes(t.key)).every(t => t.status === 'no'));
  assert.equal(track(evaluate({ ...base, everOwned: true }, apt(), today), 'firstLife').status, 'no');
  assert.equal(track(evaluate({ ...base, usedSpecial: true }, apt(), today), 'newlywed').status, 'no');
  const single = evaluate({ ...base, marriage: 'single', children: 0 }, apt(), today);
  assert.ok(track(single, 'firstLife').checks.some(text => text.includes('1인 가구')));
});

test('청년 특별공급: 본인 무주택 기준, 출생 연도 미입력·나이 경계는 확인 필요', () => {
  const youthApt = apt({ national: true, special: { youth: 5 } });
  const single = { ...base, marriage: 'single', children: 0, birthYear: 1995 };
  assert.equal(track(evaluate(single, youthApt, today), 'youth').status, 'ok');
  assert.equal(track(evaluate({ ...single, homeless: false, homes: 1 }, youthApt, today), 'youth').status, 'ok', '부모님 집에 사는 청년도 신청 대상');
  assert.equal(track(evaluate({ ...single, birthYear: null }, youthApt, today), 'youth').status, 'check');
  assert.equal(track(evaluate({ ...single, birthYear: 1986 }, youthApt, today), 'youth').status, 'check', '올해 40세가 되는 해는 생일 전이면 만 39세');
  assert.equal(track(evaluate({ ...single, birthYear: 1980 }, youthApt, today), 'youth').status, 'no');
  assert.equal(track(evaluate({ ...single, marriage: 'married' }, youthApt, today), 'youth').status, 'no');
});

test('무순위·임의공급', () => {
  assert.equal(evaluate(base, { type: 'remaining', region: '서울' }, today).best, 'ok');
  assert.equal(evaluate(base, { type: 'remaining', region: '경기' }, today).best, 'check');
  assert.equal(evaluate({ ...base, homeless: false, homes: 1 }, { type: 'remaining', region: '서울' }, today).best, 'no');
  assert.equal(evaluate({ ...base, homeless: false, homes: 1 }, { type: 'optional', region: '부산' }, today).best, 'ok');
});

test('피드는 가능성 높은 공고부터', () => {
  const feed = rankFeed([apt({ id: 'a', region: '부산' }), apt({ id: 'b' })], base, today);
  assert.deepEqual(feed.map(entry => entry.item.id), ['b', 'a']);
});

test('저장값 검증', () => {
  const profile = normalizeProfile({ sido: '서울', homeless: 'yes', accountMonths: '24', hack: 1, children: 99 });
  assert.equal(profile.sido, '서울');
  assert.equal(profile.homeless, null);
  assert.equal(profile.accountMonths, 24);
  assert.equal(profile.children, null);
  assert.equal('hack' in profile, false);
});

test('자금 계획: 10/60/30, 중도금 대출, 잔금 대출, 취득세', () => {
  assert.equal(acquisitionTax(50000), 550);
  assert.equal(acquisitionTax(100000), 3300);
  assert.equal(acquisitionTax(75000), Math.round(75000 * 0.02 * 1.1));
  const plan = fundPlan({ price: 50000, depositRate: 0.1, midLoan: true, ltv: 50, cash: 10000 });
  assert.equal(plan.deposit, 5000);
  assert.equal(plan.middle, 30000);
  assert.equal(plan.balance, 15000);
  assert.equal(plan.mortgage, 25000);
  assert.equal(plan.moveInCash, 15000 + 30000 - 25000 + 550);
  assert.equal(plan.ownTotal, 5000 + plan.moveInCash);
  assert.equal(plan.shortage, plan.ownTotal - 10000);
  const noLoan = fundPlan({ price: 50000, midLoan: false, ltv: 0, cash: 0 });
  assert.equal(noLoan.middleCash, 30000);
  assert.equal(noLoan.ownTotal, 50000 + 550);
  assert.equal(fundPlan({ price: 0 }), null);
});

test('분양가 vs 실거래 행: 면적별 최고 공급금액과 중간값 차이', async () => {
  const { gapRows } = await import('../src/features/match/priceGapRules.js');
  const units = [{ area: 59.9, amountManwon: 41110 }, { area: 59.8, amountManwon: 41042 }, { area: 84.89, amountManwon: 52000 }];
  const rows = gapRows(units, [{ area: 60, scope: 'DONG', count: 5, median: 50000, newMedian: 61000, newCount: 1 }, { area: 85, scope: 'DISTRICT', count: 0, median: 0 }]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].supply, 41110);
  assert.equal(rows[0].diff, 50000 - 41110);
  assert.equal(rows[0].newMedian, 0, '준공 10년 이내 거래가 2건 미만이면 따로 보여주지 않는다');
});
