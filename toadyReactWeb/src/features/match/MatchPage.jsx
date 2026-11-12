import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Skeleton, Snackbar, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import EditOutlined from '@mui/icons-material/EditOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import TaskAltRounded from '@mui/icons-material/TaskAltRounded';
import BoltRounded from '@mui/icons-material/BoltRounded';
import IosShareRounded from '@mui/icons-material/IosShareRounded';
import { Main } from '@/views/main/Main';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { trackAction } from '@/features/analytics/trackAction';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { koreaToday } from '@/features/planner/plannerUtils';
import { useMyScore } from '@/features/score/myScore';
import { usePushStore } from '@/features/push/pushStore';
import { withZupzup, zupzupTopic, ZUPZUP_TOPICS } from '@/features/push/pushTopics';
import { evaluate, monthsFromAccountIndex, profileComplete, rankFeed } from './eligibilityRules';
import { clearProfile, saveProfile, useEligibilityProfile } from './eligibilityProfile';
import EligibilityForm, { accountChoice, ACCOUNT_CHOICES } from './EligibilityForm';
import { priceRange, receiptBadge, shortDate, TYPE_LABEL, useEligibilityFeed } from './matchShared';
import './match.css';

const SEO = {
  match: { title: '내게 맞는 청약 · 넣을 수 있는 공고만 모아보기 · TOADY', description: '사는 곳·무주택·통장·가족 조건을 30초 만에 고르면 지금 접수 중이거나 곧 접수할 아파트 청약 중 넣을 수 있는 공고와 가능한 특별공급을 알려 드려요.', path: '/match' },
  zupzup: { title: '무순위·줍줍 청약 모아보기 · 접수 중 잔여세대 · TOADY', description: '전국 APT 무순위·잔여세대·임의공급(줍줍) 공고를 접수 일정·분양가와 함께 모았어요. 새 줍줍 공고가 올라오면 알림으로 받아 보세요.', path: '/match/zupzup' }
};

function profileSummary(profile) {
  const account = ACCOUNT_CHOICES.find(([value]) => value === accountChoice(profile.accountMonths))?.[1];
  return [
    `${profile.sido} 거주`,
    profile.homeless ? '무주택' : profile.homes >= 2 ? '2주택 이상' : '1주택',
    profile.householder ? '세대주' : '세대원',
    profile.accountMonths < 0 ? '통장 없음' : `통장 ${account}`,
    { single: '미혼', married: '기혼', engaged: '예비 신혼부부' }[profile.marriage],
    profile.children > 0 ? `자녀 ${profile.children >= 3 ? '3명 이상' : `${profile.children}명`}` : null
  ].filter(Boolean);
}

function FeedCard({ item, result, today, onOpen }) {
  const badge = receiptBadge(item, today);
  const price = priceRange(item.units);
  const possible = result ? result.tracks.filter(t => t.status === 'ok' || t.status === 'check') : [];
  return <li><button type="button" className="match-card" onClick={() => onOpen(item.id)}>
    <div className="match-card-top">
      <span className={`match-receipt is-${badge.tone}`}>{badge.text}</span>
      {item.type !== 'apt' && <span className="match-type">{TYPE_LABEL[item.type]}</span>}
      {result && result.best !== 'unknown' && <span className={`match-verdict is-${result.best}`}>{result.best === 'ok' && <TaskAltRounded fontSize="inherit" />}{result.headline}</span>}
    </div>
    <strong className="match-card-title">{item.houseNm}</strong>
    <span className="match-card-meta">{[item.region, item.totalSupply ? `${item.totalSupply.toLocaleString('ko-KR')}세대` : null, `접수 ${shortDate(item.start)}${item.end && item.end !== item.start ? ` ~ ${shortDate(item.end)}` : ''}`].filter(Boolean).join(' · ')}</span>
    {price && <span className="match-card-price">최고 공급금액 <b>{price}</b></span>}
    {possible.length > 0 && <span className="match-tracks">{possible.slice(0, 4).map(t => <em key={t.key} className={`is-${t.status}`}>{t.label.replace(/ \d+세대$/, '')}</em>)}</span>}
    <ChevronRightRounded className="match-card-go" />
  </button></li>;
}

function FeedSkeleton() { return <ul className="match-list">{[0, 1, 2, 3].map(key => <li key={key}><Skeleton variant="rounded" height={128} /></li>)}</ul>; }

