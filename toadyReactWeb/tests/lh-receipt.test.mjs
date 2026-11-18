import test from 'node:test';
import assert from 'node:assert/strict';
import { applyDays, applySchedules, scheduleRows } from '../src/features/announcement/applyDays.js';
import { groupCalendarSchedules, groupsByDate, describeGroup, foldOngoing, receiptTag } from '../src/views/main/calendar/calendarGroup.js';
import { receiptChannel, receiptChannelOf } from '../src/features/announcement/receiptChannel.js';
import { recruitHouseholds, supplyScale, detailSupplyScale } from '../src/features/announcement/supplyScale.js';
import { openTodayText, isWeekendDay, browseFiltered } from '../src/features/planner/todaySummaryRules.js';
import { detailImageSections } from '../src/features/announcement/export/exportData.js';

// 안산신길2 A-6블록 신혼희망타운(apt/2026820013) 실제 응답 모양: 특별공급 접수일 없음, 1순위 11.16~11.23, 2순위 11.24.
const rank = (r1, r1End, r2) => Object.fromEntries(['Crsparea', 'EtcGg', 'EtcArea'].flatMap((area) => [
  [`gnrlRnk1${area}Rcptde`, r1], [`gnrlRnk1${area}Endde`, r1End], [`gnrlRnk2${area}Rcptde`, r2], [`gnrlRnk2${area}Endde`, r2]
]));
const SINGIL_LH = {
  type: 'apt', houseManageNo: '2026820013', pblancNo: '2026820013', houseNm: '안산신길2 A-6블록 신혼희망타운',
  rceptBgnde: '2026-11-16', rceptEndde: '2026-11-24', spsplyRceptBgnde: null, spsplyRceptEndde: null,
  bsnsMbyNm: '한국토지주택공사 경기남부지역본부', hmpgAdres: 'https://apply.lh.or.kr/', ...rank('2026-11-16', '2026-11-23', '2026-11-24')
};
const calendarRows = (items) => items.flatMap((item) => scheduleRows(item, '01').map((row) => ({
  ...item, ...row, displayRceptSe: row.rceptSe, subscriptSeq: `${item.type}/${item.houseManageNo}/${item.pblancNo}`, receiptChannel: receiptChannel(item)
})));

test('여러 날 순위 접수 기간은 첫날만 순위 줄, 이튿날부터 접수 중 줄(신청일 수는 그대로)', () => {
  const rows = applySchedules(SINGIL_LH);
  assert.deepEqual(rows.map((row) => [row.stage, row.inDate.slice(5), row.ongoing, row.periodEnd.slice(5), row.areaLabel]), [
    ['02', '11-16', false, '11-23', ''], ['02', '11-17', true, '11-23', ''], ['02', '11-18', true, '11-23', ''], ['02', '11-19', true, '11-23', ''],
    ['02', '11-20', true, '11-23', ''], ['02', '11-21', true, '11-23', ''], ['02', '11-22', true, '11-23', ''], ['02', '11-23', true, '11-23', ''],
    ['03', '11-24', false, '11-24', '']
  ]);
  // 신청 가능일(오늘 접수·달력 칸 공고 수)은 예전과 같다: 1순위 기간 8일 + 2순위 1일. 서버 ApplyDays와 같은 날짜 집합.
  assert.equal(applyDays(SINGIL_LH).length, 9);
  const groups = groupCalendarSchedules(calendarRows([SINGIL_LH]));
  const day19 = groupsByDate(groups)['2026-11-19'][0];
  assert.equal(day19.ongoing, true);
  assert.equal(describeGroup(day19), '안산신길2 A-6블록 신혼희망타운 · 1순위 접수 기간 중(~11.23 마감) · LH청약플러스 접수');
  assert.equal(groupsByDate(groups)['2026-11-16'][0].ongoing, false, '첫날은 진한 1순위 줄');
  assert.equal(groupsByDate(groups)['2026-11-24'][0].ongoing, false, '2순위 첫날도 진한 줄');
});

test('지역별 순위 기간이 다르면 시작하는 지역 이름으로 보통 줄, 모두 이어지는 날은 접수 중 줄', () => {
  const item = {
    type: 'apt', houseManageNo: 'A', pblancNo: 'A', houseNm: 'A', rceptBgnde: '2026-11-16', rceptEndde: '2026-11-19',
    gnrlRnk1CrspareaRcptde: '2026-11-16', gnrlRnk1CrspareaEndde: '2026-11-18', gnrlRnk1EtcAreaRcptde: '2026-11-17', gnrlRnk1EtcAreaEndde: '2026-11-19'
  };
  assert.deepEqual(applySchedules(item).map((row) => [row.inDate.slice(8), row.ongoing, row.areaLabel, row.periodEnd.slice(8)]), [
    ['16', false, '해당지역', '18'], ['17', false, '기타지역', '19'], ['18', true, '해당지역/기타지역', '19'], ['19', true, '기타지역', '19']
  ]);
  // 하루 접수일만 있고 지역별 날짜가 같으면 지역 이름을 붙이지 않는다(예전 규칙 그대로).
  const sameDay = { type: 'apt', houseManageNo: 'B', pblancNo: 'B', ...rank('2026-11-17', '2026-11-17', '2026-11-18') };
  assert.deepEqual(applySchedules(sameDay).map((row) => [row.stage, row.ongoing, row.areaLabel]), [['02', false, ''], ['03', false, '']]);
});

