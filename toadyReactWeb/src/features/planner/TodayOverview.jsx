import { useState, useEffect } from 'react';
import { Alert, Box, Button, Chip, Paper, Stack, ToggleButton, ToggleButtonGroup, Typography, TextField, IconButton, Skeleton } from '@mui/material';
import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { shiftMonth, uniqueInMonth } from './browseState';
import { safeRead, ID_PATTERN, koreaToday } from './plannerUtils';
import { usePlanner } from './usePlanner';
import TodaySummary from './TodaySummary';
import NextEvent from './NextEvent';
import InterestAreaPicker from './InterestAreaPicker';
import { nextPlannerEvent } from './nextEventRules';

/** 상세를 본 공고를 '최근 본 공고'(localStorage 'toady.recent', 8개까지)에 넣는다. Main이 상세를 열 때 부른다.
 * 같은 탭의 다른 화면에는 'toady-recent' 이벤트로 알린다(storage 이벤트는 다른 탭에만 오기 때문). */
export function rememberViewed(detail) {
  if (!ID_PATTERN.test(detail?.subscriptSeq || '') || !detail?.houseNm) return;
  try {
    const current = safeRead(localStorage, 'toady.recent', []);
    localStorage.setItem('toady.recent', JSON.stringify([{id:detail.subscriptSeq,houseNm:detail.houseNm}, ...(Array.isArray(current) ? current : []).filter(x => x.id !== detail.subscriptSeq)].slice(0,8)));
    window.dispatchEvent(new Event('toady-recent'));
  } catch { /* 최근 본 공고는 기기에 저장할 수 있는 경우에만 제공한다. */ }
}
/**
 * 메인 청약 캘린더 위쪽 요약 영역(Main.jsx에서 씀). 내 다음 일정 또는 관심 지역 고르기, 오늘의 요약,
 * 조회 건수, 달력/목록 전환, 최근 본 공고, 목록 보기일 때의 공고 목록을 그린다.
 * searchTargets·onApplyAreas는 Main의 검색 조건(paramInfo.citys)과 그 변경 함수다.
 * 넘겨주지 않으면 관심 지역 고르기 줄은 나오지 않는다. filtered: 검색·필터가 걸려 있는지(오늘의 요약에 '전국 기준' 표시).
 */
