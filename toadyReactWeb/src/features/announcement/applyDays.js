/**
 * @file applyDays.js
 * @description 공고 한 건을 '실제로 신청할 수 있는 날'로 바꾸는 규칙(순수 함수, 별칭 import 없음 → node 테스트 가능).
 * 서버 ApplyDays.java(weekly-summary의 applyToday·nextApplyDay)와 같은 규칙이다. 규칙을 바꾸면 두 곳을 함께 고친다.
 * - APT: 특별공급 접수 기간(spsplyRceptBgnde~spsplyRceptEndde) + 1·2순위 지역별(해당·경기·기타) 접수일(Rcptde~Endde).
 *   특별공급 날짜가 없으면(예전 서버 응답) 청약접수 시작일이 첫 1순위 접수일보다 앞설 때 그날을 특별공급일로 본다.
 *   순위 날짜가 하나도 없으면 청약접수 기간(rceptBgnde~rceptEndde) 전체.
 * - 그 밖의 유형(무순위·임의공급·오피스텔·민간임대 등): 접수 시작일~종료일의 모든 날. 목록 API가 유형별 필드를 rceptBgnde/rceptEndde로 맞춰 준다.
 * 주말도 날짜 그대로 판정한다(데스크톱·모바일 달력은 주말 칸을 그리지 않을 뿐이다).
 * 쓰는 곳: announcementApi.getAnnouncements(달력 일정 행), todaySummaryRules(오늘 접수), ThisWeekPage(오늘 신청 표시).
 */
import { normalizeScheduleDate } from '../../views/main/calendar/calendarGroup.js';
import { isAlwaysOpenEnd } from './receiptState.js';

/**
 * 한 접수 기간을 최대 며칠까지 신청일로 펼칠지. 서버 ApplyDays.MAX_PERIOD_DAYS와 같은 값이어야 한다
 * (서버는 기간 겹침 조회에서 시작일을 이만큼만 거슬러 올라가므로, 달력에 그려지는 범위와 조회 범위가 같다).
 */
export const MAX_PERIOD_DAYS = 400;

/** 'YYYY-MM-DD'에 n일을 더한다. UTC로 계산해 기기 시간대에 흔들리지 않는다. */
export function addDays(iso, n) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d + n)).toISOString().slice(0, 10);
}

/** 시작일~종료일 날짜 목록. 종료일이 없거나 시작일보다 앞서면 시작일 하루. 시작일이 없으면 빈 목록. */
export function periodDays(startValue, endValue) {
  const start = normalizeScheduleDate(startValue);
  if (!start) return [];
  const end = normalizeScheduleDate(endValue);
  const last = end && end > start ? end : start;
  const days = [];
  for (let day = start, count = 0; day <= last && count <= MAX_PERIOD_DAYS; day = addDays(day, 1), count += 1) days.push(day);
  return days;
}

// 순위별 지역 컬럼. 지역 이름은 달력 줄에 '1순위(해당지역/경기)'처럼 붙는다.
const RANK_AREAS = [['해당지역', 'Crsparea'], ['경기', 'EtcGg'], ['기타지역', 'EtcArea']];

/**
 * 한 순위의 지역별 접수일 → [{ inDate, areaLabel, ongoing, periodEnd }].
 * - 지역 이름은 지역별 접수 기간(시작~종료)이 서로 다를 때만 붙인다.
 * - 여러 날 이어지는 순위 접수 기간(예: LH 1순위 11.16~11.23)은 특별공급처럼 첫날만 순위 줄, 이튿날부터 '접수 중' 줄이다.
 *   그날 시작하는 지역이 하나라도 있으면 보통 줄(그 지역 이름만), 모두 이어지는 날이면 접수 중 줄(이어지는 지역 이름).
 * - periodEnd: 그 줄에 속한 지역들의 접수 마지막 날. 하루 접수면 그날이다.
 * 신청 가능일(날짜 집합)은 예전과 같다: 지역별 Rcptde~Endde의 모든 날(서버 ApplyDays.java와 같은 숫자).
 */
function rankDays(item, rank) {
  const periods = RANK_AREAS.map(([label, key]) => ({ label, days: periodDays(item[`gnrlRnk${rank}${key}Rcptde`], item[`gnrlRnk${rank}${key}Endde`]) }))
    .filter((period) => period.days.length);
  const multi = new Set(periods.map((period) => `${period.days[0]}~${period.days.at(-1)}`)).size > 1;
  const byDate = new Map();
  periods.forEach(({ label, days }) => {
    const end = days.at(-1);
    days.forEach((day, index) => {
      if (!byDate.has(day)) byDate.set(day, []);
      byDate.get(day).push({ label, start: index === 0, end });
    });
  });
  return [...byDate.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([inDate, areas]) => {
    const starting = areas.filter((area) => area.start);
    const shown = starting.length ? starting : areas;
    return {
      inDate,
      areaLabel: multi ? shown.map((area) => area.label).join('/') : '',
      ongoing: !starting.length,
      periodEnd: shown.map((area) => area.end).sort().at(-1)
    };
  });
}

