import test from 'node:test';
import assert from 'node:assert/strict';
import { formatManwon } from '../src/utils/money.util.js';
import { formatCompetitionRate } from '../src/utils/rate.util.js';
import { formatManwon as researchManwon } from '../src/features/planner/researchUtils.js';
import { shortPrice, fullPrice, rangePrice } from '../src/features/announcement/location/priceMapRules.js';
import { roughEok } from '../src/features/match/priceGapRules.js';
import { formatRate as stageRate } from '../src/features/planner/stageRules.js';
import { formatRate as pollRate } from '../src/features/story/pollRules.js';
import { formatRate as tableRate } from '../src/views/main/subscriptRate/rateFormat.js';

test('금액: 표·요약은 전체 표기, 지도·칩은 짧은 표기', () => {
  assert.equal(formatManwon(35000), '3억 5,000만원');
  assert.equal(formatManwon(120000), '12억원');
  assert.equal(formatManwon(9800), '9,800만원');
  assert.equal(formatManwon(12345678), '1,234억 5,678만원');
  assert.equal(formatManwon(35000, { compact: true }), '3.5억');
  assert.equal(formatManwon(8500, { compact: true }), '8,500만');
  assert.equal(formatManwon(35000, { won: false }), '3억 5,000만');
  assert.equal(formatManwon(0), '—');
  assert.equal(formatManwon(null, { empty: '미제공' }), '미제공');
  assert.equal(formatManwon('abc'), '—');
});

test('금액: 화면별 함수가 같은 규칙을 쓴다', () => {
  assert.equal(researchManwon(35000), fullPrice(35000));
  assert.equal(researchManwon(0), '금액 미제공');
  assert.equal(shortPrice(35000), '3.5억');
  assert.equal(roughEok(35000), '3.5억');
  assert.equal(rangePrice(35000), '3억 5,000만');
  assert.equal(rangePrice(0), '0');
  assert.equal(roughEok(0), '0원');
});

test('경쟁률: 100 이상 정수, 미만은 소수 둘째 자리까지(끝의 0 제거)', () => {
  assert.equal(formatCompetitionRate(12.345), '12.35 : 1');
  assert.equal(formatCompetitionRate(21), '21 : 1');
  assert.equal(formatCompetitionRate('3.50'), '3.5 : 1');
  assert.equal(formatCompetitionRate(99.999), '100 : 1');
  assert.equal(formatCompetitionRate(1249.5), '1,250 : 1');
  assert.equal(formatCompetitionRate(null), '');
  for (const value of [0.5, 7.25, 30, 249.75, 1234.4]) {
    assert.equal(stageRate(value), pollRate(value));
    assert.equal(stageRate(value), tableRate(String(value)));
  }
});
