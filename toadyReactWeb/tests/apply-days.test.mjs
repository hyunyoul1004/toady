import test from 'node:test';
import assert from 'node:assert/strict';
import { applyDays, applySchedules, canApplyOn, scheduleRows, appliesToday, periodDays } from '../src/features/announcement/applyDays.js';
import { groupCalendarSchedules, groupsByDate, describeGroup, hasOngoing } from '../src/views/main/calendar/calendarGroup.js';
import { summaryNumbers } from '../src/features/planner/todaySummaryRules.js';
import { weeklyStatus, byStatus } from '../src/features/explore/exploreRules.js';

// 서버 ApplyDaysTest와 같은 2026-11-19 실제 사례. 접수 기간 중인 공고는 5건이지만 그날 신청할 수 있는 공고는 4건이다.
const TODAY = '2026-11-19';
const apt = (id, rceptBgnde, rceptEndde, special, rank1, rank2) => ({
  type: 'apt', houseManageNo: id, pblancNo: id, houseNm: id, rceptBgnde, rceptEndde,
  spsplyRceptBgnde: special?.[0] ?? null, spsplyRceptEndde: special?.[1] ?? null,
  gnrlRnk1CrspareaRcptde: rank1[0], gnrlRnk1CrspareaEndde: rank1[0], gnrlRnk1EtcGgRcptde: rank1[1], gnrlRnk1EtcGgEndde: rank1[1],
  gnrlRnk1EtcAreaRcptde: rank1[2], gnrlRnk1EtcAreaEndde: rank1[2],
  gnrlRnk2CrspareaRcptde: rank2[0], gnrlRnk2CrspareaEndde: rank2[0], gnrlRnk2EtcGgRcptde: rank2[1], gnrlRnk2EtcGgEndde: rank2[1],
  gnrlRnk2EtcAreaRcptde: rank2[2], gnrlRnk2EtcAreaEndde: rank2[2]
});
const other = (type, id, rceptBgnde, rceptEndde) => ({ type, houseManageNo: id, pblancNo: id, houseNm: id, rceptBgnde, rceptEndde });
const SAMPLE = [
  apt('JUNGHWA', '2026-11-16', '2026-11-19', ['2026-11-16', '2026-11-16'], ['2026-11-17', '2026-11-18', '2026-11-18'], ['2026-11-19', '2026-11-19', '2026-11-19']),
  apt('SINGIL', '2026-11-16', '2026-11-24', null, ['2026-11-16', '2026-11-16', '2026-11-16'], ['2026-11-24', '2026-11-24', '2026-11-24']),
  other('remaining', 'YAMOK', '2026-11-17', '2026-11-23'),
  other('remaining', 'ESTERA', '2026-11-18', '2026-11-19'),
  other('optional', 'RIATZ', '2026-11-18', '2026-11-20')
];
const TYPE_CODE = { apt: '01', remaining: '06', optional: '07' };
/** announcementApi.getAnnouncements와 같은 모양의 달력 행 */
const calendarRows = (items) => items.flatMap((item) => scheduleRows(item, TYPE_CODE[item.type]).map((row) => ({
  ...item, ...row, displayRceptSe: row.rceptSe, subscriptSeq: `${item.type}/${item.houseManageNo}/${item.pblancNo}`
})));

test('신청 가능일 규칙: APT는 특별공급·순위 접수일, 그 밖의 유형은 접수 기간 내내', () => {
  assert.deepEqual(applyDays(SAMPLE[0]), ['2026-11-16', '2026-11-17', '2026-11-18', '2026-11-19']);
  assert.deepEqual(applyDays(SAMPLE[1]), ['2026-11-16', '2026-11-24'], '접수 기간 중이라도 순위 접수일이 아니면 신청일이 아니다');
  assert.deepEqual(applyDays(SAMPLE[2]), ['2026-11-17', '2026-11-18', '2026-11-19', '2026-11-20', '2026-11-21', '2026-11-22', '2026-11-23'], '주말도 날짜 그대로 센다');
  assert.deepEqual(SAMPLE.filter((item) => canApplyOn(item, TODAY)).map((item) => item.houseManageNo), ['JUNGHWA', 'YAMOK', 'ESTERA', 'RIATZ']);
  // 특별공급 컬럼이 없는 예전 응답: 접수 시작일이 첫 1순위보다 앞서면 그날을 특별공급일로 본다.
  const legacy = apt('A', '2026-11-09', '2026-11-12', null, ['2026-11-10', null, '2026-11-11'], ['2026-11-12', null, null]);
  assert.deepEqual(applySchedules(legacy).map((row) => `${row.stage}:${row.inDate}:${row.areaLabel}`),
    ['01:2026-11-09:', '02:2026-11-10:해당지역', '02:2026-11-11:기타지역', '03:2026-11-12:']);
  // 순위 날짜가 없으면 접수 기간 전체(첫날 다음부터 ongoing)
  const periodOnly = apt('B', '20261117', '20261119', null, [null, null, null], [null, null, null]);
  assert.deepEqual(applySchedules(periodOnly).map((row) => [row.stage, row.inDate, row.ongoing]),
    [['02', '2026-11-17', false], ['02', '2026-11-18', true], ['02', '2026-11-19', true]]);
  assert.deepEqual(periodDays('2026-11-20', '2026-11-01'), ['2026-11-20'], '종료일이 앞서면 시작일 하루');
  assert.deepEqual(periodDays(null, '2026-11-20'), []);
  assert.deepEqual(periodDays('2026-11-30', '2026-12-02'), ['2026-11-30', '2026-12-01', '2026-12-02'], '월 경계를 넘는 기간');
});

