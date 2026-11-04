/**
 * @file date.util.js
 * @description Day.js 기반 날짜 및 시간 처리 유틸리티
 * - 날짜 포맷팅(YYYY-MM-DD, 범위 표시 등)
 * - 상대 시간(Time-ago) 포맷팅
 */
import dayjs from 'dayjs';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import isBetween from 'dayjs/plugin/isBetween';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/ko';

dayjs.extend(customParseFormat);
dayjs.extend(isBetween);
dayjs.extend(relativeTime);
dayjs.locale('ko');

export { dayjs };

/**
 * 8자리(20261028) 또는 날짜 문자열을 YYYY-MM-DD 형태로 변환합니다.
 * @param {string|Date|number} value 날짜 값
 * @param {string} [format='YYYY-MM-DD'] 출력 포맷
 * @returns {string} 포맷팅된 날짜 문자열
 */
export function formatDate(value, format = 'YYYY-MM-DD') {
  if (!value) return '-';
  const str = String(value).trim();

  // YYYYMMDD 형태인 경우
  if (str.length === 8 && !str.includes('-')) {
    const d = dayjs(str, 'YYYYMMDD');
    return d.isValid() ? d.format(format) : str;
  }

  // YYYYMM 형태인 경우 (입주예정월 등)
  if (str.length === 6 && !str.includes('-')) {
    const d = dayjs(str, 'YYYYMM');
    return d.isValid() ? d.format('YYYY-MM') : str;
  }

  const d = dayjs(str);
  return d.isValid() ? d.format(format) : str;
}

/**
 * 시작일과 종료일 문자열을 받아 'YYYY-MM-DD ~ YYYY-MM-DD' 범위 문자열을 생성합니다.
 * @param {string} start 시작일
 * @param {string} end 종료일
 * @returns {string} 범위 문자열
 */
export function formatDateRange(start, end) {
  if (!start && !end) return '-';
  const s = formatDate(start);
  const e = formatDate(end);
  if (s !== '-' && e !== '-' && s !== e) {
    return `${s} ~ ${e}`;
  }
  return s !== '-' ? s : e;
}

/**
 * 일시 포맷 (YYYY-MM-DD HH:mm:ss)
 * @param {string|Date} value 일시 값
 * @returns {string}
 */
export function formatDateTime(value) {
  if (!value) return '-';
  const d = dayjs(value);
  return d.isValid() ? d.format('YYYY-MM-DD HH:mm:ss') : String(value);
}

/**
 * 상대 시간 포맷 (방금 전, 5분 전, 2시간 전 또는 YYYY.MM.DD)
 * @param {string|Date} value 일시 값
 * @returns {string}
 */
export function formatTimeAgo(value) {
  if (!value) return '';
  const d = dayjs(value);
  if (!d.isValid()) return String(value);
  const diffHours = dayjs().diff(d, 'hour');
  if (diffHours < 24) {
    return d.fromNow();
  }
  return d.format('YYYY.MM.DD HH:mm');
}
