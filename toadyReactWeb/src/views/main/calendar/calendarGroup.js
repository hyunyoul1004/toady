/**
 * @file calendarGroup.js
 * @description 데스크톱 월 달력의 일정 묶음 계산(순수 함수, React·별칭 import 없음 → node 테스트 가능)
 * - 같은 날 같은 공고(subscriptSeq)의 특별공급/1순위/2순위 행을 한 줄로 묶는다.
 * - 공고 전체 단계(특 → 1 → 2)를 함께 담아, 칸마다 같은 단지가 반복돼도 어느 단계인지 한눈에 보이게 한다.
 */

// 공급 유형별 표시 정보. 색 의미(특별공급=자주, 1순위=빨강, 2순위=주황…)는 유지하고 채도만 낮춘다.
// accent: 왼쪽 띠·뱃지 채움, tint: 줄 배경, text: 진한 글자색(배경 대비 확보)
export const RCEPT_SE_META = {
  '01': { short: '특', label: '특별공급', order: 1, accent: '#a3557a', tint: '#f8eef3', text: '#6e2a4b' },
  '02': { short: '1', label: '1순위', order: 2, accent: '#d9485c', tint: '#fdeef0', text: '#9b1c30' },
  '03': { short: '2', label: '2순위', order: 3, accent: '#d98a3a', tint: '#fdf3e7', text: '#8a4a0f' },
  '04': { short: '민', label: '민간임대', order: 4, accent: '#7d5a5b', tint: '#f4eeee', text: '#553637' },
  '05': { short: '오', label: '오피스텔', order: 5, accent: '#4f9a94', tint: '#eaf4f3', text: '#23625d' },
  '06': { short: '무', label: '무순위', order: 6, accent: '#6474a0', tint: '#eef0f7', text: '#3a4569' },
  '07': { short: '임', label: '임의공급', order: 7, accent: '#8b97a8', tint: '#f2f4f7', text: '#465264' },
  '08': { short: '재', label: '불법행위재공급', order: 8, accent: '#2fa898', tint: '#e8f6f4', text: '#0f665c' }
};

const FALLBACK_META = { short: '공', label: '청약', order: 99, accent: '#94a3b8', tint: '#f1f5f9', text: '#334155' };

/** 공급 유형 코드의 표시 정보(알 수 없는 코드는 회색 기본값) */
export function rceptSeMeta(rceptSe) {
  return RCEPT_SE_META[rceptSe] || FALLBACK_META;
}

/** 'YYYYMMDD' · 'YYYY-MM-DD' · 'YYYY.MM.DD HH:mm' 등을 'YYYY-MM-DD'로 맞춘다. 날짜가 아니면 null */
export function normalizeScheduleDate(value) {
  if (value == null) return null;
  const digits = String(value).replace(/\D/g, '').slice(0, 8);
  if (digits.length !== 8) return null;
  const y = Number(digits.slice(0, 4));
  const m = Number(digits.slice(4, 6));
  const d = Number(digits.slice(6, 8));
  const check = new Date(Date.UTC(y, m - 1, d));
  if (check.getUTCFullYear() !== y || check.getUTCMonth() !== m - 1 || check.getUTCDate() !== d) return null;
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
}

const effectiveRceptSe = (row) => row.displayRceptSe || row.rceptSe || '01';
const byStageOrder = (a, b) => rceptSeMeta(a.rceptSe).order - rceptSeMeta(b.rceptSe).order;

/**
 * 일정 행 목록 → 날짜·공고별 묶음 목록.
 * 반환 항목: { key, date, subscriptSeq, houseNm, commentCount, stages, allStages, primaryRceptSe, order }
 * - stages: 그 날짜의 단계들 [{ rceptSe, areaLabels }] (단계 순)
 * - allStages: 같은 목록 안 공고 전체 단계 [{ rceptSe, dates }] (단계 순) — 오늘 칸이 몇 번째 단계인지 표시용
 * 정렬: 날짜 → 그날 첫 단계 순서 → 단지명
 */
export function groupCalendarSchedules(rows) {
  if (!Array.isArray(rows)) return [];
  const groups = new Map();
  const timeline = new Map(); // subscriptSeq → Map(rceptSe → Set(date))

  rows.forEach((row) => {
    if (!row || row.subscriptSeq == null || row.subscriptSeq === '') return;
    const date = normalizeScheduleDate(row.inDate);
    if (!date) return;
    const seq = String(row.subscriptSeq);
    const rceptSe = effectiveRceptSe(row);

    if (!timeline.has(seq)) timeline.set(seq, new Map());
    const stageDates = timeline.get(seq);
    if (!stageDates.has(rceptSe)) stageDates.set(rceptSe, new Set());
    stageDates.get(rceptSe).add(date);

    const key = `${date}|${seq}`;
    if (!groups.has(key)) {
      groups.set(key, {
        key,
        date,
        subscriptSeq: row.subscriptSeq,
        houseNm: row.houseNm || '',
        commentCount: 0,
        stageMap: new Map()
      });
    }
    const group = groups.get(key);
    if (!group.houseNm && row.houseNm) group.houseNm = row.houseNm;
    group.commentCount = Math.max(group.commentCount, Number(row.subscriptCommentCnt) || 0);
    if (!group.stageMap.has(rceptSe)) group.stageMap.set(rceptSe, new Set());
    if (row.areaLabel) {
      String(row.areaLabel).split('/').map((s) => s.trim()).filter(Boolean)
        .forEach((label) => group.stageMap.get(rceptSe).add(label));
    }
  });

  const result = Array.from(groups.values()).map(({ stageMap, ...group }) => {
    const stages = Array.from(stageMap.entries())
      .map(([rceptSe, labels]) => ({ rceptSe, areaLabels: Array.from(labels) }))
      .sort(byStageOrder);
    const allStages = Array.from(timeline.get(String(group.subscriptSeq)).entries())
      .map(([rceptSe, dates]) => ({ rceptSe, dates: Array.from(dates).sort() }))
      .sort(byStageOrder);
    const primaryRceptSe = stages[0].rceptSe;
    return { ...group, stages, allStages, primaryRceptSe, order: rceptSeMeta(primaryRceptSe).order };
  });

  return result.sort((a, b) => (
    a.date.localeCompare(b.date)
    || a.order - b.order
    || a.houseNm.localeCompare(b.houseNm, 'ko')
    || String(a.subscriptSeq).localeCompare(String(b.subscriptSeq))
  ));
}

/** 묶음 목록 → { 'YYYY-MM-DD': [묶음…] } (정렬 유지) */
export function groupsByDate(groups) {
  const map = {};
  (groups || []).forEach((group) => {
    (map[group.date] ||= []).push(group);
  });
  return map;
}

/** 범례용: 목록에 실제로 쓰인 공급 유형 코드(단계 순) */
export function usedRceptSes(groups) {
  const used = new Set();
  (groups || []).forEach((group) => group.stages.forEach((stage) => used.add(stage.rceptSe)));
  return Array.from(used).sort((a, b) => rceptSeMeta(a).order - rceptSeMeta(b).order);
}

/** 접근성·말줄임 보완용 설명: "단지명 · 1순위(해당지역) · 댓글 3" */
export function describeGroup(group) {
  const stages = group.stages.map((stage) => {
    const label = rceptSeMeta(stage.rceptSe).label;
    return stage.areaLabels.length ? `${label}(${stage.areaLabels.join('/')})` : label;
  });
  const parts = [group.houseNm, ...stages];
  if (group.commentCount > 0) parts.push(`이야기 ${group.commentCount}`);
  return parts.filter(Boolean).join(' · ');
}
