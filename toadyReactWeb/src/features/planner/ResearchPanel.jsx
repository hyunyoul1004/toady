import ImageExportButton from '@/features/announcement/export/ImageExportButton';
import { detailImageSections } from '@/features/announcement/export/exportData';
import { useEffect, useState, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
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
import { useNeighborhoodResearch } from './useNeighborhoodResearch';
import PushPrompt from '@/features/push/PushPrompt';
import './research.css';

const MOLIT_TRADE_SITE='https://rt.molit.go.kr/';
/** 이 화면에서 조회할 수 없을 때도 사용자가 같은 공식 자료로 이어서 확인할 수 있게 한다. */
function OfficialTradeLink(){return <Button size="small" variant="outlined" component="a" href={MOLIT_TRADE_SITE} target="_blank" rel="noopener noreferrer" endIcon={<OpenInNewRounded/>}>국토교통부 실거래가 공개시스템</Button>;}
function DataState({kind,onRetry}) {
  const copy={NOT_CONFIGURED:['지금은 동네 실거래를 바로 보여드리지 못해요','국토교통부 실거래가 공개시스템에서 같은 동네의 신고 거래를 확인할 수 있어요.'],EMPTY:['최근 3개월 동안 조건에 맞는 거래가 없어요','면적 조건을 넓히거나 공식 공개시스템에서 기간을 늘려 확인해 보세요.'],UNAVAILABLE:['지금은 실거래를 불러오지 못했어요','제공처 응답이 지연되고 있어요. 잠시 후 다시 확인해 주세요.']};
  const [title,description]=copy[kind] || copy.UNAVAILABLE;
  return <div className="research-data-state" data-state={kind || 'UNAVAILABLE'}><ApartmentRounded /><strong>{title}</strong><p>{description}</p><div className="research-data-actions">{(kind==='UNAVAILABLE' || !copy[kind]) && onRetry && <Button size="small" onClick={onRetry}>다시 불러오기</Button>}<OfficialTradeLink/></div></div>;
}
function LoadingData(){return <div className="research-loading"><Skeleton variant="rounded" height={66}/><Skeleton variant="rounded" height={66}/><Skeleton variant="rounded" height={66}/></div>;}
const shortDate=date=>date?date.slice(5).replace('-','.'):'미제공';
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

export function AnnouncementActions({detail}) {
  const planner=usePlanner(),navigate=useNavigate();
  const [busy,setBusy]=useState(false),[message,setMessage]=useState(null),[justSaved,setJustSaved]=useState(false);
  const summary=summaryOf(detail),saved=planner.items.some(x=>x.id===summary.id);
  useEffect(()=>{setJustSaved(false);},[summary.id]);
  async function save(){if(saved){navigate('/my');return;}setBusy(true);setMessage(null);try{await planner.save({id:summary.id,summary,note:'',checklist:0,emailEnabled:false});setJustSaved(true);}catch(e){setMessage(e.message);}finally{setBusy(false);}}
  return <>
    <div className="announcement-header-actions" aria-label="공고 저장과 일정 관리">    <div className="research-primary-actions"><Button size="small" className="announcement-save-action" variant={saved?'outlined':'contained'} disableElevation startIcon={saved?<BookmarkRounded />:<BookmarkBorderRounded />} disabled={busy || planner.isLoading || Boolean(planner.error)} onClick={save}>{saved?'저장한 공고 관리':busy?'저장 중…':'관심 공고 저장'}</Button><Button size="small" variant="outlined" startIcon={<CalendarMonthOutlined />} onClick={()=>downloadCalendar([summary])} disabled={!summary.events.length}>일정 추가</Button><Button size="small" className="announcement-my-action" component={Link} to="/my" variant="outlined" startIcon={<FolderOpenOutlined />}>나의 청약</Button></div>
    {!planner.loggedIn && <p className="research-storage-note">로그인 없이 저장하고, 이 브라우저에서 이어볼 수 있어요.</p>}
    </div>    {justSaved && <PushPrompt compact overlayId="push-settings-detail" announcementId={summary.id} loggedIn={planner.loggedIn} />}
    {message && <Alert severity="error" sx={{my:1}}>{message}</Alert>}
    {planner.error && <Alert severity="error" sx={{my:1}} action={<Button onClick={()=>planner.refetch()}>재시도</Button>}>저장한 공고를 확인하지 못했습니다.</Alert>}
  </>;
}

export function ResearchTabs({value,onChange}) {
  const tab=value;
  return <Tabs value={tab} onChange={(_,value)=>onChange(value)} variant="fullWidth" className="research-tabs" aria-label="청약 조사 정보"><Tab value="info" label="청약 정보"/><Tab value="trades" label="동네 실거래"/></Tabs>;
}

export default function ResearchPanel({detail,children,tab:controlledTab,onTabChange,showTabs=true}) {
  const unitSection=useRef(null);
  const [localTab,setLocalTab]=useState('info'),[area,setArea]=useState('');
  const tab=controlledTab ?? localTab, setTab=onTabChange || setLocalTab;
  const summary=summaryOf(detail),today=koreaToday();
  const research=useNeighborhoodResearch(detail,tab,area);
  const units=detail.unitPrices || [];
  const supply=Number(summary.supply);
  const hasSupply=Number.isFinite(supply) && supply>0;
  const amounts=units.map(unit=>unit.amountManwon).filter(amount=>Number.isFinite(amount) && amount>0);
  const min=amounts.length?Math.min(...amounts):null,max=amounts.length?Math.max(...amounts):null;
  const areas=[...new Set(units.map(unit=>Math.round(unit.area)).filter(Number.isFinite))].slice(0,6);
  const closing=daysUntil(summary.endDate,today),start=daysUntil(summary.startDate,today);
  const status=start>0?`접수 D-${start}`:closing>=0 && closing!==null?'접수 중':summary.endDate?'접수 종료':'일정 확인';
  const trades=research.trades.data;
  // 조회할 수 없는 상태에서 면적 필터를 보여주면 동작하지 않는 컨트롤이 되므로 조회 가능한 경우에만 노출한다.
  const tradesQueryable=research.capabilities.data?.tradesEnabled===true && !research.location.error;

  return <section className="research-panel" aria-label="공고 핵심 정보와 동네 조사">
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
        {children}
        <section ref={unitSection} className="announcement-unit-section" aria-label="주택형과 분양가"><h3>주택형·분양가</h3>
        <div className="research-section-caption"><span>주택형별 최고 공급금액</span><span>청약홈 공개 자료</span></div>

        {units.length ? <div className="research-price-list">{units.map(unit=><div className="research-price-row" key={unit.id}><div><strong>{unit.label}</strong><span>전용면적{unit.general || unit.special?` · 일반 ${unit.general} / 특별 ${unit.special}세대`:''}</span></div><div><strong>{unit.amountManwon?formatManwon(unit.amountManwon):'금액 미제공'}</strong><span>최고 공급금액</span></div></div>)}</div>:<p className="research-explanation">이 공급 유형의 금액과 납부 조건은 청약 정보 탭의 모집공고 원문에서 확인해 주세요.</p>}
        <p className="research-footnote">실제 층·타입별 금액과 추가 옵션 비용은 모집공고를 확인해 주세요. 표시 범위는 각 주택형의 최고 금액을 모은 것으로, 최저 분양가를 의미하지 않습니다.</p>
        </section>
      </>}
      {tab==='trades' && <>
        <div className="research-section-caption"><span>{research.location.data?.dong || '같은 법정동'} 아파트 매매</span><span>국토교통부 신고 자료</span></div>
        {tradesQueryable && <ToggleButtonGroup size="small" exclusive value={area} onChange={(_,value)=>{if(value!==null)setArea(value);}} className="research-area-filter" aria-label="실거래 전용면적 필터"><ToggleButton value="">전체 면적</ToggleButton>{areas.map(value=><ToggleButton key={value} value={String(value)}>{value}㎡ 안팎</ToggleButton>)}</ToggleButtonGroup>}
        {tradesQueryable && area && <p className="research-explanation">전용 {area}㎡에서 ±5㎡ 이내 거래를 조회합니다.</p>}
        {research.capabilities.isLoading || research.location.isLoading || research.location.isFetching || research.trades.isFetching ? <LoadingData/> : research.capabilities.error || research.trades.error ? <DataState kind="UNAVAILABLE" onRetry={()=>{research.capabilities.refetch();research.trades.refetch();}}/> : !research.capabilities.data?.tradesEnabled ? <DataState kind="NOT_CONFIGURED"/> : research.location.error ? <div className="research-data-state"><PlaceOutlined/><strong>거래를 조회할 위치를 확인하지 못했어요</strong><p>{research.location.error.message}</p><div className="research-data-actions"><Button size="small" onClick={()=>research.location.refetch()}>위치 다시 확인</Button><OfficialTradeLink/></div></div> : trades?.status==='READY' ? <>
          <PriceComparison comparison={tradeComparison(units,trades.items,area)} hasUnits={amounts.length>0} area={area}/>
          <p className="research-explanation">{trades.fromMonth} ~ {trades.toMonth} · 조건에 맞는 거래 {trades.total}건 중 최근 {trades.items.length}건</p>
          <div className="research-trade-list">{trades.items.map((trade,index)=><article key={`${trade.apartment}/${trade.date}/${index}`}><div><h3>{trade.apartment}</h3><span>전용 {Number(trade.area).toLocaleString('ko-KR',{maximumFractionDigits:4})}㎡ · {trade.floor}층{trade.buildYear?` · ${trade.buildYear}년 준공`:''}</span></div><div><strong>{formatManwon(trade.amountManwon)}</strong><time dateTime={trade.date}>{trade.date} 계약</time></div></article>)}</div>
          <p className="research-footnote">데이터 확인 {checkedAt(trades.checkedAt)} · 취소 표시가 있는 거래 제외</p>
        </> : <DataState kind={trades?.status} onRetry={()=>research.trades.refetch()}/>}
        <p className="research-footnote">같은 법정동의 신고 거래이며 거리순·동일 조건 비교나 현재 호가가 아닙니다. 신고 지연·정정·해제로 원본과 차이가 생길 수 있습니다.</p>
      </>}
    </div>


  </section>;
}
