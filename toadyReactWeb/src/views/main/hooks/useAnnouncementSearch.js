import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { matchesArea } from '@/features/planner/plannerUtils';
import announcementApi from '@/features/announcement/api/announcementApi';
import { ALL_REGIONS, FLAT_CITY_OPTIONS, matchesRegions } from '../announcementFilters';
import { matchesRegionTarget } from '@/features/explore/regionRules';
import { trackSearch } from '@/features/analytics/trackSearch';

/**
 * 홈 화면의 검색창 상태와 '화면에 보여 줄 공고 목록'을 만드는 커스텀 훅.
 * - 쓰이는 곳: Main.jsx. 반환값은 AnnouncementFilters(검색창)와 TodayOverview·캘린더(목록)로 넘어간다.
 * - 하는 일: ① 검색창 입력값 보관 ② 자동완성 후보 만들기(생활권 + 시·도/시·군·구 + 이번 달 단지 + 전체 기간 공고)
 *   ③ 서버에서 받은 이번 달 공고(rawSubscriptList)를 공급유형·공급지역·검색 조건으로 걸러 subscriptList로 돌려준다.
 * - API: /api/v1/living-areas(생활권, React Query 캐시를 Main과 같은 key로 공유), 공고 자동완성(announcementApi.getAnnouncementSuggestions).
 * 걸러 내기는 서버가 아니라 브라우저에서 한다. 이번 달 목록은 이미 받아 왔으므로 조건을 바꿔도 다시 조회하지 않는다.
 *
 * @param {Array} rawSubscriptList 서버에서 받은 이번 달 공고 목록
 * @param {Object} paramInfo 공급유형(rceptSe01~08: 'Y'/'N')·조회 기간·검색 대상(citys)
 * @param {Array} selectedRegions 선택한 공급지역(ALL_REGIONS 중 일부)
 */
export function useAnnouncementSearch(rawSubscriptList, paramInfo, selectedRegions) {
  // queryKey가 Main.jsx의 생활권 조회와 같아서 한 번만 받아 함께 쓴다. staleTime 1시간 동안은 다시 받지 않는다.
  const { data: livingAreas = [] } = useQuery({ queryKey:['livingAreas'], queryFn:() => apiClient.get('/api/v1/living-areas'), staleTime:3600000 });
  // 검색창 입력 텍스트 및 전체 기간 공고 자동완성 목록
  const [searchInputValue, setSearchInputValue] = useState('');
  const [globalSuggestions, setGlobalSuggestions] = useState([]);

  // 검색창 입력 시 300ms 디바운스로 백엔드 전체 기간 공고 검색 API 호출
  // 디바운스: 글자를 칠 때마다 부르지 않고 입력이 300ms 멈췄을 때만 부른다.
  // 정리 함수(return)는 다음 입력이 들어오거나 화면을 떠날 때 실행된다. 타이머를 지우고 active=false로 바꿔,
  // 늦게 도착한 이전 검색어의 응답이 최신 후보를 덮어쓰지 않게 한다.
  useEffect(() => {
    let active = true;
    const keyword = searchInputValue.trim();
    if (!keyword || keyword.length < 2) {
      setGlobalSuggestions([]);
      return;
    }

    let settleTimer = null;
    const timer = setTimeout(async () => {
      try {
        const res = await announcementApi.getAnnouncementSuggestions({ keyword, limit: 10 });
        if (active) {
          setGlobalSuggestions(res.data || []);
          // 입력을 2초 더 멈추면 확정된 검색어로 보고 통계에 센다(결과 0건 검색어는 생활권·데이터 보강 근거).
          settleTimer = setTimeout(() => { if (active) trackSearch(keyword, (res.data || []).length); }, 2000);
        }
      } catch (err) {
        if (active) setGlobalSuggestions([]);
        console.error('[GlobalSearch] 자동완성 검색 실패:', err);
      }
    }, 300);

    return () => {
      active = false;
      clearTimeout(timer);
      clearTimeout(settleTimer);
    };
  }, [searchInputValue]);

  // 시/도, 구/군 행정구역 옵션 + 현재 조회된 공고들의 아파트 단지명 + 백엔드 전체 기간 검색 결과 동적 통합
  // useMemo: 의존성(목록·자동완성·생활권)이 바뀔 때만 다시 계산한다. 검색창에 글자만 쳐서 렌더될 때는 이전 결과를 재사용한다.
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

    // 0순위: 생활권(예: 운정)
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
  // 검색 대상(citys)이 여러 개면 그중 하나라도 맞으면 남긴다(OR).
  const subscriptList = useMemo(() => {
    if (!rawSubscriptList || rawSubscriptList.length === 0) return [];
    let list = rawSubscriptList.filter((item) => paramInfo[`rceptSe${item.rceptSe}`] === 'Y');

    // 1. 공급지역 체크박스 필터링 (전체 선택이 아닌 일부 선택일 때만 필터 적용)
    if (selectedRegions.length > 0 && selectedRegions.length < ALL_REGIONS.length) {
      list = list.filter((item) => matchesRegions(item, selectedRegions));
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

          // C. 구/군 검색인 경우 (예: '경기도 파주시')
          // D. 시/도 검색인 경우 (예: '경기도')
          // C·D. 지역 검색은 지역 판정(regionOf, 서버 Regions.of와 같은 규칙)만 쓴다. 지역별 청약 건수와 같은 공고가 보이게
          // 단지명 글자로는 맞추지 않는다(단지명 검색은 B·자유 입력이 따로 맡는다).
          if (target.cityName) return matchesRegionTarget(item, target);
          const query = target.label || '';
          return supplyLoc.includes(query) || hssplyAdres.includes(query) || houseNm.includes(query);
        })
      );
    }

    return list;
  }, [rawSubscriptList, paramInfo, selectedRegions, livingAreas]);

  return { searchInputValue, setSearchInputValue, allSearchOptions, subscriptList };
}
