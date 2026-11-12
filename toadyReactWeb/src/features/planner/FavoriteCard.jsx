import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useEffect, useId, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Button, Checkbox, Chip, Collapse, Dialog, DialogActions, DialogContent, DialogTitle,
  FormControlLabel, IconButton, LinearProgress, Menu, MenuItem, Switch, TextField } from '@mui/material';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import EditNoteRounded from '@mui/icons-material/EditNoteRounded';
import LocationOnOutlined from '@mui/icons-material/LocationOnOutlined';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import NotificationsNoneRounded from '@mui/icons-material/NotificationsNoneRounded';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import { CHECKS, daysUntil, downloadCalendar, koreaToday } from './plannerUtils';
import { STAGES, nextStep, normalizeStage, resultFacts } from './stageRules';
import { gapBadge } from '@/features/match/priceGapRules';

/** 단계의 다음 할 일 버튼: 단계 바꾸기·청약홈·앱 화면·공고 탭. */
function StepAction({ action, item, busy, onStage }) {
  if (!action) return null;
  if (action.stage !== undefined) return <Button size="small" disabled={busy} onClick={() => onStage(action.stage)}>{action.label}</Button>;
  if (action.href) return <Button size="small" component="a" href={action.href} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded fontSize="inherit" />}>{action.label}</Button>;
  if (action.to) return <Button size="small" component={Link} to={action.to}>{action.label}</Button>;
  return <Button size="small" component={Link} to={`/my?subscriptSeq=${encodeURIComponent(item.id)}${action.tab === 'rate' ? '&tabType=rate' : ''}`} state={{ plannerDetail: true }}>{action.label}</Button>;
}

