import { useCallback, useEffect, useMemo, useState } from 'react';
import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useHistoryOverlay } from '@/hooks/useHistoryOverlay';
import { koreaToday } from '@/features/planner/plannerUtils';
import { DEFAULT_FILTERS, mergeSales, migrateStoredFilters, normalizeFilters, splitComplexes } from './priceMapRules';

const STORAGE_KEY = 'toady.priceMap';
/** 좌표 확인·기간 수집이 덜 끝났으면 몇 번만 다시 부른다(요청 제한을 아끼려고 횟수를 제한). */
const MAX_FOLLOW_UPS = 12;
/** 지도를 옮겨 더 불러온 시·군·구는 최근 6곳까지만 유지한다(오래된 지역부터 뺀다). */
const MAX_EXTRA_DISTRICTS = 6;
const REGION = /^[가-힣]{2,10}( [가-힣]{1,10}){0,2}$/;

function readFilters() {
  // 검색어는 공고마다 동네가 달라 기억하지 않는다.
  try { return normalizeFilters({ ...DEFAULT_FILTERS, ...migrateStoredFilters(JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}')), query: '' }); }
  catch { return { ...DEFAULT_FILTERS }; }
}
const district = ({ bcode, region }) => ({ bcode, region: REGION.test(region || '') ? region : undefined });

/**
 * 위치 탭 '주변 실거래가' 지도 상태: 서버 단지 목록, 필터(이 기기에 기억), 선택한 단지(뒤로가기로 닫힘).
 * 기본은 공고와 같은 시·군·구이고, 지도를 다른 지역으로 옮기면 addDistrict로 그 시·군·구도 함께 불러온다.
 * 정확한 공급 주소(법정동 코드)가 확인된 공고에서만 동작한다.
 */
export function usePriceMap(location, enabled, currentId = '') {
  const capabilities = useQuery({ queryKey: ['researchCapabilities'], queryFn: () => apiClient.get('/api/v1/research/capabilities'), staleTime: 300000, retry: 1, enabled });
  const bcode = location?.status === 'ADDRESS' && /^\d{10}$/.test(location?.bcode || '') ? location.bcode : '';
  const [filters, setFiltersState] = useState(readFilters);
  const setFilters = useCallback(patch => setFiltersState(current => normalizeFilters({ ...current, ...patch })), []);
  useEffect(() => { try { localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...filters, query: '', areaBasis: 'supply' })); } catch { /* 기억하지 못해도 지도는 쓸 수 있다 */ } }, [filters]);

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
  const queries = useQueries({
    queries: districts.map(item => ({
      // 서버는 시·군·구(앞 5자리) 단위로 묶으므로 키도 시·군·구로 둔다.
      queryKey: ['complexPrices', item.bcode.slice(0, 5), item.region || '', filters.months],
      queryFn: () => apiClient.get('/api/v1/research/complexes', { bcode: item.bcode, region: item.region, months: filters.months }),
      enabled: ready,
      staleTime: 600000,
      retry: false,
      placeholderData: keepPreviousData,
      refetchInterval: q => {
        const data = q.state.data;
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
  const query = queries[0] || { data: undefined, isLoading: false, isFetching: false, isError: false, refetch: () => {} };
  const others = queries.slice(1);
  const complexes = useMemo(() => {
    const seen = new Map();
    queries.forEach(item => (item.data?.complexes || []).forEach(complex => { if (!seen.has(complex.key)) seen.set(complex.key, complex); }));
    return [...seen.values()];
  }, [queries.map(item => item.dataUpdatedAt).join(','), districts.length]);
  const loadingOthers = others.some(item => item.isFetching);
  const pending = queries.reduce((sum, item) => sum + (item.data?.pending || 0), 0);

  const origin = location && Number.isFinite(location.latitude) ? { lat: location.latitude, lng: location.longitude } : null;
  const year = Number(koreaToday().slice(0, 4));
  // 조건 밖 단지(outside)도 함께 돌려줘 지도가 '거래 없음'과 '위치 미확인'을 구분해 보여 준다.
  const { rows, outside } = useMemo(() => splitComplexes(complexes, filters, { origin, year }), [complexes, filters, origin?.lat, origin?.lng, year]);
  const [selectedKey, setSelectedKey] = useHistoryOverlay('price-map-complex');
  const selectedSale = typeof selectedKey === 'string' && selectedKey.startsWith('sale:') ? sales.find(sale => `sale:${sale.id}` === selectedKey) || null : null;
  const selected = selectedKey && !selectedSale ? complexes.find(item => item.key === selectedKey) || null : null;
  const selectedRow = selected ? rows.find(row => row.key === selected.key) || outside.find(row => row.key === selected.key) || { ...selected, rep: null, distance: Infinity } : null;
  return { capabilities, available: Boolean(bcode), ready, query, filters, setFilters, rows, outside, origin, year, selected: selectedRow, select: setSelectedKey,
    addDistrict, districtCount: districts.length, unlocatedSales, districtNames, loadingOthers, pending, complexes, sales, selectedSale, selectedKey };
}