/**
 * 기간을 일정 행으로: 첫날은 시작 줄, 이튿날부터는 '접수 기간 중' 줄(ongoing). periodEnd는 그 기간(예: 특별공급)의 마지막 날.
 * 마감일이 '상시 접수' 표기(receiptState.isAlwaysOpenEnd, 예: 9999-12-31)면 펼치는 상한(MAX_PERIOD_DAYS)일은 실제 마감이 아니므로
 * periodEnd를 비우고 alwaysOpen을 붙인다(달력이 '~4.06 마감' 대신 '상시 접수'로 적는다. 상세 상태 칩과 같은 기준).
 */
function periodRows(stage, startValue, endValue) {
  const days = periodDays(startValue, endValue);
  const alwaysOpen = isAlwaysOpenEnd(endValue);
  const periodEnd = alwaysOpen ? null : days.at(-1) || null;
  return days.map((inDate, index) => ({ stage, inDate, areaLabel: '', ongoing: index > 0, periodEnd, alwaysOpen }));
}

/**
 * 공고 한 건 → 신청 가능일 일정 행 [{ stage, inDate, areaLabel, ongoing }].
 * stage: APT는 '01'(특별공급)·'02'(1순위)·'03'(2순위), 그 밖의 유형은 null(호출하는 쪽이 공급유형 코드를 붙인다).
 * ongoing: 여러 날 이어지는 접수 기간(특별공급·순위·유형별 접수)의 이튿날부터. 달력은 이 줄을 연한 '접수 중' 줄로 그린다.
 * periodEnd: 그 행이 속한 기간의 마지막 날(하루 순위 접수일 행은 그날). '접수 중' 줄의 마감 안내에 쓴다.
 * alwaysOpen: 상시 접수 기간의 행(periodEnd 없음). 순위 접수일 행에는 붙지 않는다.
 */
export function applySchedules(item = {}) {
  if ((item.type || 'apt') !== 'apt') return periodRows(null, item.rceptBgnde, item.rceptEndde);
  const rank1 = rankDays(item, 1).map((row) => ({ stage: '02', ...row }));
  const rank2 = rankDays(item, 2).map((row) => ({ stage: '03', ...row }));
  // 순위 접수일이 하나도 없으면 접수 기간 전체를 1순위 칸으로 보여 준다(예전과 같은 유형 코드).
  if (!rank1.length && !rank2.length) return periodRows('02', item.rceptBgnde, item.rceptEndde);
  const rows = [];
  if (normalizeScheduleDate(item.spsplyRceptBgnde)) {
    rows.push(...periodRows('01', item.spsplyRceptBgnde, item.spsplyRceptEndde));
  } else {
    const start = normalizeScheduleDate(item.rceptBgnde);
    if (start && (!rank1.length || start < rank1[0].inDate)) rows.push({ stage: '01', inDate: start, areaLabel: '', ongoing: false, periodEnd: start });
  }
  return [...rows, ...rank1, ...rank2];
}

/**
 * 달력 일정 행: applySchedules에 공급유형 코드(rceptSe)를 붙인다. APT는 단계 코드(01·02·03), 그 밖의 유형은 typeCode.
 * announcementApi.getAnnouncements가 공고 정보(base)와 합쳐 달력(calendarGroup)에 넘긴다.
 */
export function scheduleRows(item = {}, typeCode = '01') {
  return applySchedules(item).map((row) => ({ rceptSe: row.stage || typeCode, inDate: row.inDate, areaLabel: row.areaLabel, ongoing: row.ongoing, periodEnd: row.periodEnd, alwaysOpen: Boolean(row.alwaysOpen) }));
}

/** 신청 가능한 날짜(오름차순, 중복 없음). */
export function applyDays(item) {
  return [...new Set(applySchedules(item).map((row) => row.inDate))].sort();
}

/** 그날 신청할 수 있는지. */
export function canApplyOn(item, day) {
  const target = normalizeScheduleDate(day);
  return Boolean(target) && applyDays(item).includes(target);
}

/**
 * weekly-summary 항목이 오늘 신청 가능한지. 서버가 applyToday를 주면 그 값을 쓰고,
 * 아직 주지 않는 서버(배포 순서)면 예전처럼 접수 기간 중(status OPEN)으로 대신한다.
 */
export function appliesToday(weeklyItem) {
  if (typeof weeklyItem?.applyToday === 'boolean') return weeklyItem.applyToday;
  return weeklyItem?.status === 'OPEN';
}
