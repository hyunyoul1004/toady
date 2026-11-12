import { useEffect, useMemo, useState } from 'react';
import { TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import AutoFixHighRounded from '@mui/icons-material/AutoFixHighRounded';
import { koreaToday } from '@/features/planner/plannerUtils';
import { accountFromDate, housingFromDates } from './scoreRules';

const dateProps = today => ({ type: 'date', size: 'small', fullWidth: true, InputLabelProps: { shrink: true }, inputProps: { max: today } });

function Choice({ value, onChange, options, label }) {
  return <ToggleButtonGroup exclusive size="small" value={value} onChange={(_, next) => next !== null && onChange(next)} className="score-choice" aria-label={label}>
    {options.map(([key, text]) => <ToggleButton key={String(key)} value={key}>{text}</ToggleButton>)}
  </ToggleButtonGroup>;
}

/**
 * 무주택 기간·통장 가입기간을 날짜로 입력하면 구간을 자동으로 고른다.
 * 생년월일 등 입력값은 이 화면에서만 쓰고 저장·전송하지 않는다(저장되는 것은 고른 구간뿐).
 */
export default function PeriodByDate({ kind, options, value, onPick }) {
  const today = koreaToday();
  const [form, setForm] = useState({ birth: '', married: false, marriageDate: '', owned: false, soldDate: '', joined: '' });
  const set = (key, next) => setForm(previous => ({ ...previous, [key]: next }));
  const result = useMemo(() => kind === 'housing' ? housingFromDates(form, today) : accountFromDate(form.joined, today), [kind, form, today]);
  const touched = kind === 'housing' ? Boolean(form.birth) : Boolean(form.joined);
  // 계산된 구간이 바뀌면 바로 반영한다(같은 값이면 다시 저장하지 않는다).
  useEffect(() => { if (result.index !== undefined && result.index !== value) onPick(result.index); }, [result.index]); // eslint-disable-line react-hooks/exhaustive-deps

  return <div className="score-dates">
    {kind === 'housing' ? <>
      <div className="score-dates-grid">
        <TextField label="생년월일" value={form.birth} onChange={event => set('birth', event.target.value)} {...dateProps(today)} />
        <div className="score-dates-row"><span>혼인</span><Choice label="혼인 여부" value={form.married} onChange={next => set('married', next)} options={[[false, '미혼'], [true, '기혼']]} /></div>
        {form.married && <TextField label="혼인신고일" value={form.marriageDate} onChange={event => set('marriageDate', event.target.value)} {...dateProps(today)} />}
        <div className="score-dates-row"><span>집을 가진 적</span><Choice label="주택 소유 이력" value={form.owned} onChange={next => set('owned', next)} options={[[false, '없어요'], [true, '있어요']]} /></div>
        {form.owned && <TextField label="마지막으로 집을 처분한 날" helperText="본인·배우자 중 더 늦게 무주택이 된 날" value={form.soldDate} onChange={event => set('soldDate', event.target.value)} {...dateProps(today)} />}
      </div>
    </> : <div className="score-dates-grid">
      <TextField label="청약통장 가입일" value={form.joined} onChange={event => set('joined', event.target.value)} {...dateProps(today)} />
    </div>}
    {touched && (result.error
      ? <p className="score-dates-result is-pending">{result.error}</p>
      : <p className="score-dates-result"><AutoFixHighRounded fontSize="inherit" /><span><b>{options[result.index].label} · {options[result.index].points}점</b>으로 골랐어요<small>{result.reason}</small></span></p>)}
  </div>;
}
