import React,{useEffect,useRef,useState} from 'react';
import {useLocation,useNavigate} from 'react-router-dom';
import {Alert,Box,Button,CircularProgress,IconButton,InputAdornment,Paper,TextField,Typography} from '@mui/material';
import SearchRounded from '@mui/icons-material/SearchRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import {ensureKakaoLoaded} from '@/config/ensureKakaoLoaded';
import {koreaToday} from '@/features/planner/plannerUtils';
import {useAnnouncementLocation} from '@/features/announcement/location/useAnnouncementLocation';
import {resolveLocation} from '@/features/announcement/location/resolveLocation';
import {usePriceMap} from '@/features/announcement/location/usePriceMap';
import {areaText,bubbleText,ageTone,isAddressLike,isStaleMonth,outsideText,saleStatusLabel,shortPrice,supplyForBand} from '@/features/announcement/location/priceMapRules';
import {ComplexList,ComplexPanel,PriceModeToggle,PriceMapFilters,PriceMapStatus,PriceMapUnavailable,SalePanel} from '@/features/announcement/location/PriceMapParts';
import {MapToolbar,useMapTools} from '@/features/announcement/location/MapTools';
import './kakao.css';

/**
 * 확정된 주소와 미검증 검색 후보를 구분한다. 시청·역·견본주택을 단지 위치로 대체하지 않는다.
 * 공급 주소가 확정된 공고는 같은 시·군·구의 최근 매매 실거래를 단지별 가격 말풍선으로 함께 보여 준다.
 * 검색창은 하나다: 단지명·동은 가격 표시를 거르고, 지번·도로명은 그 위치로 지도를 옮긴다(공고 위치·실거래 조회 지역은 바꾸지 않는다).
 */
