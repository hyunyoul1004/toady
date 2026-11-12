import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { announcementShareUrl, shareLabel, scoreShareText } from '@/features/planner/shareUtils';
/**
 * @file Main.jsx
 * @description 청약 일정 캘린더 및 공고 메인 화면 컴포넌트
 */
import { useState, useMemo, useEffect, useRef } from 'react';
import { useSearchParams, useParams, useNavigate, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';

import { trackAction } from '@/features/analytics/trackAction';
import ResearchPanel, { ResearchTabs, AnnouncementActions } from '@/features/planner/ResearchPanel';
import TodayOverview, { rememberViewed } from '@/features/planner/TodayOverview';
import AreaSubscribe from '@/features/planner/AreaSubscribe';
import ServiceIntro from '@/features/intro/ServiceIntro';
import '@/features/planner/planner.css';
import { initialBrowseState, monthRange, readSupplyTypes, supplyTypesKey } from '@/features/planner/browseState';
import { safeRead, isoDate, koreaToday } from '@/features/planner/plannerUtils';
import { SubscriptionCalendar } from '@/views/main/calendar';
import { SubscriptComment } from '@/views/main/subscriptComment';
import SubscriptRate from '@/views/main/subscriptRate';
import Kakaomap from '@/components/kakao/Kakaomap';
import { ALL_REGIONS, FLAT_CITY_OPTIONS } from './announcementFilters';
import { normalizeSearchTargets, readSearchTargets } from '@/features/planner/searchState';
import AnnouncementFilters from './components/AnnouncementFilters';
import AnnouncementNotice from './components/AnnouncementNotice';
import { useAnnouncementSearch } from './hooks/useAnnouncementSearch';
import { fn_rceptSeBgColor } from './rceptSeColorUtil';

import { updateAnnouncementSeo, resetDefaultSeo } from '@/utils/seo.util';
import { useAnnouncementsQuery, useAnnouncementDetailQuery, useAptScoresQuery } from '@/features/announcement/hooks/useAnnouncements';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import Typography from '@mui/material/Typography';

import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import LeaderboardIcon from '@mui/icons-material/Leaderboard';
import FeedIcon from '@mui/icons-material/Feed';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import CloseIcon from '@mui/icons-material/Close';
import PlaceOutlined from '@mui/icons-material/PlaceOutlined';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';

import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';

import CommentIcon from '@mui/icons-material/Comment';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CircularProgress from '@mui/material/CircularProgress';

import 'simplebar-react/dist/simplebar.min.css';
import './main.css';

/** 무순위·잔여세대(06), 임의공급(07), 불법행위 재공급(08) */
const UNRANKED_RCEPT_SE=['06','07','08'];

export const Main = ({detailOnly=false,onDetailClose}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  useEffect(()=>()=>resetDefaultSeo(),[]);
  const route = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const routeDetailId = route.type ? `${route.type}/${route.house}/${route.notice}` : searchParams.get('subscriptSeq');
  useDirectDetailHistory(!detailOnly && Boolean(routeDetailId), 'detailOverlay', '/');
  const [initialBrowse] = useState(() => initialBrowseState({getItem:key=>localStorage.getItem(key)}));
  const [calendarMonth,setCalendarMonth] = useState(initialBrowse.month);
  const [viewMode,setViewMode] = useState(initialBrowse.viewMode);
  useEffect(() => {
    if (location.state?.browseView !== 'calendar') return;
    setViewMode('calendar');
    const { browseView, ...state } = location.state;
    navigate(location.pathname + location.search + location.hash, { replace: true, state });
  }, [location.key, location.state, location.pathname, location.search, location.hash, navigate]);
  useEffect(() => { if(detailOnly)return;try { localStorage.setItem('toady.viewMode',JSON.stringify(viewMode)); } catch {} },[viewMode,detailOnly]);

  // 공급지역 체크 목록 (최초에는 전국 17개 시/도가 모두 선택된 상태)
  const [selectedRegions, setSelectedRegions] = useState(() => {
    const saved = safeRead({getItem:key=>localStorage.getItem(key)},'toady.regions',null);
    return Array.isArray(saved) ? ALL_REGIONS.filter(r => saved.includes(r.code)) : ALL_REGIONS;
  });
  useEffect(() => { if(detailOnly)return;try { localStorage.setItem('toady.regions', JSON.stringify(selectedRegions.map(r=>r.code))); } catch {} },[selectedRegions,detailOnly]);

  // 필터 파라미터 상태
  // 공급유형 선택도 지역·검색어처럼 저장해 메뉴를 다녀와도 그대로 복원한다(8자리 Y/N, 잘못된 값은 모두 선택).
  const [paramInfo, setParamInfo] = useState(() => ({
    ...readSupplyTypes({getItem:key=>localStorage.getItem(key)}),
    startStr: initialBrowse.startStr,
    endStr: initialBrowse.endStr,
    citys: readSearchTargets({getItem:key=>localStorage.getItem(key)},FLAT_CITY_OPTIONS)
  }));
  const supplyFromEntry = useRef(false);
  useEffect(() => { if(detailOnly || supplyFromEntry.current)return;try { localStorage.setItem('toady.supplyTypes',JSON.stringify(supplyTypesKey(paramInfo))); } catch {} },[paramInfo,detailOnly]);
  useEffect(() => { if(detailOnly)return;try { localStorage.setItem('toady.searchAreas',JSON.stringify(normalizeSearchTargets(paramInfo.citys))); } catch {} },[paramInfo.citys,detailOnly]);

  // 캘린더 특정 날짜로 강제 이동 요청 (전체 기간 아파트 선택, 생활권·모음 페이지 진입 시 사용).
  // { date, nonce } 객체로 두어 같은 날짜를 다시 요청해도 달력이 다시 이동하게 한다.
  const [jumpToDate, setJumpToDate] = useState(null);
  const requestJumpToDate = date => setJumpToDate(previous => ({ date, nonce: (previous?.nonce || 0) + 1 }));

  useEffect(() => {
    const range = monthRange(calendarMonth);
    setParamInfo(previous => previous.startStr === range.startStr && previous.endStr === range.endStr ? previous : {...previous,...range});
    if(detailOnly)return;
    try { localStorage.setItem('toady.calendarMonth',JSON.stringify(calendarMonth)); localStorage.setItem('toady.calendarMonthDay',JSON.stringify(koreaToday())); } catch {}
  },[calendarMonth,detailOnly]);
  useEffect(() => { const date=isoDate(jumpToDate?.date); if (date) setCalendarMonth(date.slice(0,7)); },[jumpToDate]);

  // 1. TanStack Query로 공고 목록 페칭
  const {
    data: announcementsRes,
    isError: isListError,
    isPlaceholderData: isListPlaceholder,
    error: listError,
    refetch: refetchAnnouncements
  } = useAnnouncementsQuery(paramInfo,!detailOnly);

  const rawSubscriptList = useMemo(() => {
    return announcementsRes?.data?.resultList || [];
  }, [announcementsRes]);

  const { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList } =
    useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions);

  // 생활권 공개 페이지(/areas/{slug})에서 넘어오면 같은 생활권 검색과 첫 접수월을 한 번만 적용하고 주소에서 지운다.
  // 새로고침·뒤로가기에서 사용자가 바꾼 검색 조건을 다시 덮어쓰지 않게 하기 위해서다.
  const areaSlug = detailOnly ? null : searchParams.get('area');
  const livingAreaQuery = useQuery({ queryKey:['livingAreas'], queryFn:() => apiClient.get('/api/v1/living-areas'), staleTime:3600000, enabled:Boolean(areaSlug) });
  useEffect(() => {
    if (!areaSlug || (!Array.isArray(livingAreaQuery.data) && !livingAreaQuery.isError)) return;
    const area = Array.isArray(livingAreaQuery.data) ? livingAreaQuery.data.find(item => item.slug === areaSlug) : null;
    const month = searchParams.get('month');
    if (area) {
      // 생활권 페이지에서 온 건수와 맞도록 저장된 유형·지역 조건 없이 그 생활권만 보여 준다.
      resetForEntry();
      setParamInfo(previous => ({ ...previous, citys: normalizeSearchTargets([{ type:'living_area', label:area.label, key:`area_${area.label}`, terms:area.terms }]) }));
      // 달력은 첫 표시 월만 받아 두므로, 검색 결과 이동과 같은 jumpToDate 경로로 월을 옮긴다.
      if (/^\d{4}-(0[1-9]|1[0-2])$/.test(month || '')) requestJumpToDate(`${month}-01`);
    }
    const next = new URLSearchParams(searchParams);
    next.delete('area'); next.delete('month');
    setSearchParams(next, { replace:true, state:location.state });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [areaSlug, livingAreaQuery.data, livingAreaQuery.isError]);

  // 공개 모음 페이지(/zupzup, /weekly)에서 넘어온 유형·월 조건도 한 번만 적용하고 주소에서 지운다.
  const entryFocus = detailOnly || areaSlug ? null : searchParams.get('focus');
  const entryMonth = detailOnly || areaSlug ? null : searchParams.get('month');
  // 검색엔진의 사이트 검색 상자(index.html SearchAction: /?search=검색어)로 들어오면 그 검색어로 한 번 검색한다.
  const entrySearch = detailOnly || areaSlug ? null : (searchParams.get('search') || '').trim().slice(0, 50);
  // 진입 조건(검색어·유형·월)은 한 효과에서 함께 적용하고 주소에서 한 번에 지운다(따로 지우면 나중 호출이 앞의 삭제를 되돌린다).
  useEffect(() => {
    if (!entrySearch && !entryFocus && !entryMonth) return;
    if (entrySearch) {
      // 지역별 청약·사이트 검색에서 온 검색은 저장된 유형·지역 조건 없이 그 검색어만으로 보여 준다(건수가 맞도록).
      resetForEntry();
      // '경기도 파주시'처럼 검색 목록의 지역 이름이면 지역 검색으로, 아니면 검색어로 적용한다.
      setParamInfo(previous => ({ ...previous, citys: normalizeSearchTargets([entrySearch], FLAT_CITY_OPTIONS) }));
    }
    // 이번 주 청약 등에서 '캘린더에서 보기'로 오면 예전에 저장한 검색 조건 대신 전체 공고로 보여 준다(건수가 맞도록).
    if (entryFocus === 'all') resetForEntry();
    if (entryFocus === 'unranked') {
      setSelectedRegions(ALL_REGIONS);
      setSearchInputValue('');
      // 진입으로 적용한 유형은 저장하지 않는다(다음에 그냥 열었을 때 무순위만 보이지 않게). 사용자가 직접 바꾸면 그때부터 저장한다.
      supplyFromEntry.current = true;
      setParamInfo(previous => ({ ...previous, citys: [], ...Object.fromEntries(Array.from({length:8},(_,index)=>{const key=String(index+1).padStart(2,'0');return [`rceptSe${key}`,UNRANKED_RCEPT_SE.includes(key)?'Y':'N'];})) }));
    }
    if (/^\d{4}-(0[1-9]|1[0-2])$/.test(entryMonth || '')) requestJumpToDate(`${entryMonth}-01`);
    const next = new URLSearchParams(searchParams);
    next.delete('search'); next.delete('focus'); next.delete('month');
    setSearchParams(next, { replace:true, state:location.state });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entrySearch, entryFocus, entryMonth]);

  // 진입 링크(지역별 청약·생활권·'캘린더에서 보기')로 조건을 비울 때는 이번 화면에만 적용하고,
  // 사용자가 저장해 둔 공급유형 선택은 덮어쓰지 않는다(사용자가 유형을 직접 바꾸거나 '초기화'를 누를 때만 저장).
  const resetForEntry = () => { resetBrowseFilters(); supplyFromEntry.current = true; };
  const resetBrowseFilters = () => {
    supplyFromEntry.current = false;
    setSelectedRegions(ALL_REGIONS);
    setSearchInputValue('');
    setParamInfo(previous=>({...previous,citys:[],...Object.fromEntries(Array.from({length:8},(_,index)=>[`rceptSe${String(index+1).padStart(2,'0')}`,'Y']))}));
  };

  // 상세 모달 상태 및 탭 (상세 0, 위치 1, 댓글 2)
  const [modalOpen, setModalOpen] = useState(false);
  const lastViewed = useRef(null);
  const [researchTab,setResearchTab]=useState('info');
  const routeSeqRef=useRef(null);
  const detailScrollRef=useRef(null);
  // 상세 본문을 내리면 제목·탭만 남기고 상단(유형·주소·저장 버튼)을 접어 본문을 넓게 보여 준다.
  const [headerCompact,setHeaderCompact]=useState(false);
  const [detailTabValue, setDetailTabValue] = useState(0);
  const [selectedSubscriptSeq, setSelectedSubscriptSeq] = useState(null);
  useEffect(()=>{if(detailScrollRef.current) detailScrollRef.current.scrollTop=0; setHeaderCompact(false);},[researchTab,detailTabValue,selectedSubscriptSeq]);
  const headerChipsRef=useRef(null), headerAddressRef=useRef(null), headerActionsRef=useRef(null);
  const collapsibleHeightRef=useRef(0);
  const handleDetailScroll=event=>{
    const target=event.currentTarget, top=target.scrollTop;
    setHeaderCompact(previous=>{
      if(previous) return top>24;
      // 펼친 상태에서 접힐 영역 높이를 잰다. 접으면 본문이 그만큼 커지므로, 남는 스크롤 여유가 그보다 충분히 클 때만 접어 짧은 본문에서 접힘↔펼침이 반복되지 않게 한다.
      collapsibleHeightRef.current=[headerChipsRef,headerAddressRef,headerActionsRef].reduce((sum,ref)=>sum+(ref.current?.offsetHeight||0),0);
      const room=target.scrollHeight-target.clientHeight;
      return top>120 && room>collapsibleHeightRef.current+80;
    });
  };
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });
  const [addressCopied, setAddressCopied] = useState(false);

  // 2. TanStack Query로 공고 상세 정보 페칭 (모달 열릴 때만 수행)
  const {
    data: detailRes,
    isLoading: isDetailLoading,
    isError: isDetailError,
    error: detailError,
    refetch: refetchDetail
  } = useAnnouncementDetailQuery(selectedSubscriptSeq, modalOpen);

  const mainDetail = useMemo(() => {
    return detailRes?.data || {};
  }, [detailRes]);

  // 상세 모달용 공고 정보 (목록 데이터의 hssplyAdres를 안전하게 fallback)
  const currentSubscriptDetail = useMemo(() => {
    const detail = mainDetail.subscriptDetail || {};
    const matchedInList = subscriptList.find((item) => item.subscriptSeq === selectedSubscriptSeq);
    return {
      ...detail,
      hssplyAdres: detail.hssplyAdres || detail.supplyLocation || matchedInList?.hssplyAdres || matchedInList?.supplyLocation || '',
      supplyLocation: detail.supplyLocation || matchedInList?.supplyLocation || detail.hssplyAdres || '',
      houseNm: detail.houseNm || matchedInList?.houseNm || ''
    };
  }, [mainDetail.subscriptDetail, subscriptList, selectedSubscriptSeq]);

  const { data: shareScores } = useAptScoresQuery(selectedSubscriptSeq, modalOpen && detailTabValue===1 && selectedSubscriptSeq?.startsWith('apt/'));
  const scoreText = detailTabValue===1 ? scoreShareText(shareScores?.data || []) : '';

  // 공고 상세 열람 시 검색엔진 메타태그(SEO) 및 OpenGraph/Schema.org 동적 갱신
  useEffect(() => {
    if (modalOpen && currentSubscriptDetail?.houseNm) {
      updateAnnouncementSeo(currentSubscriptDetail,['detail','rate','map','comment'][detailTabValue],scoreText);
    } else if (!modalOpen) {
      resetDefaultSeo();
    }
  }, [modalOpen, currentSubscriptDetail,detailTabValue,scoreText]);

  useEffect(() => {
    if (!modalOpen) { lastViewed.current = null; return; }
    if (!isDetailLoading && !isDetailError && mainDetail.subscriptDetail?.houseNm
        && mainDetail.subscriptDetail.subscriptSeq === selectedSubscriptSeq && lastViewed.current !== selectedSubscriptSeq) {
      lastViewed.current = selectedSubscriptSeq;
      rememberViewed(mainDetail.subscriptDetail);
      trackAction('detail_view',selectedSubscriptSeq);
    }
  }, [modalOpen, isDetailLoading, isDetailError, mainDetail.subscriptDetail, selectedSubscriptSeq]);

  // URL 파라미터 감지 (/showSubscriptDetail?subscriptSeq=...)
  useEffect(() => {
    const subscriptSeq = route.type ? `${route.type}/${route.house}/${route.notice}` : searchParams.get('subscriptSeq');
    const tabType = searchParams.get('tabType');

    if (subscriptSeq) {
      setSelectedSubscriptSeq(subscriptSeq);
      // 탭 전환(setSearchParams)으로 이 효과가 다시 돌 때는 조사 탭을 유지하고, 공고가 바뀔 때만 처음 탭으로 돌린다.
      const changedSeq = routeSeqRef.current !== subscriptSeq;
      if (changedSeq) setResearchTab('info');
      routeSeqRef.current = subscriptSeq;
      setModalOpen(true);

      const tabMap = { detail: 0, rate: 1, map: 2, comment: 3 };
      if (tabType && tabMap[tabType] !== undefined) {
        setDetailTabValue(tabMap[tabType]);
      } else if (changedSeq) {
        // 알림·공유 링크처럼 탭 지정 없이 다른 공고를 열면, 직전에 보던 탭이 아니라 '상세'로 연다.
        setDetailTabValue(0);
      }
    } else {
      routeSeqRef.current = null;
      setModalOpen(false);
    }
  }, [searchParams, route.type, route.house, route.notice]);

  const handleOpenDetail = (seq) => {
    setSelectedSubscriptSeq(seq);
    setResearchTab('info');
    setDetailTabValue(0);
    setModalOpen(true);
    // 앱 안 상세는 SPA 주소를 쓴다. /announcements/…는 nginx가 검색·공유용 서버 HTML로 보내므로 새로고침하면 앱이 아닌 정적 페이지가 열린다.
    navigate(`/?subscriptSeq=${encodeURIComponent(seq)}`, {state:{detailOverlay:true}});
  };

  const handleCloseDetail = () => {
    if (onDetailClose) { onDetailClose(); return; }
    if (location.state?.detailOverlay) navigate(-1);
    else navigate('/', { replace: true });
  };

  const handleTabChange = (event, newValue) => {
    setDetailTabValue(newValue);
    if(detailOnly) return;
    const tabNames = ['detail', 'rate', 'map', 'comment'];
    if (selectedSubscriptSeq) {
      setSearchParams(
        { subscriptSeq: selectedSubscriptSeq, tabType: tabNames[newValue] },
        { replace: true, state: location.state }
      );
    }
  };

  // 공고 공유하기 (Web Share API 및 클립보드 복사 폴백)
  const handleShare = async () => {
    const seq = selectedSubscriptSeq || currentSubscriptDetail?.subscriptSeq || '';
    const tab=['detail','rate','map','comment'][detailTabValue];
    const shareUrl = announcementShareUrl(seq,tab,window.location.origin);
    const houseNm = currentSubscriptDetail?.houseNm || '청약 공고';
    const location = currentSubscriptDetail?.supplyLocation || currentSubscriptDetail?.hssplyAdres || '';
    const date = currentSubscriptDetail?.subscriptDate || '';

    const shareData = {
      title: `${houseNm} | ${scoreText?'청약 경쟁률·당첨가점':shareLabel(tab)} · TOADY`,
      text: `${houseNm}\n${scoreText?scoreText+'\n':''}공급위치: ${location}\n접수일정: ${date}`,
      url: shareUrl
    };

    if (navigator.share) {
      try {
        await navigator.share(shareData);
        trackAction('share', seq);
        return;
      } catch (err) {
        if (err.name === 'AbortError') return;
      }
    }

    try {
      await navigator.clipboard.writeText(shareUrl);
      trackAction('share', seq);
      setSnackbar({ open: true, message: '공고 링크가 클립보드에 복사되었습니다.', severity: 'success' });
    } catch {
      setSnackbar({ open: true, message: '링크 복사에 실패했습니다.', severity: 'error' });
    }
  };

  // 상단 헤더 주소 복사 핸들러
  const handleCopyHeaderAddress = async (addr) => {
    if (!addr) return;
    try {
      await navigator.clipboard.writeText(addr);
      setAddressCopied(true);
      setSnackbar({ open: true, message: '공급위치 주소가 복사되었습니다.', severity: 'success' });
      setTimeout(() => setAddressCopied(false), 2000);
    } catch {
      setSnackbar({ open: true, message: '주소 복사에 실패했습니다.', severity: 'error' });
    }
  };

  const theme = createTheme({
    palette: {
      rceptSeBgColor01: { main: fn_rceptSeBgColor('01'), contrastText: '#fff' },
      rceptSeBgColor02: { main: fn_rceptSeBgColor('02'), contrastText: '#fff' },
      rceptSeBgColor03: { main: fn_rceptSeBgColor('03'), contrastText: '#fff' },
      rceptSeBgColor04: { main: fn_rceptSeBgColor('04'), contrastText: '#fff' },
      rceptSeBgColor05: { main: fn_rceptSeBgColor('05'), contrastText: '#fff' },
      rceptSeBgColor06: { main: fn_rceptSeBgColor('06'), contrastText: '#fff' },
      rceptSeBgColor07: { main: fn_rceptSeBgColor('07'), contrastText: '#fff' },
      rceptSeBgColor08: { main: fn_rceptSeBgColor('08'), contrastText: '#fff' },
      rceptSeBgColor09: { main: fn_rceptSeBgColor('09'), contrastText: '#fff' },
      rceptSeBgColor10: { main: fn_rceptSeBgColor('10'), contrastText: '#fff' },
    },
  });

  return (
    <>
      {!detailOnly && <div>
        {/* 첫 화면: ① 검색 한 줄 → ② 오늘의 요약 → ③ 달력·목록 (소개는 처음 온 사람에게만 한 줄) */}
        <ServiceIntro />
        <Box sx={{ pt: 0.5 }}>
          <ThemeProvider theme={theme}>
            {/* 상단 통합 필터 카드 (공급유형 + 공급지역을 한 화면에 조화롭게 배치) */}
            <AnnouncementFilters
              paramInfo={paramInfo}
              setParamInfo={setParamInfo}
              // 사용자가 공급유형을 직접 바꿨을 때만 진입으로 적용한 유형을 저장 대상으로 돌린다(검색어 변경은 해당 없음).
              onSupplyTouched={() => { supplyFromEntry.current = false; }}
              selectedRegions={selectedRegions}
              setSelectedRegions={setSelectedRegions}
              allSearchOptions={allSearchOptions}
              searchInputValue={searchInputValue}
              setSearchInputValue={setSearchInputValue}
              onJumpToDate={requestJumpToDate}
              setSnackbar={setSnackbar}
              handleOpenDetail={handleOpenDetail}
            />
          </ThemeProvider>
        </Box>

        <AreaSubscribe targets={paramInfo.citys} />
        <TodayOverview onResetFilters={resetBrowseFilters} month={calendarMonth} onMonthChange={setCalendarMonth} isLoading={(announcementsRes == null || isListPlaceholder) && !isListError} items={subscriptList} onOpen={handleOpenDetail} viewMode={viewMode} setViewMode={setViewMode} />
        {/* 캘린더 또는 목록 표시 */}
        <Box sx={{ mt: 0.5 }}>
          {isListError && <Alert severity="error" sx={{ mb: 2 }}
            action={<Button color="inherit" onClick={() => refetchAnnouncements()}>다시 시도</Button>}>
            공고 목록을 불러오지 못했습니다. {listError?.message}
          </Alert>}
          {viewMode==='calendar' && announcementsRes!=null && !isListPlaceholder && !isListError && !subscriptList.some(item=>String(item.inDate || '').startsWith(calendarMonth)) && <Alert severity="info" sx={{mb:1.5}} action={<Button size="small" onClick={resetBrowseFilters}>검색 조건 초기화</Button>}>이 달에는 선택한 조건의 청약 일정이 없습니다. 다른 달을 선택하거나 검색 조건을 초기화해 주세요.</Alert>}
          {viewMode === 'calendar' && <SubscriptionCalendar
            initialMonth={calendarMonth}
            onMonthChange={setCalendarMonth}
            jumpToDate={jumpToDate}
            paramInfo={paramInfo}
            fn_subscriptList={refetchAnnouncements}
            subscriptList={subscriptList}
            handleOpen={(seq) => handleOpenDetail(seq)}
          />}
        </Box>
      </div>}

      {/* 우측 슬라이드 상세 패널 (Side Sheet Drawer) */}
      <Drawer
        anchor="right"
        open={modalOpen}
        onClose={handleCloseDetail}
        PaperProps={{
          // 화면 읽기 프로그램이 상세 패널을 단지명으로 부르도록 대화상자 이름을 연결한다.
          role: 'dialog',
          'aria-modal': true,
          'aria-labelledby': 'announcement-detail-title',
          sx: {
            width: { xs: '100%', sm: 580, md: 680, lg: 760 },
            maxWidth: '100vw',
            bgcolor: '#ffffff',
            boxShadow: '-10px 0 35px rgba(0, 0, 0, 0.12)',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden'
          }
        }}
      >
        {/* 상단 공고 요약 헤더 */}
        <Box
          sx={{
            p: { xs: 2, sm: 2.5 },
            pb: 1.5,
            borderBottom: '1px solid #f1f5f9',
            display: 'flex',
            alignItems: 'flex-start',
            justifyContent: 'space-between',
            gap: 1.5,
            bgcolor: '#ffffff',
            zIndex: 10
          }}
        >
          <Box sx={{ flex: 1, minWidth: 0 }}>
            {/* 뱃지 영역: 주택유형 */}
            {/* 접힐 때도 언마운트하지 않고 숨겨 상태(복사 완료·저장 직후 안내 등)를 유지한다. */}
            <Collapse in={!headerCompact} ref={headerChipsRef} timeout={150}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, mb: 0.8, flexWrap: 'wrap' }}>
              <Chip
                label={currentSubscriptDetail?.housingDivision || currentSubscriptDetail?.houseSecdNm || '분양공고'}
                size="small"
                sx={{
                  bgcolor: '#eff6ff',
                  color: '#2563eb',
                  fontWeight: 800,
                  fontSize: '0.72rem',
                  height: 22,
                  borderRadius: 1
                }}
              />
              {currentSubscriptDetail?.subscriptSupply?.[0]?.housingType && (
                <Chip
                  label={currentSubscriptDetail.subscriptSupply[0].housingType}
                  size="small"
                  sx={{
                    bgcolor: '#f8fafc',
                    color: '#64748b',
                    fontWeight: 700,
                    fontSize: '0.72rem',
                    height: 22,
                    borderRadius: 1
                  }}
                />
              )}
            </Box>
            </Collapse>

            {/* 단지명 (SEO 최적화: h1 시맨틱 태그) */}
            <Typography
              id="announcement-detail-title"
              component="h1"
              variant="h6"
              sx={{
                fontWeight: 800,
                fontSize: headerCompact ? { xs: '1rem', sm: '1.1rem' } : { xs: '1.15rem', sm: '1.3rem' },
                color: '#0f172a',
                lineHeight: 1.35,
                mb: 0.5,
                wordBreak: 'keep-all',
                // 긴 공고명은 모바일에서 2줄, 스크롤해 접힌 상태에서는 1줄로 줄인다(전체 이름은 title로 확인).
                display: '-webkit-box', WebkitBoxOrient: 'vertical', overflow: 'hidden',
                WebkitLineClamp: headerCompact ? 1 : { xs: 2, sm: 3 }
              }}
            >
              {currentSubscriptDetail?.houseNm || mainDetail.subscriptDetail?.houseNm || '청약 공고 상세'}
            </Typography>

            {/* 공급위치(주소) 요약 (SEO address 태그) + 주소 복사 버튼 */}
            {(currentSubscriptDetail?.hssplyAdres || currentSubscriptDetail?.supplyLocation) && (
              <Collapse in={!headerCompact} ref={headerAddressRef} timeout={150}>
              <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.8, mt: 0.3 }}>
                <Typography
                  component="address"
                  variant="caption"
                  sx={{
                    fontStyle: 'normal',
                    color: '#64748b',
                    fontSize: '0.8rem',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 0.4
                  }}
                >
                  <PlaceOutlined sx={{ fontSize: '1rem', color: '#94a3b8', flexShrink: 0 }} />
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {currentSubscriptDetail?.hssplyAdres || currentSubscriptDetail?.supplyLocation}
                  </span>
                </Typography>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => handleCopyHeaderAddress(currentSubscriptDetail?.hssplyAdres || currentSubscriptDetail?.supplyLocation)}
                  startIcon={addressCopied ? <CheckIcon fontSize="small" sx={{ color: '#16a34a' }} /> : <ContentCopyIcon fontSize="small" />}
                  sx={{
                    height: 22,
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    color: addressCopied ? '#16a34a' : '#475569',
                    borderColor: addressCopied ? '#86efac' : '#cbd5e1',
                    bgcolor: addressCopied ? '#f0fdf4' : '#ffffff',
                    borderRadius: 1,
                    px: 0.8,
                    py: 0,
                    lineHeight: 1,
                    minWidth: 0,
                    '&:hover': { bgcolor: addressCopied ? '#f0fdf4' : '#f1f5f9' }
                  }}
                >
                  {addressCopied ? '복사완료' : '주소 복사'}
                </Button>
              </Box>
              </Collapse>
            )}
          </Box>

          {/* 우측 액션 버튼들: 공유하기 버튼 + 닫기 원형 버튼 */}
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, flexShrink: 0 }}>
            <Tooltip title="공고 공유하기">
              <IconButton
                onClick={handleShare}
                aria-label="공고 공유하기"
                size="small"
                sx={{
                  width: 36,
                  height: 36,
                  color: '#2563eb',
                  bgcolor: '#eff6ff',
                  border: '1px solid #bfdbfe',
                  transition: 'all 0.15s ease',
                  '&:hover': {
                    bgcolor: '#dbeafe',
                    borderColor: '#2563eb'
                  }
                }}
              >
                {/* 모바일 브라우저에서도 크기와 색상이 명확하도록 직접 지정한다. */}
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  width="20"
                  height="20"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                  focusable="false"
                  style={{ display: 'block', flexShrink: 0 }}
                >
                  <path d="M7 11L17 6M7 13L17 18" fill="none" stroke="#2563eb" strokeWidth="2" />
                  <circle cx="5" cy="12" r="3" fill="#2563eb" />
                  <circle cx="19" cy="5" r="3" fill="#2563eb" />
                  <circle cx="19" cy="19" r="3" fill="#2563eb" />
                </svg>
              </IconButton>
            </Tooltip>

            <IconButton
              onClick={handleCloseDetail}
              aria-label="닫기"
              size="small"
              sx={{
                width: 36,
                height: 36,
                color: '#64748b',
                bgcolor: '#f8fafc',
                border: '1px solid #e2e8f0',
                transition: 'all 0.15s ease',
                '&:hover': {
                  bgcolor: '#f1f5f9',
                  color: '#0f172a',
                  transform: 'rotate(90deg)'
                }
              }}
            >
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </Box>

        {!isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <Collapse in={!headerCompact} ref={headerActionsRef} timeout={150}><AnnouncementActions key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail}/></Collapse>}

        {/* 상세 정보 탭 내비게이션 */}
        <Box sx={{ px: { xs: 2, sm: 2.5 }, pt: 1.5, pb: 1, bgcolor: '#ffffff' }}>
          <Box
            sx={{
              bgcolor: '#f1f5f9',
              borderRadius: 2,
              p: 0.5
            }}
          >
            <Tabs
              value={detailTabValue}
              onChange={handleTabChange}
              variant="fullWidth"
              TabIndicatorProps={{ style: { display: 'none' } }}
              sx={{
                minHeight: 38,
                // 좁은 화면에서는 아이콘을 숨겨 탭 4개 글자가 잘리지 않게 한다.
                '& .MuiTab-iconWrapper': { display: { xs: 'none', sm: 'inline-flex' } },
                '& .MuiTab-root': {
                  minWidth: 0,
                  minHeight: 36,
                  py: 0.5,
                  px: 1,
                  fontSize: '0.85rem',
                  fontWeight: 700,
                  color: '#64748b',
                  borderRadius: 1.5,
                  transition: 'all 0.15s ease',
                  '&.Mui-selected': {
                    bgcolor: '#ffffff',
                    color: '#2563eb',
                    fontWeight: 800,
                    boxShadow: '0 1px 3px rgba(0,0,0,0.08)'
                  }
                }
              }}
            >
              <Tab icon={<FeedIcon sx={{ fontSize: '1.05rem', mr: 0.5 }} />} iconPosition="start" label="상세" />
              <Tab icon={<LeaderboardIcon sx={{ fontSize: '1.05rem', mr: 0.5 }} />} iconPosition="start" label="경쟁률" />
              <Tab icon={<MyLocationIcon sx={{ fontSize: '1.05rem', mr: 0.5 }} />} iconPosition="start" label="위치" />
              <Tab
                icon={<CommentIcon sx={{ fontSize: '1.05rem', mr: 0.5 }} />}
                iconPosition="start"
                label={`이야기${mainDetail.subscriptDetail?.subscriptCommentCnt > 0 ? ` (${mainDetail.subscriptDetail.subscriptCommentCnt})` : ''}`}
              />
            </Tabs>
          </Box>
        </Box>

        {detailTabValue===0 && !isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <Box className="announcement-section-nav" sx={{px:{xs:2,sm:2.5},bgcolor:'#fff',flexShrink:0}}><ResearchTabs value={researchTab} onChange={setResearchTab}/></Box>}

        {/* 3. 본문 스크롤 영역 */}
        <Box ref={detailScrollRef} data-testid="announcement-scroll"
          onScroll={handleDetailScroll}
          sx={{
            flex: 1,
            overflowY: 'auto',
            p: { xs: 2, sm: 2.5 },
            pt: 1,
            // 홈 표시줄·노치와 겹치지 않게 아래·좌우 안전 영역만큼 여백을 더한다.
            pb: 'calc(20px + env(safe-area-inset-bottom))',
            pl: { xs: 'max(16px, env(safe-area-inset-left))', sm: 2.5 },
            pr: { xs: 'max(16px, env(safe-area-inset-right))', sm: 2.5 },
            bgcolor: '#ffffff'
          }}
        >
          {isDetailLoading ? (
            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', p: 8, gap: 1.5 }}>
              <CircularProgress size={36} />
              <Typography variant="caption" sx={{ color: '#64748b', fontWeight: 600 }}>
                공고 상세 정보를 불러오는 중입니다...
              </Typography>
            </Box>
          ) : isDetailError ? (
            <Alert severity="error" action={<Button color="inherit" onClick={() => refetchDetail()}>다시 시도</Button>}>
              공고 상세를 불러오지 못했습니다. {detailError?.message}
            </Alert>
          ) : (
            <>
              {detailTabValue === 0 && <ResearchPanel key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail} tab={researchTab} onTabChange={setResearchTab} showTabs={false} after={<AnnouncementNotice mainDetail={mainDetail} part="original" />}><AnnouncementNotice mainDetail={mainDetail} part="schedule" /></ResearchPanel>}
              {detailTabValue === 1 && currentSubscriptDetail && (
                <SubscriptRate subscriptDetail={currentSubscriptDetail} />
              )}
              {detailTabValue === 2 && currentSubscriptDetail && (
                <Kakaomap subscriptDetail={currentSubscriptDetail} />
              )}
              {detailTabValue === 3 && currentSubscriptDetail && (
                <SubscriptComment
                  subscriptDetail={currentSubscriptDetail}
                  subscriptSeq={selectedSubscriptSeq}
                />
              )}
            </>
          )}
        </Box>
      </Drawer>

      {/* 알림 토스트 피드백 (링크 및 주소 복사 완료 알림) */}
      <Snackbar
        open={snackbar.open}
        autoHideDuration={2500}
        onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        <Alert
          onClose={() => setSnackbar((prev) => ({ ...prev, open: false }))}
          severity={snackbar.severity}
          variant="filled"
          sx={{ width: '100%', borderRadius: 2, fontWeight: 700, fontSize: '0.85rem' }}
        >
          {snackbar.message}
        </Alert>
      </Snackbar>
    </>
  );
};

export default Main;
