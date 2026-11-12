import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { matchesArea } from '@/features/planner/plannerUtils';
import announcementApi from '@/features/announcement/api/announcementApi';
import { ALL_REGIONS, FLAT_CITY_OPTIONS } from '../announcementFilters';

export function useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions) {
  const { data: livingAreas = [] } = useQuery({ queryKey:['livingAreas'], queryFn:() => apiClient.get('/api/v1/living-areas'), staleTime:3600000 });
  // 검색창 입력 텍스트 및 전체 기간 공고 자동완성 목록
  const [searchInputValue, setSearchInputValue] = useState('');
  const [globalSuggestions, setGlobalSuggestions] = useState([]);

  // 검색창 입력 시 300ms 디바운스로 백엔드 전체 기간 공고 검색 API 호출
  useEffect(() => {
    let active = true;
    const keyword = searchInputValue.trim();
    if (!keyword || keyword.length < 2) {
      setGlobalSuggestions([]);
      return;
    }

    const timer = setTimeout(async () => {
      try {
        const res = await announcementApi.getAnnouncementSuggestions({ keyword, limit: 10 });
        if (active) setGlobalSuggestions(res.data || []);
      } catch (err) {
        if (active) setGlobalSuggestions([]);
        console.error('[GlobalSearch] 자동완성 검색 실패:', err);
      }
    }, 300);

    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, [searchInputValue]);

  // 시/도, 구/군 행정구역 옵션 + 현재 조회된 공고들의 아파트 단지명 + 백엔드 전체 기간 검색 결과 동적 통합
  const allSearchOptions = useMemo(() => {
    // 1. 백엔드 전체 기간 검색 결과 (다른 년/월 아파트 포함)
    const globalOptions = (globalSuggestions || []).map((item) => {
      const type = item.type || 'apt';
      const houseManageNo = item.houseManageNo;
      const pblancNo = item.pblancNo || item.houseManageNo;
      const subscriptSeq = `${type}/${houseManageNo}/${pblancNo}`;
      const rceptYearMonth = item.rceptBgnde ? item.rceptBgnde.substring(0, 7) : '';
      const rceptLabel = rceptYearMonth ? `[${rceptYearMonth} 청약]` : '';
      const addressLabel = item.hssplyAdres || item.subscrptAreaCodeNm || '';

      return {
        key: `global_${subscriptSeq}_${item.houseNm}`,
        type: 'global_announcement',
        label: item.houseNm,
        subText: `${rceptLabel} ${addressLabel}`,
        houseNm: item.houseNm,
        subscriptSeq,
        rceptBgnde: item.rceptBgnde,
        item,
        searchText: `${item.houseNm} ${addressLabel} ${rceptYearMonth}`
      };
    });

    // 2. 현재 월에 조회된 공고들의 아파트 단지명
    const currentMonthApts = new Map();
    rawSubscriptList.forEach((item) => {
      const houseNm = item.houseNm ? item.houseNm.trim() : '';
      if (houseNm && !currentMonthApts.has(houseNm)) {
        const subAddress = item.hssplyAdres || item.supplyLocation || '';
        currentMonthApts.set(houseNm, {
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

    // 1순위: 지역 (시/도, 구/군)
    // 2순위: 현재 월에 표시되는 아파트
    // 3순위: 과거 및 전체 기간 공고 (해당 월로 이동)
    return [
      ...livingAreas.map(area => ({ ...area, key:`area_${area.label}`, type:'living_area', subText:area.description, searchText:[...area.aliases,...area.terms].join(' ') })),
      ...FLAT_CITY_OPTIONS,
      ...Array.from(currentMonthApts.values()),
      ...globalOptions
    ];
  }, [rawSubscriptList, globalSuggestions, livingAreas]);

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
            return matchesArea(`${houseNm} ${supplyLoc} ${hssplyAdres}`, target, livingAreas);
          }

          if (target.type === 'living_area') return matchesArea(`${houseNm} ${supplyLoc} ${hssplyAdres}`, target, livingAreas);

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
  }, [rawSubscriptList, paramInfo, selectedRegions, livingAreas]);

  return { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList };
}
