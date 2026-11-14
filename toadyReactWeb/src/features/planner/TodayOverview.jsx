import { useState, useEffect } from 'react';
import { Alert, Box, Button, Chip, Paper, Stack, ToggleButton, ToggleButtonGroup, Typography, TextField, IconButton, Skeleton } from '@mui/material';
import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { shiftMonth, uniqueInMonth } from './browseState';
import { safeRead, ID_PATTERN } from './plannerUtils';
import { usePlanner } from './usePlanner';
import TodaySummary from './TodaySummary';

export function rememberViewed(detail) {
  if (!ID_PATTERN.test(detail?.subscriptSeq || '') || !detail?.houseNm) return;
  try {
    const current = safeRead(localStorage, 'toady.recent', []);
    localStorage.setItem('toady.recent', JSON.stringify([{id:detail.subscriptSeq,houseNm:detail.houseNm}, ...(Array.isArray(current) ? current : []).filter(x => x.id !== detail.subscriptSeq)].slice(0,8)));
    window.dispatchEvent(new Event('toady-recent'));
  } catch { /* 최근 본 공고는 기기에 저장할 수 있는 경우에만 제공한다. */ }
}
export default function TodayOverview({ items,onOpen,viewMode,setViewMode, month, onMonthChange, isLoading,isError,onResetFilters }) {
  const planner=usePlanner();
  const [recent,setRecent]=useState(() => safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));
  useEffect(() => { const refresh=()=>setRecent(safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));window.addEventListener('toady-recent',refresh);return()=>window.removeEventListener('toady-recent',refresh); },[]);
  // 조회 범위는 달력 앞뒤 칸 때문에 이전·다음 달 일부를 포함한다. 건수와 목록은 고른 달의 일정만 센다(달마다 같은 공고가 겹치지 않게).
  const unique=uniqueInMonth(items,month);
  const list=unique;
  return <>
  <TodaySummary savedCount={planner.items.length} />
  <Paper variant="outlined" sx={{p:1.5,mt:1.5,borderRadius:2}}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
      <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
      <Chip disabled={isLoading} label={isLoading?'공고 불러오는 중…':isError?'공고를 불러오지 못함':`조회된 공고 ${unique.length}`} color={isError?'default':'primary'} />
      </Stack>
      <ToggleButtonGroup size="small" exclusive value={viewMode} onChange={(_,v)=>{if(v)setViewMode(v);}} aria-label="청약 보기 방식"><ToggleButton value="calendar">달력</ToggleButton><ToggleButton value="list">목록</ToggleButton></ToggleButtonGroup>
    </Stack>
    {Array.isArray(recent) && recent.length>0 && <Box component="details" sx={{mt:1,'& summary':{cursor:'pointer',fontSize:12,color:'text.secondary',py:.5},'&[open] summary':{mb:.5}}}><Box component="summary">최근 본 공고 {recent.filter(x=>ID_PATTERN.test(x?.id || '')).length}개</Box><Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="caption" color="text.secondary">최근 본 공고</Typography><Button size="small" color="inherit" onClick={()=>{try{localStorage.removeItem('toady.recent');}catch{}setRecent([]);}}>기록 지우기</Button></Stack><Stack direction="row" gap={1} sx={{overflowX:'auto',pb:.5}}>{recent.filter(x=>ID_PATTERN.test(x?.id || '')).map(x=><Button size="small" key={x.id} sx={{flexShrink:0,maxWidth:240,display:'block',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}} onClick={()=>onOpen(x.id)}>{x.houseNm}</Button>)}</Stack></Box>}
    {viewMode==='list' && <Stack direction="row" alignItems="center" justifyContent="center" gap={1} sx={{mt:2,mb:2}}>
      <IconButton aria-label="목록 이전달" onClick={()=>onMonthChange(shiftMonth(month,-1))}><ChevronLeftRounded /></IconButton>
      <TextField type="month" size="small" value={month} inputProps={{'aria-label':'목록 조회 월',min:'1900-01',max:'2200-12'}} onChange={e=>{if(/^\d{4}-\d{2}$/.test(e.target.value))onMonthChange(e.target.value);}} />
      <IconButton aria-label="목록 다음달" onClick={()=>onMonthChange(shiftMonth(month,1))}><ChevronRightRounded /></IconButton>
    </Stack>}
    {viewMode==='list' && <Stack gap={1}>{isLoading ? <Skeleton variant="rounded" height={100} /> : list.map(item=><Paper key={item.subscriptSeq} variant="outlined" sx={{p:2,borderRadius:2}}><Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}><Box sx={{minWidth:0}}>{/* 같은 단지가 무순위·재공급처럼 공고를 따로 내면 이름·주소·접수일이 같아 보이므로 공급 구분을 함께 보여 준다. */}{item.houseSecdNm && <Chip size="small" label={item.houseSecdNm} variant="outlined" sx={{height:22,fontSize:12,mb:.5}} />}<Typography component="h3" sx={{fontSize:16,margin:0,overflowWrap:'anywhere'}} fontWeight={750}>{item.houseNm}</Typography><Typography variant="body2" color="text.secondary">{item.hssplyAdres || item.supplyLocation}</Typography><Typography variant="caption">접수 {item.rceptBgnde || '미제공'} ~ {item.rceptEndde || '미제공'}</Typography></Box><Button aria-label={`${item.houseNm}${item.houseSecdNm ? ` (${item.houseSecdNm})` : ''} 자세히 보기`} sx={{flexShrink:0}} onClick={()=>onOpen(item.subscriptSeq)}>자세히 보기</Button></Stack></Paper>)}{/* 오류 안내·다시 시도는 바로 아래 Main의 알림이 맡는다. 오류를 '조건에 맞는 공고 없음·필터 초기화'로 보이지 않는다. */}{!isLoading && !isError && !list.length && <Alert severity="info" action={<Button size="small" onClick={()=>onResetFilters?.()}>필터 초기화</Button>}>선택한 조건의 공고가 없습니다. 날짜를 바꾸거나 필터를 초기화해 보세요.</Alert>}</Stack>}
  </Paper>
  </>;
}