export default function TodayOverview({ items,onOpen,viewMode,setViewMode, month, onMonthChange, isLoading,isError,onResetFilters,searchTargets,onApplyAreas,filtered=false }) {
  const planner=usePlanner();
  // useState에 함수를 넘기면 처음 한 번만 실행된다(렌더마다 localStorage를 읽지 않는다).
  const [recent,setRecent]=useState(() => safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));
  // 최근 본 공고가 바뀌면 'toady-recent' 이벤트로 다시 읽는다. 의존성이 []라 처음 한 번만 등록하고, return 함수가 화면을 떠날 때 해제한다.
  useEffect(() => { const refresh=()=>setRecent(safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));window.addEventListener('toady-recent',refresh);return()=>window.removeEventListener('toady-recent',refresh); },[]);
  // 조회 범위는 달력 앞뒤 칸 때문에 이전·다음 달 일부를 포함한다. 건수와 목록은 고른 달의 일정만 센다(달마다 같은 공고가 겹치지 않게).
  // 달력은 평일 칸만 그리므로 달력 보기의 건수는 평일 일정이 있는 공고만 센다(화면에 보이는 공고 수와 같게). 목록 보기는 주말 일정 공고도 보여 주고 센다.
  const list=uniqueInMonth(items,month);
  const unique=viewMode==='calendar' ? uniqueInMonth(items,month,{weekdaysOnly:true}) : list;
  // 저장한 공고가 있으면 가장 가까운 일정 한 줄, 없으면 처음 방문자에게 관심 지역 고르기를 보여 준다.
  const nextEvent=planner.items.length ? nextPlannerEvent(planner.items,koreaToday()) : null;
  return <>
  {nextEvent ? <NextEvent event={nextEvent} variant="line" onOpen={onOpen} />
    : !planner.isLoading && <InterestAreaPicker savedCount={planner.items.length} searchTargets={searchTargets} onApply={onApplyAreas} />}
  {/* 전국 기준 오늘의 요약 숫자. 검색·필터 중이면(filtered) '전국 기준'을 작게 붙인다. */}
  <TodaySummary savedCount={planner.items.length} filtered={filtered} />
  <Paper variant="outlined" sx={{p:1.5,mt:1.5,borderRadius:2}}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
      <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
      <Chip disabled={isLoading} label={isLoading?'공고 불러오는 중…':isError?'공고를 불러오지 못함':`조회된 공고 ${unique.length}`} color={isError?'default':'primary'} />
      </Stack>
      <ToggleButtonGroup size="small" exclusive value={viewMode} onChange={(_,v)=>{if(v)setViewMode(v);}} aria-label="청약 보기 방식"><ToggleButton value="calendar">달력</ToggleButton><ToggleButton value="list">목록</ToggleButton></ToggleButtonGroup>
    </Stack>
    {/* 최근 본 공고: details로 접어 둔다. */}
    {Array.isArray(recent) && recent.length>0 && <Box component="details" sx={{mt:1,'& summary':{cursor:'pointer',fontSize:12,color:'text.secondary',py:.5},'&[open] summary':{mb:.5}}}><Box component="summary">최근 본 공고 {recent.filter(x=>ID_PATTERN.test(x?.id || '')).length}개</Box><Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="caption" color="text.secondary">최근 본 공고</Typography><Button size="small" color="inherit" onClick={()=>{try{localStorage.removeItem('toady.recent');}catch{}setRecent([]);}}>기록 지우기</Button></Stack><Stack direction="row" gap={1} sx={{overflowX:'auto',pb:.5}}>{recent.filter(x=>ID_PATTERN.test(x?.id || '')).map(x=><Button size="small" key={x.id} sx={{flexShrink:0,maxWidth:240,display:'block',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}} onClick={()=>onOpen(x.id)}>{x.houseNm}</Button>)}</Stack></Box>}
    {/* 목록 보기일 때만 달 이동과 공고 목록을 보여 준다. */}
    {viewMode==='list' && <Stack direction="row" alignItems="center" justifyContent="center" gap={1} sx={{mt:2,mb:2}}>
      <IconButton aria-label="목록 이전달" onClick={()=>onMonthChange(shiftMonth(month,-1))}><ChevronLeftRounded /></IconButton>
      <TextField type="month" size="small" value={month} inputProps={{'aria-label':'목록 조회 월',min:'1900-01',max:'2200-12'}} onChange={e=>{if(/^\d{4}-\d{2}$/.test(e.target.value))onMonthChange(e.target.value);}} />
      <IconButton aria-label="목록 다음달" onClick={()=>onMonthChange(shiftMonth(month,1))}><ChevronRightRounded /></IconButton>
    </Stack>}
    {viewMode==='list' && <Stack gap={1}>{isLoading ? <Skeleton variant="rounded" height={100} /> : list.map(item=><Paper key={item.subscriptSeq} variant="outlined" sx={{p:2,borderRadius:2}}><Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}><Box sx={{minWidth:0}}>{/* 같은 단지가 무순위·재공급처럼 공고를 따로 내면 이름·주소·접수일이 같아 보이므로 공급 구분을 함께 보여 준다. */}{item.houseSecdNm && <Chip size="small" label={item.houseSecdNm} variant="outlined" sx={{height:22,fontSize:12,mb:.5}} />}{/* LH청약플러스에서 접수하는 공고(receiptChannel.js) */}{item.receiptChannel==='LH' && <Chip size="small" label="LH 접수" sx={{height:22,fontSize:12,mb:.5,ml:item.houseSecdNm?.5:0,bgcolor:'#e8f1fb',color:'#1d4f8a',fontWeight:700}} />}<Typography component="h3" sx={{fontSize:16,margin:0,overflowWrap:'anywhere'}} fontWeight={750}>{item.houseNm}</Typography><Typography variant="body2" color="text.secondary">{item.hssplyAdres || item.supplyLocation}</Typography><Typography variant="caption">접수 {item.rceptBgnde || '미제공'} ~ {item.rceptEndde || '미제공'}</Typography></Box><Button aria-label={`${item.houseNm}${item.houseSecdNm ? ` (${item.houseSecdNm})` : ''} 자세히 보기`} sx={{flexShrink:0}} onClick={()=>onOpen(item.subscriptSeq)}>자세히 보기</Button></Stack></Paper>)}{/* 오류 안내·다시 시도는 바로 아래 Main의 알림이 맡는다. 오류를 '조건에 맞는 공고 없음·필터 초기화'로 보이지 않는다. */}{!isLoading && !isError && !list.length && <Alert severity="info" action={<Button size="small" onClick={()=>onResetFilters?.()}>필터 초기화</Button>}>선택한 조건의 공고가 없습니다. 날짜를 바꾸거나 필터를 초기화해 보세요.</Alert>}</Stack>}
  </Paper>
  </>;
}
