import { useHistoryOverlay, detailCloseSteps } from '@/hooks/useHistoryOverlay';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { trackAction } from '@/features/analytics/trackAction';
import { useEffect, useState, useRef } from 'react';
import { updatePageSeo, resetDefaultSeo } from '@/utils/seo.util';
import { Main } from '@/views/main/Main';
import { Link, useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, IconButton, Menu, MenuItem, Snackbar,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useMyScore } from '@/features/score/myScore';
import { gapBadge } from '@/features/match/priceGapRules';
import { usePlanner } from './usePlanner';
import { CHECKS, koreaToday, upcomingGroups } from './plannerUtils';
import CalendarConnect from './CalendarConnect';
import PushPrompt from '@/features/push/PushPrompt';
import PushSettingsDialog from '@/features/push/PushSettingsDialog';
import FavoriteCard from './FavoriteCard';
import {formatSupplySummary} from './researchUtils';
import UpcomingSchedule from './UpcomingSchedule';
import NextEvent from './NextEvent';
import WeeklyPicks from './WeeklyPicks';
import { nextPlannerEvent } from './nextEventRules';
import './planner.css';

// 비회원이 캘린더 연결을 누르고 로그인하러 갔다는 표시(sessionStorage라 이 탭에서만 유지). 로그인 뒤 연결 창을 다시 연다.
const CALENDAR_INTENT='toady.calendarIntent';

/**
 * '나의 청약'(/my) 화면. 다가오는 일정, 공고 비교표, 관심 공고 카드, 캘린더 연결·알림 설정 메뉴를 보여 준다.
 * 데이터: usePlanner(관심 공고), /api/v1/planner/results(공개 결과), /api/v1/planner/price-gaps(주변 시세 비교), useMyScore(내 가점).
 * 공고 상세는 주소 ?subscriptSeq= 로 이 화면 위에 Main(detailOnly)을 띄워 보여 준다. 라우터: routes/AppRoutes.jsx.
 */