/** 줍줍 새 공고 알림. 권한 요청은 버튼을 눌렀을 때만 한다. */
function ZupzupAlert() {
  const push = usePushStore();
  const [state, setState] = useState(null);
  const current = push.subscribed ? zupzupTopic(push.scope.areas) : null;
  const canPush = push.platform.mode === 'ready' && push.enabled && push.permission !== 'denied';
  if (!canPush && !current) return null;
  // 알림을 꺼 둔 상태에서도 저장된 범위가 가득 차 있으면 줍줍 주제를 넣을 수 없다(조용히 빠지지 않게 미리 막는다).
  const full = !current && withZupzup(push.scope.areas, null).length >= 6;
  function watch(slug) {
    setState('busy');
    const scope = { ...push.scope, areas: withZupzup(push.scope.areas, slug) };
    (push.subscribed ? push.save(scope) : push.enable(scope)).then(() => setState(slug ? 'done' : null)).catch(error => setState(error.message));
  }
  return <aside className="match-alert">
    <NotificationsActiveOutlined />
    <div><strong>{current ? `${ZUPZUP_TOPICS.find(t => t.slug === current)?.label} 새 공고 알림을 받고 있어요` : '새 줍줍 공고를 놓치지 마세요'}</strong>
      <span>{current ? '새 무순위·잔여세대·임의공급 공고가 올라온 날 저녁에 한 번 알려 드려요.' : '접수 기간이 하루 이틀로 짧아요. 올라온 날 알림으로 알려 드릴게요.'}</span></div>
    <div className="match-alert-actions">
      {ZUPZUP_TOPICS.map(topic => <Button key={topic.slug} size="small" variant={current === topic.slug ? 'contained' : 'outlined'} disableElevation
        disabled={state === 'busy' || full} onClick={() => watch(current === topic.slug ? null : topic.slug)}>{topic.label}{current === topic.slug ? ' 끄기' : ''}</Button>)}
    </div>
    {full && <p className="match-alert-note">알림 범위가 가득 찼어요(최대 6개). 나의 청약 → 알림 설정에서 생활권을 하나 빼 주세요.</p>}
    {state && !['busy', 'done'].includes(state) && <p className="match-alert-note is-error" role="alert">{state}</p>}
  </aside>;
}

