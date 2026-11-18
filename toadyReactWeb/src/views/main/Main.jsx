import { detailCloseSteps, useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { useDirectDetailHistory } from '@/hooks/useDirectDetailHistory';
import { announcementShareUrl, announcementShareImage, shareLabel, scoreShareText } from '@/features/planner/shareUtils';
import { kakaoShareKey, preloadKakaoShare, shareToKakao } from '@/utils/kakaoShare';
/**
 * @file Main.jsx
 * @description 홈 화면(청약 일정 캘린더)과 공고 상세 패널을 함께 그리는 메인 컴포넌트.
 *
 * - 쓰이는 곳: routes/AppRoutes.jsx의 홈(/), 지역별 청약(/regions/:sido[/:district]), 공고 상세(/announcements/:type/:house/:notice).
 *   맞춤 청약·이번 주 청약·내 플래너·가점 계산 화면은 detailOnly=true로 써서 캘린더 없이 상세 패널(Drawer)만 그린다.
 * - 화면 구성: 검색·필터(AnnouncementFilters) → 오늘의 요약(TodayOverview) → 캘린더(SubscriptionCalendar),
 *   그리고 오른쪽에서 열리는 상세 패널(상세·경쟁률·위치·이야기 4개 탭).
 * - 데이터: 공고 목록·상세·가점은 React Query 훅(useAnnouncementsQuery 등, /api/v1/...)으로 받는다.
 *   생활권 목록은 /api/v1/living-areas를 직접 조회한다.
 * - 검색 조건(달력 월·보기 방식·지역·공급유형·검색어)은 localStorage(toady.*)에 저장해 메뉴를 다녀와도 복원한다.
 * - 상세 열기/닫기는 브라우저 히스토리와 연결된다. 상세 하나가 히스토리 한 칸이라 뒤로가기로 상세만 닫힌다.
 */
import { useState, useMemo, useEffect, useRef, lazy, Suspense } from 'react';
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
import SectionErrorBoundary from '@/components/error/SectionErrorBoundary';
import { DetailBodySkeleton, DetailTabSkeleton } from './components/DetailSkeletons';
import { ALL_REGIONS, FLAT_CITY_OPTIONS } from './announcementFilters';
import { normalizeSearchTargets, readSearchTargets } from '@/features/planner/searchState';
import AnnouncementFilters from './components/AnnouncementFilters';
import AnnouncementNotice from './components/AnnouncementNotice';
import { useAnnouncementSearch } from './hooks/useAnnouncementSearch';

import { updateAnnouncementSeo, resetDefaultSeo, updatePageSeo } from '@/utils/seo.util';
import { regionPath, regionTargetOf } from '@/features/explore/regionRules';
import { useAnnouncementsQuery, useAnnouncementDetailQuery, useAptScoresQuery } from '@/features/announcement/hooks/useAnnouncements';

import { createTheme, ThemeProvider } from '@mui/material/styles';
import Typography from '@mui/material/Typography';

import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
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

import './main.css';

// 상세의 경쟁률·위치(지도)·이야기 탭은 그 탭을 열 때 내려받는다(첫 화면 JS를 줄인다).
// React.lazy는 처음 그려질 때 import()로 파일을 따로 받아 온다. 받는 동안은 바깥 <Suspense>의 fallback이 보인다.
// subscriptComment는 default export가 없어서 { default: ... } 모양으로 바꿔 lazy에 넘긴다.
const SubscriptRate = lazy(() => import('@/views/main/subscriptRate'));
const Kakaomap = lazy(() => import('@/components/kakao/Kakaomap'));
const SubscriptComment = lazy(() => import('@/views/main/subscriptComment').then(module => ({ default: module.SubscriptComment })));

/**
 * 상세 탭 하나를 오류 경계·지연 로딩 표시로 감싼다. 한 탭이 실패해도 상세의 다른 탭과 사이트 전체는 그대로 쓴다.
 * - SectionErrorBoundary: 안쪽에서 렌더링 오류가 나면 title 안내만 보여 준다. resetKey가 바뀌면(다른 공고) 다시 그려 본다.
 * - Suspense: lazy 컴포넌트를 내려받는 동안 DetailTabSkeleton을 보여 준다.
 */
function DetailTab({ title, resetKey, children }) {
  return <SectionErrorBoundary title={title} resetKey={resetKey}><Suspense fallback={<DetailTabSkeleton />}>{children}</Suspense></SectionErrorBoundary>;
}

/** 무순위·잔여세대(06), 임의공급(07), 불법행위 재공급(08) */
const UNRANKED_RCEPT_SE=['06','07','08'];

/** 상단 필터 영역은 MUI 기본 테마(기본 글꼴·색)로 표시한다. */
const FILTER_THEME = createTheme();

/**
 * @param {boolean} detailOnly 다른 화면 위에 상세 패널만 띄울 때 true. 이때는 목록 조회·localStorage 저장을 하지 않는다.
 * @param {Function} onDetailClose detailOnly일 때 닫기 버튼을 누르면 부르는 함수(부모가 닫는 방법을 정한다).
 */
export const Main = ({detailOnly=false,onDetailClose}) => {
  // useSearchParams: 주소의 ?a=b 값을 읽고 바꾼다. setSearchParams는 주소를 바꾸므로 히스토리에도 반영된다.
  const [searchParams, setSearchParams] = useSearchParams();
  // 의존성 배열이 []이면 처음 한 번만 실행된다. 여기서는 effect가 함수를 돌려주므로(정리 함수),
  // 화면을 떠날 때(언마운트) 상세용으로 바꿔 둔 SEO 메타태그를 기본값으로 되돌린다.
  useEffect(()=>()=>resetDefaultSeo(),[]);
  // useParams: 라우트 경로의 :type/:house/:notice, :sido/:district 같은 조각 값을 읽는다.
  const route = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const routeDetailId = route.type ? `${route.type}/${route.house}/${route.notice}` : searchParams.get('subscriptSeq');
  // 지역별 청약 주소(/regions/경기도/파주시): 같은 주소에서 그 지역으로 거른 캘린더를 연다(검색 봇은 같은 주소에서 서버 HTML을 받는다).
  const regionTarget = useMemo(() => (detailOnly || !route.sido ? null : regionTargetOf(route.sido, route.district)), [detailOnly, route.sido, route.district]);
  const regionKey = regionTarget?.key || null;
  // 공유 링크로 상세 주소를 바로 열었을 때, 히스토리 앞에 홈('/')을 한 칸 끼워 넣는다.
  // 그래서 첫 뒤로가기가 사이트를 떠나지 않고 상세만 닫고 캘린더로 돌아온다.
  useDirectDetailHistory(!detailOnly && Boolean(routeDetailId), 'detailOverlay', '/');
  // useState에 함수를 넘기면 첫 렌더에서 한 번만 실행된다(매 렌더마다 localStorage를 읽지 않게).
  const [initialBrowse] = useState(() => initialBrowseState({getItem:key=>localStorage.getItem(key)}));
  const [calendarMonth,setCalendarMonth] = useState(initialBrowse.month);
  const [viewMode,setViewMode] = useState(initialBrowse.viewMode);
  // 다른 화면이 navigate(..., { state:{ browseView:'calendar' } })로 보내면 캘린더 보기로 바꾸고,
  // 그 표시를 location.state에서 지운다(replace라 히스토리가 늘지 않고, 뒤로/앞으로가기 때 다시 적용되지 않는다).
  useEffect(() => {
    if (location.state?.browseView !== 'calendar') return;
    setViewMode('calendar');
    const { browseView, ...state } = location.state;
    navigate(location.pathname + location.search + location.hash, { replace: true, state });
  }, [location.key, location.state, location.pathname, location.search, location.hash, navigate]);
  // 아래 저장 effect들은 의존성 배열의 값이 바뀔 때마다 localStorage에 기록한다.
  // 사생활 보호 모드 등에서 저장이 막혀도 화면이 깨지지 않게 try/catch로 감싼다.
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
  // 지금 공급유형이 '진입 링크로 임시 적용한 값'인지 표시한다. 화면에 보일 값이 아니라서 state 대신 ref를 쓴다
  // (ref는 바꿔도 다시 렌더링되지 않고, 다음 렌더까지 값이 유지된다).
  const supplyFromEntry = useRef(false);
  useEffect(() => { if(detailOnly || supplyFromEntry.current)return;try { localStorage.setItem('toady.supplyTypes',JSON.stringify(supplyTypesKey(paramInfo))); } catch {} },[paramInfo,detailOnly]);
  useEffect(() => { if(detailOnly)return;try { localStorage.setItem('toady.searchAreas',JSON.stringify(normalizeSearchTargets(paramInfo.citys))); } catch {} },[paramInfo.citys,detailOnly]);

  // 캘린더 특정 날짜로 강제 이동 요청 (전체 기간 아파트 선택, 생활권·모음 페이지 진입 시 사용).
  // { date, nonce } 객체로 두어 같은 날짜를 다시 요청해도 달력이 다시 이동하게 한다.
  const [jumpToDate, setJumpToDate] = useState(null);
  const requestJumpToDate = date => setJumpToDate(previous => ({ date, nonce: (previous?.nonce || 0) + 1 }));

  // 달력 월이 바뀌면 조회 기간(startStr~endStr)을 그 달로 맞춘다. 값이 같으면 이전 객체를 그대로 돌려줘 불필요한 재조회를 막는다.
  // 저장할 때 오늘 날짜(KST)도 같이 남긴다. 날이 바뀐 뒤 다시 오면 저장된 월 대신 이번 달로 여는 데 쓴다(browseState 참고).
  useEffect(() => {
    const range = monthRange(calendarMonth);
    setParamInfo(previous => previous.startStr === range.startStr && previous.endStr === range.endStr ? previous : {...previous,...range});
    if(detailOnly)return;
    try { localStorage.setItem('toady.calendarMonth',JSON.stringify(calendarMonth)); localStorage.setItem('toady.calendarMonthDay',JSON.stringify(koreaToday())); } catch {}
  },[calendarMonth,detailOnly]);
  useEffect(() => { const date=isoDate(jumpToDate?.date); if (date) setCalendarMonth(date.slice(0,7)); },[jumpToDate]);

  // 1. TanStack Query로 공고 목록 페칭
  // paramInfo가 queryKey에 들어가므로 조건이 바뀌면 자동으로 다시 조회한다. 두 번째 인자는 enabled(상세 전용이면 조회 안 함).
  // isPlaceholderData: 새 조건을 받는 동안 이전 결과를 잠시 보여 주고 있다는 뜻이다(로딩 표시에 쓴다).
  const {
    data: announcementsRes,
    isError: isListError,
    isPlaceholderData: isListPlaceholder,
    error: listError,
    refetch: refetchAnnouncements
  } = useAnnouncementsQuery(paramInfo,!detailOnly);

  // useMemo: 응답이 그대로면 같은 배열을 재사용한다. 매번 새 []가 만들어지면 이 값을 쓰는 아래 계산·effect가 괜히 다시 돈다.
  const rawSubscriptList = useMemo(() => {
    return announcementsRes?.data?.resultList || [];
  }, [announcementsRes]);

  const { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList } =
    useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions);

  // 생활권 공개 페이지(/areas/{slug})에서 넘어오면 같은 생활권 검색과 첫 접수월을 한 번만 적용하고 주소에서 지운다.
  // 새로고침·뒤로가기에서 사용자가 바꾼 검색 조건을 다시 덮어쓰지 않게 하기 위해서다.
  const areaSlug = detailOnly ? null : searchParams.get('area');
  // queryKey ['livingAreas']로 캐시를 공유하고, staleTime 1시간 동안은 다시 받지 않는다. enabled가 false면(?area= 없음) 아예 조회하지 않는다.
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

  // (위 effect들이 아래 함수를 먼저 부르지만 괜찮다. effect는 렌더가 끝난 뒤 실행되므로 그때는 이미 만들어져 있다.)
  // 진입 링크(지역별 청약·생활권·'캘린더에서 보기')로 조건을 비울 때는 이번 화면에만 적용하고,
  // 사용자가 저장해 둔 공급유형 선택은 덮어쓰지 않는다(사용자가 유형을 직접 바꾸거나 '초기화'를 누를 때만 저장).
  const resetForEntry = () => { resetBrowseFilters(); supplyFromEntry.current = true; };
  const resetBrowseFilters = () => {
    supplyFromEntry.current = false;
    setSelectedRegions(ALL_REGIONS);
    setSearchInputValue('');
    setParamInfo(previous=>({...previous,citys:[],...Object.fromEntries(Array.from({length:8},(_,index)=>[`rceptSe${String(index+1).padStart(2,'0')}`,'Y']))}));
  };

  // 지역 주소에서 사용자가 검색 지역을 바꾸면 주소가 화면과 어긋나지 않게 홈 주소로 바꾼다(히스토리를 쌓지 않음).
  // 아래 적용 효과보다 먼저 둔다: 지역을 처음 적용하는 렌더에서는 appliedRegion이 아직 비어 있어 이전 검색 조건으로 판단하지 않는다.
  // 마지막으로 적용한 지역 key. 렌더와 상관없는 기록이라 ref로 둔다.
  const appliedRegion = useRef(null);
  useEffect(() => {
    if (!regionKey || appliedRegion.current !== regionKey) return;
    const citys = paramInfo.citys || [];
    if (citys.length === 1 && citys[0]?.key === regionKey) return;
    navigate({ pathname:'/', search:location.search, hash:location.hash }, { replace:true, state:location.state });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramInfo.citys]);
  // 지역 주소로 들어오면(첫 진입·뒤로/앞으로가기·메뉴 왕복) 저장된 유형·지역 조건 없이 그 지역만 보여 준다(서버 공개 페이지와 같은 건수).
  // 같은 지역 위에 연 상세를 닫고 돌아올 때는 다시 적용하지 않아 그사이 바꾼 달력 월 등을 유지한다. 홈 주소로 가면 기록을 지운다.
  useEffect(() => {
    if (detailOnly) return;
    if (!route.sido) { if (location.pathname === '/') appliedRegion.current = null; return; }
    if (!regionTarget) { navigate('/regions', { replace:true }); return; }
    // 시·군·구로 확인되지 않는 조각(지구·신도시 이름)은 시·도 주소로 바꾼다(서버 공개 페이지는 이 주소를 404로 둔다).
    if (route.district && !regionTarget.districtName) { navigate(regionPath(regionTarget) + location.search, { replace:true, state:location.state }); return; }
    if (appliedRegion.current === regionKey) return;
    appliedRegion.current = regionKey;
    resetForEntry();
    setParamInfo(previous => ({ ...previous, citys: normalizeSearchTargets([regionTarget]) }));
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [regionKey, route.sido, route.district, location.pathname, detailOnly]);

  // 상세 패널 상태 및 탭 (상세 0, 경쟁률 1, 위치 2, 이야기 3)
  // researchTab은 '상세' 탭 안의 하위 탭(info: 공고 정보, trades: 동네 실거래 등)이다.
  const [modalOpen, setModalOpen] = useState(false);
  // 열람 기록·통계를 같은 공고에 두 번 남기지 않으려고 마지막으로 기록한 공고를 ref에 둔다.
  const lastViewed = useRef(null);
  const [researchTab,setResearchTab]=useState('info');
  const routeSeqRef=useRef(null);
  const detailScrollRef=useRef(null);
  // 상세 본문을 내리면 제목·탭만 남기고 상단(유형·주소·저장 버튼)을 접어 본문을 넓게 보여 준다.
  const [headerCompact,setHeaderCompact]=useState(false);
  const [detailTabValue, setDetailTabValue] = useState(0);
  // 위치 탭 지도의 전체 화면 여부. 열고 닫는 일은 지도 컴포넌트가 하고, 여기서는 값만 읽는다.
  // useHistoryOverlay는 팝업 상태를 location.state.overlayLayers에 두어 '팝업 하나 = 히스토리 한 칸'으로 만든다(뒤로가기로 닫힘).
  const [mapFullscreenLayer] = useHistoryOverlay('map-fullscreen');
  const mapFullscreen = Boolean(mapFullscreenLayer);
  const [selectedSubscriptSeq, setSelectedSubscriptSeq] = useState(null);
  useEffect(()=>{if(detailScrollRef.current) detailScrollRef.current.scrollTop=0; setHeaderCompact(false);},[researchTab,detailTabValue,selectedSubscriptSeq]);
  // DOM 요소 높이를 재려고 접히는 영역 3곳에 ref를 단다.
  const headerChipsRef=useRef(null), headerAddressRef=useRef(null), headerActionsRef=useRef(null);
  const collapsibleHeightRef=useRef(0);
  // 본문 스크롤에 따라 머리글을 접고 편다. 접힌 상태에서는 맨 위 근처(24px 이하)로 올라와야 다시 편다.
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

  // 2. TanStack Query로 공고 상세 정보 페칭 (모달 열릴 때만 수행: 두 번째 인자가 enabled)
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

  // 경쟁률 탭을 보고 있는 APT 공고일 때만 가점을 받아, 공유 문구에 넣을 요약(scoreText)을 만든다.
  const { data: shareScores } = useAptScoresQuery(selectedSubscriptSeq, modalOpen && detailTabValue===1 && selectedSubscriptSeq?.startsWith('apt/'));
  const scoreText = detailTabValue===1 ? scoreShareText(shareScores?.data || []) : '';

  // 공고 상세 열람 시 검색엔진 메타태그(SEO) 및 OpenGraph/Schema.org 동적 갱신
  useEffect(() => {
    if (modalOpen && currentSubscriptDetail?.houseNm) {
      updateAnnouncementSeo(currentSubscriptDetail,['detail','rate','map','comment'][detailTabValue],scoreText);
    } else if (!modalOpen && regionTarget) {
      // 지역 캘린더: 서버 공개 페이지와 같은 제목·대표 주소를 쓴다.
      updatePageSeo({ title:`${regionTarget.label} 청약 일정·분양 공고 · TOADY`, path:regionPath(regionTarget),
        description:`${regionTarget.label} 아파트·오피스텔·무순위 청약 공고를 캘린더로 확인하세요. 분양가·경쟁률·당첨가점까지 한 화면에서 볼 수 있어요.` });
    } else if (!modalOpen) {
      resetDefaultSeo();
    }
  }, [modalOpen, currentSubscriptDetail,detailTabValue,scoreText,regionTarget]);

  // 상세가 실제로 다 불러와졌을 때 한 번만 '최근 본 공고'에 저장하고 열람 통계를 보낸다.
  // 응답의 subscriptSeq가 지금 고른 공고와 같은지 확인해, 이전 공고 데이터가 남아 있는 순간에는 기록하지 않는다.
  useEffect(() => {
    if (!modalOpen) { lastViewed.current = null; return; }
    if (!isDetailLoading && !isDetailError && mainDetail.subscriptDetail?.houseNm
        && mainDetail.subscriptDetail.subscriptSeq === selectedSubscriptSeq && lastViewed.current !== selectedSubscriptSeq) {
      lastViewed.current = selectedSubscriptSeq;
      rememberViewed(mainDetail.subscriptDetail);
      trackAction('detail_view',selectedSubscriptSeq);
    }
  }, [modalOpen, isDetailLoading, isDetailError, mainDetail.subscriptDetail, selectedSubscriptSeq]);

  // 주소가 상세 상태의 기준이다. /announcements/:type/:house/:notice 또는 ?subscriptSeq=...(다른 메뉴 위 상세)가 있으면 상세를 열고,
  // 없으면 닫는다. 그래서 뒤로가기·앞으로가기로 주소만 바뀌어도 상세가 따라 열리고 닫힌다. ?tabType=은 보던 탭이다.
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

      // 'trades'는 상세 탭 안의 '동네 실거래'(새로고침·공유해도 보던 하위 탭이 유지되게 주소에 남긴다).
      const tabMap = { detail: 0, trades: 0, rate: 1, map: 2, comment: 3 };
      if (tabType && tabMap[tabType] !== undefined) {
        setDetailTabValue(tabMap[tabType]);
        if (tabType === 'trades') setResearchTab('trades');
        else if (tabType === 'detail') setResearchTab('info');
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
    // 상세 주소는 공유 링크와 같은 /announcements/…를 쓴다. 웹 nginx가 사람에게는 앱을, 검색·공유 봇에게는 서버 HTML을 준다.
    // 그래서 주소창을 복사해도 공유 버튼과 같은 링크가 되고, 새로고침해도 앱 상세가 그대로 열린다.
    // state.detailOverlay는 '앱 안에서 연 상세'라는 표시다. 닫을 때 뒤로가기로 닫을 수 있는지 판단하는 데 쓴다.
    navigate(`/announcements/${seq}`, {state:{detailOverlay:true}});
  };

  // 앱 안에서 연 상세면 뒤로가기로 닫는다. 상세 위에 쌓인 팝업 칸까지 detailCloseSteps가 세어 한 번에 걷어 낸다.
  // 직접 연 주소처럼 돌아갈 칸이 없으면 홈으로 바꾼다(replace라 히스토리가 늘지 않는다).
  const handleCloseDetail = () => {
    if (onDetailClose) { onDetailClose(); return; }
    if (location.state?.detailOverlay) navigate(-detailCloseSteps(location.state));
    else navigate('/', { replace: true });
  };

  // 다른 메뉴(맞춤 청약·가점 계산 등) 위에 연 상세도 보던 탭을 주소에 남긴다. 그래야 탭 안 팝업(이미지 저장 등)이 쌓은 히스토리와
  // 앞으로가기·새로고침 뒤 화면이 맞는다. 주소의 다른 값(src 등)은 그대로 두고 탭만 바꾼다(히스토리를 쌓지 않음).
  const writeTabType = tabType => {
    if (!selectedSubscriptSeq) return;
    const next = new URLSearchParams(searchParams);
    if (!route.type) next.set('subscriptSeq', selectedSubscriptSeq);
    next.set('tabType', tabType);
    setSearchParams(next, { replace: true, state: location.state });
  };
  // 위치 탭 지도에서 고른 단지·전체 화면 레이어(히스토리 칸)가 위에 있는 채로 다른 탭으로 가면, 그 칸들이 숨은 채 남아
  // 뒤로가기가 상세를 닫지 않고 위치 탭으로 돌아간다. 먼저 그 칸들을 뒤로가기로 걷어 낸 뒤 탭을 바꾼다.
  // (PriceMapParts의 범위 팝업 레이어 이름도 포함한다. 지도 파일을 첫 화면에 싣지 않으려고 이름만 같은 글자로 둔다.)
  const MAP_LAYERS = ['price-map-complex', 'map-fullscreen', 'price-map-range-price', 'price-map-range-area'];
  // 지도 칸을 걷어 내는 뒤로가기가 끝날 때까지 기다렸다가 주소에 쓸 탭 이름(렌더와 무관하니 ref).
  const pendingTab = useRef(null);
  const handleTabChange = (event, newValue) => {
    setDetailTabValue(newValue);
    const tabNames = [researchTab === 'trades' ? 'trades' : 'detail', 'rate', 'map', 'comment'];
    const layers = location.state?.overlayLayers || [];
    let mapLayers = 0;
    for (let i = layers.length - 1; i >= 0 && MAP_LAYERS.includes(layers[i].id); i--) mapLayers++;
    if (newValue !== 2 && mapLayers > 0) { pendingTab.current = tabNames[newValue]; navigate(-mapLayers); return; }
    writeTabType(tabNames[newValue]);
  };
  // 걷어 낸 뒤(뒤로가기 완료) 고른 탭을 주소에 남긴다.
  // navigate(-n)은 바로 끝나지 않으므로, 히스토리 이동마다 바뀌는 location.key를 의존성으로 두고 지도 칸이 다 사라졌는지 확인한다.
  useEffect(() => {
    if (!pendingTab.current) return;
    if ((location.state?.overlayLayers || []).some(layer => MAP_LAYERS.includes(layer.id))) return;
    const tab = pendingTab.current;
    pendingTab.current = null;
    writeTabType(tab);
  }, [location.key]); // eslint-disable-line react-hooks/exhaustive-deps
  const handleResearchTabChange = value => {
    setResearchTab(value);
    writeTabType(value === 'trades' ? 'trades' : 'detail');
  };

  // 공유 버튼: 카카오톡 키가 있으면 '카카오톡으로 보내기 / 링크 공유' 메뉴를 열고, 없으면 바로 기존 공유.
  // 청약은 가족과 단톡방에서 함께 보는 경우가 많아, 이미지 카드로 보내는 카카오톡 공유를 앞에 둔다.
  // 메뉴도 히스토리 레이어로 연다(뒤로가기로 메뉴만 닫히고, 상세를 닫았다 다른 공고를 열어도 메뉴가 남지 않게).
  const [shareOpen, setShareOpen] = useHistoryOverlay('share-menu');
  const shareButton = useRef(null);
  const closeShareMenu = () => { if (shareOpen) setShareOpen(false); };
  const openShare = () => { if (kakaoShareKey()) { preloadKakaoShare(); setShareOpen(true); } else void handleShare(); };
  const handleKakaoShare = () => {
    closeShareMenu();
    const seq = selectedSubscriptSeq || currentSubscriptDetail?.subscriptSeq || '';
    const tab = scoreText ? 'rate' : ['detail','rate','map','comment'][detailTabValue];
    const url = announcementShareUrl(seq, tab, window.location.origin);
    const houseNm = currentSubscriptDetail?.houseNm || '청약 공고';
    const place = currentSubscriptDetail?.supplyLocation || currentSubscriptDetail?.hssplyAdres || '';
    const date = currentSubscriptDetail?.subscriptDate || '';
    const description = [scoreText || (date ? `접수 ${date}` : ''), place].filter(Boolean).join(' · ');
    // SDK가 준비돼 있으면 누른 순간 바로 보낸다(기다리면 브라우저가 공유 창을 팝업으로 막는다). 준비 전이면 링크 공유로 넘긴다.
    const result = shareToKakao({ title: `${houseNm} ${scoreText ? '청약 결과' : shareLabel(tab)}`, description, imageUrl: announcementShareImage(seq, tab, window.location.origin), url });
    if (result === 'shared') trackAction('share', seq);
    else void handleShare();
  };

  // 공고 공유하기 (Web Share API 및 클립보드 복사 폴백)
  // 휴대폰 공유 창(navigator.share)을 먼저 쓰고, 없거나 실패하면 링크를 복사한다. 사용자가 공유 창을 닫으면(AbortError) 그냥 끝낸다.
  // 참고: 안쪽의 const location은 공급위치 문자열이며, 라우터의 location과 이름만 같다.
  const handleShare = async () => {
    closeShareMenu();
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

  return (
    <>
      {!detailOnly && <div>
        {/* 첫 화면: ① 검색 한 줄 → ② 오늘의 요약 → ③ 달력·목록 (소개는 처음 온 사람에게만 한 줄) */}
        <ServiceIntro />
        <Box sx={{ pt: 0.5 }}>
          <ThemeProvider theme={FILTER_THEME}>
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
        <TodayOverview onResetFilters={resetBrowseFilters} month={calendarMonth} onMonthChange={setCalendarMonth} isLoading={(announcementsRes == null || isListPlaceholder) && !isListError} isError={isListError} items={subscriptList} onOpen={handleOpenDetail} viewMode={viewMode} setViewMode={setViewMode}
          searchTargets={paramInfo.citys} onApplyAreas={targets => setParamInfo(previous => ({ ...previous, citys: normalizeSearchTargets(targets) }))} />
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
            listStatus={isListError ? 'error' : (announcementsRes == null || isListPlaceholder) ? 'loading' : 'ready'}
            handleOpen={(seq) => handleOpenDetail(seq)}
          />}
        </Box>
      </div>}

      {/* 우측 슬라이드 상세 패널 (Side Sheet Drawer). 바깥 클릭·Esc로 닫으면 onClose → handleCloseDetail(뒤로가기)로 처리한다. */}
      <Drawer
        anchor="right"
        open={modalOpen}
        onClose={handleCloseDetail}
        // 위치 탭 지도가 전체 화면이면 그 화면이 Esc·포커스를 맡는다(상세가 함께 닫히거나 검색창 입력을 막지 않게).
        disableEscapeKeyDown={mapFullscreen}
        disableEnforceFocus={mapFullscreen}
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
                    gap: 0.4,
                    // 긴 주소가 줄바꿈 없이 패널 밖으로 밀려 모바일에서 상세가 옆으로 밀리지 않게, 한 줄 폭 안에서 말줄임한다.
                    minWidth: 0,
                    maxWidth: '100%'
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
                ref={shareButton}
                onClick={openShare}
                aria-haspopup={kakaoShareKey() ? 'menu' : undefined}
                aria-expanded={kakaoShareKey() ? Boolean(shareOpen) : undefined}
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
            {/* 공유 메뉴: shareOpen은 히스토리 레이어 값이라 뒤로가기로도 닫힌다. */}
            <Menu anchorEl={shareButton.current} open={Boolean(shareOpen) && Boolean(shareButton.current)} onClose={closeShareMenu}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }} transformOrigin={{ vertical: 'top', horizontal: 'right' }}>
              <MenuItem onClick={handleKakaoShare} sx={{ fontWeight: 700, fontSize: '0.9rem' }}>카카오톡으로 보내기</MenuItem>
              <MenuItem onClick={handleShare} sx={{ fontSize: '0.9rem' }}>링크 공유·복사</MenuItem>
            </Menu>

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

        {/* 저장·알림 등 공고 액션 버튼. key를 공고 번호로 두어 다른 공고를 열면 안쪽 상태를 새로 시작한다. */}
        {!isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <Collapse in={!headerCompact} ref={headerActionsRef} timeout={150}><AnnouncementActions key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail}/></Collapse>}
        {/* 운영 안내: 관리자가 정정·취소를 알리거나 숨긴 공고. 탭과 상관없이 늘 보이게 머리글 아래에 둔다. */}
        {!isDetailLoading && !isDetailError && currentSubscriptDetail.operatorNotice && (currentSubscriptDetail.operatorNotice.hidden || currentSubscriptDetail.operatorNotice.message) &&
          <Alert severity={currentSubscriptDetail.operatorNotice.hidden ? 'warning' : 'info'} role="status" sx={{ mx: { xs: 2, sm: 2.5 }, mt: 1, borderRadius: 2, wordBreak: 'keep-all' }}>
            <strong>운영 안내</strong> · {currentSubscriptDetail.operatorNotice.message || '운영자가 확인 중인 공고라 목록과 검색에서 잠시 숨겼어요. 일정과 내용은 청약홈 원문에서 확인해 주세요.'}
          </Alert>}

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

        {/* '상세' 탭의 하위 탭(공고 정보·동네 실거래 등). 본문과 함께 스크롤되지 않게 스크롤 영역 밖에 둔다. */}
        {detailTabValue===0 && !isDetailLoading && !isDetailError && currentSubscriptDetail.subscriptSeq && <Box className="announcement-section-nav" sx={{px:{xs:2,sm:2.5},bgcolor:'#fff',flexShrink:0}}><ResearchTabs value={researchTab} onChange={handleResearchTabChange}/></Box>}

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
            <DetailBodySkeleton />
          ) : isDetailError ? (
            detailError?.status === 404 ? (
              <Box role="alert" sx={{ textAlign: 'center', py: 6, px: 2 }}>
                <Typography component="p" sx={{ fontWeight: 800, color: '#0f172a', fontSize: '1.05rem', mb: 0.75 }}>
                  마감됐거나 찾을 수 없는 공고예요
                </Typography>
                <Typography variant="body2" sx={{ color: '#64748b', mb: 2.5, wordBreak: 'keep-all' }}>
                  공고가 정정·취소됐거나 주소가 바뀌었을 수 있어요. 청약 일정에서 다른 공고를 찾아보세요.
                </Typography>
                <Button variant="contained" disableElevation onClick={handleCloseDetail}>공고 목록으로</Button>
              </Box>
            ) : (
              <Alert severity="error" action={<Button color="inherit" onClick={() => refetchDetail()}>다시 시도</Button>}>
                {detailError?.status >= 500 || !detailError?.status
                  ? '공고 상세를 잠시 불러오지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요.'
                  : '공고 상세를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'}
              </Alert>
            )
          ) : (
            <>
              {/* 지금 고른 탭 하나만 그린다. 경쟁률·위치·이야기는 lazy라 처음 고를 때 파일을 받는다. */}
              {detailTabValue === 0 && <DetailTab title="공고 상세를 표시하지 못했어요" resetKey={`${selectedSubscriptSeq}/${researchTab}`}><ResearchPanel key={currentSubscriptDetail.subscriptSeq} detail={currentSubscriptDetail} tab={researchTab} onTabChange={handleResearchTabChange} onShowMap={() => handleTabChange(null, 2)} showTabs={false} after={<AnnouncementNotice mainDetail={mainDetail} part="original" />}><AnnouncementNotice mainDetail={mainDetail} part="schedule" /></ResearchPanel></DetailTab>}
              {detailTabValue === 1 && currentSubscriptDetail && (
                <DetailTab title="경쟁률을 표시하지 못했어요" resetKey={selectedSubscriptSeq}>
                  <SubscriptRate subscriptDetail={currentSubscriptDetail} />
                </DetailTab>
              )}
              {detailTabValue === 2 && currentSubscriptDetail && (
                <DetailTab title="위치·지도를 표시하지 못했어요" resetKey={selectedSubscriptSeq}>
                  <Kakaomap subscriptDetail={currentSubscriptDetail} />
                </DetailTab>
              )}
              {detailTabValue === 3 && currentSubscriptDetail && (
                <DetailTab title="이야기를 표시하지 못했어요" resetKey={selectedSubscriptSeq}>
                  <SubscriptComment
                    subscriptDetail={currentSubscriptDetail}
                    subscriptSeq={selectedSubscriptSeq}
                  />
                </DetailTab>
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
