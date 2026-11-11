/**
 * @file Main.jsx
 * @description 청약 일정 캘린더 및 공고 메인 화면 컴포넌트
 */
import { useState, useMemo, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

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
import ShareIcon from '@mui/icons-material/Share';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CircularProgress from '@mui/material/CircularProgress';

import 'simplebar-react/dist/simplebar.min.css';
import './main.css';

export const Main = () => {
  const [searchParams, setSearchParams] = useSearchParams();

  // 공급지역 체크 목록 (최초에는 전국 17개 시/도가 모두 선택된 상태)
  const [selectedRegions, setSelectedRegions] = useState(ALL_REGIONS);

  // 필터 파라미터 상태
  const [paramInfo, setParamInfo] = useState({
    rceptSe01: 'Y',
    rceptSe02: 'Y',
    rceptSe03: 'Y',
    rceptSe04: 'Y',
    rceptSe05: 'Y',
    rceptSe06: 'Y',
    rceptSe07: 'Y',
    rceptSe08: 'Y',
    startStr: '',
    endStr: '',
    citys: []
  });

  // 캘린더 특정 날짜로 강제 이동 상태 (전체 기간 아파트 선택 시 사용)
  const [jumpToDate, setJumpToDate] = useState(null);

  // 1. TanStack Query로 공고 목록 페칭
  const {
    data: announcementsRes,
    refetch: refetchAnnouncements
  } = useAnnouncementsQuery(paramInfo);

  const rawSubscriptList = useMemo(() => {
    return announcementsRes?.data?.resultList || [];
  }, [announcementsRes]);

  const { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList } =
    useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions);

  // 상세 모달 상태 및 탭 (상세 0, 위치 1, 댓글 2)
  const [modalOpen, setModalOpen] = useState(false);
  const [detailTabValue, setDetailTabValue] = useState(0);
  const [selectedSubscriptSeq, setSelectedSubscriptSeq] = useState(null);
  const [snackbar, setSnackbar] = useState({ open: false, message: '', severity: 'success' });
  const [addressCopied, setAddressCopied] = useState(false);

  // 2. TanStack Query로 공고 상세 정보 페칭 (모달 열릴 때만 수행)
  const {
    data: detailRes,
    isLoading: isDetailLoading
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
      updateAnnouncementSeo(currentSubscriptDetail);
    } else if (!modalOpen) {
      resetDefaultSeo();
    }
  }, [modalOpen, currentSubscriptDetail]);

  // URL 파라미터 감지 (/showSubscriptDetail?subscriptSeq=...)
  useEffect(() => {
    const subscriptSeq = searchParams.get('subscriptSeq');
    const tabType = searchParams.get('tabType');

    if (subscriptSeq) {
      setSelectedSubscriptSeq(subscriptSeq);
      setModalOpen(true);

      const tabMap = { detail: 0, rate: 1, map: 2, comment: 3 };
      if (tabType && tabMap[tabType] !== undefined) {
        setDetailTabValue(tabMap[tabType]);
      }
    }
  }, [searchParams]);

  const handleOpenDetail = (seq) => {
    setSelectedSubscriptSeq(seq);
    setDetailTabValue(0);
    setModalOpen(true);
    setSearchParams({ subscriptSeq: seq, tabType: 'detail' }, { replace: true });
  };

  const handleCloseDetail = () => {
    setModalOpen(false);
    setSelectedSubscriptSeq(null);
    setSearchParams({}, { replace: true });
    resetDefaultSeo();
  };

  const handleTabChange = (event, newValue) => {
    setDetailTabValue(newValue);
    const tabNames = ['detail', 'rate', 'map', 'comment'];
    if (selectedSubscriptSeq) {
      setSearchParams(
        { subscriptSeq: selectedSubscriptSeq, tabType: tabNames[newValue] },
        { replace: true }
      );
    }
  };

  // 공고 공유하기 (Web Share API 및 클립보드 복사 폴백)
  const handleShare = async () => {
    const seq = selectedSubscriptSeq || currentSubscriptDetail?.subscriptSeq || '';
    const shareUrl = `${window.location.origin}/?subscriptSeq=${encodeURIComponent(seq)}`;
    const houseNm = currentSubscriptDetail?.houseNm || '청약 공고';
    const location = currentSubscriptDetail?.supplyLocation || currentSubscriptDetail?.hssplyAdres || '';
    const date = currentSubscriptDetail?.subscriptDate || '';

    const shareData = {
      title: `[TOADY 청약] ${houseNm}`,
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
      <div>
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

        {/* 캘린더 단독 표시 */}
        <Box sx={{ mt: 0.5 }}>
          <SubscriptionCalendar
            jumpToDate={jumpToDate}
            paramInfo={paramInfo}
            setParamInfo={setParamInfo}
            fn_subscriptList={refetchAnnouncements}
            subscriptList={subscriptList}
            handleOpen={(seq) => handleOpenDetail(seq)}
          />
        </Box>
      </div>

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
                size="small"
                sx={{
                  width: 36,
                  height: 36,
                  color: '#2563eb',
                  bgcolor: '#eff6ff',
                  border: '1px solid #bfdbfe',
                  transition: 'all 0.15s ease',
                  '&:hover': {
                    bgcolor: '#2563eb',
                    color: '#ffffff',
                    borderColor: '#2563eb'
                  }
                }}
              >
                <ShareIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <IconButton
              onClick={handleCloseDetail}
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

        {/* 3. 본문 스크롤 영역 */}
        <Box
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
          ) : (
            <>
              {detailTabValue === 0 && <AnnouncementNotice mainDetail={mainDetail} />}
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