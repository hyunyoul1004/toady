import React,{useEffect,useLayoutEffect,useRef,useState} from 'react';
import {createPortal} from 'react-dom';
import {useLocation,useNavigate} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import apiClient from '@/api/client';
import {Alert,Autocomplete,Box,Button,CircularProgress,IconButton,InputAdornment,Paper,TextField,Typography} from '@mui/material';
import ApartmentRounded from '@mui/icons-material/ApartmentRounded';
import PlaceRounded from '@mui/icons-material/PlaceRounded';
import MapRounded from '@mui/icons-material/MapRounded';
import CampaignRounded from '@mui/icons-material/CampaignRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import TuneRounded from '@mui/icons-material/TuneRounded';
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded';
import MyLocationRounded from '@mui/icons-material/MyLocationRounded';
import {useHistoryOverlay} from '@/hooks/useHistoryOverlay';
import {closeStackSteps,liftedOverlayStates} from '@/utils/overlay.util';
import {trackAction} from '@/features/analytics/trackAction';
import {ensureKakaoLoaded} from '@/config/ensureKakaoLoaded';
import {koreaToday} from '@/features/planner/plannerUtils';
import {useAnnouncementLocation} from '@/features/announcement/location/useAnnouncementLocation';
import {municipality,resolveLocation,searchArea,suggestAddresses} from '@/features/announcement/location/resolveLocation';
import {usePriceMap} from '@/features/announcement/location/usePriceMap';
import {GROUP_LEVELS,distanceMeters,northOf,saleAreaText,bubbleText,ageTone,displayTier,groupRows,inView,isAddressLike,isPriceFiltered,isStaleMonth,outsideText,saleStatusLabel,suggestPlaces,saleStatusOf,shortPrice,supplyForBand} from '@/features/announcement/location/priceMapRules';
import {ComplexList,ComplexPanel,NearbySummary,PriceModeToggle,PriceMapFilters,PriceMapStatus,PriceMapUnavailable,SalePanel} from '@/features/announcement/location/PriceMapParts';
import {MapToolRail,MapToolbar,useMapTools} from '@/features/announcement/location/MapTools';
import {PriceGapSummary} from '@/features/match/PriceGapCard';

/**
 * 확정된 주소와 미검증 검색 후보를 구분한다. 시청·역·견본주택을 단지 위치로 대체하지 않는다.
 * 공급 주소가 확정된 공고는 같은 시·군·구의 최근 매매 실거래를 단지별 가격 말풍선으로 함께 보여 준다.
 * 검색창은 하나다: 단지명·동은 가격 표시를 거르고, 지번·도로명은 그 위치로 지도를 옮긴다(공고 위치·실거래 조회 지역은 바꾸지 않는다).
 */
