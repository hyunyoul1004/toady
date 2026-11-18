/**
 * 금액 표기 공통 규칙. 입력은 만원 단위 숫자다(공급금액·실거래가 모두 만원 단위로 받는다).
 * - 기본(표·요약·상세): 78,500 → '7억 8,500만원', 120,000 → '12억원', 9,800 → '9,800만원'
 * - compact(지도 말풍선·칩): 78,000 → '7.8억', 8,500 → '8,500만' (억은 소수 한 자리, .0은 뺀다)
 * - won:false 는 '원'을 뺀다(범위 슬라이더처럼 숫자가 이어 붙는 곳: '2억 9,000만').
 * 0 이하·숫자가 아닌 값은 empty(기본 '—')를 돌려준다.
 * 1만원 = 1, 1억 = 10,000 이므로 10,000으로 나눈 몫이 '억', 나머지가 '만'이다.
 */
export function formatManwon(value, { compact = false, won = !compact, empty = '—' } = {}) {
  const amount = Number(value);
  if (value === null || value === undefined || value === '' || !Number.isFinite(amount) || amount <= 0) return empty;
  const unit = won ? '원' : '';
  if (compact) {
    if (amount < 10000) return `${Math.round(amount).toLocaleString('ko-KR')}만${unit}`;
    const eok = Math.round(amount / 1000) / 10;
    return `${eok.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}억`;
  }
  const eok = Math.floor(amount / 10000), rest = Math.round((amount - eok * 10000) * 100) / 100;
  const restText = rest ? `${rest.toLocaleString('ko-KR', { maximumFractionDigits: 2 })}만` : '';
  if (!eok) return `${restText}${unit}`;
  return `${eok.toLocaleString('ko-KR')}억${restText ? ` ${restText}` : ''}${unit}`;
}
