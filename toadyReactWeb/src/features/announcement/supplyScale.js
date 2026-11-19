/**
 * @file supplyScale.js
 * @description 상세 '공급 규모' 계산(순수 함수, 별칭 import 없음 → node 테스트 가능).
 * 청약홈 공고의 공급 규모(TOT_SUPLY_HSHLDCO)는 단지 전체 세대일 때가 있다. 사전청약 당첨자 몫처럼 이번에 모집하지 않는
 * 세대가 들어 있으면 주택형 표의 일반+특별 합(이번 모집 세대)보다 크다(예: 전체 300세대, 주택형 합 105세대).
 * 두 숫자가 다르면 '이번 모집 105세대 (전체 300세대)'처럼 나눠 보여 준다.
 * 상세 요약 칸·공고 기본 정보·이미지 저장이 모두 이 함수를 써서 같은 숫자를 보여 준다.
 */

const positive = (value) => {
  const number = Number(String(value ?? '').replace(/,/g, '').trim());
  return Number.isFinite(number) && number > 0 ? Math.round(number) : null;
};
const fmt = (number) => number.toLocaleString('ko-KR');

/** 주택형 목록(상세 응답 models)의 일반+특별 공급 세대 합. 주택형이 없거나 합이 0이면 null. */
export function recruitHouseholds(models = []) {
  if (!Array.isArray(models) || !models.length) return null;
  const sum = models.reduce((acc, model) => acc + (Number(model.suplyHshldco ?? model.gnsplyHshldco) || 0) + (Number(model.spsplyHshldco) || 0), 0);
  return sum > 0 ? sum : null;
}

/**
 * 전체 공급 세대(total)·이번 모집 세대(recruit) → 표시 정보.
 * - split: 이번 모집이 전체보다 적을 때 true. value는 이번 모집 세대, total은 전체 세대.
 * - 한쪽만 있으면 그 숫자를 그대로 쓴다(이번 모집이 전체보다 많으면 숫자가 어긋난 자료라 전체를 쓴다).
 * 반환: { value, total, recruit, split, text, short, note }
 *   text: '이번 모집 105세대 (전체 300세대)' | '300세대' | '미제공', short: 요약 칸 아래 작은 글('전체 300세대 중')
 */
export function supplyScale({ total, recruit } = {}) {
  const all = positive(total);
  const now = positive(recruit);
  if (all && now && now < all) {
    return {
      value: now, total: all, recruit: now, split: true,
      text: `이번 모집 ${fmt(now)}세대 (전체 ${fmt(all)}세대)`,
      short: `전체 ${fmt(all)}세대 중 이번 모집`,
      note: '전체 세대에는 사전청약 당첨자 몫 등 이번에 모집하지 않는 세대가 들어 있을 수 있어요.'
    };
  }
  const value = all || now;
  return { value: value || null, total: all, recruit: now, split: false, text: value ? `${fmt(value)}세대` : '미제공', short: '', note: '' };
}

/** 상세(subscriptDetail) → 공급 규모. announcementApi가 넣은 recruitHouseholds와 totSuplyHshldco를 쓴다. */
export function detailSupplyScale(detail = {}) {
  return supplyScale({ total: detail.totSuplyHshldco ?? detail.totalSupplyHouseholds, recruit: detail.recruitHouseholds });
}

/**
 * 상세 요약 칸(ResearchPanel '공급 규모')용: 큰 숫자·단위·작은 글. 숫자는 supplyScale의 value(text와 같은 숫자)만 쓴다.
 * caption: 나눠 보일 때는 '전체 N세대 중 이번 모집', 아니면 fallback(주택 구분 등).
 */
export function supplyMetric(scale, fallback = '') {
  return { number: scale?.value ? fmt(scale.value) : '미제공', unit: scale?.value ? '세대' : '', caption: scale?.split ? scale.short : fallback };
}
