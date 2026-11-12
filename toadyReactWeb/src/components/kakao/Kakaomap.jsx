import React,{useEffect,useRef,useState} from 'react';
import {Alert,Box,Button,CircularProgress,Paper,TextField,Typography} from '@mui/material';
import {ensureKakaoLoaded} from '@/config/ensureKakaoLoaded';
import {useAnnouncementLocation} from '@/features/announcement/location/useAnnouncementLocation';
import {resolveLocation} from '@/features/announcement/location/resolveLocation';
import './kakao.css';

/** 확정된 주소와 미검증 검색 후보를 구분한다. 시청·역·견본주택을 단지 위치로 대체하지 않는다. */
export default function Kakaomap({subscriptDetail:detail={}}) {
  const container=useRef(null),generation=useRef(0);
  const location=useAnnouncementLocation(detail);
  const [manual,setManual]=useState(''),[manualResult,setManualResult]=useState(null),[manualError,setManualError]=useState(''),[searching,setSearching]=useState(false),[showCandidate,setShowCandidate]=useState(false),[mapError,setMapError]=useState('');
  const result=manualResult || location.data;
  const precise=result?.status==='ADDRESS';
  const visible=precise || (showCandidate && result?.status==='CANDIDATE');
  useEffect(()=>{setManualResult(null);setShowCandidate(false);setManualError('');generation.current++;},[detail.subscriptSeq]);
  useEffect(()=>{
    let active=true,marker,map,observer;
    setMapError('');
    if(visible && container.current) ensureKakaoLoaded().then(kakao=>{
      if(!active || !container.current)return;
      const coords=new kakao.maps.LatLng(result.latitude,result.longitude);
      map=new kakao.maps.Map(container.current,{center:coords,level:precise?4:6});
      marker=new kakao.maps.Marker({map,position:coords});
      map.addControl(new kakao.maps.ZoomControl(),kakao.maps.ControlPosition.RIGHT);
      if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>{map.relayout();map.setCenter(coords);});observer.observe(container.current);}
    }).catch(()=>{if(active)setMapError('지도를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');});
    return()=>{active=false;observer?.disconnect();marker?.setMap(null);};
  },[visible,result,precise]);
  useEffect(()=>()=>{generation.current++;},[]);
  async function search(e) {
    e.preventDefault();if(!manual.trim())return;
    const request=++generation.current;setSearching(true);setManualError('');
    try {
      const kakao=await ensureKakaoLoaded();
      const found=await resolveLocation(kakao.maps,{...detail,hssplyAdres:manual,houseNm:''});
      if(request!==generation.current)return;
      if(found.status!=='ADDRESS')setManualError('지번 또는 도로명과 건물번호까지 입력해 주세요. 지역명만으로는 위치를 확정하지 않습니다.');
      else {setManualResult(found);setShowCandidate(false);}
    }catch(e){if(request===generation.current)setManualError(e.message);}
    finally{if(request===generation.current)setSearching(false);}
  }
  const original=detail.hssplyAdres || detail.supplyLocation || '공급위치 미제공';
  return <Box sx={{pt:.5}}>
    <Paper variant="outlined" sx={{p:2,mb:2,borderRadius:2,bgcolor:'#f8fafc'}}><Typography variant="caption" color="text.secondary">모집공고 공급위치</Typography><Typography sx={{fontSize:14,fontWeight:700,mt:.5}}>{original}</Typography></Paper>
    {(location.isLoading || location.isFetching) && <Box sx={{p:3,display:'flex',gap:1}}><CircularProgress size={20}/>공급위치를 확인하고 있어요.</Box>}
    {location.error && <Alert severity="warning" action={<Button onClick={()=>location.refetch()}>다시 확인</Button>}>위치 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</Alert>}
    {result?.status==='UNRESOLVED' && <Alert severity="info" sx={{mb:2}}>정확한 위치 확인 중입니다. 지구·블록 주소는 부지를 특정하지 못할 수 있어 임의의 위치를 표시하지 않습니다.</Alert>}
    {result?.status==='CANDIDATE' && <Alert severity="warning" sx={{mb:2}} action={<Button onClick={()=>setShowCandidate(x=>!x)}>{showCandidate?'후보 숨기기':'검색 후보 보기'}</Button>}>단지명과 일치하는 검색 후보가 있으나 실제 부지는 확인되지 않았습니다. 견본주택·사업지 여부를 확인해 주세요.</Alert>}
    {visible && <>
      <Alert severity={precise?'info':'warning'} sx={{mb:1}}>{manualResult?'직접 검색한 주소 · 공고의 확정 위치가 아닙니다':result.label} · {result.address}</Alert>
      {mapError?<Alert severity="error">{mapError}</Alert>:<div ref={container} aria-label={precise?'공급 주소 기준 지도':'미확인 위치 검색 후보 지도'} style={{height:420,width:'100%',border:'1px solid #e2e8f0',borderRadius:8}}/>}
      <Typography variant="caption" color="text.secondary">표시 지점은 부지 경계·출입구를 뜻하지 않습니다.</Typography>
    </>}
    <Box component="form" onSubmit={search} sx={{mt:2,display:'flex',gap:1}}><TextField fullWidth size="small" label="지번·도로명으로 직접 확인" placeholder="예: 경기도 파주시 동패동 400-7" value={manual} onChange={e=>setManual(e.target.value)}/><Button type="submit" variant="outlined" disabled={searching || !manual.trim()} sx={{flexShrink:0}}>검색</Button></Box>
    <Typography variant="caption" color="text.secondary">직접 검색은 이 지도에서만 적용되며 공고 위치나 실거래 조회 지역을 변경하지 않습니다.</Typography>
    {manualError && <Alert severity="warning" sx={{mt:1}}>{manualError}</Alert>}
    {manualResult && <Button size="small" onClick={()=>{generation.current++;setManualResult(null);setManualError('');setSearching(false);}}>공고 위치로 돌아가기</Button>}
  </Box>;
}
