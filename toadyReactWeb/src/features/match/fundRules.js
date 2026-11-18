/**
 * 분양 대금 납부 흐름(계약금 → 중도금 → 잔금)과 시점별 필요한 현금. 금액 단위는 만원.
 * 일반적인 10/60/30 구조와 중도금 집단대출 → 입주 때 주택담보대출로 전환하는 흐름을 가정한다.
 * 실제 비율·대출 한도·이자 후불 여부는 공고와 금융기관마다 다르다.
 * 쓰는 곳: FundPlanner(공고 상세의 자금 계획). 서버 API는 쓰지 않는다.
 */
// 화면에서 고를 수 있는 계약금 비율(10%·20%)과 입주 때 주택담보대출 비율(LTV, 집값 대비 %) 선택지.
export const DEPOSIT_RATES = [0.1, 0.2];
export const LTV_OPTIONS = [0, 40, 50, 60, 70];

/** 1주택 취득세(지방교육세 포함, 전용 85㎡ 이하) 근사: 6억 이하 1.1%, 9억 초과 3.3%, 그 사이는 구간 비례. */
export function acquisitionTax(price) {
  if (!(price > 0)) return 0;
  let rate;
  if (price <= 60000) rate = 0.01;
  else if (price > 90000) rate = 0.03;
  else rate = ((price / 10000) * (2 / 3) - 3) / 100;
  const education = rate * 0.1;
  return Math.round(price * (rate + education));
}

/**
 * 분양가 하나로 시점별 필요한 현금을 계산한다.
 * - 계약금: 당첨 직후 현금으로 낸다.
 * - 중도금: 집단대출이면 이때 낼 현금은 없고, 입주 때 대출을 갚는다고 본다.
 * - 입주: 잔금 + 중도금 대출 상환 − 주담대 + 취득세. 주담대가 더 크면 남는 대출(surplusLoan)로 따로 돌려준다.
 * shortage는 입주까지 필요한 총액이 지금 현금보다 모자란 금액, depositShortage는 계약금만 따진 부족액이다.
 */
export function fundPlan({ price, depositRate = 0.1, midRate = 0.6, midLoan = true, ltv = 0, cash = 0 }) {
  if (!(price > 0)) return null;
  const deposit = Math.round(price * depositRate);
  const middle = Math.round(price * midRate);
  const balance = price - deposit - middle;
  const mortgage = Math.round(price * (ltv / 100));
  const tax = acquisitionTax(price);
  const middleCash = midLoan ? 0 : middle;
  // 입주 때: 잔금 + (중도금 대출을 받았다면) 대출 상환 − 주택담보대출 + 취득세
  const moveInRaw = balance + (midLoan ? middle : 0) - mortgage;
  const moveInCash = Math.max(0, moveInRaw) + tax;
  const ownTotal = deposit + middleCash + moveInCash;
  return {
    price, deposit, middle, balance, mortgage, tax, middleCash, moveInCash,
    ownTotal, surplusLoan: Math.max(0, -moveInRaw),
    shortage: Math.max(0, ownTotal - Math.max(0, Number(cash) || 0)),
    depositShortage: Math.max(0, deposit - Math.max(0, Number(cash) || 0))
  };
}
