import { useId, useState } from 'react';
import { Button, MenuItem, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import LockOutlined from '@mui/icons-material/LockOutlined';
import { SIDO } from './eligibilityRules';

export const ACCOUNT_CHOICES = [[-1, '없어요'], [0, '6개월 미만'], [6, '6개월~1년'], [12, '1~2년'], [24, '2년 이상']];
/** 가점 계산기에서 고른 가입 개월 수를 진단 선택지 중 같거나 작은 값으로 맞춘다. */
export function accountChoice(months) {
  if (!Number.isFinite(months)) return null;
  return [...ACCOUNT_CHOICES].reverse().find(([value]) => months >= value)?.[0] ?? null;
}

function Choice({ label, value, onChange, options, hint }) {
  const id = useId();
  return <div className="match-q">
    <div className="match-q-label" id={id}><strong>{label}</strong>{hint && <small>{hint}</small>}</div>
    <ToggleButtonGroup exclusive size="small" value={value} aria-labelledby={id} className="match-choice"
      onChange={(_, next) => next !== null && onChange(next)}>
      {options.map(([key, text]) => <ToggleButton key={String(key)} value={key}>{text}</ToggleButton>)}
    </ToggleButtonGroup>
  </div>;
}

/** 입력이 모두 끝났는지. 조건에 따라 나오는 질문(보유 주택 수·과거 소유·혼인 7년)도 확인한다. */
export function formReady(form) {
  const base = form.sido && form.accountMonths !== null && form.homeless !== null && form.householder !== null && form.marriage !== null;
  const home = form.homeless ? form.everOwned !== null : form.homes !== null;
  const marriage = form.marriage !== 'married' || form.marriedWithin7 !== null;
  return Boolean(base && home && marriage);
}

/**
 * 30초 자격 진단. 질문은 세 묶음(사는 곳·통장 / 집 / 가족·이력)으로, 앞의 답에 따라 필요한 질문만 보인다.
 * 입력값은 이 브라우저에만 저장한다.
 */
export default function EligibilityForm({ initial, onSave, onCancel }) {
  const [form, setForm] = useState(() => ({
    sido: null, birthYear: null, accountMonths: null, homeless: null, homes: null, everOwned: null, householder: null,
    marriage: null, marriedWithin7: null, children: 0, newborn: false, parents: false, won5y: false, usedSpecial: false, ...initial
  }));
  const set = patch => setForm(previous => ({ ...previous, ...patch }));
  const ready = formReady(form);
  const thisYear = new Date().getFullYear();
  return <form className="match-form" onSubmit={event => { event.preventDefault(); if (ready) onSave(form); }}>
    <fieldset>
      <legend><span>1</span>사는 곳과 청약통장</legend>
      <div className="match-q-row">
        <TextField select size="small" label="사는 곳(주민등록 기준)" value={form.sido || ''} onChange={event => set({ sido: event.target.value })} required>
          {SIDO.map(name => <MenuItem key={name} value={name}>{name}</MenuItem>)}
        </TextField>
        <TextField size="small" label="출생 연도(선택)" placeholder="예: 1991" inputProps={{ inputMode: 'numeric', maxLength: 4, pattern: '[0-9]*' }}
          value={form.birthYear ?? ''} InputLabelProps={{ shrink: true }} helperText="청년 특별공급 확인에만 써요"
          onChange={event => { const digits = event.target.value.replace(/\D/g, '').slice(0, 4); const year = Number(digits); set({ birthYear: digits.length === 4 && year > 1920 && year <= thisYear ? year : digits ? digits : null }); }} />
      </div>
      <Choice label="청약통장 가입 기간" value={form.accountMonths} onChange={value => set({ accountMonths: value })} options={ACCOUNT_CHOICES} />
    </fieldset>
    <fieldset>
      <legend><span>2</span>집</legend>
      <Choice label="세대원 모두 지금 집이 없나요?" hint="분양권·입주권도 집으로 봐요" value={form.homeless} onChange={value => set({ homeless: value })} options={[[true, '네, 무주택'], [false, '집이 있어요']]} />
      {form.homeless === false && <Choice label="세대가 가진 집" value={form.homes} onChange={value => set({ homes: value })} options={[[1, '1채'], [2, '2채 이상']]} />}
      {form.homeless === true && <Choice label="세대원 중 집을 가져 본 적이 있나요?" hint="생애최초 특별공급 기준" value={form.everOwned} onChange={value => set({ everOwned: value })} options={[[false, '한 번도 없어요'], [true, '있어요']]} />}
      <Choice label="세대주인가요?" value={form.householder} onChange={value => set({ householder: value })} options={[[true, '세대주'], [false, '세대원']]} />
    </fieldset>
    <fieldset>
      <legend><span>3</span>가족과 당첨 이력</legend>
      <Choice label="혼인" value={form.marriage} onChange={value => set({ marriage: value, ...(value !== 'married' ? { marriedWithin7: null } : {}) })} options={[['single', '미혼'], ['married', '기혼'], ['engaged', '예비 신혼부부']]} />
      {form.marriage === 'married' && <Choice label="혼인신고 7년 이내인가요?" value={form.marriedWithin7} onChange={value => set({ marriedWithin7: value })} options={[[true, '네'], [false, '7년 넘었어요']]} />}
      <Choice label="미성년 자녀(태아 포함)" value={form.children} onChange={value => set({ children: value })} options={[[0, '없음'], [1, '1명'], [2, '2명'], [3, '3명 이상']]} />
      <Choice label="2세 미만 자녀가 있거나 임신 중인가요?" value={form.newborn} onChange={value => set({ newborn: value })} options={[[false, '아니요'], [true, '네']]} />
      <Choice label="만 65세 이상 부모님을 3년 이상 모시고 있나요?" value={form.parents} onChange={value => set({ parents: value })} options={[[false, '아니요'], [true, '네']]} />
      <Choice label="최근 5년 안에 세대원이 청약에 당첨된 적이 있나요?" value={form.won5y} onChange={value => set({ won5y: value })} options={[[false, '없어요'], [true, '있어요']]} />
      <Choice label="세대원이 특별공급에 당첨된 적이 있나요?" value={form.usedSpecial} onChange={value => set({ usedSpecial: value })} options={[[false, '없어요'], [true, '있어요']]} />
    </fieldset>
    <p className="match-privacy"><LockOutlined fontSize="inherit" />입력한 내용은 이 브라우저에만 저장되고 서버로 보내지 않아요.</p>
    <div className="match-form-actions">
      {onCancel && <Button onClick={onCancel}>취소</Button>}
      <Button type="submit" variant="contained" disableElevation size="large" disabled={!ready}>{ready ? '넣을 수 있는 공고 보기' : '필수 질문에 답해 주세요'}</Button>
    </div>
  </form>;
}