export default function MyPlanner() {
  const planner = usePlanner();
  const comparisonRef=useRef(null);
  const [searchParams,setSearchParams]=useSearchParams();
  const location=useLocation(),navigate=useNavigate();
  // useSearchParams: 주소의 ?쿼리를 읽고 바꾼다. 공고 ID가 있으면 상세가 열린 상태다.
  const detailId=searchParams.get('subscriptSeq');
  // 공유 주소로 상세에 바로 들어와도 첫 뒤로가기가 사이트를 떠나지 않고 상세만 닫도록 히스토리를 한 칸 더 만든다.
  // 'plannerDetail'은 이 화면에서 연 상세라는 표시로 location.state에 남는다.
  useDirectDetailHistory(Boolean(detailId), 'plannerDetail', '/my');
  // 상세가 열려 있으면 Main이 공고 SEO를 맡는다. 화면을 떠나면 기본 SEO로 되돌린다.
  // useEffect의 return 함수는 정리(cleanup)다. detailId가 바뀌거나 화면을 떠날 때 실행된다.
  useEffect(()=>{
    if(detailId) return undefined;
    updatePageSeo({title:'나의 청약 | TOADY',description:'관심 공고의 청약 일정·체크리스트를 한곳에서 챙기고 공고끼리 비교하세요. 접수일 알림과 캘린더 연결도 할 수 있어요.',path:'/my'});
    return ()=>resetDefaultSeo();
  },[detailId]);
  // 이 화면에서 연 상세면 뒤로가기(navigate(-n))로 닫는다. 상세 위에 쌓인 팝업 수만큼 더 돌아가야 해서 detailCloseSteps를 쓴다.
  // 주소로 바로 들어와 돌아갈 곳이 없으면 쿼리만 지운다(replace: 히스토리를 늘리지 않고 현재 칸을 바꾼다).
  const closeDetail=()=>{if(location.state?.plannerDetail) navigate(-detailCloseSteps(location.state));else setSearchParams({}, {replace:true});};
  const [selected,setSelected] = useState([]), [message,setMessage] = useState(null), [busy,setBusy] = useState(false);
  // useRef: 값이 바뀌어도 다시 그리지 않는 상자. 여기서는 메뉴 위치를 잡을 버튼 DOM을 담는다.
  // useHistoryOverlay: 팝업 열림을 location.state.overlayLayers에 히스토리 한 칸으로 넣는다. 그래서 브라우저 뒤로가기가 팝업을 닫는다.
  const menuButtonRef=useRef(null);
  const [menuOpen,setMenuOpen] = useHistoryOverlay('planner-settings');
  const setMenu = anchor => setMenuOpen(Boolean(anchor));
  const [calendarOpen,setCalendarOpen] = useHistoryOverlay('calendar-connect');
  const [pushOpen,setPushOpen] = useHistoryOverlay('push-settings');
  // 지금 열린 팝업 수. 로그인 창이 닫혔는지 보고 연결 창을 다시 열 때 쓴다.
  const overlayCount = location.state?.overlayLayers?.length || 0;
  // 비회원이 연결을 시작하면 연결 창 자리를 로그인 창으로 바꾼다. 로그인 후에는 연결 창을 다시 열고, 로그인을 취소하면 의도를 지운다.
  const openLoginForCalendar = () => {
    try { sessionStorage.setItem(CALENDAR_INTENT,'1'); } catch { /* 저장소 차단 시 로그인 후 자동 재개만 생략 */ }
    const layers=(location.state?.overlayLayers || []).filter(layer=>layer.id!=='calendar-connect');
    navigate(location.pathname + location.search + location.hash, { replace:true, state:{ ...location.state, overlayLayers:[...layers,{ id:'layout', value:'login' }] } });
  };
  // 로그인 창이 닫히고(overlayCount 0) 로그인돼 있으면 연결 창을 다시 연다. 의도 표시는 한 번 쓰고 지운다.
  // 의존성 배열 [planner.loggedIn, overlayCount]: 이 두 값이 바뀔 때만 다시 실행된다.
  useEffect(() => {
    let intent=false;
    try { intent=sessionStorage.getItem(CALENDAR_INTENT)==='1'; } catch { return; }
    if(!intent || overlayCount>0) return;
    try { sessionStorage.removeItem(CALENDAR_INTENT); } catch { /* 무시 */ }
    if(planner.loggedIn) setCalendarOpen(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[planner.loggedIn,overlayCount]);
  const report = (text,error) => setMessage({text,error});
  // 계정이 바뀌면(scope) 비교 선택·안내 문구를 비운다.
  useEffect(() => {setSelected([]);setMessage(null);},[planner.scope]);
  // 해제된 공고는 비교 선택에서 뺀다. 배열은 렌더마다 새로 만들어져 의존성으로 쓰면 매번 실행되므로 문자열로 바꿔 비교한다.
  const availableIds = planner.items.map(x=>x.id).sort().join(',');
  useEffect(() => {setSelected(previous=>previous.filter(id=>availableIds.split(',').includes(id)));},[availableIds]);
  const today=koreaToday(), groups=upcomingGroups(planner.items,today), nextEvent=nextPlannerEvent(planner.items,today);
  // 관심 APT 공고의 공개 결과(1순위 경쟁률·당첨가점)를 한 번에 받아 카드의 진행 단계 안내에 쓴다.
  const aptIds=planner.items.map(x=>x.id).filter(id=>id.startsWith('apt/')).sort();
  const resultQuery=useQuery({queryKey:['plannerResults',aptIds.join(',')],enabled:aptIds.length>0,staleTime:600000,retry:1,
    queryFn:()=>apiClient.post('/api/v1/planner/results',{ids:aptIds},{auth:false})});
  const results=new Map((Array.isArray(resultQuery.data)?resultQuery.data:[]).map(result=>[result.id,result]));
  // 미리 계산한 주변 시세 비교(서버에 있을 때만). 카드와 비교표에 같은 값을 쓴다.
  const savedIds=planner.items.map(x=>x.id).sort();
  const gapQuery=useQuery({queryKey:['plannerGaps',savedIds.join(',')],enabled:savedIds.length>0,staleTime:600000,retry:1,
    queryFn:()=>apiClient.post('/api/v1/planner/price-gaps',{ids:savedIds},{auth:false})});
  const gaps=new Map((Array.isArray(gapQuery.data)?gapQuery.data:[]).map(gap=>[gap.id,gap]));
  const myScore=useMyScore();
  const compared=planner.items.filter(item=>selected.includes(item.id));
  // 체크리스트는 비트값이라 항목마다 비트(1<<i)가 켜졌는지 세어 완료 개수를 구한다.
  const completed=planner.items.reduce((total,item)=>total+CHECKS.filter((_,i)=>item.checklist & (1<<i)).length,0);
  // 메뉴 동작 공통 처리: 메뉴 닫기 → 실행 → 결과 안내.
  async function action(callback,success) {setMenu(null);setBusy(true);try{await callback();report(success,false);}catch(e){report(e.message,true);}finally{setBusy(false);}}
  // 가져오기는 한 건이 실패해도 나머지를 가져오고, 몇 건을 가져왔고 몇 건이 빠졌는지 알려 준다.
  async function importGuest() {
    setMenu(null);setBusy(true);
    try {
      const result=await planner.importGuest();
      const missed=result.failed+result.skipped;
      if(!missed && !result.imported) report('가져올 새 공고가 없어요. 기기의 관심 공고가 이미 계정에 있어요.',false);
      else if(!missed) report(`기기 공고 ${result.imported}개를 가져왔습니다. 계정의 기존 메모는 유지합니다.`,false);
      else report(`${result.imported}개를 가져왔고 ${missed}개는 가져오지 못했어요.${result.skipped?' 원본이 사라진 공고는 제외했어요.':''}${result.lastError?` (${result.lastError})`:''}`,true);
    } catch(e){report(e.message,true);} finally{setBusy(false);}
  }
  // 비교는 3개까지. 고르면 넣고 다시 누르면 뺀다.
  function select(id) {
    if(!selected.includes(id) && selected.length>=3) {report('한 번에 3개 공고까지 비교할 수 있어요.',true);return;}
    if(!selected.includes(id)) trackAction('compare',id);
    setSelected(previous=>previous.includes(id)?previous.filter(value=>value!==id):[...previous,id]);
  }
  return <Box className="planner-page">
    {/* 상단: 제목과 캘린더 연결·설정 메뉴 버튼 */}
    <header className="planner-hero"><div><h1>나의 청약</h1><p>관심 있는 집을 모으고, 다음 청약을 차근차근 준비하세요.</p></div><div className="planner-hero-actions"><Button variant="outlined" startIcon={<CalendarMonthOutlined />} onClick={()=>setCalendarOpen(true)}>캘린더 연결</Button><IconButton ref={menuButtonRef} aria-label="나의 청약 설정" onClick={e=>setMenu(e.currentTarget)} disabled={busy}><MoreHorizRounded /></IconButton></div></header>
    {!planner.loggedIn && <div className="planner-storage-notice"><BookmarkBorderRounded fontSize="small" /><span>이 브라우저에 저장 중이에요. <strong>로그인하면 다른 기기에서도 이어볼 수 있어요.</strong></span></div>}
    {planner.guestRefreshError && <Alert severity="warning" sx={{mb:2}}>최신 일정을 확인하지 못해 저장 당시 정보를 표시합니다. 원문에서 일정을 확인해 주세요.</Alert>}
    {planner.error && <Alert severity="error" action={<Button onClick={()=>planner.refetch()}>재시도</Button>}>{planner.error.message}</Alert>}
    {!planner.isLoading && planner.items.length>0 && <PushPrompt overlayId="push-prompt-settings" announcementId={planner.items[0]?.id} loggedIn={planner.loggedIn} />}
    {planner.isLoading ? <div className="planner-loading"><CircularProgress size={26} /><p>나의 청약을 불러오고 있어요.</p></div> : <>
      {/* 저장한 공고가 있을 때만 다음 일정을 맨 위에 둔다. 빈 화면에서는 의미 없는 0 숫자를 보여 주지 않는다. */}
      {/* 일정 묶음이 하나뿐이면 아래 '다가오는 일정' 첫 카드와 같은 내용이라 겹쳐 보이지 않게 뺀다. */}
      {planner.items.length>0 && groups.length>1 && <NextEvent event={nextEvent} />}
      {planner.items.length>0 && <UpcomingSchedule key={planner.scope} groups={groups} today={today} />}
      {/* 공고 비교표: 2개 이상 고르면 나온다. 좁은 화면에서는 표를 좌우로 민다. */}
      {compared.length>=2 && <section ref={comparisonRef} className="planner-section planner-comparison" aria-labelledby="compare-heading"><div className="planner-section-heading"><div><h2 id="compare-heading"><CompareArrowsRounded />공고 비교</h2><p>공개된 공고 정보 기준입니다. 상세 조건은 원문을 확인하세요.</p></div><Button size="small" onClick={()=>setSelected([])} endIcon={<CloseRounded />}>비교 닫기</Button></div><p className="planner-table-hint">표를 좌우로 밀어 선택한 공고의 조건을 비교하세요.</p><TableContainer role="region" aria-label="관심 공고 비교 표 · 좌우로 스크롤" tabIndex={0}><Table size="small" aria-label="관심 공고 비교"><TableHead><TableRow><TableCell>비교 항목</TableCell>{compared.map(x=><TableCell key={x.id}>{x.summary.houseNm}</TableCell>)}</TableRow></TableHead><TableBody>{[['주소','address'],['APT 공급금액 · 주택형별 최고가','priceSummary'],['주택 유형','housingType'],['공급 세대수','supply'],['접수 시작','startDate'],['접수 마감','endDate'],['당첨자 발표','winnerDate']].map(([label,key])=><TableRow key={key}><TableCell>{label}</TableCell>{compared.map(x=><TableCell key={x.id}>{key==='priceSummary'?formatSupplySummary(x.summary[key]):x.summary[key] || '미제공'}</TableCell>)}</TableRow>)}
        <TableRow><TableCell>주변 시세 대비</TableCell>{compared.map(x=>{const badge=gapBadge(gaps.get(x.id));return <TableCell key={x.id}>{badge?<>{badge.text}<br/><small>전용 {gaps.get(x.id).area}㎡ · 참고 비교</small></>:'비교 자료 없음'}</TableCell>;})}</TableRow></TableBody></Table></TableContainer></section>}
      {/* 저장한 공고 카드 목록. key에 scope를 넣어 계정이 바뀌면 카드 상태를 새로 만든다. */}
      {planner.items.length>0 && <section className="planner-section" aria-labelledby="favorites-heading"><div className="planner-section-heading"><div><h2 id="favorites-heading">저장한 공고 <span className="planner-count">{planner.items.length}{planner.items.length>=40?' / 50':''}</span></h2><p>준비 노트에서 체크리스트와 메모, 알림을 관리하세요.</p></div>{selected.length>0 ? <span className="planner-selection-count">비교 {selected.length}/3</span> : completed>0 && <span className="planner-favorites-meta">준비 항목 {completed}/{planner.items.length*CHECKS.length} 확인</span>}</div>
      <div className="planner-favorite-grid">{planner.items.map(item=><FavoriteCard key={`${planner.scope}/${item.id}`} item={item} planner={planner} report={report} selected={selected.includes(item.id)} onSelect={()=>select(item.id)} result={results.get(item.id)} gap={gaps.get(item.id)} myScore={myScore.score?.total} />)}</div>
      </section>}
      {/* 저장한 공고가 없으면 이번 주 접수 공고를 추천해 바로 저장할 수 있게 한다. */}
      {!planner.error && !planner.items.length && <WeeklyPicks planner={planner} report={report} />}
    </>}
    {/* 공고 상세: 메인 화면의 상세만(detailOnly) 이 화면 위에 띄운다. */}
    {detailId && <Main detailOnly onDetailClose={closeDetail}/>}
    <PushSettingsDialog open={Boolean(pushOpen)} onClose={()=>setPushOpen(false)} testAnnouncementId={planner.items[0]?.id} loggedIn={planner.loggedIn} />
    <CalendarConnect open={Boolean(calendarOpen)} onClose={()=>setCalendarOpen(false)} loggedIn={planner.loggedIn} summaries={planner.items.map(item=>item.summary)} onLogin={openLoginForCalendar} />
    {/* 설정 메뉴. 알림 설정은 메뉴 레이어를 알림 창 레이어로 바꿔(replace) 뒤로가기 한 번에 닫히게 한다. */}
    <Menu anchorEl={()=>menuButtonRef.current} open={Boolean(menuOpen)} onClose={()=>setMenu(null)}>
      {planner.loggedIn && planner.guestCount>0 && <MenuItem disabled={busy || planner.isLoading || Boolean(planner.error)} onClick={()=>importGuest()}>기기 관심 공고 가져오기 ({planner.guestCount})</MenuItem>}
      {planner.loggedIn && <MenuItem disabled={busy} onClick={()=>action(planner.disableEmails,'모든 이메일 알림을 해제했습니다.')}>이메일 알림 전체 해제</MenuItem>}
      <MenuItem onClick={()=>{const layers=(location.state?.overlayLayers || []).filter(layer=>layer.id!=='planner-settings');navigate(location.pathname+location.search+location.hash,{replace:true,state:{...location.state,overlayLayers:[...layers,{id:'push-settings',value:true}]}});}}>알림 설정</MenuItem>
      {planner.loggedIn && <MenuItem component={Link} to="/account" replace>내 정보·계정 설정</MenuItem>}
      <MenuItem component={Link} to="/" replace state={{browseView:'calendar'}}>청약캘린더로 이동</MenuItem>
    </Menu>
    {/* 비교 선택 중일 때 화면 아래에 붙는 안내 */}
    {selected.length>0 && <div className="planner-compare-hint"><CompareArrowsRounded /><span>{selected.length===1?'비교할 공고를 하나 더 선택해 주세요.':`${selected.length}개 공고를 선택했어요.`}</span>{selected.length>=2 && <Button size="small" color="inherit" onClick={()=>comparisonRef.current?.scrollIntoView({behavior:'smooth',block:'start'})}>선택한 공고 비교</Button>}<IconButton size="small" aria-label="비교 선택 해제" onClick={()=>setSelected([])}><CloseRounded fontSize="small" /></IconButton></div>}

    <Snackbar open={Boolean(message)} autoHideDuration={5000} onClose={()=>setMessage(null)} anchorOrigin={{vertical:'bottom',horizontal:'center'}}><Alert severity={message?.error?'error':'success'} variant="filled" onClose={()=>setMessage(null)} sx={{borderRadius:2}}>{message?.text || ''}</Alert></Snackbar>
  </Box>;
}
