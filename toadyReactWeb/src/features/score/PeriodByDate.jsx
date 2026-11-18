import { useEffect, useMemo, useRef, useState } from 'react';
import { ToggleButton, ToggleButtonGroup } from '@mui/material';
import DateDigitsField from './DateDigitsField';
import AutoFixHighRounded from '@mui/icons-material/AutoFixHighRounded';
import { koreaToday } from '@/features/planner/plannerUtils';
import { Button } from '@mui/material';
import EventAvailableRounded from '@mui/icons-material/EventAvailableRounded';
import { accountFromDate, housingFromDates, nextRise, periodPickUpdate, riseCalendarText } from './scoreRules';


/** 미혼/기혼처럼 버튼 하나만 고르는 선택지. 이미 고른 버튼을 다시 눌러 null이 오면 무시해 답이 지워지지 않게 한다. */
function Choice({ value, onChange, options, label }) {
  return <ToggleButtonGroup exclusive size="small" value={value} onChange={(_, next) => next !== null && onChange(next)} className="score-choice" aria-label={label}>
    {options.map(([key, text]) => <ToggleButton key={String(key)} value={key}>{text}</ToggleButton>)}
  </ToggleButtonGroup>;
}

/**
 * 무주택 기간·통장 가입기간을 날짜로 입력하면 구간을 자동으로 고른다.
 * 생년월일 등 입력값은 이 화면에서만 쓰고 저장·전송하지 않는다(저장되는 것은 고른 구간뿐).
 * 쓰는 곳: ScoreCalculator의 무주택 기간·통장 가입기간 칸. kind로 둘 중 하나를 고르고, 계산한 순번은 onPick으로 부모에 넘긴다.
 * active가 false면('직접 고르기' 모드) 숨기기만 해서 입력한 날짜가 사라지지 않는다.
 */
export default function PeriodByDate({ kind, options, value, onPick, active = true }) {
  const today = koreaToday();
  const [form, setForm] = useState({ birth: '', married: false, marriageDate: '', owned: false, soldDate: '', joined: '' });
  // 한 번이라도 날짜를 만졌는지. 만진 뒤 입력이 부족해지면(예: 기혼으로 바꾸고 혼인신고일 미입력) 이전 자동 선택을 지운다.
  const [dirty, setDirty] = useState(false);
  // useRef: 값이 바뀌어도 다시 그리지 않아도 되는 기억용 칸이다. 화면에 안 보이는 값이라 state 대신 ref를 쓴다.
  // 이 날짜 입력이 마지막으로 자동 선택한 구간. 입력이 부족해졌을 때 이 값일 때만 비운다(저장된 값·직접 고른 값 보호).
  const lastPicked = useRef(undefined);
  const set = (key, next) => { setDirty(true); setForm(previous => ({ ...previous, [key]: next })); };
  // 날짜 → 구간 계산. form·today가 바뀔 때만 다시 계산한다(useMemo).
  const result = useMemo(() => kind === 'housing' ? housingFromDates(form, today) : accountFromDate(form.joined, today), [kind, form, today]);
  const touched = kind === 'housing' ? Boolean(form.birth) : Boolean(form.joined);
  const rise = useMemo(() => nextRise(kind, form, result, today), [kind, form, result, today]);
  // 가점이 오르는 날을 캘린더 파일로 받아 두면 서버에 날짜를 저장하지 않고도 그날 알림을 받는다.
  const saveRise = () => {
    const url = URL.createObjectURL(new Blob([riseCalendarText(rise, window.location.origin)], { type: 'text/calendar;charset=utf-8' }));
    const link = document.createElement('a'); link.href = url; link.download = `toady-score-${rise.date}.ics`; link.click();
    // 임시 다운로드 주소는 다운로드가 시작된 뒤 풀어 메모리를 돌려준다.
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };
  // 계산된 구간이 바뀌면 바로 반영한다(같은 값이면 다시 저장하지 않는다).
  // 입력이 부족해지면 null 을 넘겨 점수 계산·저장에서 빠지게 한다. '직접 고르기'에서 돌아오면(active) 날짜 결과를 다시 반영한다.
  useEffect(() => {
    if (!active) return;
    const next = periodPickUpdate(result, value, dirty, lastPicked.current);
    if (next === undefined) return;
    lastPicked.current = next;
    onPick(next);
  // 의존성 배열에 계산 결과(index·error)만 넣었다. value·onPick까지 넣으면 부모가 다시 그릴 때마다 실행돼 같은 값을 반복해 넘기게 된다.
  }, [result.index, result.error, dirty, active]); // eslint-disable-line react-hooks/exhaustive-deps

  return <div className="score-dates" hidden={!active}>
    {kind === 'housing' ? <>
      <div className="score-dates-grid">
        <DateDigitsField label="생년월일" value={form.birth} onChange={next => set('birth', next)} max={today} />
        <div className="score-dates-row"><span>혼인</span><Choice label="혼인 여부" value={form.married} onChange={next => set('married', next)} options={[[false, '미혼'], [true, '기혼']]} /></div>
        {form.married && <DateDigitsField label="혼인신고일" value={form.marriageDate} onChange={next => set('marriageDate', next)} max={today} />}
        <div className="score-dates-row"><span>집을 가진 적</span><Choice label="주택 소유 이력" value={form.owned} onChange={next => set('owned', next)} options={[[false, '없어요'], [true, '있어요']]} /></div>
        {form.owned && <DateDigitsField label="마지막으로 집을 처분한 날" helperText="본인·배우자 중 더 늦게 무주택이 된 날" value={form.soldDate} onChange={next => set('soldDate', next)} max={today} />}
      </div>
    </> : <div className="score-dates-grid">
      <DateDigitsField label="청약통장 가입일" value={form.joined} onChange={next => set('joined', next)} max={today} />
    </div>}
    {touched && (result.error
      ? <p className="score-dates-result is-pending">{result.error}</p>
      : <p className="score-dates-result"><AutoFixHighRounded fontSize="inherit" /><span><b>{options[result.index].label} · {options[result.index].points}점</b>으로 골랐어요<small>{result.reason}</small></span></p>)}
    {touched && rise && <p className="score-dates-next"><EventAvailableRounded fontSize="inherit" /><span><b>{rise.date.replaceAll('-', '.')}</b>에 {rise.label} 점수가 <b>+{rise.plus}점</b> 올라요</span>
      <Button size="small" onClick={saveRise}>캘린더에 알림 추가</Button></p>}
  </div>;
}
