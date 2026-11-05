/**
 * @file Main.jsx
 * @description 청약 일정 캘린더 및 공고 메인 화면 컴포넌트
 */
import React, { useState, useMemo, useEffect } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';

import {
  SubscriptDetail1,
  SubscriptDetail2,
  SubscriptDetail3,
  SubscriptDetail4,
  SubscriptDetail5
} from '@/views/main/subscriptDetail';
import { SubscriptionCalendar } from '@/views/main/calendar';
import { SubscriptComment } from '@/views/main/subscriptComment';
import SubscriptRate from '@/views/main/subscriptRate';
import Kakaomap from '@/components/kakao/Kakaomap';
import Citys from './Citys';
import { fn_rceptSeColor, fn_rceptSeBgColor } from './rceptSeColorUtil';
import { formatDate } from '@/utils/date.util';
import { updateAnnouncementSeo, resetDefaultSeo } from '@/utils/seo.util';
import { useAnnouncementsQuery, useAnnouncementDetailQuery } from '@/features/announcement/hooks/useAnnouncements';

import { styled, createTheme, ThemeProvider } from '@mui/material/styles';
import Typography from '@mui/material/Typography';
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import CalendarMonthIcon from '@mui/icons-material/CalendarMonth';
import LeaderboardIcon from '@mui/icons-material/Leaderboard';
import FeedIcon from '@mui/icons-material/Feed';
import MyLocationIcon from '@mui/icons-material/MyLocation';
import CloseIcon from '@mui/icons-material/Close';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Grid from '@mui/material/Grid';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import ListIcon from '@mui/icons-material/List';
import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import Paper from '@mui/material/Paper';
import Collapse from '@mui/material/Collapse';
import Divider from '@mui/material/Divider';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';
import DisabledByDefaultIcon from '@mui/icons-material/DisabledByDefault';
import AlertTitle from '@mui/material/AlertTitle';
import Modal from '@mui/material/Modal';
import CommentIcon from '@mui/icons-material/Comment';
import Alert from '@mui/material/Alert';
import Snackbar from '@mui/material/Snackbar';
import Tooltip from '@mui/material/Tooltip';
import ShareIcon from '@mui/icons-material/Share';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import CheckIcon from '@mui/icons-material/Check';
import CircularProgress from '@mui/material/CircularProgress';
import { green } from '@mui/material/colors';
import Avatar from '@mui/material/Avatar';
import AssignmentIcon from '@mui/icons-material/Assignment';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import SimpleBar from 'simplebar-react';
import 'simplebar-react/dist/simplebar.min.css';
import './main.css';

// 청약 순위 텍스트 매핑
function subscriptTypeName(item) {
  if (item.type === 'remaining' && item.houseSecdNm) {
    return item.houseSecdNm;
  }

  const map = {
    '01': '특별공급',
    '02': '1순위',
    '03': '2순위',
    '04': '민간임대',
    '05': '오피스텔',
    '06': '무순위',
    '07': '임의공급',
    '08': '불법행위재공급'
  };
  return map[item.rceptSe] || '일반공급';
}

// 청약홈 표준 공급지역 코드 (SUBSCRPT_AREA_CODE) 정의
const REGION_SEOUL = { code: '100', name: '서울', matchNames: ['서울', '서울특별시'] };

const REGION_METRO = [
  { code: '500', name: '광주', matchNames: ['광주광역시', '광주'] },
  { code: '700', name: '대구', matchNames: ['대구광역시', '대구'] },
  { code: '300', name: '대전', matchNames: ['대전광역시', '대전'] },
  { code: '600', name: '부산', matchNames: ['부산광역시', '부산'] },
  { code: '338', name: '세종', matchNames: ['세종특별자치시', '세종'] },
  { code: '680', name: '울산', matchNames: ['울산광역시', '울산'] },
  { code: '400', name: '인천', matchNames: ['인천광역시', '인천'] }
];

const REGION_PROVINCE = [
  { code: '200', name: '강원', matchNames: ['강원특별자치도', '강원도', '강원'] },
  { code: '410', name: '경기', matchNames: ['경기도', '경기'] },
  { code: '621', name: '경남', matchNames: ['경상남도', '경남'] },
  { code: '712', name: '경북', matchNames: ['경상북도', '경북'] },
  { code: '513', name: '전남', matchNames: ['전라남도', '전남'] },
  { code: '560', name: '전북', matchNames: ['전북특별자치도', '전라북도', '전북'] },
  { code: '690', name: '제주', matchNames: ['제주특별자치도', '제주'] },
  { code: '312', name: '충남', matchNames: ['충청남도', '충남'] },
  { code: '360', name: '충북', matchNames: ['충청북도', '충북'] }
];

