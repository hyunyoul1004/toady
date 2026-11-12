import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, MenuItem, Skeleton, Snackbar, TextField, ToggleButton, ToggleButtonGroup } from '@mui/material';
import IosShareRounded from '@mui/icons-material/IosShareRounded';
import RestartAltRounded from '@mui/icons-material/RestartAltRounded';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import apiClient from '@/api/client';
import { Main } from '@/views/main/Main';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { trackAction } from '@/features/analytics/trackAction';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { MAX_SCORE, SCORE_PARTS, compareToRange, fmt, normalizeSelection, scoreOf, selectionFromParams, selectionToParams } from './scoreRules';
import { saveMyScore, clearMyScore, useMyScore } from './myScore';
import ScoreRangeBar from './ScoreRangeBar';
import './score.css';

const HELP = {
  housing: '만 30세부터 계산해요. 30세 전에 혼인했다면 혼인신고일부터예요. 세대원 모두 무주택이어야 해요.',
  dependents: '본인은 빼고 세요. 배우자, 같은 세대의 미혼 자녀, 3년 이상 같은 세대로 등록된 부모님 등이 해당돼요.',
  account: '청약통장에 처음 가입한 날부터 입주자모집공고일까지의 기간이에요.'
};
const AREA_KEY = 'toady.score.area';
const readArea = () => { try { return sessionStorage.getItem(AREA_KEY) || 'recent'; } catch { return 'recent'; } };

function Benchmarks({ total, onOpen }) {
  const query = useQuery({ queryKey: ['scoreBenchmarks'], queryFn: () => apiClient.get('/api/v1/public/score-benchmarks'), staleTime: 1800000, retry: 1 });
  const [area, setArea] = useState(readArea);
  const data = query.data;
  const tabs = useMemo(() => [{ slug: 'recent', label: '전국 최근', items: data?.recent || [] }, ...(data?.areas || [])], [data]);
  const current = tabs.find(tab => tab.slug === area) || tabs[0];
  const choose = value => { if (!value) return; setArea(value); try { sessionStorage.setItem(AREA_KEY, value); } catch { /* 무시 */ } };
  return <section className="score-section" aria-labelledby="benchmark-heading">
    <div className="score-section-heading">
      <div><h2 id="benchmark-heading">최근 공개 당첨가점과 비교</h2><p>청약홈이 공개한 아파트 공고별 최저·최고 당첨가점이에요. 공고를 누르면 주택형별 경쟁률과 가점을 볼 수 있어요.</p></div>
    </div>
    {query.isLoading ? <div className="score-bench-list"><Skeleton variant="rounded" height={64} /><Skeleton variant="rounded" height={64} /><Skeleton variant="rounded" height={64} /></div>
      : query.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => query.refetch()}>다시 시도</Button>}>당첨가점 자료를 불러오지 못했어요.</Alert>
      : <>
        <ToggleButtonGroup exclusive size="small" value={current.slug} onChange={(_, value) => choose(value)} className="score-area-tabs" aria-label="비교할 생활권">
          {tabs.map(tab => <ToggleButton key={tab.slug} value={tab.slug}>{tab.label}{tab.slug !== 'recent' && <em>{tab.items.length}</em>}</ToggleButton>)}
        </ToggleButtonGroup>
        {current.items.length ? <ul className="score-bench-list">{current.items.map(item => {
          const result = compareToRange(total, { low: item.low, high: item.high });
          return <li key={item.id}><button type="button" onClick={() => onOpen(item.id)}>
            <div className="score-bench-title"><strong>{item.houseNm}</strong><span>{item.receiptDate.slice(0, 7).replace('-', '.')} 접수{item.low !== null && <> · 최저 <b>{fmt(item.low)}</b></>}{item.high !== null && <> · 최고 <b>{fmt(item.high)}</b>점</>}</span></div>
            <ScoreRangeBar low={item.low} high={item.high} mine={total} />
            {Number.isFinite(total) && <span className={`score-bench-status is-${result.status}`}>{result.text}</span>}
            <ChevronRightRounded className="score-bench-go" />
          </button></li>;
        })}</ul> : <p className="score-empty">최근 {data?.years || 3}년 동안 {current.label}에서 공개된 아파트 당첨가점이 없어요. 다른 생활권이나 전국 최근을 확인해 보세요.</p>}
        {!Number.isFinite(total) && current.items.length > 0 && <p className="score-hint">위에서 내 가점을 계산하면 공고마다 비교 결과를 함께 보여드려요.</p>}
      </>}
  </section>;
}

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
  const score = scoreOf(draft);
  const filled = SCORE_PARTS.filter(part => draft[part.key] !== undefined && draft[part.key] !== null && draft[part.key] !== '').length;
  const same = (a, b) => Boolean(a && b) && a.housing === b.housing && a.dependents === b.dependents && a.account === b.account;
  // 공유받은 점수를 보고 있는 동안(내 저장값과 다를 때)은 '저장했어요' 대신 공유 점수임을 알린다.
  const showingShared = Boolean(shared) && same(normalizeSelection(draft), shared) && !same(saved.selection, shared);

  useEffect(() => {
    if (detailId) return undefined;
    updatePageSeo({ title: '청약 가점 계산기 · 84점 만점 내 점수와 당첨가점 비교 · TOADY', description: '무주택 기간·부양가족 수·청약통장 가입기간만 고르면 청약 가점을 계산하고, 운정·동탄·검단 등 생활권별 최근 공개 당첨가점과 비교해 드려요.', path: '/score' });
    return () => resetDefaultSeo();
  }, [detailId]);

  const change = (key, value) => {
    const next = { ...draft, [key]: value };
    setDraft(next);
    if (normalizeSelection(next)) { const first = !saved.selection; saveMyScore(next); if (first) trackAction('score_save', 'tool/score'); }
  };
  const clearShared = () => { if (searchParams.has('h')) { const next = new URLSearchParams(searchParams); ['h', 'd', 'a'].forEach(key => next.delete(key)); setSearchParams(next, { replace: true }); } };
  // 공유 점수를 보는 중이면 내 저장값은 지우지 않고 내 점수로 돌아간다.
  const reset = () => { if (showingShared) { setDraft(saved.selection || {}); clearShared(); return; } setDraft({}); clearMyScore(); clearShared(); };
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
      <p>세 가지만 고르면 84점 만점 기준으로 계산하고, 생활권별 최근 당첨가점과 비교해 드려요.</p>
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
              : <TextField select fullWidth size="small" value={value ?? ''} onChange={event => change(part.key, Number(event.target.value))} inputProps={{ 'aria-labelledby': `score-${part.key}` }}
                  SelectProps={{ displayEmpty: true, MenuProps: { PaperProps: { sx: { maxHeight: 360 } } } }}>
                  <MenuItem value="" disabled>기간을 선택해 주세요</MenuItem>
                  {part.options.map((option, optionIndex) => <MenuItem key={option.label} value={optionIndex}><span className="score-option">{option.label}{option.hint && <small>{option.hint}</small>}</span><span className="score-option-points">{option.points}점</span></MenuItem>)}
                </TextField>}
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

    <Benchmarks total={score?.total} onOpen={openDetail} />

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
