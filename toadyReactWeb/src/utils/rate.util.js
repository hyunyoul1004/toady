/**
 * 경쟁률 표기 공통 규칙(서버와 같음): 100 이상은 정수, 그 미만은 소수 둘째 자리까지 두고 끝의 0은 뺀다.
 * 12.345 → '12.35 : 1', 21 → '21 : 1', 3.5 → '3.5 : 1', 1,249.5 → '1,250 : 1'. 숫자가 아니면 빈 문자열.
 */
export function rateNumber(value) {
  const number = typeof value === 'number' ? value : Number(String(value ?? '').replaceAll(',', '').trim());
  if (value === null || value === undefined || value === '' || !Number.isFinite(number)) return '';
  return number >= 100
    ? Math.round(number).toLocaleString('ko-KR')
    : (Math.round(number * 100) / 100).toLocaleString('ko-KR', { maximumFractionDigits: 2 });
}
export function formatCompetitionRate(value) {
  const text = rateNumber(value);
  return text ? `${text} : 1` : '';
}