export default function FavoriteCard({ item, planner, selected, onSelect, report, result, myScore, gap }) {
  const [note,setNote] = useState(item.note || '');
  const [checks,setChecks] = useState(item.checklist || 0);
  const [email,setEmail] = useState(Boolean(item.emailEnabled));
  const [busy,setBusy] = useState(false), [open,setOpen] = useState(false);
  const menuButtonRef=useRef(null);
  const [cardOverlay,setCardOverlay] = useHistoryOverlay(`favorite:${item.id}`);
  const confirm = cardOverlay === 'confirm';
  const setMenu = anchor => { if (anchor) setCardOverlay('menu'); else if (cardOverlay === 'menu') setCardOverlay(null); };
  const setConfirm = open => setCardOverlay(open ? 'confirm' : null);
  const editorId = useId();
  useEffect(() => { setNote(item.note || '');setChecks(item.checklist || 0);setEmail(Boolean(item.emailEnabled)); },[item.note,item.checklist,item.emailEnabled]);
  const s = item.summary, today = koreaToday();
  const completed = CHECKS.filter((_,i) => checks & (1<<i)).length;
  const dirty = note !== (item.note || '') || checks !== (item.checklist || 0) || email !== Boolean(item.emailEnabled);
  const deadline = daysUntil(s.endDate,today);
  const status = s.unavailable ? '원본 확인 필요' : s.startDate && s.startDate > today ? '접수 예정' : deadline != null && deadline >= 0 ? '접수 중' : s.endDate ? '접수 종료' : '일정 미제공';
  const [stage,setStageValue] = useState(normalizeStage(item.stage));
  useEffect(() => { setStageValue(normalizeStage(item.stage)); },[item.stage]);
  // 단계는 누르는 즉시 저장한다(메모·체크리스트의 저장 전 변경과 섞지 않도록 저장된 값과 함께 보낸다).
  async function changeStage(next) {
    const previous = stage, value = next === stage ? 0 : next;
    setStageValue(value); setBusy(true);
    try { await planner.save({ ...item, stage: value }); }
    catch (e) { setStageValue(previous); report(e.message,true); }
    finally { setBusy(false); }
  }
  const step = s.unavailable ? null : nextStep(stage, s, today, result);
  const gapText = gapBadge(gap)?.text;
  const facts = [...(gapText ? [gapText] : []), ...resultFacts(result, myScore)];
  async function save() { setBusy(true);try {await planner.save({...item,note,checklist:checks,emailEnabled:email});report('준비 노트를 저장했습니다.',false);}catch(e){report(e.message,true);}finally{setBusy(false);} }
  async function remove() { setBusy(true);try {await planner.remove(item.id);report('관심 공고를 해제했습니다.',false);}catch(e){report(e.message,true);setBusy(false);}setConfirm(false); }
  return <article className={`planner-favorite-card ${selected?'is-selected':''}`}>
    <div className="planner-favorite-top"><div className="planner-card-tags"><Chip size="small" label={status} color={status==='접수 중'?'primary':'default'} variant="outlined" />{s.housingType && <span>{s.housingType}</span>}{item.emailEnabled && <span className="planner-mail-indicator"><NotificationsNoneRounded fontSize="inherit" /> 알림 켜짐</span>}</div><IconButton ref={menuButtonRef} aria-label={`${s.houseNm} 더 보기`} onClick={e=>setMenu(e.currentTarget)} size="small"><MoreHorizRounded /></IconButton></div>
    <h3>{s.unavailable?s.houseNm:<Link to={`/my?subscriptSeq=${encodeURIComponent(item.id)}`} state={{plannerDetail:true}}>{s.houseNm}</Link>}</h3>
    <div className="planner-address"><LocationOnOutlined fontSize="inherit" /><span>{s.address || '주소 미제공'}</span></div>
    <div className="planner-card-dates"><div><span>청약 접수</span><strong>{s.startDate?.slice(5).replace('-','.') || '미제공'}<em>—</em>{s.endDate?.slice(5).replace('-','.') || '미제공'}</strong></div><div><span>당첨자 발표</span><strong>{s.winnerDate || '미제공'}</strong></div></div>
    {s.unavailable && <Alert severity="warning" sx={{mb:2}}>원본을 찾을 수 없습니다. 준비 노트는 유지됩니다.</Alert>}
    {!s.unavailable && <div className="planner-stage">
      <div className="planner-stage-steps" role="group" aria-label={`${s.houseNm} 청약 진행 단계`}>
        {STAGES.slice(1).map(option => <button type="button" key={option.value} disabled={busy} aria-pressed={stage === option.value}
          className={stage === option.value ? `is-active is-${option.value}` : ''} onClick={() => changeStage(option.value)}>{option.label}</button>)}
      </div>
      {step && <div className={`planner-next is-${step.tone}`} role="status"><span>{step.text}</span><StepAction action={step.action} item={item} busy={busy} onStage={changeStage} /></div>}
      {facts.length > 0 && <ul className="planner-results" aria-label="시세 비교와 공개된 청약 결과">{facts.map(text => <li key={text}>{text}</li>)}</ul>}
    </div>}
    <div className="planner-progress-line"><span>청약 준비 <strong>{completed}/{CHECKS.length}</strong></span><span>{completed===CHECKS.length?'준비 항목 확인 완료':`${CHECKS.length-completed}개 항목을 확인해 주세요`}</span></div>
    <LinearProgress variant="determinate" value={completed/CHECKS.length*100} sx={{height:4,borderRadius:4,bgcolor:'#eef2f7',mb:2.5}} />
    <div className="planner-card-actions"><Button component={Link} to={`/my?subscriptSeq=${encodeURIComponent(item.id)}`} state={{plannerDetail:true}} disabled={s.unavailable} endIcon={<ArrowForwardRounded />} variant="contained" disableElevation>공고 보기</Button><Button onClick={()=>setOpen(x=>!x)} aria-expanded={open} aria-controls={editorId} startIcon={<EditNoteRounded />}>{open?'노트 접기':'준비 노트'}</Button><Button className="planner-compare-toggle" aria-pressed={selected} onClick={onSelect} startIcon={<CompareArrowsRounded />} color={selected?'primary':'inherit'}>{selected?'비교 선택됨':'비교'}</Button></div>
    <Collapse in={open} unmountOnExit><div className="planner-editor" id={editorId}>
      <div className="planner-editor-title"><h4>신청 전 체크리스트</h4><span>{completed}/{CHECKS.length}</span></div>
      <div className="planner-checklist">{CHECKS.map((label,i)=><FormControlLabel key={label} label={label} control={<Checkbox size="small" disabled={busy} checked={Boolean(checks & (1<<i))} onChange={()=>setChecks(x=>x^(1<<i))} />} />)}</div>
      <TextField label="나만의 조사 메모" placeholder="주변 시세, 출퇴근 동선, 현장 방문 기록을 남겨보세요." multiline minRows={3} fullWidth value={note} disabled={busy} onChange={e=>setNote(e.target.value)} inputProps={{maxLength:2000}} helperText={`${note.length.toLocaleString()}/2,000 · 나에게만 보이는 메모`} sx={{my:2}} />
      <div className="planner-email-setting"><div><strong>관심 일정 이메일</strong><p>{!planner.loggedIn?'로그인하면 이메일 알림을 받을 수 있어요.':!planner.remindersEnabled?'이메일 알림 서비스를 준비하고 있어요.':'오늘·내일 일정을 하루 한 통으로 알려드려요.'}</p></div><Switch inputProps={{'aria-label':`${s.houseNm} 이메일 알림`}} checked={email} disabled={busy || !planner.loggedIn || !planner.remindersEnabled} onChange={e=>setEmail(e.target.checked)} /></div>
      <div className="planner-editor-footer"><span role="status">{dirty?'저장하지 않은 변경사항이 있어요.':'저장된 준비 상태입니다.'}</span><Button variant="contained" disableElevation disabled={busy || !dirty} onClick={save}>{busy?'저장 중…':'변경사항 저장'}</Button></div>
    </div></Collapse>
    <Menu anchorEl={()=>menuButtonRef.current} open={cardOverlay === 'menu'} onClose={()=>setMenu(null)}><MenuItem disabled={!s.events?.length} onClick={()=>{downloadCalendar([s]);setMenu(null);}}><CalendarMonthOutlined fontSize="small" sx={{mr:1}} />캘린더에 일정 추가</MenuItem><MenuItem disabled={busy} onClick={()=>{setConfirm(true);}} sx={{color:'error.main'}}>관심 공고 해제</MenuItem></Menu>
    <Dialog open={confirm} onClose={()=>{if(!busy)setConfirm(false);}} maxWidth="xs" fullWidth><DialogTitle>관심 공고를 해제할까요?</DialogTitle><DialogContent>이 공고의 체크리스트와 조사 메모도 함께 삭제됩니다.</DialogContent><DialogActions><Button disabled={busy} onClick={()=>setConfirm(false)}>취소</Button><Button disabled={busy} color="error" onClick={remove}>관심 해제</Button></DialogActions></Dialog>
  </article>;
}