export default function Kakaomap({subscriptDetail:detail={}}) {
  const container=useRef(null),generation=useRef(0),mapRef=useRef(null),kakaoRef=useRef(null),bubbles=useRef(new Map()),dots=useRef(new Map());
  const location=useAnnouncementLocation(detail);
  const [text,setText]=useState(''),[manualResult,setManualResult]=useState(null),[searchMessage,setSearchMessage]=useState(''),[searching,setSearching]=useState(false),[showCandidate,setShowCandidate]=useState(false),[mapError,setMapError]=useState(''),[attempt,setAttempt]=useState(0),[mapReady,setMapReady]=useState(0),[zoomedOut,setZoomedOut]=useState(false);
  const result=manualResult || location.data;
  const precise=result?.status==='ADDRESS';
  const visible=precise || (showCandidate && result?.status==='CANDIDATE');
  // 실거래 조회 지역과 '이 공고' 표시는 공고의 확정 주소로만 정한다(직접 검색한 주소로 바꾸지 않는다).
  const announced=location.data?.status==='ADDRESS'?location.data:null;
  const priceMap=usePriceMap(announced,Boolean(announced),detail.subscriptSeq || '');
  const navigate=useNavigate(),routeLocation=useLocation();
  // 다른 분양 공고 열기: 지금 화면 주소에서 상세 값만 바꿔 히스토리 한 단계를 쌓는다(뒤로가기로 이 공고 지도에 돌아온다).
  // 화면별 상세 표시(state)는 그대로 넘겨 useDirectDetailHistory가 목록 단계를 다시 끼우지 않게 한다.
  const openSale=id=>{const next=new URLSearchParams(routeLocation.search);next.set('subscriptSeq',id);next.set('tabType','map');navigate({pathname:routeLocation.pathname,search:`?${next}`},{state:routeLocation.state});};
  const today=koreaToday();
  const saleBubbles=useRef(new Map());
  const showPrices=priceMap.ready;
  const supply=supplyForBand(detail.unitPrices || [],priceMap.filters);
  // 거리 재기·반경의 기준: 지금 지도 가운데 있는 확정 위치(공고 위치 또는 직접 검색한 주소). 검색 후보 위치는 기준으로 쓰지 않는다.
  const origin=precise?{lat:result.latitude,lng:result.longitude}:null;
  const tools=useMapTools({mapRef,kakaoRef,mapReady,origin});
  const selectRef=useRef(priceMap.select);selectRef.current=priceMap.select;
  const addPointRef=useRef(tools.addPoint);addPointRef.current=tools.addPoint;
  const addDistrictRef=useRef(priceMap.addDistrict);addDistrictRef.current=priceMap.addDistrict;

  useEffect(()=>{setManualResult(null);setShowCandidate(false);setSearchMessage('');setText('');generation.current++;},[detail.subscriptSeq]);
  useEffect(()=>{
    let active=true,marker,map,observer;
    setMapError('');
    if(visible && container.current) ensureKakaoLoaded().then(kakao=>{
      if(!active || !container.current)return;
      const coords=new kakao.maps.LatLng(result.latitude,result.longitude);
      // 확정 주소는 주변 단지가 함께 보이도록 조금 넓게(레벨 5) 연다.
      map=new kakao.maps.Map(container.current,{center:coords,level:precise?5:6});
      // 실거래 지도에서는 공고 위치를 '분양' 말풍선으로 표시하므로 기본 핀은 직접 검색한 위치·가격 미제공일 때만 둔다.
      if(!showPrices || manualResult)marker=new kakao.maps.Marker({map,position:coords});
      map.addControl(new kakao.maps.ZoomControl(),kakao.maps.ControlPosition.RIGHT);
      const far=()=>container.current?.classList.toggle('is-far',map.getLevel()>=6);
      kakao.maps.event.addListener(map,'zoom_changed',far);far();
      // 탭 전환 등으로 크기가 바뀌면 다시 그린다. 사용자가 지도를 움직이기 전까지는 기준 위치를 가운데로 유지한다.
      let moved=false;kakao.maps.event.addListener(map,'dragstart',()=>{moved=true;});
      if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>{map.relayout();if(!moved)map.setCenter(coords);});observer.observe(container.current);}
      mapRef.current=map;kakaoRef.current=kakao;setMapReady(value=>value+1);
    }).catch(()=>{if(active)setMapError('지도를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');});
    return()=>{active=false;observer?.disconnect();marker?.setMap(null);mapRef.current=null;};
  },[visible,result,precise,attempt,showPrices]);

  // 이 공고(분양) 말풍선: 같은 면적 구간의 분양가. 아무 단지도 고르지 않았을 때는 이 공고가 선택된 상태다.
  const saleRef=useRef(null);
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current;
    if(!map || !kakao || !showPrices || !announced)return;
    const button=document.createElement('button');
    button.type='button';button.className='pm-bubble pm-bubble--sale';
    const tag=document.createElement('i');tag.textContent='분양';
    const strong=document.createElement('b');
    const small=document.createElement('small');
    if(supply){strong.textContent=priceMap.filters.mode==='pyeong'?shortPrice(supply.pyeong):shortPrice(supply.amount);small.textContent=priceMap.filters.mode==='pyeong'?(priceMap.filters.unit==='pyeong'?'평당':'3.3㎡당'):areaText(supply.area,priceMap.filters.unit);}
    else {strong.textContent='이 공고';small.textContent='분양가 미제공';}
    button.append(tag,strong,small);
    button.setAttribute('aria-label',`이 공고 ${detail.houseNm || ''} 분양 ${strong.textContent} ${small.textContent}`);
    button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current({lat:announced.latitude,lng:announced.longitude});else selectRef.current(null);});
    saleRef.current=button;
    const overlay=new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(announced.latitude,announced.longitude),content:button,yAnchor:1,clickable:true,zIndex:5});
    return()=>{overlay.setMap(null);saleRef.current=null;};
  },[mapReady,showPrices,announced,supply?.amount,supply?.area,supply?.pyeong,priceMap.filters.mode,priceMap.filters.unit,detail.houseNm]);

  // 단지 가격 말풍선. 글자는 textContent로만 넣는다(단지명은 외부 자료). 거리를 재는 중에는 누르면 그 단지를 지점으로 더한다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    const overlays=priceMap.rows.filter(row=>Number.isFinite(row.lat) && Number.isFinite(row.lng)).map(row=>{
      const {price,sub}=bubbleText(row,priceMap.filters.mode,priceMap.filters.unit,today);
      const button=document.createElement('button');
      button.type='button';button.className=`pm-bubble tone-${ageTone(row.buildYear,priceMap.year)}${isStaleMonth(row.rep?.month,today)?' is-stale':''}`;
      button.setAttribute('aria-label',`${row.name} ${price} ${sub}`);button.title=row.name;
      const strong=document.createElement('b');strong.textContent=price;
      const small=document.createElement('small');small.textContent=sub;
      button.append(strong,small);
      button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current(row);else selectRef.current(row.key);});
      made.set(row.key,button);
      return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(row.lat,row.lng),content:button,yAnchor:1,clickable:true,zIndex:3});
    });
    bubbles.current=made;
    return()=>{overlays.forEach(overlay=>overlay.setMap(null));bubbles.current=new Map();};
  },[mapReady,showPrices,priceMap.rows,priceMap.filters.mode,priceMap.filters.unit,priceMap.year,today]);

  // 조건 밖 단지: 거래는 있지만 고른 면적·연식·가격에 맞는 거래가 없는 단지를 작은 회색 점으로 둔다.
  // 말풍선이 없다고 '위치를 못 찾은 것'으로 오해하지 않게 하고, 누르면 그 단지의 면적별 거래를 볼 수 있다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    const overlays=priceMap.outside.filter(row=>Number.isFinite(row.lat) && Number.isFinite(row.lng)).map(row=>{
      const label=`${row.name} · ${outsideText(row,priceMap.filters.unit)}`;
      const button=document.createElement('button');
      button.type='button';button.className='pm-dot';button.title=label;button.setAttribute('aria-label',label);
      button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current(row);else selectRef.current(row.key);});
      made.set(row.key,button);
      return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(row.lat,row.lng),content:button,yAnchor:.5,clickable:true,zIndex:2});
    });
    dots.current=made;
    return()=>{overlays.forEach(overlay=>overlay.setMap(null));dots.current=new Map();};
  },[mapReady,showPrices,priceMap.outside,priceMap.filters.unit]);

  // 다른 청약 중·예정 공고 말풍선(같은 시·군·구). 눌러서 분양가와 주변 거래를 보고, 그 공고로 이동할 수 있다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    const overlays=priceMap.sales.map(sale=>{
      const units=(sale.units || []).map(unit=>({area:Number(unit.area),amountManwon:unit.amountManwon}));
      const price=supplyForBand(units,priceMap.filters);
      const button=document.createElement('button');
      button.type='button';button.className=`pm-bubble pm-bubble--sale is-other is-${String(sale.status||'').toLowerCase()}`;
      const tag=document.createElement('i');tag.textContent=saleStatusLabel(sale.status);
      const strong=document.createElement('b'),small=document.createElement('small');
      if(price){strong.textContent=priceMap.filters.mode==='pyeong'?shortPrice(price.pyeong):shortPrice(price.amount);small.textContent=priceMap.filters.mode==='pyeong'?(priceMap.filters.unit==='pyeong'?'평당':'3.3㎡당'):areaText(price.area,priceMap.filters.unit);}
      else {strong.textContent='분양';small.textContent='가격 미제공';}
      button.append(tag,strong,small);
      button.setAttribute('aria-label',`${sale.houseNm} ${tag.textContent} ${strong.textContent} ${small.textContent}`);button.title=sale.houseNm;
      button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current(sale);else selectRef.current(`sale:${sale.id}`);});
      made.set(`sale:${sale.id}`,button);
      return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(sale.lat,sale.lng),content:button,yAnchor:1,clickable:true,zIndex:4});
    });
    saleBubbles.current=made;
    return()=>{overlays.forEach(overlay=>overlay.setMap(null));saleBubbles.current=new Map();};
  },[mapReady,showPrices,priceMap.sales,priceMap.filters.areaMin,priceMap.filters.areaMax,priceMap.filters.mode,priceMap.filters.unit]);

  // 지도를 옮기거나 확대·축소해 멈추면, 화면 가운데와 네 모서리가 속한 시·군·구를 찾아 그 지역 거래도 불러온다.
  // 너무 넓게(레벨 7 이상) 보면 지역이 많아지므로 확대하라고만 안내한다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current;
    if(!map || !kakao || !showPrices || !kakao.maps.services)return;
    const geocoder=new kakao.maps.services.Geocoder(),seen=new Set();let timer;
    const look=()=>{
      clearTimeout(timer);
      timer=setTimeout(()=>{
        const far=map.getLevel()>=7;setZoomedOut(far);
        if(far)return;
        const bounds=map.getBounds(),sw=bounds.getSouthWest(),ne=bounds.getNorthEast();
        const spots=[map.getCenter(),sw,ne,new kakao.maps.LatLng(sw.getLat(),ne.getLng()),new kakao.maps.LatLng(ne.getLat(),sw.getLng())];
        spots.forEach(spot=>{
          // 같은 곳(약 1km 격자)은 다시 묻지 않는다.
          const cell=`${spot.getLat().toFixed(2)},${spot.getLng().toFixed(2)}`;
          if(seen.has(cell))return;seen.add(cell);
          geocoder.coord2RegionCode(spot.getLng(),spot.getLat(),(rows,status)=>{
            if(status!==kakao.maps.services.Status.OK)return;
            const legal=rows.find(row=>row.region_type==='B');
            if(legal)addDistrictRef.current({bcode:legal.code,region:[legal.region_1depth_name,legal.region_2depth_name].filter(Boolean).join(' ')});
          });
        });
      },500);
    };
    kakao.maps.event.addListener(map,'idle',look);look();
    return()=>{clearTimeout(timer);kakao.maps.event.removeListener(map,'idle',look);};
  },[mapReady,showPrices]);

  // 선택한 단지는 색만 바꾼다(말풍선 수백 개를 다시 만들지 않게).
  const selectedKey=priceMap.selectedKey || null;
  useEffect(()=>{
    bubbles.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
    dots.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
    saleRef.current?.classList.toggle('is-selected',!selectedKey);
    saleBubbles.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
  },[selectedKey,priceMap.rows,priceMap.outside,priceMap.sales,priceMap.filters.mode,priceMap.filters.unit,priceMap.filters.areaMin,priceMap.filters.areaMax,mapReady,showPrices,supply?.amount]);

  function chooseFromList(row) {
    priceMap.select(row.key);
    const map=mapRef.current,kakao=kakaoRef.current;
    if(map && kakao && Number.isFinite(row.lat)) {
      map.panTo(new kakao.maps.LatLng(row.lat,row.lng));
      container.current?.scrollIntoView({behavior:'smooth',block:'center'});
    }
  }

  // 입력하는 동안: 단지명·동이면 가격 표시를 거르고, 주소처럼 보이면 거르지 않는다(검색 버튼을 누르면 그 위치로 이동).
  function changeText(value) {
    setText(value);setSearchMessage('');
    if(showPrices)priceMap.setFilters({query:isAddressLike(value)?'':value});
  }
  function clearText() {changeText('');}

  useEffect(()=>()=>{generation.current++;},[]);
  async function search(e) {
    e.preventDefault();
    const value=text.trim();if(!value)return;
    // 단지 이름으로 걸러진 단지가 있으면 가장 위 단지를 고른다.
    if(showPrices && !isAddressLike(value) && priceMap.rows.length){chooseFromList(priceMap.rows[0]);return;}
    const request=++generation.current;setSearching(true);setSearchMessage('');
    try {
      const kakao=await ensureKakaoLoaded();
      // '동패동 400-7'처럼 시·도를 빼고 쓰면 공고의 시·군·구를 앞에 붙여 찾는다(주소 확인 규칙은 그대로).
      const regionPrefix=!/^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)/.test(value) && announced?.region?`${announced.region} `:'';
      const found=await resolveLocation(kakao.maps,{...detail,hssplyAdres:regionPrefix+value,houseNm:''});
      if(request!==generation.current)return;
      if(found.status!=='ADDRESS')setSearchMessage(showPrices?'일치하는 단지가 없어요. 주소로 찾으려면 동·도로명과 번지까지 입력해 주세요(예: 동패동 400-7).':'지번 또는 도로명과 건물번호까지 입력해 주세요. 지역명만으로는 위치를 확정하지 않습니다.');
      else {setManualResult(found);setShowCandidate(false);setText('');if(showPrices)priceMap.setFilters({query:''});}
    }catch(e){if(request===generation.current)setSearchMessage(e.message);}
    finally{if(request===generation.current)setSearching(false);}
  }
  function backToAnnouncement() {generation.current++;setManualResult(null);setSearchMessage('');setSearching(false);setText('');if(showPrices)priceMap.setFilters({query:''});}

  const original=detail.hssplyAdres || detail.supplyLocation || '공급위치 미제공';
  const tradesOff=priceMap.capabilities.data && priceMap.capabilities.data.tradesEnabled!==true;
  const priceError=priceMap.query.isError || (priceMap.query.data && priceMap.query.data.status!=='READY' && priceMap.query.data.status!=='EMPTY');
  const searchBar=<Box component="form" onSubmit={search} sx={{display:'flex',gap:1,my:1.5}} role="search">
    <TextField fullWidth size="small" value={text} onChange={event=>changeText(event.target.value)}
      placeholder={showPrices?'단지명·동 이름 또는 지번·도로명':'지번·도로명으로 위치 찾기 (예: 동패동 400-7)'}
      inputProps={{'aria-label':showPrices?'단지명·동 이름 또는 지번·도로명 검색':'지번·도로명으로 위치 찾기',maxLength:60}}
      InputProps={{startAdornment:<InputAdornment position="start"><SearchRounded fontSize="small"/></InputAdornment>,
        endAdornment:text?<InputAdornment position="end"><IconButton size="small" aria-label="검색어 지우기" onClick={clearText}><CloseRounded fontSize="small"/></IconButton></InputAdornment>:null}}/>
    <Button type="submit" variant="outlined" disabled={searching || !text.trim()} sx={{flexShrink:0}}>{searching?<CircularProgress size={18}/>:'검색'}</Button>
  </Box>;
  const searchHint=showPrices && text.trim()
    ? (isAddressLike(text)?'검색을 누르면 이 주소로 지도를 옮겨요. 공고 위치와 실거래 조회 지역은 바뀌지 않아요.':`이름이 맞는 단지 ${priceMap.rows.length}곳만 보여요. 검색을 누르면 목록 맨 위 단지로 이동해요.`)
    : '';

  return <Box sx={{pt:.5}}>
    <Paper variant="outlined" sx={{p:2,mb:1,borderRadius:2,bgcolor:'#f8fafc'}}><Typography variant="caption" color="text.secondary">모집공고 공급위치</Typography><Typography sx={{fontSize:14,fontWeight:700,mt:.5}}>{original}</Typography></Paper>
    {(location.isLoading || location.isFetching) && <Box sx={{p:3,display:'flex',gap:1}}><CircularProgress size={20}/>공급위치를 확인하고 있어요.</Box>}
    {location.error && <Alert severity="warning" action={<Button onClick={()=>location.refetch()}>다시 확인</Button>}>위치 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</Alert>}
    {result?.status==='UNRESOLVED' && <Alert severity="info" sx={{mb:1}}>정확한 위치 확인 중입니다. 지구·블록 주소는 부지를 특정하지 못할 수 있어 임의의 위치를 표시하지 않습니다. 아래에서 지번·도로명으로 직접 찾아볼 수 있어요.</Alert>}
    {result?.status==='CANDIDATE' && <Alert severity="warning" sx={{mb:1}} action={<Button onClick={()=>setShowCandidate(x=>!x)}>{showCandidate?'후보 숨기기':'검색 후보 보기'}</Button>}>단지명과 일치하는 검색 후보가 있으나 실제 부지는 확인되지 않았습니다. 견본주택·사업지 여부를 확인해 주세요.</Alert>}
    {location.data && location.data.status!=='ADDRESS' && <PriceMapUnavailable reason="주변 실거래가는 공급 주소(지번)가 확인된 공고에서만 보여 드려요. 확인되지 않은 위치 주변의 가격은 표시하지 않습니다."/>}
    {showPrices && <Typography component="h3" sx={{m:0,mt:2,fontSize:16,fontWeight:800}}>주변 아파트 실거래가</Typography>}
    {(location.data || manualResult) && searchBar}
    {searchHint && <Typography variant="caption" color="text.secondary" component="p" sx={{mt:-1,mb:1}}>{searchHint}</Typography>}
    {searchMessage && <Alert severity="warning" sx={{mb:1}}>{searchMessage}</Alert>}
    {visible && <>
      <Alert severity={precise?'info':'warning'} sx={{mb:1}} action={manualResult?<Button color="inherit" size="small" onClick={backToAnnouncement}>공고 위치로</Button>:null}>
        {manualResult?'직접 검색한 위치 · 공고의 확정 위치가 아닙니다':result.label} · {result.address}
      </Alert>
      {showPrices && <PriceMapFilters filters={priceMap.filters} setFilters={priceMap.setFilters}/>}
      {/* 일시적인 네트워크 오류 뒤에도 탭을 나갔다 오지 않고 다시 불러올 수 있게 한다. */}
      {mapError?<Alert severity="error" action={<Button color="inherit" size="small" onClick={()=>{setMapError('');setAttempt(value=>value+1);}}>다시 시도</Button>}>{mapError}</Alert>
        :<Box className="price-map-wrap">
          <div ref={container} className="price-map-canvas" aria-label={precise?'공급 주소 기준 지도':'미확인 위치 검색 후보 지도'}/>
          {mapReady>0 && <MapToolbar tools={tools} originLabel={manualResult?'검색한 위치':'공고 위치'}>{showPrices && <PriceModeToggle filters={priceMap.filters} setFilters={priceMap.setFilters}/>}</MapToolbar>}
        </Box>}
      <Typography variant="caption" color="text.secondary">표시 지점은 부지 경계·출입구를 뜻하지 않습니다.</Typography>
      {showPrices && <>
        {priceError?<Alert severity="warning" sx={{mt:1}} action={<Button color="inherit" size="small" onClick={()=>priceMap.query.refetch()}>다시 시도</Button>}>주변 실거래를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.</Alert>
          :<PriceMapStatus query={priceMap.query} rows={priceMap.rows} outside={priceMap.outside} sales={priceMap.sales} unlocatedSales={priceMap.unlocatedSales} onOpenSale={openSale} filters={priceMap.filters} pending={priceMap.pending} districtCount={priceMap.districtCount} loadingOthers={priceMap.loadingOthers} zoomedOut={zoomedOut}/>}
        {priceMap.query.data?.status==='READY' && !priceMap.rows.length && <Alert severity="info" sx={{mt:1}} action={<Button color="inherit" size="small" onClick={()=>{clearText();priceMap.setFilters({areaMin:0,areaMax:165,priceMin:0,priceMax:400000,age:'all',query:''});}}>조건 풀기</Button>}>{priceMap.outside.length?`조건에 맞는 거래가 없어요. 거래가 있는 단지 ${priceMap.outside.length.toLocaleString('ko-KR')}곳은 지도에 회색 점으로 보여요. 조건을 풀어 보세요.`:'조건에 맞는 거래가 없어요. 조건을 풀어 보세요.'}</Alert>}
        {priceMap.query.data?.status==='EMPTY' && <Alert severity="info" sx={{mt:1}}>이 기간에 신고된 아파트 매매 거래가 없어요. 기간을 늘려 보세요.</Alert>}
        {priceMap.selectedSale?<SalePanel detail={detail} sale={priceMap.selectedSale} complexes={priceMap.complexes} origin={priceMap.origin} unit={priceMap.filters.unit}
            onClose={()=>priceMap.select(null)} onOpen={sale=>openSale(sale.id)}/>
          :priceMap.selected?<ComplexPanel row={priceMap.selected} supply={supply} unit={priceMap.filters.unit} onClose={()=>priceMap.select(null)}/>
          :!manualResult && <SalePanel detail={detail} complexes={priceMap.complexes} origin={priceMap.origin} unit={priceMap.filters.unit}/>}
        <ComplexList rows={priceMap.rows} filters={priceMap.filters} setFilters={priceMap.setFilters} selectedKey={selectedKey} onSelect={chooseFromList}/>
      </>}
      {precise && tradesOff && <PriceMapUnavailable reason="지금은 실거래 정보를 제공하지 않고 있어요."/>}
    </>}
  </Box>;
}
