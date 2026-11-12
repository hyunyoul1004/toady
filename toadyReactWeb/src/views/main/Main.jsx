import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { announcementShareUrl, shareLabel } from '@/features/planner/shareUtils';
/**
 * @file Main.jsx
 * @description 청약 일정 캘린더 및 공고 메인 화면 컴포넌트
 */
import { useState, useMemo, useEffect, useRef } from 'react';
import { useSearchParams, useParams, useNavigate, useLocation } from 'react-router-dom';

import { trackAction } from '@/features/analytics/trackAction';
import ResearchPanel, { ResearchTabs, AnnouncementActions } from '@/features/planner/ResearchPanel';
import TodayOverview, { rememberViewed } from '@/features/planner/TodayOverview';
import { initialBrowseState, monthRange } from '@/features/planner/browseState';
import { safeRead, isoDate } from '@/features/planner/plannerUtils';
import { SubscriptionCalendar } from '@/views/main/calendar';
import { SubscriptComment } from '@/views/main/subscriptComment';
import SubscriptRate from '@/views/main/subscriptRate';
import Kakaomap from '@/components/kakao/Kakaomap';
import { ALL_REGIONS } from './announcementFilters';
import AnnouncementFilters from './components/AnnouncementFilters';
import AnnouncementNotice from './components/AnnouncementNotice';
import { useAnnouncementSearch } from './hooks/useAnnouncementSearch';
import { fn_rceptSeBgColor } from './rceptSeColorUtil';

import { updateAnnouncementSeo, resetDefaultSeo } from '@/utils/seo.util';
import { useAnnouncementsQuery, useAnnouncementDetailQuery } from '@/features/announcement/hooks/useAnnouncements';

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
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';

import Chip from '@mui/material/Chip';

import CommentIcon from '@mui/icons-material/Comment';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Tooltip from '@mui/material/Tooltip';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CircularProgress from '@mui/material/CircularProgress';