const ALL_REGIONS = [REGION_SEOUL, ...REGION_METRO, ...REGION_PROVINCE];

// Citys.js의 계층형 데이터를 Autocomplete용 플랫(flat) 리스트로 변환 (시/도 + 시/군/구)
const FLAT_CITY_OPTIONS = Citys.flatMap((c) => {
  const districtList = c.districts || [];
  // 시/도 전체 항목: 하위 구/군 목록을 괄호 예시로 요약 표시 (예: "강남구, 강동구, 강북구 외 20개")
  const districtNamesPreview = districtList.slice(0, 3).map((d) => d.districtName).join(', ');
  const moreCount = districtList.length > 3 ? ` 외 ${districtList.length - 3}개` : '';
  const subText = districtList.length > 0 ? `(${districtNamesPreview}${moreCount})` : '';

  const cityItem = {
    key: c.cityName,
    type: 'city',
    label: c.cityName,
    subText,
    cityName: c.cityName,
    districtName: '',
    searchText: `${c.cityName} ${districtList.map((d) => d.districtName).join(' ')}`
  };

  const districtItems = districtList.map((d) => {
    const fullLabel = `${c.cityName} ${d.districtName}`;
    const townsList = d.towns || [];
    // 구/군 항목: 하위 동/읍/면 목록을 괄호 예시로 요약 표시 (예: "(개포동, 대치동, 도곡동 외 4개)")
    const townsPreview = townsList.slice(0, 3).join(', ');
    const townsMore = townsList.length > 3 ? ` 외 ${townsList.length - 3}개` : '';
    const districtSubText = townsList.length > 0 ? `(${townsPreview}${townsMore})` : '';

    return {
      key: fullLabel,
      type: 'district',
      label: fullLabel,
      subText: districtSubText,
      cityName: c.cityName,
      districtName: d.districtName,
      towns: townsList,
      searchText: `${fullLabel} ${townsList.join(' ')}`
    };
  });

  return [cityItem, ...districtItems];
});

// 공급 유형 필터 칩 목록 정의
const SUPPLY_FILTER_ITEMS = [
  { id: '01', label: '특별공급', color: '#8b5cf6' },
  { id: '02', label: '1순위', color: '#ef4444' },
  { id: '03', label: '2순위', color: '#16a34a' },
  { id: '06', label: '무순위', color: '#3b82f6' },
  { id: '07', label: '임의공급', color: '#ea580c' },
  { id: '08', label: '취소재공급', color: '#0d9488' },
  { id: '05', label: '오피스텔/도시형', color: '#0284c7' },
  { id: '04', label: '민간임대', color: '#64748b' }
];