test('LH 순위 기간 접수 중 줄도 접수 중 줄 접기(foldOngoing)에 들어간다', () => {
  const items = Array.from({ length: 6 }, (_, i) => ({ ...SINGIL_LH, houseManageNo: `L${i}`, pblancNo: `L${i}`, houseNm: `L${i}` }));
  const { shown, folded } = foldOngoing(groupCalendarSchedules(calendarRows(items)));
  assert.equal(folded['2026-11-18'], 3);
  assert.equal(shown.filter((group) => group.date === '2026-11-16').length, 6, '첫날 순위 줄은 접지 않는다');
});

test('LH청약플러스 접수 판정: LH 사업주체 + apply.lh.or.kr + 특별공급 접수일 없음', () => {
  assert.equal(receiptChannelOf(SINGIL_LH), 'LH');
  assert.equal(receiptChannelOf({ ...SINGIL_LH, bsnsMbyNm: '한국토지주택공사경기남부지역본부', hmpgAdres: 'apply.lh.or.kr' }), 'LH', '띄어쓰기·스킴 없음');
  // 고덕신도시 아테라(apt/2026000124): LH 사업이지만 청약홈 접수. 특별공급 접수일이 있고 홈페이지가 분양 사이트.
  assert.equal(receiptChannelOf({ ...SINGIL_LH, hmpgAdres: 'https://www.arteragd.co.kr', spsplyRceptBgnde: '2026-04-27' }), null);
  assert.equal(receiptChannelOf({ ...SINGIL_LH, spsplyRceptBgnde: '2026-11-16' }), null, '특별공급 접수일이 있으면 청약홈');
  // 삼도이동 토지임대부(apt/2026000434): 제주개발공사, 청약홈 접수
  assert.equal(receiptChannelOf({ ...SINGIL_LH, bsnsMbyNm: '제주특별자치도개발공사', hmpgAdres: 'https://samdo.jpdc.co.kr' }), null);
  assert.equal(receiptChannelOf({ ...SINGIL_LH, hmpgAdres: 'https://apply.lh.or.kr.example.com' }), null, '비슷한 주소는 아니다');
  assert.equal(receiptChannelOf({ ...SINGIL_LH, type: 'remaining' }), null);
  // 서버 목록 응답의 receiptChannel이 있으면 그 값을 쓴다(null이면 청약홈).
  assert.equal(receiptChannel({ receiptChannel: 'LH' }), 'LH');
  assert.equal(receiptChannel({ ...SINGIL_LH, receiptChannel: null }), null);
  assert.equal(receiptTag({ receiptChannel: 'LH' }), 'LH 접수');
  assert.equal(receiptTag({ receiptChannel: null }), '');
});

test('공급 규모: 주택형 합이 전체보다 적으면 이번 모집과 전체를 나눠 보여 준다', () => {
  // 시흥거모 S-1블록(apt/2026000531): 전체 300세대, 주택형 일반+특별 105세대
  const models = [{ suplyHshldco: '30', spsplyHshldco: '40' }, { suplyHshldco: '15', spsplyHshldco: '20' }];
  assert.equal(recruitHouseholds(models), 105);
  const scale = supplyScale({ total: '300', recruit: 105 });
  assert.equal(scale.text, '이번 모집 105세대 (전체 300세대)');
  assert.equal(scale.value, 105);
  assert.ok(scale.split);
  assert.equal(supplyScale({ total: '1,200', recruit: 1200 }).text, '1,200세대', '같으면 한 숫자');
  assert.equal(supplyScale({ total: '300', recruit: null }).text, '300세대');
  assert.equal(supplyScale({ total: '', recruit: null }).text, '미제공');
  assert.equal(supplyScale({ total: '100', recruit: 120 }).text, '100세대', '주택형 합이 더 크면 전체 숫자');
  // 이미지 저장 표도 같은 함수를 쓴다.
  const detail = { houseNm: 'S-1', totSuplyHshldco: '300', recruitHouseholds: 105, unitPrices: [] };
  assert.equal(detailSupplyScale(detail).text, '이번 모집 105세대 (전체 300세대)');
  const facts = detailImageSections(detail)[0].rows;
  assert.deepEqual(facts.find(([label]) => label === '공급 세대수'), ['공급 세대수', '이번 모집 105세대 (전체 300세대)']);
});

test('주말(KST)에는 오늘 접수 대신 주말 접수 기간 중으로 부른다', () => {
  assert.equal(isWeekendDay('2026-11-21'), true, '토');
  assert.equal(isWeekendDay('2026-11-22'), true, '일');
  assert.equal(isWeekendDay('2026-11-23'), false, '월');
  assert.deepEqual(openTodayText('2026-11-19'), { label: '오늘 접수', note: '오늘 신청할 수 있는 공고' });
  assert.equal(openTodayText('2026-11-22').label, '주말 접수 기간 중');
});

test('검색·필터가 걸려 있으면 오늘의 요약에 전국 기준 표시', () => {
  const all = Object.fromEntries(Array.from({ length: 8 }, (_, i) => [`rceptSe0${i + 1}`, 'Y']));
  assert.equal(browseFiltered({ paramInfo: { ...all, citys: [] }, selectedRegionCount: 17, totalRegionCount: 17 }), false);
  assert.equal(browseFiltered({ paramInfo: { ...all, citys: ['운정'] }, selectedRegionCount: 17, totalRegionCount: 17 }), true);
  assert.equal(browseFiltered({ paramInfo: { ...all, rceptSe06: 'N', citys: [] }, selectedRegionCount: 17, totalRegionCount: 17 }), true);
  assert.equal(browseFiltered({ paramInfo: { ...all, citys: [] }, selectedRegionCount: 3, totalRegionCount: 17 }), true);
});
