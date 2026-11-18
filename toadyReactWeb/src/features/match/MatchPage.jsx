import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Skeleton, Snackbar, ToggleButton, ToggleButtonGroup } from '@mui/material';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import EditOutlined from '@mui/icons-material/EditOutlined';
import NotificationsActiveOutlined from '@mui/icons-material/NotificationsActiveOutlined';
import TaskAltRounded from '@mui/icons-material/TaskAltRounded';
import BoltRounded from '@mui/icons-material/BoltRounded';
import IosShareRounded from '@mui/icons-material/IosShareRounded';
import { Main } from '@/views/main/Main';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { useHistoryOverlay, detailCloseSteps } from '@/hooks/useHistoryOverlay';
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
import { gapBadge, sortZupzup } from './priceGapRules';
import './match.css';
import { shareLink, toolShareText } from '@/utils/share.util';

// 탭별 검색엔진용 제목·설명. 상세를 열지 않았을 때만 적용한다.
const SEO = {
  match: { title: '내게 맞는 청약 · 넣을 수 있는 공고만 모아보기 · TOADY', description: '사는 곳·무주택·통장·가족 조건을 30초 만에 고르면 지금 접수 중이거나 곧 접수할 아파트 청약 중 넣을 수 있는 공고와 가능한 특별공급을 알려 드려요.', path: '/match' },
  zupzup: { title: '무순위·줍줍 청약 모아보기 · 접수 중 잔여세대 · TOADY', description: '전국 APT 무순위·잔여세대·임의공급(줍줍) 공고를 접수 일정·분양가와 함께 모았어요. 새 줍줍 공고가 올라오면 알림으로 받아 보세요.', path: '/zupzup' }
};

/** 저장된 진단 값을 '서울 거주 · 무주택 · 세대주 …' 같은 짧은 칩 문구 목록으로 바꾼다. */
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

/**
 * 목록의 공고 카드 한 장. 접수 상태, 판정 요약, 공급금액, 시세 비교 뱃지, 가능한 신청 유형을 보여준다.
 * result가 null이면(진단 전 줍줍 목록) 판정 부분은 빼고 그린다. 누르면 onOpen으로 상세를 연다.
 */
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
    {gapBadge(item.gap) && <span className={`match-gap-badge is-${item.gap.tone}`}>{gapBadge(item.gap).text}</span>}
    {possible.length > 0 && <span className="match-tracks">{possible.slice(0, 4).map(t => <em key={t.key} className={`is-${t.status}`}>{t.label.replace(/ \d+세대$/, '')}</em>)}</span>}
    <ChevronRightRounded className="match-card-go" />
  </button></li>;
}

// 공고를 받는 동안 보여 줄 회색 자리 표시.
function FeedSkeleton() { return <ul className="match-list">{[0, 1, 2, 3].map(key => <li key={key}><Skeleton variant="rounded" height={128} /></li>)}</ul>; }

/**
 * 줍줍 새 공고 알림. 권한 요청은 버튼을 눌렀을 때만 한다.
 * 푸시 구독 상태는 zustand 스토어(usePushStore)에 있다. 줍줍 주제는 구독 범위(scope.areas)에 한 칸으로 넣는다.
 * state: null(평소) · 'busy'(저장 중) · 'done'(완료) · 그 밖의 문자열(오류 메시지).
 */
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

/**
 * 맞춤 청약 화면(/match)과 무순위·줍줍 화면(/match/zupzup). tab으로 둘을 나눈다.
 * - match: 자격 진단 양식 → 저장 후 접수 중·예정 APT 공고를 신청 가능성 순으로 보여준다.
 * - zupzup: 무순위·잔여세대·임의공급 공고 목록과 새 공고 알림.
 * 공고는 useEligibilityFeed(서버), 진단 값은 이 브라우저(eligibilityProfile), 통장 기간 미리 채우기는 가점 계산기(useMyScore).
 * 카드를 누르면 ?subscriptSeq=로 주소를 바꿔 같은 화면 위에 공고 상세(Main detailOnly)를 띄운다.
 */