import 'simplebar-react/dist/simplebar.min.css';
import './main.css';

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
  useEffect(() => { try { localStorage.setItem('toady.viewMode',JSON.stringify(viewMode)); } catch {} },[viewMode]);

  // 공급지역 체크 목록 (최초에는 전국 17개 시/도가 모두 선택된 상태)
  const [selectedRegions, setSelectedRegions] = useState(() => {
    const saved = safeRead({getItem:key=>localStorage.getItem(key)},'toady.regions',null);
    return Array.isArray(saved) ? ALL_REGIONS.filter(r => saved.includes(r.code)) : ALL_REGIONS;
  });
  useEffect(() => { try { localStorage.setItem('toady.regions', JSON.stringify(selectedRegions.map(r=>r.code))); } catch {} },[selectedRegions]);

  // 필터 파라미터 상태
  const [paramInfo, setParamInfo] = useState(() => ({
    rceptSe01: 'Y',
    rceptSe02: 'Y',
    rceptSe03: 'Y',
    rceptSe04: 'Y',
    rceptSe05: 'Y',
    rceptSe06: 'Y',
    rceptSe07: 'Y',
    rceptSe08: 'Y',
    startStr: initialBrowse.startStr,
    endStr: initialBrowse.endStr,
    citys: (() => { const value=safeRead({getItem:key=>localStorage.getItem(key)},'toady.searchAreas',[]);return Array.isArray(value)?value.filter(x=>typeof x==='string' && x.length<=100).slice(0,10):[]; })()
  }));
  useEffect(() => { try { localStorage.setItem('toady.searchAreas',JSON.stringify(paramInfo.citys.map(x=>typeof x==='string'?x:x.label).filter(Boolean).slice(0,10))); } catch {} },[paramInfo.citys]);

  // 캘린더 특정 날짜로 강제 이동 상태 (전체 기간 아파트 선택 시 사용)
  const [jumpToDate, setJumpToDate] = useState(null);

  useEffect(() => {
    const range = monthRange(calendarMonth);
    setParamInfo(previous => previous.startStr === range.startStr && previous.endStr === range.endStr ? previous : {...previous,...range});
    try { localStorage.setItem('toady.calendarMonth',JSON.stringify(calendarMonth)); } catch {}
  },[calendarMonth]);
  useEffect(() => { const date=isoDate(jumpToDate); if (date) setCalendarMonth(date.slice(0,7)); },[jumpToDate]);

  // 1. TanStack Query로 공고 목록 페칭
  const {
    data: announcementsRes,
    isError: isListError,
    error: listError,
    refetch: refetchAnnouncements
  } = useAnnouncementsQuery(paramInfo,!detailOnly);

  const rawSubscriptList = useMemo(() => {
    return announcementsRes?.data?.resultList || [];
  }, [announcementsRes]);

  const { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList } =
    useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions);

  // 상세 모달 상태 및 탭 (상세 0, 위치 1, 댓글 2)
  const [modalOpen, setModalOpen] = useState(false);
  const lastViewed = useRef(null);
  const [researchTab,setResearchTab]=useState('info');
  const detailScrollRef=useRef(null);
  const [detailTabValue, setDetailTabValue] = useState(0);
  useEffect(()=>{if(detailScrollRef.current) detailScrollRef.current.scrollTop=0;},[researchTab,detailTabValue]);
  const [selectedSubscriptSeq, setSelectedSubscriptSeq] = useState(null);
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

  // 공고 상세 열람 시 검색엔진 메타태그(SEO) 및 OpenGraph/Schema.org 동적 갱신
  useEffect(() => {
    if (modalOpen && currentSubscriptDetail?.houseNm) {
      updateAnnouncementSeo(currentSubscriptDetail,['detail','rate','map','comment'][detailTabValue]);
    } else if (!modalOpen) {
      resetDefaultSeo();
    }
  }, [modalOpen, currentSubscriptDetail,detailTabValue]);

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
      setResearchTab('info');
      setModalOpen(true);

      const tabMap = { detail: 0, rate: 1, map: 2, comment: 3 };
      if (tabType && tabMap[tabType] !== undefined) {
        setDetailTabValue(tabMap[tabType]);
      }
    } else {
      setModalOpen(false);
    }
  }, [searchParams, route.type, route.house, route.notice]);

  const handleOpenDetail = (seq) => {
    setSelectedSubscriptSeq(seq);
    setResearchTab('info');
    setDetailTabValue(0);
    setModalOpen(true);
    navigate(`/announcements/${seq}`, {state:{detailOverlay:true}});
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
      title: `${houseNm} | ${shareLabel(tab)} · TOADY`,
      text: `${houseNm}\n공급위치: ${location}\n접수일정: ${date}`,
      url: shareUrl
    };

    if (navigator.share) {
      try {
        await navigator.share(shareData);
        return;
      } catch (err) {
        if (err.name === 'AbortError') return;
      }
    }

    try {
      await navigator.clipboard.writeText(shareUrl);
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
        <Box component="header" sx={{ mb: 2 }}>
          <Typography component="h1" sx={{ fontSize: { xs: 22, sm: 26 }, fontWeight: 800, color: 'text.primary', letterSpacing: '-.03em' }}>청약 공고</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: .5 }}>관심 지역의 청약 일정과 공고를 확인하세요.</Typography>
        </Box>
        <Box sx={{ pt: 0.5 }}>
          <ThemeProvider theme={theme}>
            {/* 상단 통합 필터 카드 (공급유형 + 공급지역을 한 화면에 조화롭게 배치) */}
            <AnnouncementFilters
              paramInfo={paramInfo}
              setParamInfo={setParamInfo}
              selectedRegions={selectedRegions}
              setSelectedRegions={setSelectedRegions}
              allSearchOptions={allSearchOptions}
              searchInputValue={searchInputValue}
              setSearchInputValue={setSearchInputValue}
              setJumpToDate={setJumpToDate}
              setSnackbar={setSnackbar}
              handleOpenDetail={handleOpenDetail}
            />
          </ThemeProvider>
        </Box>

        <TodayOverview onResetFilters={()=>{setSelectedRegions(ALL_REGIONS);setSearchInputValue('');setParamInfo(previous=>({...previous,citys:[],...Object.fromEntries(Array.from({length:8},(_,i)=>[`rceptSe${String(i+1).padStart(2,'0')}`,'Y']))}));}} month={calendarMonth} onMonthChange={setCalendarMonth} isLoading={announcementsRes == null && !isListError} items={subscriptList} onOpen={handleOpenDetail} viewMode={viewMode} setViewMode={setViewMode} />
        {/* 캘린더 또는 목록 표시 */}
        <Box sx={{ mt: 0.5 }}>
          {isListError && <Alert severity="error" sx={{ mb: 2 }}
            action={<Button color="inherit" onClick={() => refetchAnnouncements()}>다시 시도</Button>}>
            공고 목록을 불러오지 못했습니다. {listError?.message}
          </Alert>}
          {viewMode === 'calendar' && <SubscriptionCalendar
            initialMonth={calendarMonth}
            onMonthChange={setCalendarMonth}
            jumpToDate={jumpToDate}
            paramInfo={paramInfo}
            setParamInfo={setParamInfo}
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

            {/* 단지명 (SEO 최적화: h1 시맨틱 태그) */}
            <Typography
              component="h1"
              variant="h6"
              sx={{
                fontWeight: 800,
                fontSize: { xs: '1.15rem', sm: '1.3rem' },
                color: '#0f172a',
                lineHeight: 1.35,
                mb: 0.5,
                wordBreak: 'keep-all'
              }}
            >
              {currentSubscriptDetail?.houseNm || mainDetail.subscriptDetail?.houseNm || '청약 공고 상세'}
            </Typography>

            {/* 공급위치(주소) 요약 (SEO address 태그) + 주소 복사 버튼 */}
            {(currentSubscriptDetail?.hssplyAdres || currentSubscriptDetail?.supplyLocation) && (
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
                  <span>📍</span>
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

        {!isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <AnnouncementActions key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail}/>}

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
                '& .MuiTab-root': {
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
                label={`댓글${mainDetail.subscriptDetail?.subscriptCommentCnt > 0 ? ` (${mainDetail.subscriptDetail.subscriptCommentCnt})` : ''}`}
              />
            </Tabs>
          </Box>
        </Box>

        {detailTabValue===0 && !isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <Box className="announcement-section-nav" sx={{px:{xs:2,sm:2.5},bgcolor:'#fff',flexShrink:0}}><ResearchTabs value={researchTab} onChange={setResearchTab}/></Box>}

        {/* 3. 본문 스크롤 영역 */}
        <Box ref={detailScrollRef} data-testid="announcement-scroll"
          sx={{
            flex: 1,
            overflowY: 'auto',
            p: { xs: 2, sm: 2.5 },
            pt: 1,
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
              {detailTabValue === 0 && <ResearchPanel key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail} tab={researchTab} onTabChange={setResearchTab} showTabs={false}><AnnouncementNotice mainDetail={mainDetail} /></ResearchPanel>}
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