export default function Kakaomap({subscriptDetail:detail={}}) {
  const container=useRef(null),keepCenter=useRef(null),generation=useRef(0),mapRef=useRef(null),kakaoRef=useRef(null),bubbles=useRef(new Map()),dots=useRef(new Map());
  const location=useAnnouncementLocation(detail);
  const [text,setText]=useState(''),[manualResult,setManualResult]=useState(null),[searchMessage,setSearchMessage]=useState(''),[searching,setSearching]=useState(false),[searchInfo,setSearchInfo]=useState(''),[showCandidate,setShowCandidate]=useState(false),[mapError,setMapError]=useState(''),[attempt,setAttempt]=useState(0),[mapReady,setMapReady]=useState(0),[zoomedOut,setZoomedOut]=useState(false),[mapLevel,setMapLevel]=useState(5),[view,setView]=useState(null);
  const result=manualResult || location.data;
  const precise=result?.status==='ADDRESS';
  // 지구·블록 주소: 부지는 확정하지 않고 주소의 법정동(또는 시·군·구) 일대만 '추정'으로 보여 준다(핀 없음).
  const approx=!manualResult && result?.status==='UNRESOLVED' && result.area ? result.area : null;
  const visible=precise || Boolean(approx) || (showCandidate && result?.status==='CANDIDATE');
  // 실거래 조회 지역과 '이 공고' 표시는 공고의 확정 주소로만 정한다(직접 검색한 주소로 바꾸지 않는다).
  const announced=location.data?.status==='ADDRESS'?location.data:null;
  const approxArea=location.data?.status==='UNRESOLVED' && location.data.area ? location.data.area : null;
  const priceLocation=announced || (approxArea && {status:'AREA',bcode:approxArea.bcode,region:approxArea.region,dong:approxArea.dong});
  const priceMap=usePriceMap(priceLocation,Boolean(priceLocation),detail.subscriptSeq || '',detail.unitPrices);
  const navigate=useNavigate(),routeLocation=useLocation();
  // 다른 분양 공고 열기: 지금 화면 주소에서 상세 값만 바꿔 히스토리 한 단계를 쌓는다(뒤로가기로 이 공고 지도에 돌아온다).
  // 화면별 상세 표시(state)는 그대로 넘겨 useDirectDetailHistory가 목록 단계를 다시 끼우지 않게 한다.
  // 지도에서 고른 단지·공고(price-map-complex)와 전체 화면(map-fullscreen) 레이어는 새 공고로 따라가지 않게 빼고 넘긴다
  // (남기면 새 공고에서 '닫기'·전체 화면 닫기가 이전 공고로 뒤로가기 됐다).
  const openSale=id=>{const next=new URLSearchParams(routeLocation.search);next.set('subscriptSeq',id);next.set('tabType','map');
    const state=routeLocation.state?{...routeLocation.state,overlayLayers:(routeLocation.state.overlayLayers || []).filter(layer=>layer.id!=='price-map-complex' && layer.id!=='map-fullscreen')}:routeLocation.state;
    navigate({pathname:routeLocation.pathname,search:`?${next}`},{state});};
  const today=koreaToday();
  const saleBubbles=useRef(new Map()),salePanelRef=useRef(null);
  // 검색 예시는 공고 지역의 동 이름으로(다른 지역 공고에 파주 예시가 보이지 않게).
  const addressExample=`${(location.data?.dong || location.data?.area?.dong || '○○동').split(' ').pop()} 123-4`;
  const showPrices=priceMap.ready;
  const supply=supplyForBand(detail.unitPrices || [],priceMap.filters);
  // 표시 단계: 축소 레벨 + 화면 밀도(레벨 5에서도 화면 안 단지가 많으면 법정동 묶음).
  const tier=displayTier(mapLevel,priceMap.rows,view);

  // ── 전체 화면 지도 ──
  // 히스토리 한 단계로 연다(뒤로가기·Esc·닫기로 원래 자리로). 지도는 다시 만들지 않고, 지도 영역(host)만 화면 최상단으로 옮겨
  // 보던 위치·확대 단계·말풍선을 그대로 이어 간다. 상세 패널의 슬라이드 효과(transform) 안에서는 fixed가 화면 기준이 아니므로 body로 옮긴다.
  const [fullscreenLayer,setFullscreenLayer]=useHistoryOverlay('map-fullscreen');
  const fullscreen=Boolean(fullscreenLayer) && visible && !mapError;
  const [fsPanel,setFsPanel]=useState(null);   // 전체 화면 옆(모바일은 아래) 목록 패널: 'list' | null
  const [mapHost]=useState(()=>(typeof document==='undefined'?null:document.createElement('div')));
  const mapSlot=useRef(null),holdTimer=useRef(null);
  /** 지도 크기가 바뀌어도(전체 화면 전환·패널 열고 닫기) 보던 가운데를 유지한다. */
  const holdCenter=()=>{const map=mapRef.current;if(!map)return;keepCenter.current=map.getCenter();clearTimeout(holdTimer.current);holdTimer.current=setTimeout(()=>{keepCenter.current=null;},900);};
  // 붙이기와 떼기를 한 효과에서 처리한다(전체 화면이면 body에, 아니면 지도 자리에). 지도 자리에 있을 때는 자리가 사라지면 함께 사라진다.
  useLayoutEffect(()=>{
    if(!mapHost)return undefined;
    const target=fullscreen?document.body:mapSlot.current;
    if(!target)return undefined;
    mapHost.className=fullscreen?'pm-fs-host':'pm-map-host';
    if(mapHost.parentNode!==target)target.appendChild(mapHost);
    if(!fullscreen)return undefined;
    // 전체 화면 동안 뒤 화면이 스크롤되지 않게 한다.
    const before=document.body.style.overflow;document.body.style.overflow='hidden';
    return()=>{document.body.style.overflow=before;if(mapHost.parentNode===document.body)mapHost.remove();};
  },[fullscreen,mapHost,visible,mapError]);
  useEffect(()=>()=>clearTimeout(holdTimer.current),[]);
  useEffect(()=>{if(!fullscreen)setFsPanel(null);},[fullscreen]);
  // 고른 단지가 있으면 전체 화면 위로 옮겨 쌓는다([단지, 전체 화면]이 되면 단지만 닫을 때 히스토리가 어긋난다).
  // 전체 화면 표시(히스토리)는 남았는데 지도를 띄울 수 없으면(지도 오류·확정 위치 없음) 그 표시를 걷어 낸다.
  // 남겨 두면 상세가 Esc로 닫히지 않고(전체 화면이 Esc를 맡는다고 보므로) 화면에는 전체 화면이 없다.
  useEffect(()=>{
    if(fullscreenLayer && (mapError || (location.isSuccess && !visible)))setFullscreenLayer(false);
  },[fullscreenLayer,mapError,location.isSuccess,visible]); // eslint-disable-line react-hooks/exhaustive-deps
  const openFullscreen=()=>{
    holdCenter();trackAction('map_tool','tool/map-fullscreen');
    const states=liftedOverlayStates(routeLocation.state,'map-fullscreen',true,['price-map-complex']);
    if(!states){setFullscreenLayer(true);return;}
    const url=routeLocation.pathname+routeLocation.search+routeLocation.hash;
    navigate(url,{state:states[0],replace:true});navigate(url,{state:states[1]});
  };
  // 위에 고른 단지가 있으면 함께 닫는다(전체 화면만 빼면 뒤로가기 때 전체 화면이 다시 열린다).
  const closeFullscreen=()=>{holdCenter();const steps=closeStackSteps(routeLocation.state,'map-fullscreen');if(steps>1)navigate(-steps);else setFullscreenLayer(false);};
  const toggleFsPanel=panel=>{holdCenter();setFsPanel(current=>(current===panel?null:panel));};
  // Esc: 열린 패널이 있으면 패널부터, 아니면 전체 화면을 닫는다(고른 단지는 그 단지 레이어가 따로 닫는다).
  const escRef=useRef(null);
  escRef.current=()=>{if(priceMap.selectedKey){priceMap.select(null);return;}if(fsPanel){toggleFsPanel(fsPanel);return;}closeFullscreen();};
  useEffect(()=>{
    if(!fullscreen)return undefined;
    const onKey=event=>{if(event.key==='Escape' && !event.defaultPrevented){event.preventDefault();escRef.current();}};
    window.addEventListener('keydown',onKey);
    return()=>window.removeEventListener('keydown',onKey);
  },[fullscreen]);
  /** 기준 위치(공고 위치 또는 직접 검색한 위치)로 지도를 되돌린다. */
  // 검색으로 공고와 다른 지역으로 옮겼을 때 그 이름(지도 위에 '○○ 일대 보는 중'으로 계속 보여 준다).
  const [viewing,setViewing]=useState(null);
  function recenter(){
    setViewing(null);
    const map=mapRef.current,kakao=kakaoRef.current;if(!map || !kakao)return;
    const point=approx?{lat:approx.latitude,lng:approx.longitude}:result && Number.isFinite(result.latitude)?{lat:result.latitude,lng:result.longitude}:null;
    if(!point)return;
    if(map.getLevel()>5)map.setLevel(5);
    map.panTo(new kakao.maps.LatLng(point.lat,point.lng));
  }
  // 가까운 역·초등학교·대형마트: 확정 지번 좌표가 있을 때만(서버가 카카오 키로 조회, 직선거리).
  const nearby=useQuery({queryKey:['nearbyPlaces',announced?.latitude?.toFixed(5),announced?.longitude?.toFixed(5)],enabled:Boolean(announced),staleTime:86400000,retry:false,
    queryFn:()=>apiClient.get('/api/v1/research/nearby',{lat:Number(announced.latitude.toFixed(5)),lng:Number(announced.longitude.toFixed(5))})});
  // 거리 재기·반경의 기준: 지금 지도 가운데 있는 확정 위치(공고 위치 또는 직접 검색한 주소). 검색 후보 위치는 기준으로 쓰지 않는다.
  const origin=precise?{lat:result.latitude,lng:result.longitude}:null;
  const tools=useMapTools({mapRef,kakaoRef,containerRef:container,mapReady,origin});
  // 기능 사용 측정: 거리를 실제로 잰 때(두 지점째)와 반경을 다 그린 때만 센다(켰다 끈 것은 세지 않음).
  const measured=tools.points.length>=2,radiusDone=Boolean(tools.circle && !tools.circle.live);
  useEffect(()=>{if(measured)trackAction('map_tool','tool/map-measure');},[measured]);
  useEffect(()=>{if(radiusDone)trackAction('map_tool','tool/map-radius');},[radiusDone,tools.circle?.meters]);
  const selectRef=useRef(priceMap.select);selectRef.current=priceMap.select;
  const addPointRef=useRef(tools.addPoint);addPointRef.current=tools.addPoint;
  const addDistrictRef=useRef(priceMap.addDistrict);addDistrictRef.current=priceMap.addDistrict;

  useEffect(()=>{setManualResult(null);setShowCandidate(false);setSearchMessage('');setSearchInfo('');setText('');setViewing(null);generation.current++;},[detail.subscriptSeq]);
  // 직접 검색한 주소로 바뀌면(지도를 다시 만듦) '보는 중' 표시는 그 위치 안내(직접 검색한 위치)로 대신한다.
  useEffect(()=>{setViewing(null);},[manualResult]);
  // 짧은 안내(검색 결과 등)는 몇 초 뒤 저절로 닫는다. 경고(찾지 못함)는 닫을 때까지 둔다.
  useEffect(()=>{if(!searchInfo)return undefined;const timer=setTimeout(()=>setSearchInfo(''),4000);return()=>clearTimeout(timer);},[searchInfo]);
  useEffect(()=>{
    let active=true,map,observer,kakaoApi;const listeners=[],areaShapes=[];
    setMapError('');
    if(visible && container.current) ensureKakaoLoaded().then(kakao=>{
      if(!active || !container.current)return;
      kakaoApi=kakao;
      const listen=(type,handler)=>{kakao.maps.event.addListener(map,type,handler);listeners.push([type,handler]);};
      const coords=approx?new kakao.maps.LatLng(approx.latitude,approx.longitude):new kakao.maps.LatLng(result.latitude,result.longitude);
      // 확정 주소는 주변 단지가 함께 보이도록 조금 넓게(레벨 5) 연다.
      map=new kakao.maps.Map(container.current,{center:coords,level:precise?5:approx?(approx.level==='dong'?6:8):6});
      // 대략 지역은 점선 원과 '○○ 일대(추정)' 이름표로만 표시한다. 확정 위치처럼 보이는 핀은 놓지 않는다.
      if(approx && approx.level==='dong'){
        areaShapes.push(new kakao.maps.Circle({map,center:coords,radius:700,strokeWeight:2,strokeColor:'#7c3aed',strokeOpacity:.8,strokeStyle:'dash',fillColor:'#7c3aed',fillOpacity:.05}));
      }
      if(approx){const tag=document.createElement('div');tag.className='pm-approx-tag';tag.textContent=`${approx.name} 일대(추정) · 부지 미확정`;
        // 이름표는 원의 위쪽 가장자리에 둔다(가운데 생기는 법정동 묶음 말풍선과 겹치지 않게).
        const top=approx.level==='dong'?northOf({lat:approx.latitude,lng:approx.longitude},700):{lat:approx.latitude,lng:approx.longitude};
        areaShapes.push(new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(top.lat,top.lng),content:tag,yAnchor:.5,zIndex:6}));}
      // 실거래 지도에서는 공고 위치를 '이 공고' 말풍선으로 표시하므로 기본 핀은 직접 검색한 위치·가격 미제공일 때만 둔다.
      const far=()=>container.current?.classList.toggle('is-far',map.getLevel()>=6);
      listen('zoom_changed',far);far();
      // 멈출 때마다 축소 단계(단지별·법정동·시군구 묶음)와 화면 범위를 기억한다. 단지별 말풍선은 화면 근처만 만든다(큰 시·군·구에서도 가볍게).
      const remember=()=>{
        const bounds=map.getBounds(),sw=bounds.getSouthWest(),ne=bounds.getNorthEast(),round=value=>Math.round(value*1000)/1000;
        setMapLevel(map.getLevel());
        setView(current=>{const next={swLat:round(sw.getLat()),swLng:round(sw.getLng()),neLat:round(ne.getLat()),neLng:round(ne.getLng())};
          return current && ['swLat','swLng','neLat','neLng'].every(key=>current[key]===next[key])?current:next;});
      };
      listen('idle',remember);remember();
      // 탭 전환 등으로 크기가 바뀌면 다시 그린다. 사용자가 지도를 움직이기 전까지는 기준 위치를 가운데로 유지한다.
      let moved=false;listen('dragstart',()=>{moved=true;});
      if(typeof ResizeObserver!=='undefined'){observer=new ResizeObserver(()=>{map.relayout();if(keepCenter.current)map.setCenter(keepCenter.current);else if(!moved)map.setCenter(coords);});observer.observe(container.current);}
      mapRef.current=map;kakaoRef.current=kakao;setMapReady(value=>value+1);
    }).catch(()=>{if(active)setMapError('지도를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');});
    // 다시 만들 때(가격 표시 여부·위치가 바뀔 때) 이전 지도의 이벤트와 화면을 지운다. 남겨 두면 같은 칸에 지도가 겹치고
    // 이전 지도의 확대·축소 단추가 지금 지도와 다른 축소 단계를 기록한다.
    return()=>{active=false;observer?.disconnect();areaShapes.forEach(shape=>shape.setMap(null));
      if(map && kakaoApi)listeners.forEach(([type,handler])=>kakaoApi.maps.event.removeListener(map,type,handler));
      if(container.current)container.current.replaceChildren();
      mapRef.current=null;};
  },[visible,result,precise,approx,attempt]);

  // 기본 핀: 직접 검색한 위치나 가격 지도를 쓸 수 없을 때만(가격 지도에서는 공고 위치를 '이 공고' 말풍선으로 표시).
  // 지도 생성과 나눠, 가격 표시 가능 여부가 늦게 정해져도 지도를 다시 만들지 않는다(옮긴 위치·확대 수준 유지).
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current;
    if(!map || !kakao || approx || !result || !Number.isFinite(result.latitude) || (showPrices && !manualResult))return;
    const marker=new kakao.maps.Marker({map,position:new kakao.maps.LatLng(result.latitude,result.longitude)});
    return()=>marker.setMap(null);
  },[mapReady,showPrices,manualResult,approx,result]);

  // 이 공고 말풍선: 같은 면적 구간의 분양가. 상태는 다른 분양 말풍선과 같은 규칙(접수 중·예정·종료)으로 붙이고, '이 공고'와 꽉 찬 보라로 구분한다.
  // 아무 단지도 고르지 않았을 때는 이 공고가 선택된 상태다.
  const saleRef=useRef(null);
  const siteStatus=saleStatusOf(detail.rceptBgnde || detail.subscrptRceptBgnde,detail.rceptEndde || detail.subscrptRceptEndde,today);
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current;
    if(!map || !kakao || !showPrices || !announced)return;
    const button=document.createElement('button');
    button.type='button';button.className='pm-bubble pm-bubble--sale';
    const tag=document.createElement('i');tag.textContent=siteStatus?`이 공고 · ${saleStatusLabel(siteStatus)}`:'이 공고';
    const strong=document.createElement('b');
    const small=document.createElement('small');
    if(supply){strong.textContent=priceMap.filters.mode==='pyeong'?shortPrice(supply.pyeong):shortPrice(supply.amount);small.textContent=priceMap.filters.mode==='pyeong'?(priceMap.filters.unit==='pyeong'?'평당':'3.3㎡당'):saleAreaText(supply,priceMap.filters.unit);}
    else {strong.textContent='이 공고';small.textContent='분양가 미제공';}
    button.append(tag,strong,small);
    button.setAttribute('aria-label',`이 공고 ${detail.houseNm || ''} 분양 ${strong.textContent} ${small.textContent}`);
    button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current({lat:announced.latitude,lng:announced.longitude});else selectRef.current(null);});
    saleRef.current=button;
    const overlay=new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(announced.latitude,announced.longitude),content:button,yAnchor:1,clickable:true,zIndex:5});
    return()=>{overlay.setMap(null);saleRef.current=null;};
  },[mapReady,showPrices,announced,supply?.amount,supply?.area,supply?.pyeong,priceMap.filters.mode,priceMap.filters.unit,detail.houseNm,siteStatus]);

  // 가까운 시설 표시(역·초·마트). 이름은 textContent로만 넣는다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current;
    const places=nearby.data?.status==='READY'?nearby.data.places:[];
    if(!map || !kakao || !places.length || manualResult)return;
    const overlays=places.map(place=>{
      const tag=document.createElement('div');tag.className='pm-place';
      const icon=document.createElement('i');icon.className=`pm-nearby-icon is-${place.kind.toLowerCase()}`;icon.textContent=({STATION:'역',SCHOOL:'초',MART:'마트'})[place.kind] || '';
      const name=document.createElement('span');name.textContent=place.name;
      tag.append(icon,name);
      return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(place.lat,place.lng),content:tag,yAnchor:.5,zIndex:1});
    });
    return()=>overlays.forEach(overlay=>overlay.setMap(null));
  },[mapReady,nearby.data,manualResult]);
  function focusPlace(place) {
    const map=mapRef.current,kakao=kakaoRef.current;
    if(!map || !kakao)return;
    if(map.getLevel()>5)map.setLevel(5);
    map.panTo(new kakao.maps.LatLng(place.lat,place.lng));
    container.current?.scrollIntoView({behavior:'smooth',block:'center'});
  }

  // 단지 가격 말풍선. 글자는 textContent로만 넣는다(단지명은 외부 자료). 거리를 재는 중에는 누르면 그 단지를 지점으로 더한다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    if(tier!=='single'){
      // 넓게 보면 법정동(더 넓으면 시·군·구)별 묶음 하나씩: 이름 · 대표 가격 중간값 · 단지 수. 누르면 그 묶음 범위로 확대한다.
      const groups=groupRows(priceMap.rows,tier,{mode:priceMap.filters.mode,names:priceMap.districtNames});
      const overlays=groups.map(group=>{
        const button=document.createElement('button');
        button.type='button';button.className=`pm-group is-${tier}`;
        const name=document.createElement('span');name.textContent=group.label;
        const strong=document.createElement('b');strong.textContent=`${priceMap.filters.mode==='pyeong'?'평당 ':''}${shortPrice(group.price)}`;
        const small=document.createElement('small');small.textContent=`${group.count.toLocaleString('ko-KR')}곳`;
        button.append(name,strong,small);
        button.setAttribute('aria-label',`${group.label} 단지 ${group.count}곳, 대표 가격 중간값 ${strong.textContent}. 누르면 확대`);
        button.addEventListener('click',event=>{
          event.stopPropagation();
          const {swLat,swLng,neLat,neLng}=group.bounds;
          // 묶음을 누르면 한 단계 아래로: 시·군·구 → 법정동 묶음, 법정동 → 단지별(밀집 판정이 다시 묶지 않는 레벨 4).
          const limit=tier==='district'?GROUP_LEVELS.district-1:GROUP_LEVELS.dong-2;
          const center=new kakao.maps.LatLng(group.lat,group.lng);
          // 단지가 한 곳뿐이면 범위가 점이라 setBounds가 최대로 확대되므로, 그 단계 레벨로 옮기기만 한다.
          if(group.count===1){map.setLevel(limit,{anchor:center});map.panTo(center);return;}
          map.setBounds(new kakao.maps.LatLngBounds(new kakao.maps.LatLng(swLat,swLng),new kakao.maps.LatLng(neLat,neLng)),60,60,60,60);
          if(map.getLevel()>limit)map.setLevel(limit,{anchor:center});
        });
        return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(group.lat,group.lng),content:button,yAnchor:.5,clickable:true,zIndex:3});
      });
      bubbles.current=made;
      return()=>{overlays.forEach(overlay=>overlay.setMap(null));};
    }
    const overlays=priceMap.rows.filter(row=>Number.isFinite(row.lat) && Number.isFinite(row.lng) && inView(row,view) && row.key!==priceMap.siteComplex?.key).map(row=>{
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
  },[mapReady,showPrices,priceMap.rows,priceMap.siteComplex?.key,priceMap.filters.mode,priceMap.filters.unit,priceMap.year,today,tier,tier==='single'?view:null,priceMap.districtNames]);

  // 조건 밖 단지: 거래는 있지만 고른 면적·연식·가격에 맞는 거래가 없는 단지를 작은 회색 점으로 둔다.
  // 말풍선이 없다고 '위치를 못 찾은 것'으로 오해하지 않게 하고, 누르면 그 단지의 면적별 거래를 볼 수 있다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    // 넓게 볼 때는 조건 밖 점을 그리지 않는다(묶음과 섞여 읽기 어렵다).
    if(tier!=='single')return;
    const overlays=priceMap.outside.filter(row=>Number.isFinite(row.lat) && Number.isFinite(row.lng) && inView(row,view) && row.key!==priceMap.siteComplex?.key).map(row=>{
      const label=`${row.name} · ${outsideText(row,priceMap.filters.unit)}`;
      const button=document.createElement('button');
      button.type='button';button.className='pm-dot';button.title=label;button.setAttribute('aria-label',label);
      button.addEventListener('click',event=>{event.stopPropagation();if(tools.measuringRef.current)addPointRef.current(row);else selectRef.current(row.key);});
      made.set(row.key,button);
      return new kakao.maps.CustomOverlay({map,position:new kakao.maps.LatLng(row.lat,row.lng),content:button,yAnchor:.5,clickable:true,zIndex:2});
    });
    dots.current=made;
    return()=>{overlays.forEach(overlay=>overlay.setMap(null));dots.current=new Map();};
  },[mapReady,showPrices,priceMap.outside,priceMap.siteComplex?.key,priceMap.filters.unit,tier,tier==='single'?view:null]);

  // 다른 분양 공고 말풍선(같은 시·군·구, 접수 중·예정·종료). 눌러서 분양가와 주변 거래를 보고, 그 공고로 이동할 수 있다.
  useEffect(()=>{
    const map=mapRef.current,kakao=kakaoRef.current,made=new Map();
    if(!map || !kakao || !showPrices)return;
    const overlays=priceMap.sales.map(sale=>{
      const units=(sale.units || []).map(unit=>({area:Number(unit.area),amountManwon:unit.amountManwon,supplyArea:unit.supplyArea==null?null:Number(unit.supplyArea),type:unit.type}));
      // 고른 면적 범위에 그 공고 주택형이 없으면 전체에서 대표 주택형을 쓴다('가격 미제공'으로 잘못 보이지 않게).
      const price=supplyForBand(units,priceMap.filters) || supplyForBand(units,'all');
      const button=document.createElement('button');
      button.type='button';button.className=`pm-bubble pm-bubble--sale is-other is-${String(sale.status||'').toLowerCase()}`;
      const tag=document.createElement('i');tag.textContent=saleStatusLabel(sale.status);
      const strong=document.createElement('b'),small=document.createElement('small');
      if(price){strong.textContent=priceMap.filters.mode==='pyeong'?shortPrice(price.pyeong):shortPrice(price.amount);small.textContent=priceMap.filters.mode==='pyeong'?(priceMap.filters.unit==='pyeong'?'평당':'3.3㎡당'):saleAreaText(price,priceMap.filters.unit);}
      else {strong.textContent='분양';small.textContent='분양가 자료 없음';}
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
    // 물어본 격자(약 1km) → 시·군·구. 오래돼 목록에서 빠진 지역으로 다시 오면 기억한 값으로 다시 더한다(addDistrict가 중복을 거른다).
    const geocoder=new kakao.maps.services.Geocoder(),known=new Map();let timer,active=true;
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
          if(known.has(cell)){const remembered=known.get(cell);if(remembered)addDistrictRef.current(remembered);return;}
          known.set(cell,null);
          geocoder.coord2RegionCode(spot.getLng(),spot.getLat(),(rows,status)=>{
            // 지도를 닫았거나 다른 공고로 바뀐 뒤 늦게 온 응답은 쓰지 않는다.
            if(!active)return;
            if(status!==kakao.maps.services.Status.OK){known.delete(cell);return;}
            const legal=rows.find(row=>row.region_type==='B');
            if(!legal)return;
            const found={bcode:legal.code,region:[legal.region_1depth_name,legal.region_2depth_name].filter(Boolean).join(' ')};
            known.set(cell,found);addDistrictRef.current(found);
          });
        });
      },500);
    };
    kakao.maps.event.addListener(map,'idle',look);look();
    return()=>{active=false;clearTimeout(timer);kakao.maps.event.removeListener(map,'idle',look);};
  },[mapReady,showPrices]);

  // 선택한 단지는 색만 바꾼다(말풍선 수백 개를 다시 만들지 않게).
  const selectedKey=priceMap.selectedKey || null;
  useEffect(()=>{
    bubbles.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
    dots.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
    saleRef.current?.classList.toggle('is-selected',!selectedKey);
    saleBubbles.current.forEach((element,key)=>element.classList.toggle('is-selected',key===selectedKey));
  },[selectedKey,tier,view,priceMap.rows,priceMap.outside,priceMap.sales,priceMap.filters.mode,priceMap.filters.unit,priceMap.filters.areaMin,priceMap.filters.areaMax,mapReady,showPrices,supply?.amount]);

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
  // 자동완성: TOADY에서 불러온 단지·분양·법정동(바로)과 카카오 주소·장소(입력을 멈추면 0.3초 뒤).
  const [remote,setRemote]=useState({text:'',items:[],loading:false});
  useEffect(()=>{
    const value=text.trim();
    if(value.length<2){setRemote({text:'',items:[],loading:false});return undefined;}
    let active=true;setRemote(current=>({...current,loading:true}));
    const timer=setTimeout(()=>{
      ensureKakaoLoaded().then(kakao=>{
        const center=mapRef.current?.getCenter?.()||null;
        return suggestAddresses(kakao.maps,value,{center,limit:5});
      }).then(items=>{if(active)setRemote({text:value,items,loading:false});})
        .catch(()=>{if(active)setRemote({text:value,items:[],loading:false});});
    },350);
    return()=>{active=false;clearTimeout(timer);};
  },[text]);
  const localSuggestions=React.useMemo(()=>showPrices?suggestPlaces(priceMap.complexes,priceMap.sales,text.trim(),{limit:6,statusLabel:saleStatusLabel,districtNames:priceMap.districtNames}):[],[showPrices,priceMap.complexes,priceMap.sales,text]);
  const suggestions=React.useMemo(()=>{
    const names=new Set(localSuggestions.map(item=>item.label));
    const fresh=remote.text===text.trim()?remote.items.filter(item=>!names.has(item.label)):[];
    return text.trim()?[...localSuggestions,...fresh]:[];
  },[localSuggestions,remote,text]);
  /** 자동완성 후보를 고르면: 단지·분양은 그 말풍선을 고르고, 번지까지 있는 주소는 위치로 확정, 나머지는 그 일대로 지도만 옮긴다. */
  function pickSuggestion(option){
    if(!option || typeof option==='string')return;
    trackAction('map_tool','tool/map-suggest');
    generation.current++;setSearching(false);setSearchMessage('');setSearchInfo('');
    if(option.kind==='address' && option.exact){runSearch(option.label);return;}
    setText('');if(showPrices)priceMap.setFilters({query:''});
    const map=mapRef.current,kakao=kakaoRef.current;if(!map || !kakao)return;
    if(option.kind==='complex' || option.kind==='sale'){
      if(map.getLevel()>4)map.setLevel(4);
      map.setCenter(new kakao.maps.LatLng(option.lat,option.lng));
      holdCenter();priceMap.select(option.key);
      return;
    }
    moveTo({latitude:option.lat,longitude:option.lng,level:option.kind==='place'?4:5,name:option.label});
  }
  // '검색' 단추. 엔터는 자동완성 onChange가 먼저 처리하므로 바로 뒤의 폼 제출은 건너뛴다(두 번 검색하지 않게).
  const enterAt=useRef(0);
  function search(e) {e.preventDefault();if(Date.now()-enterAt.current<500)return;if(text.trim())trackAction('map_tool','tool/map-search');runSearch(text.trim());}
  async function runSearch(value) {
    if(!value)return;
    // 단지 이름으로 걸러진 단지가 있으면 가장 위 단지를 고른다.
    if(showPrices && !isAddressLike(value) && priceMap.rows.length){chooseFromList(priceMap.rows[0]);return;}
    const request=++generation.current;setSearching(true);setSearchMessage('');setSearchInfo('');
    try {
      const kakao=await ensureKakaoLoaded();
      // '동패동 400-7'처럼 시·도와 시·군·구를 빼고 쓰면 공고의 시·군·구를 앞에 붙여 찾는다(주소 확인 규칙은 그대로).
      // '고양시 덕양구 …'처럼 시·군·구를 직접 쓴 값에는 붙이지 않는다(공고 지역과 겹쳐 어떤 결과도 확정되지 않게 되므로).
      const ownRegion=/^(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충청|충북|충남|전라|전북|전남|경상|경북|경남|제주)/.test(value) || municipality(value).length>0;
      const regionPrefix=!ownRegion && (announced || approxArea)?.region?`${(announced || approxArea).region} `:'';
      const found=await resolveLocation(kakao.maps,{...detail,hssplyAdres:regionPrefix+value,houseNm:''});
      if(request!==generation.current)return;
      if(found.status!=='ADDRESS'){
        // 번지까지 맞는 주소가 아니면(동·도로명·지역·장소 이름) 위치를 확정하지 않고 그 일대로 지도만 옮긴다.
        const area=await searchArea(kakao.maps,value,regionPrefix);
        if(request!==generation.current)return;
        if(area && moveTo(area)){setText('');if(showPrices)priceMap.setFilters({query:''});}
        else setSearchMessage(`'${value}'을(를) 찾지 못했어요. 동·도로명·지역 이름이나 번지까지 입력해 보세요(예: ${addressExample}).`);
      }
      // 공고의 확정 위치와 같은 곳(약 20m 안)이면 '직접 검색한 위치'로 바꾸지 않고 공고 위치 그대로 둔다.
      else if(announced && Number.isFinite(found.latitude) && distanceMeters({lat:found.latitude,lng:found.longitude},{lat:announced.latitude,lng:announced.longitude})<20){
        setManualResult(null);setText('');setSearchInfo(`검색한 주소는 공고 위치와 같습니다 · ${found.address}`);if(showPrices)priceMap.setFilters({query:''});
      }
      else {setManualResult(found);setShowCandidate(false);setText('');setSearchInfo('');if(showPrices)priceMap.setFilters({query:''});}
    }catch(e){if(request===generation.current)setSearchMessage(e.message);}
    finally{if(request===generation.current)setSearching(false);}
  }
  /** 오른쪽 도구 막대의 확대(+1)·축소(-1). */
  function zoomBy(step){
    const map=mapRef.current;if(!map)return;
    const level=Math.min(14,Math.max(1,map.getLevel()-step));
    if(level!==map.getLevel())map.setLevel(level,{animate:{duration:200}});
  }
  /** 확정 위치를 바꾸지 않고 지도 화면만 옮긴다(가격 지도는 멈춘 뒤 그 지역 거래를 불러온다). */
  function moveTo(area){
    const map=mapRef.current,kakao=kakaoRef.current;if(!map || !kakao)return false;
    priceMap.select(null);setViewing(area.name || null);
    map.setLevel(area.level);
    map.setCenter(new kakao.maps.LatLng(area.latitude,area.longitude));
    return true;
  }
  function backToAnnouncement() {setViewing(null);generation.current++;setManualResult(null);setSearchMessage('');setSearching(false);setText('');if(showPrices)priceMap.setFilters({query:''});}

  // 시·군·구가 없는 값(예: 공급지역명 '경기')은 주소가 아니다. 오피스텔·도시형 일부는 청약홈 자료에 주소가 없어 이렇게 온다.
  const rawAddress=detail.hssplyAdres || detail.supplyLocation || '';
  const hasAddress=municipality(rawAddress).length>0;
  const original=hasAddress?rawAddress:(rawAddress && rawAddress!=='-'?`${rawAddress} (상세 주소 미제공)`:'공급위치 미제공');
  const tradesOff=priceMap.capabilities.data && priceMap.capabilities.data.tradesEnabled!==true;
  const unsupported=priceMap.query.data?.status==='UNSUPPORTED';
  const priceError=priceMap.query.isError || (priceMap.query.data && !['READY','EMPTY','UNSUPPORTED'].includes(priceMap.query.data.status));
  const kindIcon={complex:<ApartmentRounded fontSize="small"/>,sale:<CampaignRounded fontSize="small"/>,dong:<MapRounded fontSize="small"/>,address:<PlaceRounded fontSize="small"/>,place:<PlaceRounded fontSize="small"/>};
  const searchBar=<Box component="form" onSubmit={search} sx={{display:'flex',gap:1,my:1.5}} role="search">
    <Autocomplete freeSolo fullWidth size="small" value={null} inputValue={text} options={suggestions}
      filterOptions={options=>options} getOptionLabel={option=>typeof option==='string'?option:option.label}
      isOptionEqualToValue={(option,value)=>option.key===value?.key} getOptionKey={option=>option.key}
      groupBy={option=>(['complex','sale','dong'].includes(option.kind)?'TOADY 단지·분양':'주소·장소')}
      loading={remote.loading} blurOnSelect selectOnFocus={false} clearText="검색어 지우기"
      slotProps={{popper:{className:'pm-suggest-popper'},listbox:{className:'pm-suggest-list'}}}
      onInputChange={(_,value,reason)=>{if(reason==='input')changeText(value);else if(reason==='clear')clearText();}}
      onChange={(_,option)=>{
        // 후보를 고르지 않고 엔터: 자동완성이 입력값(문자열)을 넘겨 준다. 폼 제출 대신 여기서 검색한다.
        if(typeof option==='string'){enterAt.current=Date.now();if(option.trim()){trackAction('map_tool','tool/map-search');runSearch(option.trim());}return;}
        pickSuggestion(option);
      }}
      renderOption={({key,...props},option)=><li key={key} {...props}>
        <span className={`pm-suggest-icon is-${option.kind}`}>{kindIcon[option.kind]}</span>
        <span className="pm-suggest-text"><b>{option.label}</b>{option.detail && <small>{option.detail}</small>}</span>
      </li>}
      renderInput={params=><TextField {...params}
        placeholder={showPrices?'단지명·동 이름 또는 지번·도로명':`주소·동 이름으로 지도 옮기기 (예: ${addressExample})`}
        inputProps={{...params.inputProps,'aria-label':showPrices?'단지명·동 이름 또는 지번·도로명 검색':'주소·동 이름으로 지도 옮기기',maxLength:60,enterKeyHint:'search'}}
        InputProps={{...params.InputProps,startAdornment:<InputAdornment position="start"><SearchRounded fontSize="small"/></InputAdornment>}}/>}/>
    <Button type="submit" variant="outlined" disabled={searching || !text.trim()} sx={{flexShrink:0}}>{searching?<CircularProgress size={18}/>:'검색'}</Button>
  </Box>;
  const searchHint=showPrices && text.trim()
    ? (isAddressLike(text)?'검색을 누르면 이 주소로 지도를 옮겨요. 공고 위치와 실거래 조회 지역은 바뀌지 않아요.':priceMap.rows.length?`이름이 맞는 단지 ${priceMap.rows.length}곳만 보여요. 검색을 누르면 목록 맨 위 단지로 이동해요.`:'지금 지도에는 맞는 단지가 없어요. 검색을 누르면 그 지역으로 지도를 옮겨요.')
    : '';

  // 전체 화면 패널: 고른 분양·단지가 먼저, 아니면 필터·목록. PC는 오른쪽, 모바일은 아래에서 올라온다.
  const areaBase=priceMap.areaDefault || {areaMin:0,areaMax:165};
  const fsFilterCount=showPrices?[priceMap.filters.areaMin!==areaBase.areaMin || priceMap.filters.areaMax!==areaBase.areaMax,isPriceFiltered(priceMap.filters),priceMap.filters.age!=='all',Boolean(priceMap.filters.query)].filter(Boolean).length:0;
  const fsPanelTitle=fsPanel==='list'?`단지 목록 ${priceMap.rows.length.toLocaleString('ko-KR')}곳`:'';
  const fsPanelBody=!fullscreen || !showPrices?null
    :priceMap.selectedSale?<SalePanel detail={detail} sale={priceMap.selectedSale} complexes={priceMap.complexes} origin={priceMap.origin} unit={priceMap.filters.unit} onClose={()=>priceMap.select(null)} onOpen={sale=>openSale(sale.id)}/>
    :priceMap.selected?<ComplexPanel row={priceMap.selected} supply={supply} unit={priceMap.filters.unit} onClose={()=>priceMap.select(null)}/>
    :fsPanel==='list'?<ComplexList compact rows={priceMap.rows} filters={priceMap.filters} setFilters={priceMap.setFilters} selectedKey={selectedKey} onSelect={chooseFromList}/>
    :null;
  const fsPanelHeader=fullscreen && fsPanelBody && !priceMap.selected && !priceMap.selectedSale;
  const fsMessage=searchMessage || searchInfo || (fullscreen?searchHint:'');
  // 전체 화면 안에서 누른 Esc는 여기서 처리하고 위(상세 패널)로 올리지 않는다.
  const onFsKeyDown=event=>{if(fullscreen && event.key==='Escape' && !event.defaultPrevented){event.preventDefault();event.stopPropagation();escRef.current();}};
  const mapContent=<Box className={`price-map-wrap${fullscreen?' is-fullscreen':''}${fsPanelBody?' has-panel':''}`} onKeyDown={onFsKeyDown}>
    {fullscreen && <div key="fs-top" className="pm-fs-top">
      <IconButton className="pm-fs-back" aria-label="전체 화면 닫기" onClick={closeFullscreen}><ArrowBackRounded/></IconButton>
      <div className="pm-fs-title"><b>{detail.houseNm || '공고 위치'}</b><small>{showPrices?'주변 아파트 실거래가':approx?'주소 일대(추정)':'위치 지도'}</small></div>
      {(location.data || manualResult) && <div className="pm-fs-search">{searchBar}</div>}
      {showPrices && <div className="pm-fs-actions">
        <Button size="small" variant={fsPanel==='list'?'contained':'outlined'} disableElevation startIcon={<FormatListBulletedRounded/>} aria-pressed={fsPanel==='list'} onClick={()=>toggleFsPanel('list')}>목록</Button>
      </div>}
    </div>}
    {/* 필터는 지도 바로 위 칩 줄로 늘 보인다(PC·모바일 같음, 모바일은 가로로 밀어 본다). 드롭다운은 전체 화면 위에 뜬다. */}
    {fullscreen && showPrices && <div key="fs-filters" className="pm-fs-filters"><TuneRounded className="pm-fs-filters-icon" aria-hidden="true"/>
      <PriceMapFilters filters={priceMap.filters} setFilters={priceMap.setFilters} areaDefault={priceMap.areaDefault}/>
      {fsFilterCount>0 && <span className="pm-fs-filter-count">바뀐 조건 {fsFilterCount}개</span>}</div>}
    <div key="fs-body" className="pm-fs-body">
      <div key="canvas" ref={container} className="price-map-canvas" role="region" aria-label={precise?'공급 주소 기준 지도':approx?'주소의 법정동 일대 지도(추정, 부지 미확정)':'미확인 위치 검색 후보 지도'}/>
      {/* 처음 보는 지역은 국토부 실거래 조회에 몇 초 걸려 지도가 비어 보이므로, 지도 위에 받는 중임을 표시한다. */}
      {mapReady>0 && showPrices && (priceMap.query.isLoading || priceMap.loadingOthers) && <Box className="pm-map-loading" role="status" aria-live="polite"><CircularProgress size={14} thickness={5}/>{priceMap.query.isLoading?'주변 실거래를 불러오는 중…':'옮긴 지역 실거래를 불러오는 중…'}</Box>}
      {mapReady>0 && showPrices && !priceMap.query.isLoading && !priceMap.loadingOthers && (priceMap.waitingOthers || priceMap.query.data?.status==='UNAVAILABLE') && <Box className="pm-map-loading is-waiting" role="status" aria-live="polite">실거래 제공처가 잠시 바빠요 · 곧 자동으로 다시 불러와요</Box>}
      {mapReady>0 && <MapToolRail tools={tools} onZoom={zoomBy}/>}
      {viewing && <div className="pm-viewing" role="status"><span><b>{viewing}</b> 일대 보는 중</span><button type="button" onClick={recenter}><MyLocationRounded fontSize="inherit"/>{manualResult?'검색한 위치로':'공고 위치로'}</button></div>}
      {mapReady>0 && <MapToolbar tools={tools} rows={showPrices?priceMap.rows:[]} supply={showPrices?supply:null}>
        {showPrices && <PriceModeToggle filters={priceMap.filters} setFilters={priceMap.setFilters}/>}
        {!fullscreen && <button type="button" className="pm-fs-open" onClick={openFullscreen} aria-label="지도 전체 화면으로 보기"><OpenInFullRounded fontSize="inherit"/>전체 화면</button>}
      </MapToolbar>}
      {fullscreen && fsMessage && <div className="pm-fs-toast" role="status">{fsMessage}{(searchMessage || searchInfo) && <IconButton size="small" aria-label="안내 닫기" onClick={()=>{setSearchMessage('');setSearchInfo('');}}><CloseRounded fontSize="inherit"/></IconButton>}</div>}
      {fullscreen && showPrices && priceMap.query.data?.status==='READY' && <div className="pm-fs-count" role="status">조건에 맞는 단지 <b>{priceMap.rows.length.toLocaleString('ko-KR')}</b>곳{priceMap.outside.length?` · 조건 밖 ${priceMap.outside.length.toLocaleString('ko-KR')}곳`:''}{zoomedOut?' · 확대하면 옮긴 지역도 불러와요':''}</div>}
      {fullscreen && mapReady>0 && <button type="button" className="pm-fs-recenter" onClick={recenter}><MyLocationRounded fontSize="inherit"/>{manualResult?'검색한 위치':approx?'주소 일대':'공고 위치'}</button>}
      {fsPanelBody && <aside className="pm-fs-panel" aria-label={fsPanelTitle || '고른 단지 정보'}>
        {fsPanelHeader && <div className="pm-fs-panel-head"><b>{fsPanelTitle}</b><IconButton size="small" aria-label={`${fsPanelTitle} 닫기`} onClick={()=>toggleFsPanel(fsPanel)}><CloseRounded fontSize="small"/></IconButton></div>}
        <div className="pm-fs-panel-body">{fsPanelBody}</div>
      </aside>}
    </div>
  </Box>;

  return <Box sx={{pt:.5}}>
    {/* 공급위치와 위치 판정 결과를 한 카드로(모바일 첫 화면에서 주소가 여러 번 반복되지 않게). */}
    <Paper variant="outlined" className="loc-summary" sx={{p:1.5,mb:1,borderRadius:2,bgcolor:'#f8fafc'}}>
      <Typography variant="caption" color="text.secondary">모집공고 공급위치</Typography>
      <Typography sx={{fontSize:14,fontWeight:700,mt:.25,overflowWrap:'anywhere'}}>{original}</Typography>
      {location.data?.status==='ADDRESS' && <Typography className="loc-status is-exact" variant="caption" component="p">지번 확인 · {location.data.address}</Typography>}
      {location.data?.status==='UNRESOLVED' && <Typography className="loc-status is-approx" variant="caption" component="p">
        {hasAddress?'지구·블록 단위 주소라 정확한 부지는 확인되지 않았어요.':'청약홈 자료에 공급 위치(주소)가 없어 지도에 표시할 수 없어요. 모집공고 원문에서 확인해 주세요.'}
        {location.data.area?` 지도는 ${location.data.area.name} 일대를 추정으로 보여 주고, 공고 위치 핀은 놓지 않아요.`:''}</Typography>}
    </Paper>
    {announced && !manualResult && <NearbySummary query={nearby} onFocus={focusPlace}/>}
    {/* 분양가 적정성 한 줄(상세 탭과 같은 요약). 누르면 지도 아래 주택형별 비교로 내려간다. */}
    {announced && !manualResult && <Box className="pm-gap-summary"><PriceGapSummary detail={detail} onMore={()=>{priceMap.select(null);setTimeout(()=>salePanelRef.current?.scrollIntoView({behavior:'smooth',block:'start'}),0);}}/></Box>}
    {(location.isLoading || location.isFetching) && <Box sx={{p:3,display:'flex',gap:1}}><CircularProgress size={20}/>공급위치를 확인하고 있어요.</Box>}
    {location.error && <Alert severity="warning" action={<Button onClick={()=>location.refetch()}>다시 확인</Button>}>위치 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.</Alert>}
    {result?.status==='CANDIDATE' && <Alert severity="warning" sx={{mb:1}} action={<Button onClick={()=>setShowCandidate(x=>!x)}>{showCandidate?'후보 숨기기':'검색 후보 보기'}</Button>}>단지명과 일치하는 검색 후보가 있으나 실제 부지는 확인되지 않았습니다. 견본주택·사업지 여부를 확인해 주세요.</Alert>}
    {location.data && location.data.status!=='ADDRESS' && !approxArea && hasAddress && <PriceMapUnavailable reason="주변 실거래가는 공급 주소(지번)가 확인된 공고에서만 보여 드려요. 확인되지 않은 위치 주변의 가격은 표시하지 않습니다."/>}
    {showPrices && <Typography component="h3" sx={{m:0,mt:2,fontSize:16,fontWeight:800}}>주변 아파트 실거래가</Typography>}
    {!visible && <>{(location.data || manualResult) && searchBar}{searchHint && <Typography variant="caption" color="text.secondary" component="p" sx={{mt:-1,mb:1}}>{searchHint}</Typography>}{searchMessage && <Alert severity="warning" sx={{mb:1}}>{searchMessage}</Alert>}{searchInfo && <Alert severity="info" sx={{mb:1}} onClose={()=>setSearchInfo('')}>{searchInfo}</Alert>}</>}
    {visible && <>
      {/* 공고 확정 위치는 위 카드에 한 번만 적는다. 직접 검색한 위치·검색 후보일 때만 따로 알린다. */}
      {(manualResult || result?.status==='CANDIDATE') && <Alert severity="warning" sx={{mb:1}} action={manualResult?<Button color="inherit" size="small" onClick={backToAnnouncement}>공고 위치로</Button>:null}>
        {manualResult?'직접 검색한 위치 · 공고의 확정 위치가 아닙니다':result.label} · {result.address}
      </Alert>}
      {showPrices && !fullscreen && <PriceMapFilters filters={priceMap.filters} setFilters={priceMap.setFilters} areaDefault={priceMap.areaDefault}/>}
      {/* 일시적인 네트워크 오류 뒤에도 탭을 나갔다 오지 않고 다시 불러올 수 있게 한다. */}
      {mapError?<Alert severity="error" action={<Button color="inherit" size="small" onClick={()=>{setMapError('');setAttempt(value=>value+1);}}>다시 시도</Button>}>{mapError}</Alert>
        :<div ref={mapSlot} className="pm-map-slot"/>}
      <Typography variant="caption" color="text.secondary">{approx?'점선 원은 주소의 법정동 일대를 대략 보여 줄 뿐 부지 위치가 아니에요.':'표시 지점은 부지 경계·출입구를 뜻하지 않습니다.'}</Typography>
      {(location.data || manualResult) && !fullscreen && searchBar}
      {searchHint && !fullscreen && <Typography variant="caption" color="text.secondary" component="p" sx={{mt:-1,mb:1}}>{searchHint}</Typography>}
      {searchMessage && <Alert severity="warning" sx={{mb:1}}>{searchMessage}</Alert>}{searchInfo && <Alert severity="info" sx={{mb:1}} onClose={()=>setSearchInfo('')}>{searchInfo}</Alert>}
      {showPrices && <>
        {unsupported?<Alert severity="info" sx={{mt:1}}>이 지역은 아직 주변 실거래 조회를 지원하지 않아요.</Alert>
          :priceError?<Alert severity="warning" sx={{mt:1}} action={<Button color="inherit" size="small" onClick={()=>priceMap.query.refetch()}>다시 시도</Button>}>주변 실거래를 아직 불러오지 못했어요. 잠시 뒤 자동으로 다시 불러오고, 바로 다시 시도할 수도 있어요.</Alert>
          :<PriceMapStatus grouping={tier} approx={Boolean(approx)} nearby={nearby.data?.status==='READY' && nearby.data.places.length>0} query={priceMap.query} rows={priceMap.rows} outside={priceMap.outside} sales={priceMap.sales} unlocatedSales={priceMap.unlocatedSales} onOpenSale={openSale} filters={priceMap.filters} pending={priceMap.pending} districtCount={priceMap.districtCount} loadingOthers={priceMap.loadingOthers} zoomedOut={zoomedOut} grouped={tier!=='single'?tier:''}/>}
        {priceMap.query.data?.status==='READY' && !priceMap.rows.length && <Alert severity="info" sx={{mt:1}} action={<Button color="inherit" size="small" onClick={()=>{clearText();priceMap.setFilters({areaMin:0,areaMax:165,priceMin:0,priceMax:400000,age:'all',query:''});}}>조건 풀기</Button>}>{priceMap.outside.length?`조건에 맞는 거래가 없어요. 거래가 있는 단지 ${priceMap.outside.length.toLocaleString('ko-KR')}곳은 지도에 회색 점으로 보여요. 조건을 풀어 보세요.`:'조건에 맞는 거래가 없어요. 조건을 풀어 보세요.'}</Alert>}
        {priceMap.query.data?.status==='EMPTY' && <Alert severity="info" sx={{mt:1}}>이 기간에 신고된 아파트 매매 거래가 없어요. 기간을 늘려 보세요.</Alert>}
        {fullscreen?null:priceMap.selectedSale?<SalePanel detail={detail} sale={priceMap.selectedSale} complexes={priceMap.complexes} origin={priceMap.origin} unit={priceMap.filters.unit}
            onClose={()=>priceMap.select(null)} onOpen={sale=>openSale(sale.id)}/>
          :priceMap.selected?<ComplexPanel row={priceMap.selected} supply={supply} unit={priceMap.filters.unit} onClose={()=>priceMap.select(null)}/>
          :!manualResult && <SalePanel panelRef={salePanelRef} detail={detail} precise={announced} siteStatus={siteStatus} siteComplex={priceMap.siteComplex} onSelectComplex={chooseFromList} origin={priceMap.origin} unit={priceMap.filters.unit}/>}
        <ComplexList rows={priceMap.rows} filters={priceMap.filters} setFilters={priceMap.setFilters} selectedKey={selectedKey} onSelect={chooseFromList}/>
      </>}
      {precise && tradesOff && <PriceMapUnavailable reason="지금은 실거래 정보를 제공하지 않고 있어요."/>}
    </>}
    {visible && !mapError && mapHost ? <>{createPortal(mapContent,mapHost)}</> : null}
  </Box>;
}
