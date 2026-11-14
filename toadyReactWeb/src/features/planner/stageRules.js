import { daysUntil } from './plannerUtils.js';

/** 관심 공고의 청약 진행 단계. 서버(TB_FAVORITE.STAGE)와 같은 번호를 쓴다. */
export const STAGES = [
  { value: 0, label: '관심' },
  { value: 1, label: '신청 예정' },
  { value: 2, label: '신청 완료' },
  { value: 3, label: '당첨' },
  { value: 4, label: '미당첨' },
  { value: 5, label: '계약 완료' }
];
const APPLYHOME_URL = 'https://www.applyhome.co.kr';
export function normalizeStage(value) { const n = Number(value); return Number.isInteger(n) && n >= 0 && n <= 5 ? n : 0; }
const md = date => (date ? `${Number(date.slice(5, 7))}.${Number(date.slice(8, 10))}` : '');
export const formatRate = rate => (Number.isFinite(rate) ? `${rate >= 100 ? Math.round(rate).toLocaleString('ko-KR') : String(Math.round(rate * 100) / 100)} : 1` : '');

/** 공개된 결과 한 줄: 1순위 최고 경쟁률과 당첨가점 범위(내 가점과 비교). 공개 전이면 빈 배열. */
export function resultFacts(result, myScore) {
  if (!result) return [];
  const facts = [];
  if (result.ratePublished) facts.push(result.topRate !== null && result.topRate !== undefined ? `1순위 최고 경쟁률 ${formatRate(result.topRate)}` : '1순위 미달');
  if (result.scorePublished) {
    const low = Number(result.scoreLow), range = [result.scoreLow, result.scoreHigh].filter(Boolean).join('~');
    let text = `당첨가점 ${range}점`;
    if (Number.isFinite(myScore) && Number.isFinite(low) && result.scoreLow) text += myScore >= low ? ` · 내 가점 ${myScore}점(최저 이상)` : ` · 내 가점 ${myScore}점(최저보다 ${low - myScore}점 낮음)`;
    facts.push(text);
  }
  return facts;
}

/**
 * 단계와 일정에 맞는 다음 할 일 한 줄. tone: info · urgent · done.
 * action: { label, href(외부) | to(앱 경로) | tab(공고 상세 탭) }
 */
export function nextStep(stage, summary = {}, today, result) {
  const start = daysUntil(summary.startDate, today), end = daysUntil(summary.endDate, today), winner = daysUntil(summary.winnerDate, today);
  const contract = (summary.events || []).find(event => event.label === '계약 시작')?.date;
  if (stage <= 1) {
    if (start !== null && start > 0) return { tone: 'info', text: `접수 D-${start} (${md(summary.startDate)}) · 신청 전 체크리스트를 확인하세요`, action: stage === 0 ? { label: '신청 예정으로', stage: 1 } : null };
    if (end !== null && end >= 0) return { tone: 'urgent', text: end === 0 ? '오늘 접수 마감이에요 · 청약홈에서 신청하세요' : `접수 중 · 마감 D-${end} · 청약홈에서 신청하세요`, action: { label: '청약홈', href: APPLYHOME_URL } };
    if (end !== null) return { tone: 'info', text: '접수가 끝났어요. 신청했다면 ‘신청 완료’로 바꾸면 발표와 결과를 챙겨 드려요.', action: { label: '신청 완료로', stage: 2 } };
    return { tone: 'info', text: '일정이 정해지면 알려 드릴게요.' };
  }
  if (stage === 2) {
    if (winner !== null && winner > 0) return { tone: 'info', text: `당첨자 발표 D-${winner} (${md(summary.winnerDate)})${result?.ratePublished ? ' · 경쟁률이 공개됐어요' : ''}`, action: result?.ratePublished ? { label: '경쟁률 보기', tab: 'rate' } : null };
    if (winner !== null) return { tone: 'urgent', text: '당첨자가 발표됐어요. 청약홈에서 결과를 확인하고 기록해 두세요.', action: { label: '청약홈', href: APPLYHOME_URL } };
    return { tone: 'info', text: '발표일이 공개되면 알려 드릴게요.' };
  }
  if (stage === 3) {
    const left = daysUntil(contract, today);
    if (left !== null && left >= 0) return { tone: 'urgent', text: `축하해요! 계약 ${left === 0 ? '오늘' : `D-${left}`} (${md(contract)}) · 계약금과 서류를 준비하세요`, action: { label: '자금 계획', tab: 'detail' } };
    return { tone: 'done', text: '축하해요! 계약을 마쳤다면 ‘계약 완료’로 바꿔 두세요.', action: { label: '계약 완료로', stage: 5 } };
  }
  if (stage === 4) {
    return result?.scorePublished
      ? { tone: 'info', text: '공개 당첨가점과 내 가점을 비교하고 다음 청약을 준비해요.', action: { label: '다음 청약 찾기', to: '/match' } }
      : { tone: 'info', text: '당첨가점이 공개되면 비교해 드릴게요. 다음 청약도 찾아보세요.', action: { label: '다음 청약 찾기', to: '/match' } };
  }
  return { tone: 'done', text: '계약 완료! 입주 일정이 정해지면 공고에서 확인하세요.' };
}
