import { useState, useEffect } from 'react';
import { Alert, Box, Button, Chip, Paper, Stack, ToggleButton, ToggleButtonGroup, Typography, TextField, IconButton, Skeleton } from '@mui/material';
import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import { shiftMonth } from './browseState';
import { Link } from 'react-router-dom';
import { koreaToday, safeRead, ID_PATTERN } from './plannerUtils';
import { usePlanner } from './usePlanner';

export function rememberViewed(detail) {
  if (!ID_PATTERN.test(detail?.subscriptSeq || '') || !detail?.houseNm) return;
  try {
    const current = safeRead(localStorage, 'toady.recent', []);
    localStorage.setItem('toady.recent', JSON.stringify([{id:detail.subscriptSeq,houseNm:detail.houseNm}, ...(Array.isArray(current) ? current : []).filter(x => x.id !== detail.subscriptSeq)].slice(0,8)));
    window.dispatchEvent(new Event('toady-recent'));
  } catch { /* 최근 본 공고는 기기에 저장할 수 있는 경우에만 제공한다. */ }
}
export default function TodayOverview({ items,onOpen,viewMode,setViewMode, month, onMonthChange, isLoading,onResetFilters }) {
  const planner=usePlanner();
  const [recent,setRecent]=useState(() => safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));
  useEffect(() => { const refresh=()=>setRecent(safeRead({getItem:k=>localStorage.getItem(k)},'toady.recent',[]));window.addEventListener('toady-recent',refresh);return()=>window.removeEventListener('toady-recent',refresh); },[]);
  const today=koreaToday();
  const unique=[...new Map(items.map(x=>[x.subscriptSeq,x])).values()];
  const current=unique.filter(x => x.rceptBgnde && x.rceptEndde && x.rceptBgnde <= today && x.rceptEndde >= today);
  const [filter,setFilter]=useState('all');
  const list=filter==='active' ? current : unique;
  return <Paper variant="outlined" sx={{p:1.5,mt:1.5,borderRadius:2}}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
      <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
      <Chip disabled={isLoading} label={isLoading?'공고 불러오는 중…':`조회된 공고 ${unique.length}`} onClick={()=>setFilter('all')} color={filter==='all'?'primary':'default'} />
      <Chip disabled={isLoading} label={`오늘 접수 중 ${current.length}`} onClick={()=>{onMonthChange(today.slice(0,7));setFilter('active');setViewMode('list');}} color={filter==='active'?'primary':'default'} />
      <ToggleButtonGroup size="small" exclusive value={viewMode} onChange={(_,v)=>{if(v){setViewMode(v);if(v==='calendar')setFilter('all');}}} aria-label="청약 보기 방식"><ToggleButton value="calendar">달력</ToggleButton><ToggleButton value="list">목록</ToggleButton></ToggleButtonGroup>
      </Stack>
      <Button size="small" component={Link} to="/my">나의 청약 {planner.items.length}개 →</Button>
    </Stack>
    {Array.isArray(recent) && recent.length>0 && <Box component="details" sx={{mt:1,'& summary':{cursor:'pointer',fontSize:12,color:'text.secondary',py:.5},'&[open] summary':{mb:.5}}}><Box component="summary">최근 본 공고 {recent.filter(x=>ID_PATTERN.test(x?.id || '')).length}개</Box><Stack direction="row" justifyContent="space-between" alignItems="center"><Typography variant="caption" color="text.secondary">최근 본 공고</Typography><Button size="small" color="inherit" onClick={()=>{try{localStorage.removeItem('toady.recent');}catch{}setRecent([]);}}>기록 지우기</Button></Stack><Stack direction="row" gap={1} sx={{overflowX:'auto',pb:.5}}>{recent.filter(x=>ID_PATTERN.test(x?.id || '')).map(x=><Button size="small" key={x.id} sx={{flexShrink:0,maxWidth:240,display:'block',overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}} onClick={()=>onOpen(x.id)}>{x.houseNm}</Button>)}</Stack></Box>}
    {viewMode==='list' && <Stack direction="row" alignItems="center" justifyContent="center" gap={1} sx={{mt:2,mb:2}}>
      <IconButton aria-label="목록 이전달" onClick={()=>onMonthChange(shiftMonth(month,-1))}><ChevronLeftRounded /></IconButton>
      <TextField type="month" size="small" value={month} inputProps={{'aria-label':'목록 조회 월',min:'1900-01',max:'2200-12'}} onChange={e=>{if(/^\d{4}-\d{2}$/.test(e.target.value))onMonthChange(e.target.value);}} />
      <IconButton aria-label="목록 다음달" onClick={()=>onMonthChange(shiftMonth(month,1))}><ChevronRightRounded /></IconButton>
    </Stack>}
    {viewMode==='list' && <Stack gap={1}>{isLoading ? <Skeleton variant="rounded" height={100} /> : list.map(item=><Paper key={item.subscriptSeq} variant="outlined" sx={{p:2,borderRadius:2}}><Stack direction="row" alignItems="center" justifyContent="space-between" gap={1}><Box sx={{minWidth:0}}><Typography component="h3" sx={{fontSize:16,margin:0,overflowWrap:'anywhere'}} fontWeight={750}>{item.houseNm}</Typography><Typography variant="body2" color="text.secondary">{item.hssplyAdres || item.supplyLocation}</Typography><Typography variant="caption">접수 {item.rceptBgnde || '미제공'} ~ {item.rceptEndde || '미제공'}</Typography></Box><Button aria-label={`${item.houseNm} 자세히 보기`} sx={{flexShrink:0}} onClick={()=>onOpen(item.subscriptSeq)}>자세히 보기</Button></Stack></Paper>)}{!isLoading && !list.length && <Alert severity="info" action={<Button size="small" onClick={()=>{setFilter('all');onResetFilters?.();}}>필터 초기화</Button>}>선택한 조건의 공고가 없습니다. 날짜를 바꾸거나 필터를 초기화해 보세요.</Alert>}</Stack>}
  </Paper>;
}
