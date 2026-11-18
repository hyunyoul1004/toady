import { useCallback, useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { koreaToday } from '@/features/planner/plannerUtils';
import { DEFAULT_FILTERS, defaultAreaRange, isSameComplex, mergeSales, migrateStoredFilters, normalizeFilters, splitComplexes } from './priceMapRules';

// 가격·연식·표시 방식 같은 공통 필터를 이 기기(localStorage)에 기억하는 키.
const STORAGE_KEY = 'toady.priceMap';
/** 면적 범위는 공고마다 따로 기억한다(공고 id → { areaMin, areaMax }, 최근 40개). */
const AREA_KEY = 'toady.priceMap.area', MAX_AREA_MEMORY = 40;
/** 좌표 확인·기간 수집이 덜 끝났으면 몇 번만 다시 부른다(요청 제한을 아끼려고 횟수를 제한). */
const MAX_FOLLOW_UPS = 12;
/** 지도를 옮겨 더 불러온 시·군·구는 최근 6곳까지만 유지한다(오래된 지역부터 뺀다). */
const MAX_EXTRA_DISTRICTS = 6;
// 서버에 넘길 지역 이름('경기 파주시' 같은 한글 1~3단어)인지 확인한다. 이상한 값은 보내지 않는다.
const REGION = /^[가-힣]{2,10}( [가-힣]{1,10}){0,2}$/;

/** 저장된 공통 필터를 읽는다. 옛 형식은 옮기고(migrate) 잘못된 값은 기본값으로 맞춘다(normalize). */
function readFilters() {
  // 검색어는 공고마다 동네가 달라 기억하지 않는다.
  try { return normalizeFilters({ ...DEFAULT_FILTERS, ...migrateStoredFilters(JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')), query: '' }); }
  catch { return { ...DEFAULT_FILTERS }; }
}
/** 이 공고에서 사용자가 고른 면적 범위. 없으면 null(그때는 공고 주택형에 맞춘 기본 범위를 쓴다). */
function readArea(id) {
  try { const saved = JSON.parse(localStorage.getItem(AREA_KEY) || '{}')[id]; return saved && Number.isFinite(saved.areaMin) && Number.isFinite(saved.areaMax) ? saved : null; }
  catch { return null; }
}
/** 공고별 면적 범위를 저장한다. 이 공고를 맨 뒤로 옮기고 최근 40개만 남긴다. */
function saveArea(id, area) {
  try {
    const all = JSON.parse(localStorage.getItem(AREA_KEY) || '{}');
    delete all[id];
    const next = Object.fromEntries([...Object.entries(all), [id, area]].slice(-MAX_AREA_MEMORY));
    localStorage.setItem(AREA_KEY, JSON.stringify(next));
  } catch { /* 기억하지 못해도 지도는 쓸 수 있다 */ }
}
/** 시·군·구 하나를 나타내는 값 { bcode(법정동 코드 10자리), region }. */
const district = ({ bcode, region }) => ({ bcode, region: REGION.test(region || '') ? region : undefined });

/**
 * 위치 탭 '주변 실거래가' 지도 상태: 서버 단지 목록, 필터(이 기기에 기억), 선택한 단지(뒤로가기로 닫힘).
 * 기본은 공고와 같은 시·군·구이고, 지도를 다른 지역으로 옮기면 addDistrict로 그 시·군·구도 함께 불러온다.
 * 정확한 공급 주소(법정동 코드)가 확인된 공고에서만 동작한다.
 *
 * 데이터 흐름(큰 그림)
 * - 서버(/api/v1/research/complexes)가 국토교통부(MOLIT) 아파트 매매 실거래 신고 자료를 시·군·구 단위로 받아
 *   단지별로 묶어 준다. 단지 좌표는 서버가 지번으로 확인하며, 덜 끝났으면 pending·partial로 알려 준다.
 * - 이 훅은 그 결과를 필터·정렬(priceMapRules)해 rows / outside로 나누고, 지도 그리기는 Kakaomap.jsx가 카카오 지도로 한다.
 * - /api/v1/research/capabilities의 tradesEnabled가 true일 때만 요청한다(서버에 실거래 키가 없으면 끔).
 *
 * @param location useAnnouncementLocation 결과(또는 Kakaomap이 만든 AREA 위치). status·bcode·region·좌표를 쓴다.
 * @param enabled 위치 탭이 보일 때만 true
 * @param currentId 지금 공고 id('apt/…/…'). 면적 기억·이 공고 제외에 쓴다.
 * @param units 공고 주택형 목록(면적 기본 범위 계산용)
 * 사용처: components/kakao/Kakaomap.jsx
 */
export function usePriceMap(location, enabled, currentId = '', units = []) {
  // 서버가 실거래 조회를 켜 두었는지 묻는다. 5분 캐시라 지도를 여러 번 열어도 한 번만 묻는다.
  const capabilities = useQuery({ queryKey: ['researchCapabilities'], queryFn: () => apiClient.get('/api/v1/research/capabilities'), staleTime: 300000, retry: 1, enabled });
  // ADDRESS: 지번이 확정된 공고. AREA: 지구·블록 주소라 법정동(또는 시·군·구) 일대만 아는 공고(거리·같은 필지 계산은 하지 않는다).
  const bcode = ['ADDRESS', 'AREA'].includes(location?.status) && /^\d{10}$/.test(location?.bcode || '') ? location.bcode : '';
  // 가격·연식·표시 방식은 이 기기에 하나로 기억하고, 면적 범위는 공고마다 따로 둔다.
  // 처음 여는 공고는 그 공고 주택형 범위(areaDefault)로 시작한다(다른 공고에서 고른 면적이 따라오지 않게).
  // useState에 함수를 넘기면 첫 렌더에만 실행된다(localStorage를 매 렌더 읽지 않게).
  const [base, setBase] = useState(readFilters);
  // 검색어는 공고마다 동네가 달라 다른 공고로 바뀌면 비운다(지도가 다시 마운트되지 않고 공고만 바뀌는 경우에도 이전 검색어로 걸러지지 않게).
  useEffect(() => { setBase(current => (current.query ? { ...current, query: '' } : current)); }, [currentId]);
  // useMemo: 의존성 값이 같으면 이전 계산 결과를 그대로 쓴다. units 배열 자체 대신 면적 문자열을 의존성으로 둬,
  // 내용이 같은 새 배열이 와도 다시 계산하지 않는다.
  const areaDefault = useMemo(() => defaultAreaRange(units), [(units || []).map(unit => unit.area).join(',')]);
  // 공고 id와 함께 둬, 다른 공고로 바뀐 첫 렌더에서 이전 공고의 면적이 잠깐 쓰이지 않게 한다.
  const [areaState, setAreaState] = useState(() => ({ id: currentId, area: readArea(currentId) }));
  const userArea = areaState.id === currentId ? areaState.area : readArea(currentId);
  useEffect(() => { setAreaState(current => (current.id === currentId ? current : { id: currentId, area: readArea(currentId) })); }, [currentId]);
  const filters = useMemo(() => normalizeFilters({ ...base, ...(userArea || areaDefault) }), [base, userArea, areaDefault]);
  // useCallback: 함수 자체를 기억해 렌더마다 새 함수가 생기지 않게 한다(자식 컴포넌트·effect 의존성이 흔들리지 않게).
  // patch에 면적이 있으면 공고별 저장소에, 나머지는 공통 필터에 반영한다.
  const setFilters = useCallback(patch => {
    if ('areaMin' in patch || 'areaMax' in patch) {
      setAreaState(current => {
        const before = current.id === currentId ? current.area : readArea(currentId);
        const next = normalizeFilters({ ...(before || areaDefault), ...patch });
        const area = { areaMin: next.areaMin, areaMax: next.areaMax };
        if (currentId) saveArea(currentId, area);
        return { id: currentId, area };
      });
    }
    setBase(current => normalizeFilters({ ...current, ...patch }));
  }, [currentId, areaDefault]);
  // 공통 필터가 바뀔 때마다 이 기기에 저장한다(의존성 [base]: base가 바뀐 렌더 뒤에만 실행).
  useEffect(() => {
    // 면적은 공고별 저장소에 두므로 공통 저장값에는 넣지 않는다.
    const { areaMin, areaMax, ...rest } = base;
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...rest, query: '', areaBasis: 'supply' })); } catch { /* 기억하지 못해도 지도는 쓸 수 있다 */ }
  }, [base]);

  // 지도를 옮겨 더 불러온 시·군·구 목록. 공고(기준 bcode)가 바뀌면 비운다.
  const [extra, setExtra] = useState([]);
  useEffect(() => { setExtra([]); }, [bcode]);
  /** 지도에 보이는 다른 시·군·구를 더한다(법정동 코드 10자리). */
  const addDistrict = useCallback(next => {
    if (!/^\d{10}$/.test(next?.bcode || '')) return;
    setExtra(list => (next.bcode.slice(0, 5) === bcode.slice(0, 5) || list.some(item => item.bcode.slice(0, 5) === next.bcode.slice(0, 5))
      ? list : [...list, district(next)].slice(-MAX_EXTRA_DISTRICTS)));
  }, [bcode]);

  const ready = enabled && Boolean(bcode) && capabilities.data?.tradesEnabled === true;
  const districts = useMemo(() => (bcode ? [district({ bcode, region: location?.region }), ...extra] : []), [bcode, location?.region, extra]);
  // useQueries: 개수가 바뀌는 여러 쿼리를 한 번에 돌린다(기준 시·군·구 + 지도를 옮겨 더한 시·군·구).
  // staleTime·refetchInterval에 함수를 주면 응답 상태를 보고 캐시 시간·다시 부를 간격을 정할 수 있다.
  // placeholderData: keepPreviousData는 기간 등 키가 바뀌어 새로 받는 동안 이전 결과를 계속 보여 준다.
  const queries = useQueries({
    queries: districts.map(item => ({
      // 서버는 시·군·구(앞 5자리) 단위로 묶으므로 키도 시·군·구로 둔다.
      queryKey: ['complexPrices', item.bcode.slice(0, 5), item.region || '', filters.months],
      queryFn: () => apiClient.get('/api/v1/research/complexes', { bcode: item.bcode, region: item.region, months: filters.months }),
      enabled: ready,
      // 제공처 일시 실패(UNAVAILABLE)는 캐시하지 않고 20초 간격으로 8번(약 3분)까지 다시 묻는다(다시 시도 단추도 그대로 둔다).
      // 서버는 초당 한도(429)를 받으면 1분 쉬므로, 그 뒤에 저절로 채워지도록 그보다 길게 기다린다.
      staleTime: q => (q.state.data?.status === 'UNAVAILABLE' ? 0 : 600000),
      retry: false,
      placeholderData: keepPreviousData,
      refetchInterval: q => {
        const data = q.state.data;
        if (data?.status === 'UNAVAILABLE') return q.state.dataUpdateCount < 8 ? 20000 : false;
        // 좌표 확인 중(pending)이거나 기간 일부만 받은(partial) 응답이면 4초마다 다시 불러 지도를 채운다.
        return data && (data.pending > 0 || data.partial) && q.state.dataUpdateCount < MAX_FOLLOW_UPS ? 4000 : false;
      }
    }))
  });
  // 같은 시·군·구의 분양 공고(접수 중·예정·종료 말풍선). 지금 보고 있는 공고는 따로 그리므로 뺀다.
  const saleQueries = useQueries({
    queries: districts.map(item => ({
      queryKey: ['mapSales', item.bcode.slice(0, 5), item.region || ''],
      queryFn: () => apiClient.get('/api/v1/research/sales', { bcode: item.bcode, region: item.region }),
      enabled: ready,
      staleTime: 600000,
      retry: false,
      refetchInterval: q => (q.state.data?.pending > 0 && q.state.dataUpdateCount < MAX_FOLLOW_UPS ? 4000 : false)
    }))
  });
  // 접수 중·예정과 접수 종료(입주 전) 공고. 지번으로 위치를 확정하지 못한 공고는 지도에 놓지 않고 unlocatedSales로 따로 둔다.
  const { sales, unlocated: unlocatedSales } = useMemo(() => mergeSales(saleQueries.map(item => item.data), currentId),
    [saleQueries.map(item => item.dataUpdatedAt).join(','), currentId]);
  // 시·군·구 묶음 이름: 시군구코드(5자리) → '파주시'.
  const districtNames = useMemo(() => Object.fromEntries(districts.map(item => [item.bcode.slice(0, 5), String(item.region || '').split(' ').pop()])), [districts]);
  // 첫 쿼리(공고의 시·군·구)가 상태 줄의 기준이다. 위치가 없을 때도 화면이 깨지지 않게 빈 쿼리 모양을 둔다.
  const query = queries[0] || { data: undefined, isLoading: false, isFetching: false, isError: false, refetch: () => {} };
  const others = queries.slice(1);
  // 여러 시·군·구 응답을 합치되 같은 단지(key)는 한 번만 둔다.
  const complexes = useMemo(() => {
    const seen = new Map();
    queries.forEach(item => (item.data?.complexes || []).forEach(complex => { if (!seen.has(complex.key)) seen.set(complex.key, complex); }));
    return [...seen.values()];
  }, [queries.map(item => item.dataUpdatedAt).join(','), districts.length]);
  // 이 공고와 같은 필지의 실거래 단지(무순위·잔여세대 등 이미 지어진 단지). 지도에서는 이 공고 말풍선 하나로 합친다.
  const siteType = String(currentId).split('/')[0], siteYear = Number(koreaToday().slice(0, 4));
  const siteComplex = useMemo(() => complexes.find(complex => isSameComplex(complex, location, { type: siteType, year: siteYear })) || null, [complexes, location?.address, location?.dong, siteType, siteYear]);
  const loadingOthers = others.some(item => item.isFetching);
  // 옮긴 지역 중 제공처가 잠시 응답하지 않은 곳이 있으면(자동으로 다시 부르는 중) 지도에 짧게 알린다.
  const waitingOthers = others.some(item => item.data?.status === 'UNAVAILABLE' && !item.isFetching);
  const pending = queries.reduce((sum, item) => sum + (item.data?.pending || 0), 0);

  // 거리 계산 기준점은 정확한 주소(ADDRESS)일 때만 둔다. AREA(지구·블록 일대)에서는 거리를 보여 주지 않는다.
  const origin = location?.status === 'ADDRESS' && Number.isFinite(location.latitude) ? { lat: location.latitude, lng: location.longitude } : null;
  const year = Number(koreaToday().slice(0, 4));
  // 조건 밖 단지(outside)도 함께 돌려줘 지도가 '거래 없음'과 '위치 미확인'을 구분해 보여 준다.
  const { rows, outside } = useMemo(() => splitComplexes(complexes, filters, { origin, year }), [complexes, filters, origin?.lat, origin?.lng, year]);
  // 지도에서 고른 단지(또는 'sale:공고id')를 히스토리 레이어로 둔다. location.state.overlayLayers에 저장되므로
  // 뒤로가기를 누르면 상세가 아니라 고른 단지만 풀리고, 앞으로가기로 다시 골라진다.
  const [selectedKey, setSelectedKey] = useHistoryOverlay('price-map-complex');
  const selectedSale = typeof selectedKey === 'string' && selectedKey.startsWith('sale:') ? sales.find(sale => `sale:${sale.id}` === selectedKey) || null : null;
  const selected = selectedKey && !selectedSale ? complexes.find(item => item.key === selectedKey) || null : null;
  const selectedRow = selected ? rows.find(row => row.key === selected.key) || outside.find(row => row.key === selected.key) || { ...selected, rep: null, distance: Infinity } : null;
  return { capabilities, available: Boolean(bcode), ready, query, filters, setFilters, rows, outside, origin, year, selected: selectedRow, select: setSelectedKey,
    addDistrict, districtCount: districts.length, waitingOthers, unlocatedSales, districtNames, siteComplex, areaDefault, loadingOthers, pending, complexes, sales, selectedSale, selectedKey };
}