test('달력 오늘 칸 건수와 오늘 접수 숫자가 같은 판정을 쓴다(4건)', () => {
  const byDate = groupsByDate(groupCalendarSchedules(calendarRows(SAMPLE)));
  const todayCell = byDate[TODAY];
  assert.equal(todayCell.length, 4, '같은 공고·같은 날은 한 줄');
  assert.deepEqual(todayCell.map((group) => group.subscriptSeq).sort(), ['apt/JUNGHWA/JUNGHWA', 'optional/RIATZ/RIATZ', 'remaining/ESTERA/ESTERA', 'remaining/YAMOK/YAMOK']);
  // 서버 weekly-summary가 applyToday를 주면 그 값으로 센다.
  const weekly = { current: SAMPLE.map((item) => ({ id: item.houseManageNo, status: 'OPEN', applyToday: canApplyOn(item, TODAY) })) };
  assert.equal(summaryNumbers({ weekly }).openToday, todayCell.length);
  // 아직 applyToday를 주지 않는 서버면 예전처럼 접수 기간 중(OPEN)으로 센다.
  assert.equal(summaryNumbers({ weekly: { current: SAMPLE.map(() => ({ status: 'OPEN' })) } }).openToday, 5);
  assert.equal(appliesToday({ status: 'OPEN', applyToday: false }), false);
});

test('기간 공고는 시작일 다음 날부터 연한 접수 중 줄로 매일 보인다', () => {
  const groups = groupCalendarSchedules(calendarRows(SAMPLE));
  const yamok = groups.filter((group) => group.subscriptSeq === 'remaining/YAMOK/YAMOK');
  assert.deepEqual(yamok.map((group) => [group.date, group.ongoing]), [
    ['2026-11-17', false], ['2026-11-18', true], ['2026-11-19', true], ['2026-11-20', true], ['2026-11-21', true], ['2026-11-22', true], ['2026-11-23', true]
  ]);
  assert.ok(hasOngoing(groups));
  assert.equal(describeGroup(yamok[2]), 'YAMOK · 무순위 접수 기간 중(~11.23 마감)');
  // 같은 날 시작 줄이 접수 중 줄보다 먼저 쌓인다.
  const day18 = groupsByDate(groups)['2026-11-18'].map((group) => [group.subscriptSeq.split('/')[1], group.ongoing]);
  assert.deepEqual(day18.at(-1), ['YAMOK', true]);
  // APT 특별공급·순위 접수일 줄은 접수 중 줄이 아니다.
  assert.ok(groups.filter((group) => group.subscriptSeq.startsWith('apt/')).every((group) => !group.ongoing));
  // 안산 신길처럼 순위 접수일 사이(11/19)에는 칸에 나오지 않는다.
  assert.ok(!groupsByDate(groups)[TODAY].some((group) => group.subscriptSeq === 'apt/SINGIL/SINGIL'));
});

test('이번 주 청약 카드: 접수 기간 중이어도 오늘 신청일이 아니면 따로 표시하고 다음 신청일을 보여 준다', () => {
  assert.deepEqual(weeklyStatus({ status: 'OPEN', applyToday: true }), { key: 'open', text: '오늘 신청', note: '' });
  assert.deepEqual(weeklyStatus({ status: 'OPEN', applyToday: false, nextApplyDay: '2026-11-24' }), { key: 'between', text: '접수 기간 중', note: '다음 신청일 11.24 (화)' });
  assert.equal(weeklyStatus({ status: 'OPEN' }).text, '오늘 신청', '예전 서버 응답은 접수 중을 오늘 신청으로 본다');
  assert.equal(weeklyStatus({ status: 'UPCOMING' }).text, '접수 예정');
  const sorted = byStatus([{ id: 'a', status: 'UPCOMING', start: '1' }, { id: 'b', status: 'OPEN', applyToday: false, start: '1' }, { id: 'c', status: 'OPEN', applyToday: true, start: '2' }]);
  assert.deepEqual(sorted.map((item) => item.id), ['c', 'b', 'a']);
});

test('여러 날 특별공급의 접수 중 줄은 특별공급 마감일을 보여 준다', () => {
  const item = apt('SP', '2026-11-17', '2026-11-20', ['2026-11-17', '2026-11-18'], ['2026-11-19', null, null], ['2026-11-20', null, null]);
  const groups = groupCalendarSchedules(calendarRows([item]));
  const day18 = groups.find((group) => group.date === '2026-11-18');
  assert.equal(day18.ongoing, true);
  assert.equal(day18.endDate, '2026-11-18');
  assert.equal(describeGroup(day18), 'SP · 특별공급 접수 기간 중(~11.18 마감)');
});

