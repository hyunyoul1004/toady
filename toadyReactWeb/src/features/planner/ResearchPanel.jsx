/**
 * 공고 상세의 핵심 요약과 '동네 실거래' 탭. views/main/Main.jsx가 상세 화면에서 쓴다.
 * - AnnouncementActions: 관심 공고 저장/해제, 일정(.ics) 추가, 나의 청약 이동 버튼
 * - ResearchTabs: 청약 정보 / 동네 실거래 탭 버튼
 * - ResearchPanel(기본 내보내기): 접수·발표·공급 규모 요약, 주택형·분양가, 동네 실거래 목록
 * 데이터: usePlanner(관심 공고), useNeighborhoodResearch(실거래).
 */
import ImageExportButton from '@/features/announcement/export/ImageExportButton';
import { detailImageSections } from '@/features/announcement/export/exportData';
import { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { Alert, Box, Button, Chip, Skeleton, Tab, Tabs, ToggleButton, ToggleButtonGroup } from '@mui/material';
import BookmarkBorderRounded from '@mui/icons-material/BookmarkBorderRounded';
import BookmarkRounded from '@mui/icons-material/BookmarkRounded';
import FolderOpenOutlined from '@mui/icons-material/FolderOpenOutlined';
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined';
import EventAvailableOutlined from '@mui/icons-material/EventAvailableOutlined';
import ApartmentRounded from '@mui/icons-material/ApartmentRounded';
import PlaceOutlined from '@mui/icons-material/PlaceOutlined';
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded';
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded';
import { summaryOf, downloadCalendar, koreaToday, daysUntil } from './plannerUtils';
import { formatManwon, tradeComparison } from './researchUtils';
import { usePlanner } from './usePlanner';
import { normalizeStage } from './stageRules';
import { useNeighborhoodResearch } from './useNeighborhoodResearch';
import PushPrompt from '@/features/push/PushPrompt';
import EligibilityCard from '@/features/match/EligibilityCard';
import PriceGapCard, { PriceGapSummary } from '@/features/match/PriceGapCard';
import FundPlanner from '@/features/match/FundPlanner';
import './research.css';

const MOLIT_TRADE_SITE='https://rt.molit.go.kr/';
/** 이 화면에서 조회할 수 없을 때도 사용자가 같은 공식 자료로 이어서 확인할 수 있게 한다. */
function OfficialTradeLink(){return <Button size="small" variant="outlined" component="a" href={MOLIT_TRADE_SITE} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded/>}>국토교통부 실거래가 공개시스템</Button>;}
/** 실거래를 보여 줄 수 없을 때의 안내. kind: NOT_CONFIGURED(서버에서 꺼 둠) · EMPTY(거래 없음) · UNAVAILABLE(오류). */
function DataState({kind,onRetry}) {
  const copy={NOT_CONFIGURED:['지금은 동네 실거래를 바로 보여드리지 못해요','국토교통부 실거래가 공개시스템에서 같은 동네의 신고 거래를 확인할 수 있어요.'],EMPTY:['최근 3개월 동안 조건에 맞는 거래가 없어요','면적 조건을 넓히거나 공식 공개시스템에서 기간을 늘려 확인해 보세요.'],UNAVAILABLE:['지금은 실거래를 불러오지 못했어요','제공처 응답이 지연되고 있어요. 잠시 후 다시 확인해 주세요.']};
  const [title,description]=copy[kind] || copy.UNAVAILABLE;
  return <div className="research-data-state" data-state={kind || 'UNAVAILABLE'}><ApartmentRounded /><strong>{title}</strong><p>{description}</p><div className="research-data-actions">{(kind==='UNAVAILABLE' || !copy[kind]) && onRetry && <Button size="small" onClick={onRetry}>다시 불러오기</Button>}<OfficialTradeLink/></div></div>;
}
/** 실거래를 불러오는 동안 보이는 자리 표시 */
function LoadingData(){return <div className="research-loading"><Skeleton variant="rounded" height={66}/><Skeleton variant="rounded" height={66}/><Skeleton variant="rounded" height={66}/></div>;}
// 'YYYY-MM-DD' → 'MM.DD'.
const shortDate=date=>date?date.slice(5).replace('-','.'):'미제공';
// 자료 확인 시각을 한국 시간 기준 월·일·시각으로.
const checkedAt=value=>value?new Intl.DateTimeFormat('ko-KR',{timeZone:'Asia/Seoul',month:'numeric',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(new Date(value)):'';

/** 같은 면적의 공급금액과 동네 실거래 범위를 나란히 보여준다. 연식·입지가 달라 판정 문구는 쓰지 않는다. */
function PriceComparison({comparison,hasUnits,area}) {
  if(!hasUnits) return null;
  if(!area) return <p className="research-compare-hint"><CompareArrowsRounded/>면적을 고르면 이 공고의 공급금액과 같은 동네 실거래를 나란히 비교해 드려요.</p>;
  if(!comparison) return null;
  const supply=comparison.supplyMin===comparison.supplyMax?formatManwon(comparison.supplyMax):`${formatManwon(comparison.supplyMin)} ~ ${formatManwon(comparison.supplyMax)}`;
  return <section className="research-compare" aria-label={`전용 ${comparison.area}㎡ 공급금액과 실거래 비교`}>
    <h3><CompareArrowsRounded/>전용 {comparison.area}㎡ 안팎 비교</h3>
    <div className="research-compare-grid">
      <div><span>이 공고 최고 공급금액</span><strong>{supply}</strong><small>주택형별 최고가 · 청약홈</small></div>
      <div><span>같은 동 최근 실거래</span><strong>{formatManwon(comparison.median)}</strong><small>중간값 · {comparison.count}건 {formatManwon(comparison.min)} ~ {formatManwon(comparison.max)}</small></div>
    </div>
    <p>실거래는 같은 법정동의 기존 아파트 거래라 연식·입지·옵션이 달라요. 참고용으로 비교해 주세요.</p>
  </section>;
}

/** 출처·유의사항을 화면 곳곳의 작은 글씨 대신 한곳에 접어 둔다. */
function Notes({items}) {
  return <details className="research-notes"><summary>자료 기준·유의사항</summary><ul>{items.map(item=><li key={item}>{item}</li>)}</ul></details>;
}

/** 상세 머리의 저장·일정·나의 청약 버튼. 해제 뒤 '되돌리기'를 위해 지운 항목을 removed에 잠시 들고 있는다. */
export function AnnouncementActions({detail}) {
  const planner=usePlanner();
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(null),[justSaved,setJustSaved]=useState(false),[removed,setRemoved]=useState(null),[pending,setPending]=useState(null),[confirmRemove,setConfirmRemove]=useState(false);
  const summary=summaryOf(detail),saved=planner.items.some(x=>x.id===summary.id);
  // 다른 공고로 바뀌면(summary.id) 지난 공고의 안내·되돌리기 상태를 비운다.
  useEffect(()=>{setJustSaved(false);setRemoved(null);setConfirmRemove(false);},[summary.id]);
  // 저장 버튼은 저장·해제를 오가는 토글이다(나의 청약 이동은 옆 버튼). 해제하면 메모·체크리스트·단계까지 지워지므로 바로 되돌릴 수 있게 남겨 둔다.
  // 메모·준비 체크·진행 단계를 적어 둔 공고는 해제하면 함께 지워지므로 한 번 더 확인한다(되돌리기는 이 상세를 보는 동안만 가능하다).
  async function save(force=false){
    const item=saved?planner.items.find(x=>x.id===summary.id):null;
    if(saved && !force && item && ((item.note||'').trim() || item.checklist>0 || normalizeStage(item.stage)>0)){setConfirmRemove(true);return;}
    setConfirmRemove(false);setPending(saved?'remove':'save');setBusy(true);setMessage(null);
    try{
      if(saved){await planner.remove(summary.id);setJustSaved(false);setRemoved(item||null);}
      else{await planner.save({id:summary.id,summary,note:'',checklist:0,emailEnabled:false});setRemoved(null);setJustSaved(true);}
    }catch(e){setMessage(e.message);}finally{setBusy(false);setPending(null);}
  }
  async function undoRemove(){
    if(!removed)return;setBusy(true);setMessage(null);
    try{await planner.save({...removed,summary:removed.summary||summary});setRemoved(null);}catch(e){setMessage(e.message);}finally{setBusy(false);}
  }
  return <>
    {/* 저장 버튼은 저장/해제 토글이다. 일정 추가는 이 공고 일정만 .ics 파일로 받는다. */}
    <div className="announcement-header-actions" aria-label="공고 저장과 일정 관리">    <div className="research-primary-actions"><Button size="small" className="announcement-save-action" variant={saved?'outlined':'contained'} disableElevation startIcon={saved?<BookmarkRounded />:<BookmarkBorderRounded />} disabled={busy || planner.isLoading || Boolean(planner.error)} aria-label={saved?'관심 공고 저장됨, 누르면 저장 해제':undefined} title={saved?'다시 누르면 관심 공고에서 빠져요':undefined} onClick={()=>save()}>{pending==='remove'?'해제 중…':pending==='save'?'저장 중…':saved?'관심 공고 저장됨':'관심 공고 저장'}</Button><Button size="small" variant="outlined" startIcon={<CalendarMonthOutlined />} onClick={()=>downloadCalendar([summary])} disabled={!summary.events.length}>일정 추가</Button><Button size="small" className="announcement-my-action" component={Link} to="/my" variant="outlined" startIcon={<FolderOpenOutlined />}>나의 청약</Button></div>
    {!planner.loggedIn && <p className="research-storage-note">로그인 없이 저장하고, 이 브라우저에서 이어볼 수 있어요.</p>}
    </div>    {justSaved && <PushPrompt compact overlayId="push-settings-detail" announcementId={summary.id} loggedIn={planner.loggedIn} onCalendar={summary.events.length ? () => downloadCalendar([summary]) : undefined} />}
    {/* 해제 확인 → 해제 뒤 되돌리기 → 오류 순서로 안내 */}
    {confirmRemove && saved && <Alert severity="warning" sx={{my:1}} action={<><Button color="inherit" size="small" onClick={()=>setConfirmRemove(false)}>취소</Button><Button color="inherit" size="small" sx={{fontWeight:800}} disabled={busy} onClick={()=>save(true)}>해제</Button></>}>메모·준비 체크·진행 단계가 있는 공고예요. 해제하면 함께 지워져요.</Alert>}
    {removed && !saved && <Alert severity="info" sx={{my:1}} action={<Button color="inherit" size="small" disabled={busy} onClick={undoRemove}>되돌리기</Button>}>관심 공고에서 뺐어요.</Alert>}
    {message && <Alert severity="error" sx={{my:1}}>{message}</Alert>}
    {planner.error && <Alert severity="error" sx={{my:1}} action={<Button onClick={()=>planner.refetch()}>재시도</Button>}>저장한 공고를 확인하지 못했습니다.</Alert>}
  </>;
}

/** 상세 하위 탭 버튼. 탭 상태는 부모(Main)가 들고 있을 수 있어 value·onChange로 받는다. */
export function ResearchTabs({value,onChange}) {
  const tab=value;
  return <Tabs value={tab} onChange={(_,value)=>onChange(value)} variant="fullWidth" className="research-tabs" aria-label="청약 조사 정보"><Tab value="info" label="청약 정보"/><Tab value="trades" label="동네 실거래"/></Tabs>;
}

/**
 * 공고 상세 본문. tab을 넘기면 부모가 탭을 관리하고(제어 컴포넌트), 안 넘기면 이 안에서 관리한다.
 * children은 일정 영역, after는 원문 영역처럼 Main이 끼워 넣는 내용이다.
 */
export default function ResearchPanel({detail,children,after,tab:controlledTab,onTabChange,showTabs=true,onShowMap}) {
  const unitSection=useRef(null);
  const [localTab,setLocalTab]=useState('info'),[area,setArea]=useState('');
  // controlledTab ?? localTab: 부모가 준 값이 있으면 그것을, 없으면 내부 상태를 쓴다.
  const tab=controlledTab ?? localTab, setTab=onTabChange || setLocalTab;
  const summary=summaryOf(detail),today=koreaToday();
  const research=useNeighborhoodResearch(detail,tab,area);
  const units=detail.unitPrices || [];
  const supply=Number(summary.supply);
  const hasSupply=Number.isFinite(supply) && supply>0;
  const amounts=units.map(unit=>unit.amountManwon).filter(amount=>Number.isFinite(amount) && amount>0);
  const min=amounts.length?Math.min(...amounts):null,max=amounts.length?Math.max(...amounts):null;
  // 실거래 면적 필터 버튼용: 주택형 면적을 반올림해 중복을 빼고 6개까지.
  const areas=[...new Set(units.map(unit=>Math.round(unit.area)).filter(Number.isFinite))].slice(0,6);
  const closing=daysUntil(summary.endDate,today),start=daysUntil(summary.startDate,today);
  const status=start>0?`접수 D-${start}`:closing>=0 && closing!==null?'접수 중':summary.endDate?'접수 종료':'일정 확인';
  const trades=research.trades.data;
  // 조회할 수 없는 상태에서 면적 필터를 보여주면 동작하지 않는 컨트롤이 되므로 조회 가능한 경우에만 노출한다.
  const tradesQueryable=research.capabilities.data?.tradesEnabled===true && !research.location.error;

  return <section className="research-panel" aria-label="공고 핵심 정보와 동네 조사">
    {/* 접수·발표·공급 규모 세 칸 요약 */}
    <header className="research-heading"><span className="research-eyebrow">모집공고 핵심 요약</span><Chip size="small" label={status} variant="outlined" /></header>
    <div className="research-metrics">
      <div className="research-metric"><span><CalendarMonthOutlined />청약 접수</span><strong>{shortDate(summary.startDate)}<i>—</i>{shortDate(summary.endDate)}</strong><small>{summary.startDate?`${summary.startDate.slice(0,4)}년`:'일정 미제공'} · 공급 유형별 접수일 확인</small></div>
      <div className="research-metric"><span><EventAvailableOutlined />당첨자 발표</span><strong>{shortDate(summary.winnerDate)}</strong><small>{summary.winnerDate?`${summary.winnerDate.slice(0,4)}년`:'일정 미제공'} · 공식 발표 기준</small></div>
      <div className="research-metric"><span><ApartmentRounded />공급 규모</span><strong>{hasSupply?supply.toLocaleString('ko-KR'):'미제공'}{hasSupply && <em>세대</em>}</strong><small>{summary.housingType || '모집공고 기준'}</small></div>
    </div>
    {showTabs && <ResearchTabs value={tab} onChange={setTab}/>}
    <div className="research-tab-content" role="tabpanel" aria-label={tab==='info'?'청약 정보':tab==='prices'?'주택형과 공급금액':'같은 법정동의 실제 매매 거래'}>
      {tab==='info' && <>
        <Box sx={{display:'flex',justifyContent:'flex-end',mb:1.5}}><ImageExportButton detail={detail} sections={detailImageSections(detail)} label="청약정보 이미지 저장"/></Box>
        {amounts.length>0 && <div className="announcement-price-highlight"><span>주택형별 최고 공급금액</span><strong>{formatManwon(min)}{min!==max && <> ~ {formatManwon(max)}</>}</strong><div><span>{units.length}개 주택형 · 층·타입별 금액은 원문 확인</span><Button size="small" onClick={()=>unitSection.current?.scrollIntoView({behavior:'smooth',block:'start'})}>주택형별 보기 ↓</Button></div></div>}
        {amounts.length>0 && <PriceGapSummary detail={detail} onMore={()=>document.getElementById('price-gap-card')?.scrollIntoView({behavior:'smooth',block:'start'})}/>}
        {/* 순서: 핵심 요약 → 공급금액·시세 한 줄 → 내 신청 가능성 → 일정 → 주택형·자금 계획 → 원문 */}
        <EligibilityCard detail={detail}/>
        {children}
        <section ref={unitSection} className="announcement-unit-section" aria-label="주택형과 분양가"><h3>주택형·분양가</h3>
        <div className="research-section-caption"><span>주택형별 최고 공급금액</span><span>청약홈 공개 자료</span></div>

        {units.length ? <div className="research-price-list">{units.map(unit=><div className="research-price-row" key={unit.id}><div><strong>{unit.label}</strong><span>전용면적{unit.general || unit.special?` · 일반 ${unit.general} / 특별 ${unit.special}세대`:''}</span></div><div><strong>{unit.amountManwon?formatManwon(unit.amountManwon):'금액 미제공'}</strong><span>최고 공급금액</span></div></div>)}</div>:<p className="research-explanation">이 공급 유형의 금액과 납부 조건은 아래 모집공고 원문에서 확인해 주세요.</p>}
        <PriceGapCard id="price-gap-card" detail={detail} onShowTrades={()=>setTab('trades')}/>
        <FundPlanner key={summary.id} detail={detail}/>
        <Notes items={['표시 금액은 각 주택형의 최고 공급금액이며 최저 분양가가 아니에요.','층·타입별 금액과 발코니 확장 등 옵션 비용은 모집공고에서 확인해 주세요.','출처: 청약홈 공개 자료']} />
        </section>
        {after}
      </>}
      {/* 동네 실거래 탭: 불러오는 중 → 오류 → 서버에서 꺼 둠 → 위치 미확인 → 거래 목록 순서로 판단한다. */}
      {tab==='trades' && <>
        <div className="research-section-caption"><span>{research.location.data?.dong || '같은 법정동'} 아파트 매매 · 최근 3개월 거래 목록</span><span>국토교통부 신고 자료</span></div>
        {/* 위치 탭과 역할을 나눈다: 여기는 같은 법정동의 최근 거래 목록, 주변 단지별 시세(시·군·구·최근 1년)는 위치 탭 지도. */}
        {onShowMap && research.location.data?.status==='ADDRESS' && <Box className="research-map-link"><span>주변 단지별 시세는 위치 탭 지도에서 시·군·구 최근 1년 거래로 볼 수 있어요.</span><Button size="small" startIcon={<PlaceOutlined/>} onClick={onShowMap}>지도에서 보기</Button></Box>}
        {tradesQueryable && <ToggleButtonGroup size="small" exclusive value={area} onChange={(_,value)=>{if(value!==null)setArea(value);}} className="research-area-filter" aria-label="실거래 전용면적 필터"><ToggleButton value="">전체 면적</ToggleButton>{areas.map(value=><ToggleButton key={value} value={String(value)}>{value}㎡ 안팎</ToggleButton>)}</ToggleButtonGroup>}
        {tradesQueryable && area && <p className="research-explanation">전용 {area}㎡에서 ±5㎡ 이내 거래를 조회합니다.</p>}
        {research.capabilities.isLoading || research.location.isLoading || research.location.isFetching || research.trades.isFetching ? <LoadingData/> : research.capabilities.error || research.trades.error ? <DataState kind="UNAVAILABLE" onRetry={()=>{research.capabilities.refetch();research.trades.refetch();}}/> : !research.capabilities.data?.tradesEnabled ? <DataState kind="NOT_CONFIGURED"/> : research.location.error ? <div className="research-data-state"><PlaceOutlined/><strong>거래를 조회할 위치를 확인하지 못했어요</strong><p>{research.location.error.message}</p><div className="research-data-actions"><Button size="small" onClick={()=>research.location.refetch()}>위치 다시 확인</Button><OfficialTradeLink/></div></div> : trades?.status==='READY' ? <>
          <PriceComparison comparison={tradeComparison(units,trades.items,area)} hasUnits={amounts.length>0} area={area}/>
          <p className="research-explanation">{trades.fromMonth} ~ {trades.toMonth} · 조건에 맞는 거래 {trades.total}건 중 최근 {trades.items.length}건</p>
          <div className="research-trade-list">{trades.items.map((trade,index)=><article key={`${trade.apartment}/${trade.date}/${index}`}><div><h3>{trade.apartment}</h3><span>전용 {Number(trade.area).toLocaleString('ko-KR',{maximumFractionDigits:4})}㎡ · {trade.floor}층{trade.buildYear?` · ${trade.buildYear}년 준공`:''}</span></div><div><strong>{formatManwon(trade.amountManwon)}</strong><time dateTime={trade.date}>{trade.date} 계약</time></div></article>)}</div>

        </> : <DataState kind={trades?.status} onRetry={()=>research.trades.refetch()}/>}
        <Notes items={[trades?.checkedAt?`데이터 확인 ${checkedAt(trades.checkedAt)} · 취소 표시가 있는 거래는 제외했어요.`:'취소 표시가 있는 거래는 제외했어요.','같은 법정동의 신고 거래이며, 거리순 비교나 현재 호가가 아니에요.','신고 지연·정정·해제로 원본과 차이가 생길 수 있어요. 출처: 국토교통부 실거래가 공개 자료']} />
      </>}
    </div>


  </section>;
}
