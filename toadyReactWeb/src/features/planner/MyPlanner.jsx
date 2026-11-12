import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { trackAction } from '@/features/analytics/trackAction';
import { useEffect, useState, useRef } from 'react';
import { Main } from '@/views/main/Main';
import { Link, useSearchParams, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Box, Button, CircularProgress, IconButton, Menu, MenuItem, Snackbar,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import { usePlanner } from './usePlanner';
import { CHECKS, koreaToday, upcomingGroups } from './plannerUtils';
import CalendarConnect from './CalendarConnect';
import FavoriteCard from './FavoriteCard';
import {formatSupplySummary} from './researchUtils';
import UpcomingSchedule from './UpcomingSchedule';
import './planner.css';

const CALENDAR_INTENT='toady.calendarIntent';

export default function MyPlanner() {
  const planner = usePlanner();
  const comparisonRef=useRef(null);
  const [searchParams,setSearchParams]=useSearchParams();
  const location=useLocation(),navigate=useNavigate();
  const detailId=searchParams.get('subscriptSeq');
  useDirectDetailHistory(Boolean(detailId), 'plannerDetail', '/my');
  useEffect(()=>{if(!detailId)document.title='나의 청약 | TOADY';},[detailId]);
  const closeDetail=()=>{if(location.state?.plannerDetail) navigate(-1);else setSearchParams({}, {replace:true});};
  const [selected,setSelected] = useState([]), [message,setMessage] = useState(null), [busy,setBusy] = useState(false);
  const menuButtonRef=useRef(null);
  const [menuOpen,setMenuOpen] = useHistoryOverlay('planner-settings');
  const setMenu = anchor => setMenuOpen(Boolean(anchor));
  const [calendarOpen,setCalendarOpen] = useHistoryOverlay('calendar-connect');
  const overlayCount = location.state?.overlayLayers?.length || 0;
  // 비회원이 연결을 시작하면 연결 창 자리를 로그인 창으로 바꾼다. 로그인 후에는 연결 창을 다시 열고, 로그인을 취소하면 의도를 지운다.
  const openLoginForCalendar = () => {
    try { sessionStorage.setItem(CALENDAR_INTENT,'1'); } catch { /* 저장소 차단 시 로그인 후 자동 재개만 생략 */ }
    const layers=(location.state?.overlayLayers || []).filter(layer=>layer.id!=='calendar-connect');
    navigate(location.pathname + location.search + location.hash, { replace:true, state:{ ...location.state, overlayLayers:[...layers,{ id:'layout', value:'login' }] } });
  };
  useEffect(() => {
    let intent=false;
    try { intent=sessionStorage.getItem(CALENDAR_INTENT)==='1'; } catch { return; }
    if(!intent || overlayCount>0) return;
    try { sessionStorage.removeItem(CALENDAR_INTENT); } catch { /* 무시 */ }
    if(planner.loggedIn) setCalendarOpen(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[planner.loggedIn,overlayCount]);
  const report = (text,error) => setMessage({text,error});
  useEffect(() => {setSelected([]);setMessage(null);},[planner.scope]);
  const availableIds = planner.items.map(x=>x.id).sort().join(',');
  useEffect(() => {setSelected(previous=>previous.filter(id=>availableIds.split(',').includes(id)));},[availableIds]);
  const today=koreaToday(), groups=upcomingGroups(planner.items,today);
  const compared=planner.items.filter(item=>selected.includes(item.id));
  const completed=planner.items.reduce((total,item)=>total+CHECKS.filter((_,i)=>item.checklist & (1<<i)).length,0);
  async function action(callback,success) {setMenu(null);setBusy(true);try{await callback();report(success,false);}catch(e){report(e.message,true);}finally{setBusy(false);}}
  function select(id) {
    if(!selected.includes(id) && selected.length>=3) {report('한 번에 3개 공고까지 비교할 수 있어요.',true);return;}
    if(!selected.includes(id)) trackAction('compare',id);
    setSelected(previous=>previous.includes(id)?previous.filter(value=>value!==id):[...previous,id]);
  }
  return <Box className="planner-page">
    <header className="planner-hero"><div><span className="planner-eyebrow">MY HOME PLAN</span><h1>나의 청약</h1><p>관심 있는 집을 모으고, 다음 청약을 차근차근 준비하세요.</p></div><div className="planner-hero-actions"><Button variant="outlined" startIcon={<CalendarMonthOutlined />} onClick={()=>setCalendarOpen(true)}>캘린더 연결</Button><IconButton ref={menuButtonRef} aria-label="나의 청약 설정" onClick={e=>setMenu(e.currentTarget)} disabled={busy}><MoreHorizRounded /></IconButton></div></header>
    {!planner.loggedIn && <div className="planner-storage-notice"><BookmarkBorderRounded fontSize="small" /><span>이 브라우저에 저장 중이에요. <strong>로그인하면 다른 기기에서도 이어볼 수 있어요.</strong></span></div>}
    {planner.guestRefreshError && <Alert severity="warning" sx={{mb:2}}>최신 일정을 확인하지 못해 저장 당시 정보를 표시합니다. 원문에서 일정을 확인해 주세요.</Alert>}
    {planner.error && <Alert severity="error" action={<Button onClick={()=>planner.refetch()}>재시도</Button>}>{planner.error.message}</Alert>}
    {planner.isLoading ? <div className="planner-loading"><CircularProgress size={26} /><p>나의 청약을 불러오고 있어요.</p></div> : <>
      <div className="planner-stats"><div><span>관심 공고</span><strong>{planner.items.length}<small>/ 50</small></strong></div><div><span>예정 일정</span><strong>{groups.reduce((n,g)=>n+g.events.length,0)}<small>개</small></strong></div><div><span>확인한 준비 항목</span><strong>{completed}<small>/ {planner.items.length*CHECKS.length}</small></strong></div></div>
      {planner.items.length>0 && <UpcomingSchedule key={planner.scope} groups={groups} today={today} />}
      {compared.length>=2 && <section ref={comparisonRef} className="planner-section planner-comparison" aria-labelledby="compare-heading"><div className="planner-section-heading"><div><h2 id="compare-heading"><CompareArrowsRounded />공고 비교</h2><p>공개된 공고 정보 기준입니다. 상세 조건은 원문을 확인하세요.</p></div><Button size="small" onClick={()=>setSelected([])} endIcon={<CloseRounded />}>비교 닫기</Button></div><p className="planner-table-hint">표를 좌우로 밀어 선택한 공고의 조건을 비교하세요.</p><TableContainer role="region" aria-label="관심 공고 비교 표 · 좌우로 스크롤" tabIndex={0}><Table size="small" aria-label="관심 공고 비교"><TableHead><TableRow><TableCell>비교 항목</TableCell>{compared.map(x=><TableCell key={x.id}>{x.summary.houseNm}</TableCell>)}</TableRow></TableHead><TableBody>{[['주소','address'],['APT 공급금액 · 주택형별 최고가','priceSummary'],['주택 유형','housingType'],['공급 세대수','supply'],['접수 시작','startDate'],['접수 마감','endDate'],['당첨자 발표','winnerDate']].map(([label,key])=><TableRow key={key}><TableCell>{label}</TableCell>{compared.map(x=><TableCell key={x.id}>{key==='priceSummary'?formatSupplySummary(x.summary[key]):x.summary[key] || '미제공'}</TableCell>)}</TableRow>)}</TableBody></Table></TableContainer></section>}
      <section className="planner-section" aria-labelledby="favorites-heading"><div className="planner-section-heading"><div><h2 id="favorites-heading">저장한 공고 <span className="planner-count">{planner.items.length}</span></h2><p>준비 노트에서 체크리스트와 메모, 알림을 관리하세요.</p></div>{selected.length>0 && <span className="planner-selection-count">비교 {selected.length}/3</span>}</div>
      <div className="planner-favorite-grid">{planner.items.map(item=><FavoriteCard key={`${planner.scope}/${item.id}`} item={item} planner={planner} report={report} selected={selected.includes(item.id)} onSelect={()=>select(item.id)} />)}</div>
      {!planner.error && !planner.items.length && <div className="planner-empty"><div><BookmarkBorderRounded /></div><h3>어떤 집이 마음에 드세요?</h3><p>공고를 저장하면 일정과 준비 노트를<br />이곳에서 함께 관리할 수 있어요.</p><Button variant="contained" disableElevation component={Link} to="/" endIcon={<ArrowForwardRounded />}>청약 찾아보기</Button></div>}</section>
    </>}
    {detailId && <Main detailOnly onDetailClose={closeDetail}/>}
    <CalendarConnect open={Boolean(calendarOpen)} onClose={()=>setCalendarOpen(false)} loggedIn={planner.loggedIn} summaries={planner.items.map(item=>item.summary)} onLogin={openLoginForCalendar} />
    <Menu anchorEl={()=>menuButtonRef.current} open={Boolean(menuOpen)} onClose={()=>setMenu(null)}>
      {planner.loggedIn && planner.guestCount>0 && <MenuItem disabled={busy || planner.isLoading || Boolean(planner.error)} onClick={()=>action(planner.importGuest,'기기 공고를 가져왔습니다. 계정의 기존 메모는 유지합니다.')}>기기 관심 공고 가져오기 ({planner.guestCount})</MenuItem>}
      {planner.loggedIn && <MenuItem disabled={busy} onClick={()=>action(planner.disableEmails,'모든 이메일 알림을 해제했습니다.')}>이메일 알림 전체 해제</MenuItem>}
      <MenuItem component={Link} to="/" replace state={{browseView:'calendar'}}>청약캘린더로 이동</MenuItem>
    </Menu>
    {selected.length>0 && <div className="planner-compare-hint"><CompareArrowsRounded /><span>{selected.length===1?'비교할 공고를 하나 더 선택해 주세요.':`${selected.length}개 공고를 선택했어요.`}</span>{selected.length>=2 && <Button size="small" color="inherit" onClick={()=>comparisonRef.current?.scrollIntoView({behavior:'smooth',block:'start'})}>선택한 공고 비교</Button>}<IconButton size="small" aria-label="비교 선택 해제" onClick={()=>setSelected([])}><CloseRounded fontSize="small" /></IconButton></div>}

    <Snackbar open={Boolean(message)} autoHideDuration={5000} onClose={()=>setMessage(null)} anchorOrigin={{vertical:'bottom',horizontal:'center'}}><Alert severity={message?.error?'error':'success'} variant="filled" onClose={()=>setMessage(null)} sx={{borderRadius:2}}>{message?.text || ''}</Alert></Snackbar>
  </Box>;
}
