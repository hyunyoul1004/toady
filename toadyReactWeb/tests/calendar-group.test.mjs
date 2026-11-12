import test from 'node:test';
import assert from 'node:assert/strict';
import {
  groupCalendarSchedules, groupsByDate, usedRceptSes, describeGroup, normalizeScheduleDate, rceptSeMeta
} from '../src/views/main/calendar/calendarGroup.js';

const row = (subscriptSeq, houseNm, rceptSe, inDate, extra = {}) => ({ subscriptSeq, houseNm, rceptSe, displayRceptSe: rceptSe, inDate, ...extra });

test('같은 날 같은 공고의 여러 단계 행은 한 줄로 묶이고 단계 순으로 정렬된다', () => {
  const groups = groupCalendarSchedules([
    row('apt/1/1', '가단지', '03', '2026-11-12'),
    row('apt/1/1', '가단지', '02', '20261112', { areaLabel: '해당지역/경기' }),
    row('apt/1/1', '가단지', '02', '2026-11-12', { areaLabel: '기타지역' }),
    row('apt/2/2', '나단지', '01', '2026-11-12')
  ]);
  assert.equal(groups.length, 2);
  const ga = groups.find((g) => g.subscriptSeq === 'apt/1/1');
  assert.deepEqual(ga.stages.map((s) => s.rceptSe), ['02', '03']);
  assert.deepEqual(ga.stages[0].areaLabels, ['해당지역', '경기', '기타지역']);
  assert.equal(ga.primaryRceptSe, '02');
  // 특별공급이 첫 단계인 공고가 먼저 온다
  assert.equal(groups[0].subscriptSeq, 'apt/2/2');
});

test('날짜가 다른 단계는 날짜별 줄로 남고, 공고 전체 단계 흐름(allStages)을 함께 담는다', () => {
  const groups = groupCalendarSchedules([
    row('apt/1/1', '가단지', '01', '2026-11-10'),
    row('apt/1/1', '가단지', '02', '2026-11-11'),
    row('apt/1/1', '가단지', '03', '2026-11-12'),
    row('apt/1/1', '가단지', '01', '2026-11-10') // 중복 행
  ]);
  assert.deepEqual(groups.map((g) => g.date), ['2026-11-10', '2026-11-11', '2026-11-12']);
  for (const g of groups) {
    assert.deepEqual(g.allStages.map((s) => s.rceptSe), ['01', '02', '03']);
    assert.equal(g.stages.length, 1);
  }
  assert.deepEqual(groups[0].allStages[0].dates, ['2026-11-10']);
  const byDate = groupsByDate(groups);
  assert.deepEqual(Object.keys(byDate), ['2026-11-10', '2026-11-11', '2026-11-12']);
});

test('잘못된 행은 건너뛰고, 범례·설명·기본 색 정보가 일관된다', () => {
  const groups = groupCalendarSchedules([
    row('', '공고번호없음', '02', '2026-11-12'),
    row('apt/3/3', '날짜없음', '02', null),
    row('apt/4/4', '다단지', '06', '2026-11-13', { subscriptCommentCnt: 3 }),
    { subscriptSeq: 'officetell/5/5', houseNm: '라오피스텔', rceptSe: '05', inDate: '2026-11-13' },
    row('apt/6/6', '마단지', '02', '2026-02-30')
  ]);
  assert.equal(groups.length, 2);
  assert.deepEqual(usedRceptSes(groups), ['05', '06']);
  assert.equal(describeGroup(groups.find((g) => g.subscriptSeq === 'apt/4/4')), '다단지 · 무순위 · 이야기 3');
  assert.equal(groupCalendarSchedules(null).length, 0);
  assert.equal(rceptSeMeta('99').short, '공');
  assert.equal(normalizeScheduleDate('2026.01.05 10:00'), '2026-01-05');
  assert.equal(normalizeScheduleDate('2026-13-01'), null);
});

test('공급 유형 약어는 서로 겹치지 않는다', () => {
  const shorts = ['01', '02', '03', '04', '05', '06', '07', '08'].map((c) => rceptSeMeta(c).short);
  assert.equal(new Set(shorts).size, shorts.length);
});
