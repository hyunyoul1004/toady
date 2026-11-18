/**
 * @file receiptChannel.js
 * @description 청약 신청을 어디서 받는지(접수처) 판정하는 공통 규칙(순수 함수, 별칭 import 없음 → node 테스트 가능).
 * 서버 ReceiptChannel.java(목록 응답 receiptChannel)와 같은 규칙이다. 규칙을 바꾸면 두 곳을 함께 고친다.
 *
 * 'LH': 한국토지주택공사가 LH청약플러스(apply.lh.or.kr)에서 직접 접수하는 공고(신혼희망타운·공공분양 본청약 등).
 *   청약홈 공개 API에는 있지만 청약홈 청약 캘린더에는 나오지 않는다(2026-11 청약홈 캘린더와 대조해 확인).
 *   판정 근거(셋 다 맞아야 한다):
 *   ① 사업주체명(BSNS_MBY_NM)에 '한국토지주택공사'
 *   ② 홈페이지 주소(HMPG_ADRES)가 LH청약플러스(apply.lh.or.kr)
 *   ③ 특별공급 접수일(SPSPLY_RCEPT_BGNDE)이 없음 — 청약홈에서 받는 LH 공공분양(예: 고덕신도시 아테라)은
 *      특별공급 접수일이 따로 있고 홈페이지가 분양 홍보 사이트다.
 * 그 밖에는 null(청약홈 접수로 본다). 확실하지 않은 공고에 'LH'를 붙이지 않는다.
 * 쓰는 곳: announcementApi(목록·상세), 달력 줄(calendarGroup), 공고 상세 안내(AnnouncementNotice).
 */

export const LH_APPLY_URL = 'https://apply.lh.or.kr/';
export const LH_APPLY_NAME = 'LH청약플러스';

const LH_HOST = 'apply.lh.or.kr';

/** 주소 문자열의 호스트(소문자). 'apply.lh.or.kr'처럼 스킴이 없어도 읽는다. 주소가 아니면 ''. */
function hostOf(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  try {
    return new URL(/^https?:\/\//i.test(raw) ? raw : `https://${raw}`).hostname.toLowerCase();
  } catch {
    return '';
  }
}

/** 공고 원본 필드(bsnsMbyNm·hmpgAdres·spsplyRceptBgnde)로 접수처를 판정한다. */
export function receiptChannelOf(item = {}) {
  if ((item.type || 'apt') !== 'apt') return null;
  const owner = String(item.bsnsMbyNm || '').replace(/\s+/g, '');
  const host = hostOf(item.hmpgAdres);
  const hasSpecial = Boolean(String(item.spsplyRceptBgnde || '').trim());
  return owner.includes('한국토지주택공사') && (host === LH_HOST || host === `www.${LH_HOST}`) && !hasSpecial ? 'LH' : null;
}

/**
 * 목록·상세 항목의 접수처. 서버가 receiptChannel을 주면(목록 응답) 그 값을 쓰고,
 * 주지 않으면(상세 응답, 예전 서버) 원본 필드로 직접 판정한다.
 */
export function receiptChannel(item = {}) {
  if (item && Object.prototype.hasOwnProperty.call(item, 'receiptChannel')) return item.receiptChannel === 'LH' ? 'LH' : null;
  return receiptChannelOf(item);
}

/** 접수처 이름. LH면 'LH청약플러스', 아니면 '청약홈'. */
export function receiptPlaceName(channel) {
  return channel === 'LH' ? LH_APPLY_NAME : '청약홈';
}