export const Main = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  // 공급지역 체크 목록 (최초에는 전국 17개 시/도가 모두 선택된 상태)
  const [selectedRegions, setSelectedRegions] = useState(ALL_REGIONS);

  // 공급지역 필터 레이어 펼침/접힘 상태 (기본값: 접힘/숨김)
  const [isRegionOpen, setIsRegionOpen] = useState(false);

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

  // 1. TanStack Query로 공고 목록 페칭
  const {
    data: announcementsRes,
    isPending: isListLoading, // enabled=false(날짜 세팅 전)일 때도 로딩으로 표시
    refetch: refetchAnnouncements
  } = useAnnouncementsQuery(paramInfo);

  const rawSubscriptList = useMemo(() => {
    return announcementsRes?.data?.resultList || [];
  }, [announcementsRes]);

  // 시/도, 구/군 행정구역 옵션 + 현재 조회된 공고들의 아파트 단지명(houseNm) 옵션 동적 통합
  const allSearchOptions = useMemo(() => {
    const aptMap = new Map();

    rawSubscriptList.forEach((item) => {
      const houseNm = item.houseNm ? item.houseNm.trim() : '';
      if (houseNm && !aptMap.has(houseNm)) {
        const subAddress = item.hssplyAdres || item.supplyLocation || '';
        aptMap.set(houseNm, {
          key: `apt_${houseNm}`,
          type: 'apartment',
          label: houseNm,
          subText: subAddress ? `(${subAddress})` : '',
          houseNm,
          cityName: '',
          districtName: '',
          searchText: `${houseNm} ${subAddress}`
        });
      }
    });

    return [...FLAT_CITY_OPTIONS, ...Array.from(aptMap.values())];
  }, [rawSubscriptList]);

  // 검색어/지역 + 체크박스(특별공급/1순위/2순위 등) + 공급지역 체크박스 필터 적용된 목록
  const subscriptList = useMemo(() => {
    if (!rawSubscriptList || rawSubscriptList.length === 0) return [];
    let list = rawSubscriptList.filter((item) => paramInfo[`rceptSe${item.rceptSe}`] === 'Y');

    // 1. 공급지역 체크박스 필터링 (전체 선택이 아닌 일부 선택일 때만 필터 적용)
    if (selectedRegions.length > 0 && selectedRegions.length < ALL_REGIONS.length) {
      list = list.filter((item) => {
        const itemRegionCode = item.regionCode ? String(item.regionCode).trim() : '';
        const itemLocation = item.supplyLocation || '';

        return selectedRegions.some((reg) => {
          // 1. API 응답의 regionCode 코드값 일치 확인 (예: '100', '410' 등)
          if (itemRegionCode && itemRegionCode === reg.code) {
            return true;
          }
          // 2. 지역명 텍스트 일치 확인 (백업 비교)
          return reg.matchNames.some((m) => itemLocation.includes(m));
        });
      });
    } else if (selectedRegions.length === 0) {
      // 아무것도 선택 안 된 경우 목록 비움
      list = [];
    }

    // 2. 검색창(Autocomplete) citys 필터링 (시/도, 구/군, 아파트명(houseNm), 공급주소(hssplyAdres) 매칭)
    if (paramInfo.citys && paramInfo.citys.length > 0) {
      list = list.filter((item) =>
        paramInfo.citys.some((target) => {
          const supplyLoc = item.supplyLocation || '';
          const houseNm = item.houseNm || '';
          const hssplyAdres = item.hssplyAdres || '';

          // A. freeSolo로 직접 텍스트를 입력/엔터한 경우 (문자열)
          if (typeof target === 'string') {
            const query = target.trim().toLowerCase();
            if (!query) return true;
            return (
              houseNm.toLowerCase().includes(query) ||
              supplyLoc.toLowerCase().includes(query) ||
              hssplyAdres.toLowerCase().includes(query)
            );
          }

          // B. 아파트 단지명(apartment) 옵션을 선택한 경우
          if (target.type === 'apartment') {
            const targetApt = (target.houseNm || target.label || '').trim();
            return houseNm.includes(targetApt);
          }

          // C. 구/군 검색인 경우 (예: '파주시' 또는 '경기도 파주시')
          if (target.districtName) {
            return (
              supplyLoc.includes(target.districtName) ||
              hssplyAdres.includes(target.districtName) ||
              houseNm.includes(target.districtName) ||
              houseNm.includes(target.label)
            );
          }

          // D. 시/도 전체 검색인 경우 (예: '서울특별시', '경기도')
          const query = target.cityName || target.label;
          return supplyLoc.includes(query) || hssplyAdres.includes(query) || houseNm.includes(query);
        })
      );
    }

    return list;
  }, [rawSubscriptList, paramInfo, selectedRegions]);

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

  // 공급지역 개별 토글 핸들러
  const handleToggleRegion = (region) => {
    setSelectedRegions((prev) => {
      const exists = prev.some((r) => r.code === region.code);
      if (exists) {
        return prev.filter((r) => r.code !== region.code);
      }
      return [...prev, region];
    });
  };

  // 공급지역 그룹 전체 토글 핸들러 (광역 전체 / 도 전체)
  const handleToggleRegionGroup = (groupList) => {
    setSelectedRegions((prev) => {
      const isAllChecked = groupList.every((g) => prev.some((p) => p.code === g.code));
      if (isAllChecked) {
        // 그룹 전체 해제
        return prev.filter((p) => !groupList.some((g) => g.code === p.code));
      }
      // 그룹 전체 선택
      const rest = prev.filter((p) => !groupList.some((g) => g.code === p.code));
      return [...rest, ...groupList];
    });
  };

  // 공급유형 칩/체크박스 토글
  const handleRceptSeToggle = (rceptSe) => {
    setParamInfo((prev) => {
      const next = { ...prev };
      if (rceptSe === '01ALL') {
        const isAllApt =
          prev.rceptSe01 === 'Y' &&
          prev.rceptSe02 === 'Y' &&
          prev.rceptSe03 === 'Y' &&
          prev.rceptSe06 === 'Y' &&
          prev.rceptSe07 === 'Y' &&
          prev.rceptSe08 === 'Y';
        const newYn = isAllApt ? 'N' : 'Y';
        next.rceptSe01 = newYn;
        next.rceptSe02 = newYn;
        next.rceptSe03 = newYn;
        next.rceptSe06 = newYn;
        next.rceptSe07 = newYn;
        next.rceptSe08 = newYn;
      } else if (rceptSe === 'ALL') {
        const isAll =
          prev.rceptSe01 === 'Y' &&
          prev.rceptSe02 === 'Y' &&
          prev.rceptSe03 === 'Y' &&
          prev.rceptSe04 === 'Y' &&
          prev.rceptSe05 === 'Y' &&
          prev.rceptSe06 === 'Y' &&
          prev.rceptSe07 === 'Y' &&
          prev.rceptSe08 === 'Y';
        const newYn = isAll ? 'N' : 'Y';
        ['01', '02', '03', '04', '05', '06', '07', '08'].forEach((code) => {
          next[`rceptSe${code}`] = newYn;
        });
      } else {
        next[`rceptSe${rceptSe}`] = prev[`rceptSe${rceptSe}`] === 'Y' ? 'N' : 'Y';
      }
      return next;
    });
  };

  // 주택 유형별 상세 컴포넌트 분기
  const renderDetailComponent = () => {
    if (!mainDetail.subscriptDetail) return null;
    const rceptSe = mainDetail.subscriptDetail.rceptSe;
    const detailProp = { mainDetail1: { detail: mainDetail } };

    if (rceptSe === '06') return <SubscriptDetail2 {...detailProp} />;
    if (rceptSe === '07') return <SubscriptDetail3 {...detailProp} />;
    if (rceptSe === '05') return <SubscriptDetail4 {...detailProp} />;
    if (rceptSe === '04') return <SubscriptDetail5 {...detailProp} />;
    return <SubscriptDetail1 {...detailProp} />;
  };

  // URL 유효성 검사 (http:// 또는 https:// 로 시작하는 정상 웹 URL 여부)
  const isValidWebUrl = (url) => {
    if (!url || typeof url !== 'string') return false;
    const trimmed = url.trim();
    if (trimmed === '' || trimmed === '#' || trimmed === 'about:blank') return false;
    return trimmed.startsWith('http://') || trimmed.startsWith('https://');
  };

  // 상세 탭 본문 렌더링 (pblancUrl이 유효하면 상단 안내 네모박스 + 새 창 열기 버튼 + iframe 표시, 유효하지 않으면 기존 구현 상세 컴포넌트 표시)
  const renderDetailTabContent = () => {
    if (!mainDetail.subscriptDetail) return null;

    const detail = mainDetail.subscriptDetail;
    // 1순위: pblancUrl, 2순위: recruitmentNoticeFileUrl
    const webUrl = (detail.pblancUrl && isValidWebUrl(detail.pblancUrl))
      ? detail.pblancUrl.trim()
      : (isValidWebUrl(detail.recruitmentNoticeFileUrl) ? detail.recruitmentNoticeFileUrl.trim() : '');

    // URL이 유효하지 않은 경우 기존에 구현된 상세 컴포넌트 표시
    if (!webUrl) {
      return renderDetailComponent();
    }

    // URL이 유효한 경우: 상단 네모박스(안내문 + 새 창 열기 버튼) + iframe 표시
    return (
      <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: { xs: 550, sm: 650 } }}>
        {/* 상단 네모박스: 페이지 안내문 + 새 창 열기 버튼 */}
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 1.5,
            py: 0.8,
            mb: 1,
            bgcolor: '#f8fafc',
            borderRadius: 1.5,
            border: '1px solid #e2e8f0',
            gap: 1
          }}
        >
          <Typography variant="caption" sx={{ color: '#64748b', fontSize: '0.78rem', fontWeight: 500 }}>
            페이지가 정상 표시되지 않을 경우 새 창으로 열어보세요.
          </Typography>

          <Button
            size="small"
            variant="outlined"
            href={webUrl}
            target="_blank"
            rel="noopener noreferrer"
            startIcon={<OpenInNewIcon fontSize="small" />}
            sx={{
              fontSize: '0.75rem',
              fontWeight: 700,
              minWidth: 0,
              py: 0.3,
              px: 1.2,
              borderRadius: 1,
              flexShrink: 0
            }}
          >
            새 창 열기
          </Button>
        </Box>

        {/* iframe 본문 영역 */}
        <Box
          sx={{
            flex: 1,
            width: '100%',
            minHeight: { xs: 520, sm: 620 },
            borderRadius: 2,
            overflow: 'hidden',
            border: '1px solid #e2e8f0',
            bgcolor: '#ffffff',
            position: 'relative'
          }}
        >
          <iframe
            src={webUrl}
            title="모집공고 상세 웹페이지"
            style={{
              width: '100%',
              height: '100%',
              minHeight: '620px',
              border: 'none',
              display: 'block'
            }}
            loading="lazy"
          />
        </Box>
      </Box>
    );
  };

  const isAllAptChecked =
    paramInfo.rceptSe01 === 'Y' &&
    paramInfo.rceptSe02 === 'Y' &&
    paramInfo.rceptSe03 === 'Y' &&
    paramInfo.rceptSe06 === 'Y' &&
    paramInfo.rceptSe07 === 'Y' &&
    paramInfo.rceptSe08 === 'Y';

  const isAllChecked =
    isAllAptChecked &&
    paramInfo.rceptSe04 === 'Y' &&
    paramInfo.rceptSe05 === 'Y';

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
            <Paper
              variant="outlined"
              sx={{
                p: { xs: 1.5, md: 2 },
                mb: 1.5,
                borderRadius: 2.5,
                bgcolor: '#ffffff',
                borderColor: '#e2e8f0',
                boxShadow: '0 1px 3px rgba(0,0,0,0.03)'
              }}
            >
              {/* [섹션 1] 공급유형 필터 (모던 칩 UI) */}
              <Box sx={{ mb: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
                  <Typography sx={{ fontWeight: 800, fontSize: '0.86rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 0.6 }}>
                    <span style={{ color: '#2563eb' }}>●</span> 공급유형
                  </Typography>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.6 }}>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => handleRceptSeToggle('01ALL')}
                      sx={{
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        color: isAllAptChecked ? '#2563eb' : '#64748b',
                        minWidth: 0,
                        py: 0.2,
                        px: 0.8,
                        borderRadius: 1,
                        bgcolor: isAllAptChecked ? '#eff6ff' : 'transparent',
                        '&:hover': { bgcolor: '#eff6ff' }
                      }}
                    >
                      {isAllAptChecked ? 'APT 해제' : 'APT 전체'}
                    </Button>
                    <Typography sx={{ color: '#cbd5e1', fontSize: '0.7rem' }}>|</Typography>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => handleRceptSeToggle('ALL')}
                      sx={{
                        fontSize: '0.74rem',
                        fontWeight: 700,
                        color: '#2563eb',
                        minWidth: 0,
                        py: 0.2,
                        px: 0.8,
                        borderRadius: 1,
                        '&:hover': { bgcolor: '#eff6ff' }
                      }}
                    >
                      {isAllChecked ? '전체 해제' : '전체 선택'}
                    </Button>
                  </Box>
                </Box>

                {/* 칩 리스트 */}
                <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 0.8 }}>
                  {/* 공급 유형별 알약형 칩 */}
                  {SUPPLY_FILTER_ITEMS.map((item) => {
                    const isChecked = paramInfo[`rceptSe${item.id}`] === 'Y';
                    return (
                      <Box
                        key={item.id}
                        onClick={() => handleRceptSeToggle(item.id)}
                        sx={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 0.6,
                          px: 1.3,
                          py: 0.55,
                          borderRadius: '16px',
                          cursor: 'pointer',
                          fontSize: '0.8rem',
                          fontWeight: isChecked ? 700 : 500,
                          border: isChecked ? '1.5px solid #94a3b8' : '1px solid #e2e8f0',
                          bgcolor: isChecked ? '#ffffff' : '#f8fafc',
                          color: isChecked ? '#0f172a' : '#94a3b8',
                          boxShadow: isChecked ? '0 1px 2px rgba(0,0,0,0.04)' : 'none',
                          transition: 'all 0.15s ease',
                          userSelect: 'none',
                          '&:hover': {
                            borderColor: '#64748b',
                            bgcolor: '#ffffff'
                          }
                        }}
                      >
                        {/* 공급 유형 컬러 닷 */}
                        <Box
                          sx={{
                            width: 7,
                            height: 7,
                            borderRadius: '50%',
                            bgcolor: isChecked ? item.color : '#cbd5e1',
                            transition: 'background-color 0.15s ease'
                          }}
                        />
                        {item.label}
                      </Box>
                    );
                  })}
                </Box>
              </Box>

              <Divider sx={{ mb: 1.2, borderStyle: 'dashed' }} />

              {/* [섹션 2] 공급지역 필터 헤더 + 우측 구/군/단지명 검색 (인라인 통합) */}
              <Box>
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: { xs: 'stretch', sm: 'center' },
                    justifyContent: 'space-between',
                    flexDirection: { xs: 'column', sm: 'row' },
                    gap: 1.5,
                    py: 0.5
                  }}
                >
                  {/* 좌측: 공급지역 타이틀 + 선택 카운트 뱃지 + 지역 선택 토글 버튼 */}
                  <Box
                    onClick={() => setIsRegionOpen((prev) => !prev)}
                    sx={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 1,
                      cursor: 'pointer',
                      py: 0.5,
                      px: 0.6,
                      borderRadius: 1.5,
                      transition: 'all 0.15s ease',
                      '&:hover': { bgcolor: 'rgba(0, 0, 0, 0.03)' }
                    }}
                  >
                    <Typography sx={{ fontWeight: 800, fontSize: '0.85rem', color: '#1e293b', display: 'flex', alignItems: 'center', gap: 0.5 }}>
                      <span style={{ color: '#16a34a' }}>●</span> 공급지역
                    </Typography>
                    <Box
                      sx={{
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        px: 1,
                        py: 0.2,
                        borderRadius: 1,
                        bgcolor: selectedRegions.length === ALL_REGIONS.length ? '#f1f5f9' : '#eff6ff',
                        color: selectedRegions.length === ALL_REGIONS.length ? '#64748b' : '#2563eb'
                      }}
                    >
                      {selectedRegions.length === ALL_REGIONS.length
                        ? '전국 (17개)'
                        : `${selectedRegions.length}개 선택됨`}
                    </Box>
                    <Button
                      size="small"
                      variant="text"
                      endIcon={
                        <ExpandMoreIcon
                          sx={{
                            transform: isRegionOpen ? 'rotate(180deg)' : 'rotate(0deg)',
                            transition: 'transform 0.2s ease',
                            fontSize: '1.1rem'
                          }}
                        />
                      }
                      sx={{
                        fontSize: '0.78rem',
                        fontWeight: 700,
                        color: isRegionOpen ? '#16a34a' : '#64748b',
                        p: 0,
                        minWidth: 'auto',
                        '&:hover': { bgcolor: 'transparent', color: '#16a34a' }
                      }}
                    >
                      {isRegionOpen ? '접기' : '지역 선택'}
                    </Button>
                  </Box>

                  {/* 우측: 구/군/단지명 통합 검색 (인라인 수납) */}
                  <Box sx={{ width: { xs: '100%', sm: 380, md: 440 }, flexShrink: 0 }}>
                    <Autocomplete
                      multiple
                      freeSolo
                      size="small"
                      id="tags-outlined"
                      options={allSearchOptions}
                      getOptionLabel={(option) => {
                        if (typeof option === 'string') return option;
                        return option.label || option.cityName || option.houseNm || '';
                      }}
                      isOptionEqualToValue={(option, value) => {
                        if (typeof option === 'string' || typeof value === 'string') {
                          return option === value;
                        }
                        return option.key === value.key;
                      }}
                      filterOptions={(options, { inputValue }) => {
                        const query = inputValue.trim().toLowerCase();
                        if (!query) return options.slice(0, 50); // 기본 50개 노출
                        return options.filter((opt) => {
                          if (typeof opt === 'string') return opt.toLowerCase().includes(query);
                          return opt.searchText?.toLowerCase().includes(query);
                        });
                      }}
                      filterSelectedOptions
                      onChange={(_, newCitys) => setParamInfo((prev) => ({ ...prev, citys: newCitys }))}
                      renderOption={(props, option) => {
                        if (typeof option === 'string') {
                          return (
                            <li {...props} key={`custom_${option}`}>
                              <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', py: 0.4 }}>
                                <Chip
                                  label="직접입력"
                                  size="small"
                                  sx={{
                                    height: 20,
                                    fontSize: '0.68rem',
                                    fontWeight: 700,
                                    mr: 1,
                                    flexShrink: 0,
                                    bgcolor: '#f1f5f9',
                                    color: '#475569',
                                    border: '1px solid #cbd5e1'
                                  }}
                                />
                                <Typography sx={{ fontSize: '0.85rem', fontWeight: 600, color: '#0f172a' }}>
                                  "{option}" 검색
                                </Typography>
                              </Box>
                            </li>
                          );
                        }

                        const isApt = option.type === 'apartment';
                        const isCity = option.type === 'city';
                        const chipLabel = isApt ? '단지명' : isCity ? '시/도' : '구/군';
                        const chipBg = isApt ? '#fef3c7' : isCity ? '#eff6ff' : '#f8fafc';
                        const chipColor = isApt ? '#d97706' : isCity ? '#2563eb' : '#64748b';
                        const chipBorder = isApt ? '1px solid #fde68a' : isCity ? '1px solid #bfdbfe' : '1px solid #e2e8f0';

                        return (
                          <li {...props} key={option.key || option.label}>
                            <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', py: 0.4, overflow: 'hidden' }}>
                              <Chip
                                label={chipLabel}
                                size="small"
                                sx={{
                                  height: 20,
                                  fontSize: '0.68rem',
                                  fontWeight: 700,
                                  mr: 1,
                                  flexShrink: 0,
                                  bgcolor: chipBg,
                                  color: chipColor,
                                  border: chipBorder
                                }}
                              />
                              <Typography
                                sx={{
                                  fontSize: '0.85rem',
                                  fontWeight: isCity ? 800 : 600,
                                  color: '#0f172a',
                                  whiteSpace: 'nowrap',
                                  mr: 1
                                }}
                              >
                                {option.label}
                              </Typography>
                              {option.subText && (
                                <Typography
                                  variant="caption"
                                  sx={{
                                    fontSize: '0.75rem',
                                    color: '#94a3b8',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap'
                                  }}
                                >
                                  {option.subText}
                                </Typography>
                              )}
                            </Box>
                          </li>
                        );
                      }}
                      renderInput={(params) => (
                        <TextField
                          {...params}
                          size="small"
                          placeholder="지역, 구/군, 아파트명 검색..."
                          InputProps={{
                            ...params.InputProps,
                            startAdornment: (
                              <>
                                <InputAdornment position="start" sx={{ ml: 0.5 }}>
                                  <SearchIcon sx={{ color: '#94a3b8', fontSize: '1.15rem' }} />
                                </InputAdornment>
                                {params.InputProps.startAdornment}
                              </>
                            )
                          }}
                          sx={{
                            bgcolor: '#f8fafc',
                            borderRadius: 1.5,
                            '& .MuiOutlinedInput-root': {
                              py: '2px',
                              px: '6px',
                              fontSize: '0.82rem',
                              '& fieldset': { borderColor: '#e2e8f0' },
                              '&:hover fieldset': { borderColor: '#cbd5e1' },
                              '&.Mui-focused fieldset': { borderColor: '#2563eb' }
                            }
                          }}
                        />
                      )}
                    />
                  </Box>
                </Box>

                {/* 공급지역 상세 체크박스 레이어 (Collapse 애니메이션 적용) */}
                <Collapse in={isRegionOpen} timeout="auto" unmountOnExit>
                  <Box sx={{ display: 'flex', gap: 1.2, flexWrap: { xs: 'wrap', lg: 'nowrap' }, mt: 1 }}>
                  {/* 1. 서울 카드 (블루 단독) */}
                  <Box
                    sx={{
                      bgcolor: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#2563eb' : '#f8fafc',
                      color: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#fff' : '#64748b',
                      border: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '1px solid #1d4ed8' : '1px solid #cbd5e1',
                      borderRadius: 1.5,
                      px: 2,
                      py: 1,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      cursor: 'pointer',
                      minWidth: { xs: '100%', sm: '90px' },
                      transition: 'all 0.15s ease',
                      '&:hover': {
                        bgcolor: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#1d4ed8' : '#e2e8f0'
                      }
                    }}
                    onClick={() => handleToggleRegion(REGION_SEOUL)}
                  >
                    <FormControlLabel
                      sx={{ m: 0, pointerEvents: 'none' }}
                      control={
                        <Checkbox
                          size="small"
                          checked={selectedRegions.some((r) => r.code === REGION_SEOUL.code)}
                          sx={{
                            color: selectedRegions.some((r) => r.code === REGION_SEOUL.code) ? '#fff' : '#94a3b8',
                            '&.Mui-checked': { color: '#fff' },
                            p: 0.2,
                            mr: 0.5
                          }}
                        />
                      }
                      label={<Typography sx={{ fontWeight: 800, fontSize: '0.9rem' }}>서울</Typography>}
                    />
                  </Box>

                  {/* 2. 도 카드 (그린 헤더 + 9개 도 하단 그리드) */}
                  <Box
                    sx={{
                      flex: 1.25,
                      minWidth: { xs: '100%', sm: '380px' },
                      border: '1px solid #bbf7d0',
                      borderRadius: 1.5,
                      overflow: 'hidden',
                      bgcolor: '#fff'
                    }}
                  >
                    <Box
                      sx={{
                        bgcolor: '#16a34a',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        '&:hover': { bgcolor: '#15803d' }
                      }}
                      onClick={() => handleToggleRegionGroup(REGION_PROVINCE)}
                    >
                      <FormControlLabel
                        sx={{ m: 0, pointerEvents: 'none' }}
                        control={
                          <Checkbox
                            size="small"
                            checked={REGION_PROVINCE.every((g) => selectedRegions.some((p) => p.code === g.code))}
                            indeterminate={
                              REGION_PROVINCE.some((g) => selectedRegions.some((p) => p.code === g.code)) &&
                              !REGION_PROVINCE.every((g) => selectedRegions.some((p) => p.code === g.code))
                            }
                            sx={{ color: '#fff', '&.Mui-checked': { color: '#fff' }, '&.MuiCheckbox-indeterminate': { color: '#fff' }, p: 0.2, mr: 0.5 }}
                          />
                        }
                        label={<Typography sx={{ fontWeight: 700, fontSize: '0.85rem' }}>도</Typography>}
                      />
                    </Box>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_PROVINCE.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <Box
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              px: 0.9,
                              py: 0.25,
                              bgcolor: checked ? '#f0fdf4' : '#ffffff',
                              border: checked ? '1px solid #86efac' : '1px solid #e2e8f0',
                              borderRadius: 1,
                              cursor: 'pointer',
                              transition: 'all 0.1s ease',
                              '&:hover': { borderColor: '#86efac' }
                            }}
                          >
                            <Checkbox
                              size="small"
                              checked={checked}
                              sx={{ p: 0, mr: 0.4, color: '#94a3b8', '&.Mui-checked': { color: '#16a34a' } }}
                            />
                            <Typography sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#15803d' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </Box>
                        );
                      })}
                    </Box>
                  </Box>

                  {/* 3. 광역 카드 (블루 헤더 + 7개 광역시 하단 그리드) */}
                  <Box
                    sx={{
                      flex: 1,
                      minWidth: { xs: '100%', sm: '320px' },
                      border: '1px solid #bfdbfe',
                      borderRadius: 1.5,
                      overflow: 'hidden',
                      bgcolor: '#fff'
                    }}
                  >
                    <Box
                      sx={{
                        bgcolor: '#2563eb',
                        color: '#fff',
                        py: 0.5,
                        px: 1.5,
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        cursor: 'pointer',
                        '&:hover': { bgcolor: '#1d4ed8' }
                      }}
                      onClick={() => handleToggleRegionGroup(REGION_METRO)}
                    >
                      <FormControlLabel
                        sx={{ m: 0, pointerEvents: 'none' }}
                        control={
                          <Checkbox
                            size="small"
                            checked={REGION_METRO.every((g) => selectedRegions.some((p) => p.code === g.code))}
                            indeterminate={
                              REGION_METRO.some((g) => selectedRegions.some((p) => p.code === g.code)) &&
                              !REGION_METRO.every((g) => selectedRegions.some((p) => p.code === g.code))
                            }
                            sx={{ color: '#fff', '&.Mui-checked': { color: '#fff' }, '&.MuiCheckbox-indeterminate': { color: '#fff' }, p: 0.2, mr: 0.5 }}
                          />
                        }
                        label={<Typography sx={{ fontWeight: 700, fontSize: '0.85rem' }}>광역</Typography>}
                      />
                    </Box>
                    <Box sx={{ p: 0.8, display: 'flex', flexWrap: 'wrap', gap: 0.6, bgcolor: '#f8fafc' }}>
                      {REGION_METRO.map((region) => {
                        const checked = selectedRegions.some((r) => r.code === region.code);
                        return (
                          <Box
                            key={region.code}
                            onClick={() => handleToggleRegion(region)}
                            sx={{
                              display: 'inline-flex',
                              alignItems: 'center',
                              px: 0.9,
                              py: 0.25,
                              bgcolor: checked ? '#eff6ff' : '#ffffff',
                              border: checked ? '1px solid #93c5fd' : '1px solid #e2e8f0',
                              borderRadius: 1,
                              cursor: 'pointer',
                              transition: 'all 0.1s ease',
                              '&:hover': { borderColor: '#93c5fd' }
                            }}
                          >
                            <Checkbox
                              size="small"
                              checked={checked}
                              sx={{ p: 0, mr: 0.4, color: '#94a3b8', '&.Mui-checked': { color: '#2563eb' } }}
                            />
                            <Typography sx={{ fontSize: '0.8rem', fontWeight: checked ? 700 : 500, color: checked ? '#1e40af' : '#475569' }}>
                              {region.name}
                            </Typography>
                          </Box>
                        );
                      })}
                    </Box>
                  </Box>
                </Box>
                </Collapse>
              </Box>
            </Paper>
          </ThemeProvider>
        </Box>

        {/* 캘린더 단독 표시 */}
        <Box sx={{ mt: 0.5 }}>
          <SubscriptionCalendar
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
              {detailTabValue === 0 && renderDetailTabContent()}
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