export default function MatchPage({ tab = 'match' }) {
  const location = useLocation(), navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const detailId = searchParams.get('subscriptSeq');
  const base = tab === 'zupzup' ? '/match/zupzup' : '/match';
  // 공유 링크로 상세가 바로 열렸을 때 목록을 한 단계 앞에 끼워 넣어, 첫 뒤로가기가 사이트 이탈 대신 상세 닫기가 되게 한다.
  useDirectDetailHistory(Boolean(detailId), 'matchDetail', base);
  // 페이지 제목·설명을 바꾸고, 화면을 떠나거나 조건이 바뀌면 cleanup(return 함수)에서 기본값으로 되돌린다.
  // 의존성 배열 [detailId, tab]: 이 두 값이 바뀔 때만 다시 실행된다. 상세가 열려 있으면 상세가 제목을 정하므로 건너뛴다.
  useEffect(() => {
    if (detailId) return undefined;
    updatePageSeo(SEO[tab]);
    return () => resetDefaultSeo();
  }, [detailId, tab]);

  const today = koreaToday();
  const profile = useEligibilityProfile();
  const complete = profileComplete(profile);
  const score = useMyScore();
  // useHistoryOverlay: 팝업 상태를 location.state.overlayLayers에 넣고 히스토리를 한 단계 쌓는다.
  // 그래서 브라우저 뒤로가기를 누르면 페이지를 떠나지 않고 팝업만 닫힌다.
  // 조건 수정은 히스토리 한 단계로 열어, 수정 중 뒤로가기가 페이지 이탈 대신 수정 취소가 되게 한다.
  const [editLayer, setEditLayer] = useHistoryOverlay('match-edit');
  const editing = Boolean(editLayer);
  const setEditing = open => setEditLayer(open ? true : null);
  // 진단 기록 지우기는 되돌릴 수 없어 확인 창을 한 단계 띄운다(뒤로가기로 취소).
  const [clearLayer, setClearLayer] = useHistoryOverlay('match-clear');
  // 필터는 주소(?filter=)에 둬서 상세·다른 화면에 갔다가 뒤로 와도 그대로 보이게 한다.
  const filter = ['ok', 'possible', 'all'].includes(searchParams.get('filter')) ? searchParams.get('filter') : 'possible';
  const setFilter = value => {
    const next = new URLSearchParams(searchParams);
    if (value === 'possible') next.delete('filter'); else next.set('filter', value);
    // replace: 필터를 바꿀 때마다 히스토리가 쌓이지 않게 현재 항목을 바꿔 쓴다. state는 그대로 넘겨 열린 팝업 정보를 잃지 않는다.
    setSearchParams(next, { replace: true, state: location.state });
  };
  const [shareMessage, setShareMessage] = useState('');
  /** 개인 조건은 넣지 않고, 같은 진단을 해 볼 수 있는 주소만 공유한다. 휴대폰은 기본 공유 창, 안 되면 클립보드 복사. */
  async function share(count) {
    const url = new URL(tab === 'zupzup' ? '/match/zupzup' : '/match', window.location.origin).href;
    // 공고 공유와 같은 형식: 한 줄 문구 + 링크(복사 시 '문구\n링크'). 사용자가 공유 창을 닫으면(cancelled) 아무 안내도 하지 않는다.
    const text = toolShareText(tab === 'zupzup' ? `지금 접수 중·예정인 줍줍 청약 ${count}건` : `30초 자격 진단으로 찾은 넣을 수 있는 청약 ${count}건`);
    const result = await shareLink({ text, url });
    if (result === 'shared' || result === 'copied') trackAction('share', 'tool/match');
    if (result === 'copied') setShareMessage('공유 문구와 링크를 복사했어요.');
    else if (result === 'failed') setShareMessage('링크를 복사하지 못했어요.');
  }
  const feed = useEligibilityFeed();
  const items = useMemo(() => (Array.isArray(feed.data?.items) ? feed.data.items : []), [feed.data]);

  // 상세 열기: 주소에 subscriptSeq를 붙여 새 히스토리로 이동한다. state.matchDetail은 '목록에서 연 상세'라는 표시다.
  // overlayLayers를 비워 목록에서 열려 있던 팝업이 상세 위로 따라오지 않게 한다.
  const openDetail = id => {
    const next = new URLSearchParams(searchParams); next.set('subscriptSeq', id);
    navigate(`${base}?${next}`, { state: { ...location.state, overlayLayers: [], matchDetail: true } });
  };
  // 상세 닫기: 목록에서 열었으면 그만큼 뒤로 가서(앞으로가기도 자연스럽게) 닫고,
  // 그렇지 않으면 주소에서 상세 값만 지워 현재 항목을 바꿔 쓴다.
  const closeDetail = () => {
    if (location.state?.matchDetail) { navigate(-detailCloseSteps(location.state)); return; }
    const next = new URLSearchParams(searchParams); next.delete('subscriptSeq'); next.delete('tabType');
    setSearchParams(next, { replace: true });
  };

  // useMemo: 판정·정렬은 공고 수만큼 계산하므로, 진단 값이나 공고가 바뀔 때만 다시 한다.
  // ranked는 APT만, counts는 상태별(ok·check·no) 개수, shown은 현재 필터에 맞는 카드다.
  const ranked = useMemo(() => (complete ? rankFeed(items.filter(item => item.type === 'apt'), profile, today) : []), [complete, items, profile, today]);
  const counts = useMemo(() => ranked.reduce((all, { result }) => ({ ...all, [result.best]: (all[result.best] || 0) + 1 }), {}), [ranked]);
  const shown = ranked.filter(({ result }) => (filter === 'all' ? true : filter === 'ok' ? result.best === 'ok' : result.best === 'ok' || result.best === 'check'));
  // 줍줍 정렬도 주소(?sort=gap)에 둬서 상세를 열었다 돌아와도 유지한다.
  const zupzupSort = searchParams.get('sort') === 'gap' ? 'gap' : 'date';
  const zupzup = useMemo(() => sortZupzup(items.filter(item => item.type !== 'apt'), zupzupSort), [items, zupzupSort]);
  const hasGap = zupzup.some(item => item.gap);
  const setZupzupSort = value => {
    const next = new URLSearchParams(searchParams);
    if (value === 'gap') next.set('sort', 'gap'); else next.delete('sort');
    setSearchParams(next, { replace: true, state: location.state });
  };

  // 양식 초기값: 저장된 진단이 있으면 그 값, 없으면 가점 계산기에서 고른 통장 기간만 미리 채운다.
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

    {/* 맞춤 청약 탭: 진단 전이거나 수정 중이면 양식, 아니면 내 조건 요약 + 필터 + 공고 목록 */}
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
              <p>{ranked.length ? '조건에 맞는 접수 중·예정 공고가 지금은 없어요.' : '지금 접수 중이거나 180일 안에 접수하는 아파트 공고가 없어요.'}</p>
              <div>{filter !== 'all' && ranked.length > 0 && <Button size="small" onClick={() => setFilter('all')}>전체 공고 보기</Button>}
                <Button size="small" component={Link} to="/match/zupzup">무순위·줍줍 보기</Button></div>
            </div>}
          <p className="match-disclaimer">공통 요건만 단순화해 본 신청 가능성이에요. 소득·자산·예치금·거주기간 등 세부 자격은 모집공고 원문과 청약홈에서 꼭 확인하세요. 카드를 누르면 공고별 판정 이유를 볼 수 있어요. 기준이 궁금하면 <Link to="/faq">청약 자주 묻는 질문</Link>을 참고하세요.</p>
          <Button size="small" color="inherit" className="match-reset" onClick={() => setClearLayer(true)}>진단 기록 지우기</Button>
        </>}
      </>}
    </>}

    {/* 줍줍 탭: 진단을 했으면 카드마다 판정도 함께 보여준다 */}
    {tab === 'zupzup' && <>
      <ZupzupAlert />
      {zupzup.length > 0 && <div className="match-share-row">
        {hasGap && <ToggleButtonGroup exclusive size="small" value={zupzupSort} onChange={(_, value) => value && setZupzupSort(value)} aria-label="줍줍 정렬" className="match-sort">
          <ToggleButton value="date">접수일 순</ToggleButton><ToggleButton value="gap">시세 대비 낮은 순</ToggleButton>
        </ToggleButtonGroup>}
        <Button size="small" startIcon={<IosShareRounded />} onClick={() => share(zupzup.length)}>줍줍 목록 공유</Button>
      </div>}
      {feed.isLoading ? <FeedSkeleton /> : feed.isError ? <Alert severity="warning" action={<Button color="inherit" size="small" onClick={() => feed.refetch()}>다시 시도</Button>}>공고를 불러오지 못했어요.</Alert>
        : zupzup.length ? <ul className="match-list">{zupzup.map(item => <FeedCard key={item.id} item={item} result={complete ? evaluate(profile, item, today) : null} today={today} onOpen={openDetail} />)}</ul>
          : <div className="match-empty"><p>지금 접수 중이거나 예정된 무순위·줍줍 공고가 없어요. 새 공고는 매일 반영돼요.</p></div>}
      <p className="match-disclaimer">2025년 6월부터 무순위(사후) 청약은 원칙적으로 무주택 세대구성원만 신청할 수 있어요. 거주지역·재당첨 제한은 공고마다 달라 원문을 확인하세요(<Link to="/faq#zupzup-who">무순위 자주 묻는 질문</Link>).{!complete && <> <Link to="/match">자격 진단</Link>을 하면 공고마다 신청 가능성을 함께 보여 드려요.</>}</p>
    </>}

    {/* 주소에 subscriptSeq가 있으면 목록 위에 공고 상세를 띄운다. 목록 화면은 아래에 그대로 남아 있다. */}
    {detailId && <Main detailOnly onDetailClose={closeDetail} />}
    <Dialog open={Boolean(clearLayer)} onClose={() => setClearLayer(null)} aria-labelledby="match-clear-title" maxWidth="xs" fullWidth>
      <DialogTitle id="match-clear-title">진단 기록을 지울까요?</DialogTitle>
      <DialogContent><p className="match-clear-text">이 기기에 저장한 나이·세대·무주택·통장 조건이 지워지고, 처음부터 다시 입력해야 해요. 가점 계산기 점수는 그대로 남아요.</p></DialogContent>
      <DialogActions><Button onClick={() => setClearLayer(null)}>취소</Button>
        <Button color="error" variant="contained" disableElevation onClick={() => { clearProfile(); setClearLayer(null); }}>지우기</Button></DialogActions>
    </Dialog>
    <Snackbar open={Boolean(shareMessage)} autoHideDuration={4000} onClose={() => setShareMessage('')} message={shareMessage} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }} />
  </div>;
}