export default function MatchPage({ tab = 'match' }) {
  const location = useLocation(), navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const detailId = searchParams.get('subscriptSeq');
  const base = tab === 'zupzup' ? '/match/zupzup' : '/match';
  useDirectDetailHistory(Boolean(detailId), 'matchDetail', base);
  useEffect(() => {
    if (detailId) return undefined;
    updatePageSeo(SEO[tab]);
    return () => resetDefaultSeo();
  }, [detailId, tab]);

  const today = koreaToday();
  const profile = useEligibilityProfile();
  const complete = profileComplete(profile);
  const score = useMyScore();
  // 조건 수정은 히스토리 한 단계로 열어, 수정 중 뒤로가기가 페이지 이탈 대신 수정 취소가 되게 한다.
  const [editLayer, setEditLayer] = useHistoryOverlay('match-edit');
  const editing = Boolean(editLayer);
  const setEditing = open => setEditLayer(open ? true : null);
  // 필터는 주소(?filter=)에 둬서 상세·다른 화면에 갔다가 뒤로 와도 그대로 보이게 한다.
  const filter = ['ok', 'possible', 'all'].includes(searchParams.get('filter')) ? searchParams.get('filter') : 'possible';
  const setFilter = value => {
    const next = new URLSearchParams(searchParams);
    if (value === 'possible') next.delete('filter'); else next.set('filter', value);
    setSearchParams(next, { replace: true, state: location.state });
  };
  const [shareMessage, setShareMessage] = useState('');
  /** 개인 조건은 넣지 않고, 같은 진단을 해 볼 수 있는 주소만 공유한다. */
  async function share(count) {
    const url = new URL(tab === 'zupzup' ? '/match/zupzup' : '/match', window.location.origin).href;
    const data = tab === 'zupzup'
      ? { title: '무순위·줍줍 청약 모아보기 · TOADY', text: `지금 접수 중·예정인 줍줍 청약 ${count}건, 같이 확인해 봐요.`, url }
      : { title: '내게 맞는 청약 · TOADY', text: `30초 자격 진단으로 지금 넣을 수 있는 청약을 찾아봤어요(${count}건). 내 조건으로도 확인해 보세요.`, url };
    try {
      if (navigator.share) { await navigator.share(data); trackAction('share', 'tool/match'); return; }
      await navigator.clipboard.writeText(`${data.text} ${url}`); trackAction('share', 'tool/match'); setShareMessage('공유 문구와 링크를 복사했어요.');
    } catch (error) { if (error?.name !== 'AbortError') setShareMessage('링크를 복사하지 못했어요.'); }
  }
  const feed = useEligibilityFeed();
  const items = useMemo(() => (Array.isArray(feed.data?.items) ? feed.data.items : []), [feed.data]);

  const openDetail = id => {
    const next = new URLSearchParams(searchParams); next.set('subscriptSeq', id);
    navigate(`${base}?${next}`, { state: { ...location.state, matchDetail: true } });
  };
  const closeDetail = () => {
    if (location.state?.matchDetail) { navigate(-1); return; }
    const next = new URLSearchParams(searchParams); next.delete('subscriptSeq'); next.delete('tabType');
    setSearchParams(next, { replace: true });
  };

  const ranked = useMemo(() => (complete ? rankFeed(items.filter(item => item.type === 'apt'), profile, today) : []), [complete, items, profile, today]);
  const counts = useMemo(() => ranked.reduce((all, { result }) => ({ ...all, [result.best]: (all[result.best] || 0) + 1 }), {}), [ranked]);
  const shown = ranked.filter(({ result }) => (filter === 'all' ? true : filter === 'ok' ? result.best === 'ok' : result.best === 'ok' || result.best === 'check'));
  const zupzup = useMemo(() => items.filter(item => item.type !== 'apt')
    .sort((a, b) => (a.status === 'OPEN' ? 0 : 1) - (b.status === 'OPEN' ? 0 : 1) || String(a.status === 'OPEN' ? a.end : a.start).localeCompare(String(b.status === 'OPEN' ? b.end : b.start))), [items]);

  const prefill = useMemo(() => {
    const months = monthsFromAccountIndex(score.selection?.account);
    return profile || (months !== null ? { accountMonths: accountChoice(months) } : {});
  }, [profile, score.selection]);
  const showForm = tab === 'match' && (!complete || editing);
  function save(form) {
    const wasComplete = complete;
    saveProfile(form);
    if (editing) setEditing(false);
    if (!wasComplete) trackAction('match_save', 'tool/match');
    // 긴 진단 양식이 결과 목록으로 바뀐 뒤에 맨 위로 올린다(바꾸기 전에 올리면 줄어든 화면 아래에 머문다).
    requestAnimationFrame(() => window.scrollTo({ top: 0 }));
  }

  return <div className="match-page">
    <header className="match-hero">
      <span className="match-eyebrow">{tab === 'zupzup' ? '무순위·줍줍' : '맞춤 청약'}</span>
      <h1>{tab === 'zupzup' ? '지금 접수할 수 있는 줍줍 청약' : '지금 내가 넣을 수 있는 청약'}</h1>
      <p>{tab === 'zupzup' ? '계약 포기·부적격으로 다시 나온 무순위·잔여세대와 임의공급 공고예요. 접수 기간이 짧으니 일정부터 확인하세요.'
        : '조건을 한 번만 고르면 전국의 접수 중·예정 아파트 청약 중 넣을 수 있는 공고와 가능한 특별공급을 골라 드려요.'}</p>
    </header>
    <nav className="match-tabs" aria-label="맞춤 청약 메뉴">
      <Link to="/match" aria-current={tab === 'match' ? 'page' : undefined} className={tab === 'match' ? 'is-active' : ''}>내게 맞는 공고</Link>
      <Link to="/match/zupzup" aria-current={tab === 'zupzup' ? 'page' : undefined} className={tab === 'zupzup' ? 'is-active' : ''}><BoltRounded fontSize="inherit" />무순위·줍줍{zupzup.length > 0 && <em>{zupzup.length}</em>}</Link>
    </nav>

    {tab === 'match' && <>
      {showForm ? <section className="match-section" aria-label="자격 진단">
        {!complete && <div className="match-intro"><strong>30초 자격 진단</strong><span>필수 질문 6개만 답하면 돼요. 가점 계산기에서 저장한 통장 기간은 미리 채워 드려요.</span></div>}
        <EligibilityForm initial={prefill} onSave={save} onCancel={complete ? () => setEditing(false) : null} />
      </section> : <>
        <div className="match-profile">
          <div className="match-profile-chips">{profileSummary(profile).map(text => <span key={text}>{text}</span>)}</div>
          <div className="match-profile-actions">
            <Button size="small" startIcon={<IosShareRounded />} onClick={() => share((counts.ok || 0) + (counts.check || 0))}>공유</Button>
            <Button size="small" startIcon={<EditOutlined />} onClick={() => setEditing(true)}>조건 수정</Button>
          </div>
        </div>
        {feed.isLoading ? <FeedSkeleton /> : feed.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => feed.refetch()}>다시 시도</Button>}>공고를 불러오지 못했어요.</Alert> : <>
          <div className="match-filter">
            <ToggleButtonGroup exclusive size="small" value={filter} onChange={(_, value) => value && setFilter(value)} aria-label="신청 가능성 필터">
              <ToggleButton value="ok">가능성 높음 {counts.ok || 0}</ToggleButton>
              <ToggleButton value="possible">확인 필요 포함 {(counts.ok || 0) + (counts.check || 0)}</ToggleButton>
              <ToggleButton value="all">전체 {ranked.length}</ToggleButton>
            </ToggleButtonGroup>
          </div>
          {shown.length ? <ul className="match-list">{shown.map(({ item, result }) => <FeedCard key={item.id} item={item} result={result} today={today} onOpen={openDetail} />)}</ul>
            : <div className="match-empty">
              <p>{ranked.length ? '조건에 맞는 접수 중·예정 공고가 지금은 없어요.' : '지금 접수 중이거나 60일 안에 접수하는 아파트 공고가 없어요.'}</p>
              <div>{filter !== 'all' && ranked.length > 0 && <Button size="small" onClick={() => setFilter('all')}>전체 공고 보기</Button>}
                <Button size="small" component={Link} to="/match/zupzup">무순위·줍줍 보기</Button></div>
            </div>}
          <p className="match-disclaimer">공통 요건만 단순화해 본 신청 가능성이에요. 소득·자산·예치금·거주기간 등 세부 자격은 모집공고 원문과 청약홈에서 꼭 확인하세요. 카드를 누르면 공고별 판정 이유를 볼 수 있어요. 기준이 궁금하면 <Link to="/faq">청약 자주 묻는 질문</Link>을 참고하세요.</p>
          <Button size="small" color="inherit" className="match-reset" onClick={() => clearProfile()}>진단 기록 지우기</Button>
        </>}
      </>}
    </>}

    {tab === 'zupzup' && <>
      <ZupzupAlert />
      {zupzup.length > 0 && <div className="match-share-row"><Button size="small" startIcon={<IosShareRounded />} onClick={() => share(zupzup.length)}>줍줍 목록 공유</Button></div>}
      {feed.isLoading ? <FeedSkeleton /> : feed.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => feed.refetch()}>다시 시도</Button>}>공고를 불러오지 못했어요.</Alert>
        : zupzup.length ? <ul className="match-list">{zupzup.map(item => <FeedCard key={item.id} item={item} result={complete ? evaluate(profile, item, today) : null} today={today} onOpen={openDetail} />)}</ul>
          : <div className="match-empty"><p>지금 접수 중이거나 예정된 무순위·줍줍 공고가 없어요. 새 공고는 매일 반영돼요.</p></div>}
      <p className="match-disclaimer">2025년 6월부터 무순위(사후) 청약은 원칙적으로 무주택 세대구성원만 신청할 수 있어요. 거주지역·재당첨 제한은 공고마다 달라 원문을 확인하세요(<Link to="/faq#zupzup-who">무순위 자주 묻는 질문</Link>).{!complete && <> <Link to="/match">자격 진단</Link>을 하면 공고마다 신청 가능성을 함께 보여 드려요.</>}</p>
    </>}

    {detailId && <Main detailOnly onDetailClose={closeDetail} />}
    <Snackbar open={Boolean(shareMessage)} autoHideDuration={4000} onClose={() => setShareMessage('')} message={shareMessage} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} />
  </div>;
}

