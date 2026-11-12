import { useEffect, useId, useState } from 'react';
import { TextField } from '@mui/material';
import { formatDigits, normalizeDateInput, toIsoDate } from './scoreRules';

/**
 * 날짜 선택 달력 대신 숫자 8자리를 입력받는 칸. 모바일에서는 숫자 키패드가 뜨고 점은 자동으로 들어간다.
 * 생년월일처럼 수십 년 전 날짜를 달력으로 넘기는 불편을 없애기 위해서다.
 */
export default function DateDigitsField({ label, value, onChange, max, helperText, autoFocus }) {
  const id = useId();
  const [text, setText] = useState(() => formatDigits(value));
  // 부모 값이 바뀌었을 때만 글자를 맞춘다. onChange 와 같은 규칙(올바른 날짜·max 이하가 아니면 빈 값)으로 비교해
  // 미래 날짜처럼 넘기지 않은 입력이 지워지지 않고 그대로 남아 오류 안내가 보이게 한다.
  useEffect(() => { if (normalizeDateInput(text, max) !== (value || '')) setText(formatDigits(value)); }, [value]); // eslint-disable-line react-hooks/exhaustive-deps
  const iso = toIsoDate(text), complete = text.replace(/\D/g, '').length === 8;
  const error = complete && (!iso ? '없는 날짜예요' : max && iso > max ? '오늘 이후 날짜는 넣을 수 없어요' : '');
  return <TextField id={id} label={label} value={text} size="small" fullWidth autoFocus={autoFocus}
    placeholder="예: 19890302" inputProps={{ inputMode: 'numeric', autoComplete: 'off', maxLength: 10 }}
    InputLabelProps={{ shrink: true }}
    error={Boolean(error)} helperText={error || helperText || (complete ? ' ' : '숫자 8자리 (연도·월·일)')}
    onChange={event => {
      const next = formatDigits(event.target.value);
      setText(next);
      onChange(normalizeDateInput(next, max));
    }} />;
}
