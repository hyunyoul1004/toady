import { useMemo, useState } from 'react';
import { InputAdornment, MenuItem, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import SavingsOutlined from '@mui/icons-material/SavingsOutlined';
import { formatManwon } from '@/features/planner/researchUtils';
import { DEPOSIT_RATES, LTV_OPTIONS, fundPlan } from './fundRules';
import './match.css';

const CASH_KEY = 'toady.fund.cash';
function readCash() { try { const value = Number(localStorage.getItem(CASH_KEY)); return Number.isFinite(value) && value > 0 ? String(value) : ''; } catch { return ''; } }
const ym = value => { const digits = String(value || '').replace(/\D/g, ''); return digits.length >= 6 ? `${digits.slice(0, 4)}.${digits.slice(4, 6)}` : null; };
const ymd = value => { const digits = String(value || '').replace(/\D/g, ''); return digits.length >= 8 ? `${digits.slice(0, 4)}.${digits.slice(4, 6)}.${digits.slice(6, 8)}` : null; };

/**
 * 주택형을 고르면 계약금·중도금·잔금과 시점별로 필요한 내 돈을 계산한다.
 * 보유 현금은 이 브라우저에만 저장한다. 금액은 만원 단위.
 */
export default function FundPlanner({ detail }) {
  const units = useMemo(() => (detail.unitPrices || []).filter(unit => Number.isFinite(unit.amountManwon) && unit.amountManwon > 0), [detail.unitPrices]);
  const [unitId, setUnitId] = useState(() => units[0]?.id || '');
  const [depositRate, setDepositRate] = useState(0.1);
  const [midLoan, setMidLoan] = useState(true);
  const [ltv, setLtv] = useState(50);
  const [cash, setCash] = useState(readCash);
  if (!units.length) return null;
  const unit = units.find(entry => entry.id === unitId) || units[0];
  const plan = fundPlan({ price: unit.amountManwon, depositRate, midLoan, ltv, cash: Number(cash) || 0 });
  const updateCash = value => {
    const digits = value.replace(/\D/g, '').slice(0, 7);
    setCash(digits);
    try { if (digits) localStorage.setItem(CASH_KEY, digits); else localStorage.removeItem(CASH_KEY); } catch { /* 저장 실패 시 이번 화면에서만 */ }
  };
  const contract = ymd(detail.cntrctCnclsBgnde), moveIn = ym(detail.mvnPrearngeYm);
  const hasCash = Number(cash) > 0;
  return <section className="match-fund" aria-labelledby="match-fund-heading">
    <h3 id="match-fund-heading"><SavingsOutlined />자금 계획</h3>
    <p className="match-fund-sub">일반적인 계약금 {Math.round(depositRate * 100)}% · 중도금 60% · 잔금 {100 - Math.round(depositRate * 100) - 60}% 구조로 계산해요.</p>
    <div className="match-fund-controls">
      <TextField select size="small" label="주택형" value={unit.id} onChange={event => setUnitId(event.target.value)}>
        {units.map(entry => <MenuItem key={entry.id} value={entry.id}>{entry.label} · {formatManwon(entry.amountManwon)}</MenuItem>)}
      </TextField>
      <TextField size="small" label="지금 쓸 수 있는 현금" value={cash} onChange={event => updateCash(event.target.value)} placeholder="예: 8000"
        inputProps={{ inputMode: 'numeric' }} InputLabelProps={{ shrink: true }} InputProps={{ endAdornment: <InputAdornment position="end">만원</InputAdornment> }}
        helperText={hasCash ? formatManwon(Number(cash)) : '이 브라우저에만 저장돼요'} />
      <div className="match-fund-toggles">
        <div><span>계약금</span><ToggleButtonGroup exclusive size="small" value={depositRate} onChange={(_, value) => value !== null && setDepositRate(value)} aria-label="계약금 비율">
          {DEPOSIT_RATES.map(rate => <ToggleButton key={rate} value={rate}>{Math.round(rate * 100)}%</ToggleButton>)}</ToggleButtonGroup></div>
        <div><span>중도금</span><ToggleButtonGroup exclusive size="small" value={midLoan} onChange={(_, value) => value !== null && setMidLoan(value)} aria-label="중도금 납부 방법">
          <ToggleButton value>집단대출</ToggleButton><ToggleButton value={false}>현금</ToggleButton></ToggleButtonGroup></div>
        <div><span>입주 때 주담대</span><ToggleButtonGroup exclusive size="small" value={ltv} onChange={(_, value) => value !== null && setLtv(value)} aria-label="잔금 대출 비율">
          {LTV_OPTIONS.map(rate => <ToggleButton key={rate} value={rate}>{rate ? `${rate}%` : '없음'}</ToggleButton>)}</ToggleButtonGroup></div>
      </div>
    </div>
    <ol className="match-fund-steps">
      <li><div><strong>계약금</strong><span>{contract ? `${contract} 계약` : '당첨 후 계약일'}</span></div><b>{formatManwon(plan.deposit)}</b>
        {hasCash && plan.depositShortage > 0 && <em className="is-short">계약금이 {formatManwon(plan.depositShortage)} 부족해요</em>}</li>
      <li><div><strong>중도금</strong><span>계약 뒤 입주 전까지 보통 4~6회 나눠 내요</span></div><b>{midLoan ? '대출 ' : ''}{formatManwon(plan.middle)}</b>
        <em>{midLoan ? '집단대출이면 이 시기에 낼 현금은 없어요(이자 후불제 가정)' : '모두 현금으로 내요'}</em></li>
      <li><div><strong>입주 · 잔금</strong><span>{moveIn ? `${moveIn} 입주 예정` : '입주 때'}</span></div><b>{formatManwon(plan.moveInCash)}</b>
        <em>잔금 {formatManwon(plan.balance)}{midLoan ? ` + 중도금 대출 상환 ${formatManwon(plan.middle)}` : ''}{plan.mortgage ? ` − 주담대 ${formatManwon(plan.mortgage)}` : ''} + 취득세 약 {formatManwon(plan.tax)}</em></li>
    </ol>
    <div className={`match-fund-total${hasCash ? plan.shortage > 0 ? ' is-short' : ' is-ok' : ''}`}>
      <span>입주까지 필요한 내 돈</span><strong>{formatManwon(plan.ownTotal)}</strong>
      {hasCash && <small>{plan.shortage > 0 ? `지금 현금보다 ${formatManwon(plan.shortage)} 더 필요해요` : '지금 현금으로 충분해요'}</small>}
    </div>
    <p className="match-fund-note">발코니 확장·옵션, 중도금 이자, 이사 비용은 빠져 있어요. 대출 한도는 규제지역·소득(DSR)·금융기관에 따라 달라요. 취득세는 1주택·전용 85㎡ 이하 기준 근사값이에요.</p>
  </section>;
}
