import { useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, MenuItem, Snackbar, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import IosShareRounded from '@mui/icons-material/IosShareRounded';
import RestartAltRounded from '@mui/icons-material/RestartAltRounded';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import { Main } from '@/views/main/Main';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { trackAction } from '@/features/analytics/trackAction';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { MAX_SCORE, SCORE_PARTS, normalizeSelection, scoreOf, selectionFromParams, selectionToParams } from './scoreRules';
import { saveMyScore, clearMyScore, useMyScore } from './myScore';
import ScoreBenchmarkSearch from './ScoreBenchmarkSearch';
import PeriodByDate from './PeriodByDate';
import './score.css';

const HELP = {
  housing: '만 30세부터 계산해요(30세 전에 혼인했다면 혼인신고일부터). 본인과 배우자 모두 집이 없어야 하고, 소형·저가 주택 등 예외는 모집공고 기준을 확인해 주세요.',
  dependents: '본인은 빼고 세요. 배우자, 같은 세대의 미혼 자녀, 3년 이상 같은 세대로 등록된 부모님 등이 해당돼요.',
  account: '청약통장에 처음 가입한 날부터 입주자모집공고일까지의 기간이에요. 여기서는 오늘을 기준으로 계산해요.'
};
export default function ScoreCalculator() {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation(), navigate = useNavigate();
  const detailId = searchParams.get('subscriptSeq');
  useDirectDetailHistory(Boolean(detailId), 'scoreDetail', '/score');
  const saved = useMyScore();
  // 공유받은 주소(?h=&d=&a=)는 받은 사람의 저장값을 덮어쓰지 않고 보여만 준다. 항목을 바꾸면 그때 내 점수로 저장한다.
  const [shared] = useState(() => selectionFromParams(searchParams));
  const [draft, setDraft] = useState(() => shared || saved.selection || {});
  const [message, setMessage] = useState(null);
  // 날짜로 계산(처음 쓰는 사람) / 직접 고르기(이미 고른 값이 있으면). 날짜 입력값은 저장하지 않는다.
  const [resetKey, setResetKey] = useState(0);
  const [modes, setModes] = useState(() => ({ housing: draft.housing === undefined ? 'dates' : 'select', account: draft.account === undefined ? 'dates' : 'select' }));
  const score = scoreOf(draft);
  const filled = SCORE_PARTS.filter(part => draft[part.key] !== undefined && draft[part.key] !== null && draft[part.key] !== '').length;
  const same = (a, b) => Boolean(a && b) && a.housing === b.housing && a.dependents === b.dependents && a.account === b.account;
  // 공유받은 점수를 보고 있는 동안(내 저장값과 다를 때)은 '저장했어요' 대신 공유 점수임을 알린다.
  const showingShared = Boolean(shared) && same(normalizeSelection(draft), shared) && !same(saved.selection, shared);

  useEffect(() => {
    if (detailId) return undefined;
    updatePageSeo({ title: '청약 가점 계산기 · 84점 만점 내 점수와 당첨가점 비교 · TOADY', description: '무주택 기간·부양가족 수·청약통장 가입기간만 고르면 청약 가점을 계산하고, 서울·경기 등 전국 시·군·구별 공개 당첨가점과 비교해 드려요.', path: '/score' });
    return () => resetDefaultSeo();
  }, [detailId]);

  const change = (key, value) => {
    const next = { ...draft };
    // null/undefined 는 그 항목을 비운다(날짜 입력이 부족해진 경우). 항목이 비면 점수를 계산·저장하지 않는다.
    if (value === null || value === undefined) delete next[key]; else next[key] = value;
    setDraft(next);
    if (normalizeSelection(next)) { const first = !saved.selection; saveMyScore(next); if (first) trackAction('score_save', 'tool/score'); }
    // 날짜 입력이 부족해져 항목이 비면 예전에 계산된 점수도 지운다. 공고 상세의 '내 가점 비교'가 잘못된 점수로 보이지 않게 하기 위해서다.
    // 공유받은 점수를 보는 중에는 내 저장값을 건드리지 않는다.
    else if ((value === null || value === undefined) && saved.selection && !showingShared) clearMyScore();
    // 공유 주소(h·d·a)를 지우지 않으면 새로고침·뒤로가기 때 공유받은 값이 다시 적용돼 방금 고친 값이 덮인다.
    clearShared();
  };
  const clearShared = () => { if (searchParams.has('h')) { const next = new URLSearchParams(searchParams); ['h', 'd', 'a'].forEach(key => next.delete(key)); setSearchParams(next, { replace: true }); } };
  // 공유 점수를 보는 중이면 내 저장값은 지우지 않고 내 점수로 돌아간다.
  const reset = () => { if (showingShared) { setDraft(saved.selection || {}); clearShared(); return; } setDraft({}); setResetKey(value => value + 1); clearMyScore(); clearShared(); };
  const share = async () => {
    const url = new URL('/score', window.location.origin);
    selectionToParams(draft).forEach((value, key) => url.searchParams.set(key, value));
    const data = { title: `내 청약 가점 ${score.total}점 · TOADY`, text: `청약 가점 ${score.total}점 (무주택 ${score.housing} · 부양가족 ${score.dependents} · 통장 ${score.account})`, url: url.href };
    try {
      if (navigator.share) { await navigator.share(data); trackAction('share', 'tool/score'); return; }
      await navigator.clipboard.writeText(url.href); trackAction('share', 'tool/score'); setMessage('결과 링크를 복사했어요.');
    } catch (error) { if (error?.name !== 'AbortError') setMessage('링크를 복사하지 못했어요.'); }
  };
  const openDetail = id => {
    const next = new URLSearchParams(searchParams); next.set('subscriptSeq', id); next.set('tabType', 'rate');
    navigate(`/score?${next}`, { state: { ...location.state, scoreDetail: true } });
  };
  const closeDetail = () => {
    if (location.state?.scoreDetail) { navigate(-1); return; }
    const next = new URLSearchParams(searchParams); next.delete('subscriptSeq'); next.delete('tabType');
    setSearchParams(next, { replace: true });
  };

  return <div className="score-page">
    <header className="score-hero">
      <span className="score-eyebrow">청약 가점 계산기</span>
      <h1>내 청약 가점은 몇 점일까요?</h1>
      <p>생년월일과 통장 가입일만 넣으면 기간을 알아서 골라 84점 만점으로 계산하고, 전국 지역별 공개 당첨가점과 비교해 드려요.</p>
    </header>

    <div className="score-layout">
      <section className="score-form" aria-label="가점 항목 입력">
        {SCORE_PARTS.map((part, index) => {
          const value = draft[part.key];
          const points = value !== undefined && value !== null && value !== '' ? part.options[value]?.points : null;
          return <div className="score-field" key={part.key}>
            <div className="score-field-head">
              <span className="score-step">{index + 1}</span>
              <h2 id={`score-${part.key}`}>{part.title}</h2>
              <span className={`score-points${points === null ? ' is-empty' : ''}`}>{points === null ? `최대 ${part.max}점` : <><b>{points}</b> / {part.max}점</>}</span>
            </div>
            {part.key === 'dependents'
              ? <ToggleButtonGroup exclusive value={value ?? null} onChange={(_, next) => next !== null && change(part.key, next)} className="score-dependents" aria-labelledby={`score-${part.key}`}>
                  {part.options.map((option, optionIndex) => <ToggleButton key={option.label} value={optionIndex} aria-label={`${option.label} ${option.points}점`}>{option.label.replace('명 이상', '+').replace('명', '')}<small>{option.points}점</small></ToggleButton>)}
                </ToggleButtonGroup>
              : <>
                <ToggleButtonGroup exclusive size="small" value={modes[part.key]} onChange={(_, mode) => mode && setModes(previous => ({ ...previous, [part.key]: mode }))} className="score-choice score-mode" aria-label={`${part.title} 입력 방식`}>
                  <ToggleButton value="dates">날짜로 계산</ToggleButton><ToggleButton value="select">직접 고르기</ToggleButton>
                </ToggleButtonGroup>
                {/* '직접 고르기'로 바꿔도 날짜 입력값을 잃지 않도록 날짜 입력은 숨기기만 한다. */}
                <PeriodByDate key={`${part.key}-${resetKey}`} kind={part.key} options={part.options} value={value} active={modes[part.key] === 'dates'} onPick={index => change(part.key, index)} />
                {modes[part.key] === 'select' && <TextField select fullWidth size="small" value={value ?? ''} onChange={event => change(part.key, Number(event.target.value))} inputProps={{ 'aria-labelledby': `score-${part.key}` }}
                  SelectProps={{ displayEmpty: true, MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
                  <MenuItem value="" disabled>기간을 선택해 주세요</MenuItem>
                  {part.options.map((option, optionIndex) => <MenuItem key={option.label} value={optionIndex}><span className="score-option">{option.label}{option.hint && <small>{option.hint}</small>}</span><span className="score-option-points">{option.points}점</span></MenuItem>)}
                </TextField>}
              </>}
            <p className="score-help">{HELP[part.key]}</p>
          </div>;
        })}
      </section>

      <aside className={`score-result${score ? " has-score" : ""}`} aria-live="polite" aria-label="계산 결과">
        {score ? <>
          <span className="score-result-label">{showingShared ? '공유받은 가점' : '내 청약 가점'}</span>
          <div className="score-total"><strong>{score.total}</strong><span>/ {MAX_SCORE}점</span></div>
          <div className="score-stack" role="img" aria-label={`무주택 ${score.housing}점, 부양가족 ${score.dependents}점, 통장 ${score.account}점`}>
            <span className="is-housing" style={{ width: `${(score.housing / MAX_SCORE) * 100}%` }} />
            <span className="is-dependents" style={{ width: `${(score.dependents / MAX_SCORE) * 100}%` }} />
            <span className="is-account" style={{ width: `${(score.account / MAX_SCORE) * 100}%` }} />
          </div>
          <dl className="score-breakdown">
            {SCORE_PARTS.map(part => <div key={part.key} className={`is-${part.key}`}><dt>{part.title}</dt><dd><b>{score[part.key]}</b> / {part.max}</dd></div>)}
          </dl>
          <p className="score-saved">{showingShared ? (saved.selection ? `내 저장 가점은 ${scoreOf(saved.selection).total}점이에요. 항목을 바꾸면 내 가점으로 저장돼요.` : '항목을 바꾸면 내 가점으로 저장돼요.') : <><CheckCircleRounded fontSize="inherit" />이 브라우저에 저장했어요. 공고의 경쟁률 탭에서 내 점수와 비교해 드려요.</>}</p>
          <div className="score-actions">
            <Button variant="contained" disableElevation startIcon={<IosShareRounded />} onClick={share}>결과 공유</Button>
            <Button variant="outlined" startIcon={<RestartAltRounded />} onClick={reset}>{showingShared && saved.selection ? '내 가점 보기' : '다시 계산'}</Button>
          </div>
        </> : <div className="score-result-empty">
          <span className="score-result-label">내 청약 가점</span>
          <div className="score-total is-empty"><strong>{filled}</strong><span>/ 3 항목 선택</span></div>
          <p>세 항목을 모두 고르면 점수가 바로 계산돼요. 입력한 내용은 서버로 보내지 않아요.</p>
        </div>}
      </aside>
    </div>

    <ScoreBenchmarkSearch total={score?.total} onOpen={openDetail} />

    <section className="score-section score-notes" aria-labelledby="score-notes-heading">
      <h2 id="score-notes-heading">알아두면 좋은 점</h2>
      <ul>
        <li><strong>가점제와 추첨제</strong><span>주택형과 지역에 따라 가점으로 뽑는 비율과 추첨 비율이 달라요. 가점이 낮아도 추첨 물량으로 당첨될 수 있어요.</span></li>
        <li><strong>공개 당첨가점은 지난 결과예요</strong><span>최저·최고 당첨가점은 주택형·거주지역 전체를 모은 범위예요. 다음 공고의 당첨을 보장하거나 예측하지 않아요.</span></li>
        <li><strong>정확한 기준은 모집공고에서</strong><span>무주택 인정 예외, 부양가족 인정 범위 등 세부 기준은 공고마다 확인해 주세요. 청약홈에서 공식 가점 확인도 할 수 있어요.</span></li>
      </ul>
    </section>

    {detailId && <Main detailOnly onDetailClose={closeDetail} />}
    <Snackbar open={Boolean(message)} autoHideDuration={2500} onClose={() => setMessage(null)} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
      <Alert severity="success" variant="filled" onClose={() => setMessage(null)} sx={{ borderRadius: 2 }}>{message || ''}</Alert>
    </Snackbar>
  </div>;
}
