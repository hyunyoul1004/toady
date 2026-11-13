import Citys from './Citys.js';
import { RCEPT_SE_META } from './calendar/calendarGroup.js';

// 청약 순위 텍스트 매핑
export function subscriptTypeName(item) {
  if (item.type === 'remaining' && item.houseSecdNm) {
    return item.houseSecdNm;
  }

  // 이름은 달력·필터와 같은 기준(RCEPT_SE_META)을 쓴다.
  const map = Object.fromEntries(Object.entries(RCEPT_SE_META).map(([code, meta]) => [code, meta.label]));
  return map[item.rceptSe] || '일반공급';
}

// 청약홈 표준 공급지역 코드 (SUBSCRPT_AREA_CODE) 정의
export const REGION_SEOUL = { code: '100', name: '서울', matchNames: ['서울', '서울특별시'] };

export const REGION_METRO = [
  { code: '500', name: '광주', matchNames: ['광주광역시', '광주'] },
  { code: '700', name: '대구', matchNames: ['대구광역시', '대구'] },
  { code: '300', name: '대전', matchNames: ['대전광역시', '대전'] },
  { code: '600', name: '부산', matchNames: ['부산광역시', '부산'] },
  { code: '338', name: '세종', matchNames: ['세종특별자치시', '세종'] },
  { code: '680', name: '울산', matchNames: ['울산광역시', '울산'] },
  { code: '400', name: '인천', matchNames: ['인천광역시', '인천'] }
];

export const REGION_PROVINCE = [
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

export const ALL_REGIONS = [REGION_SEOUL, ...REGION_METRO, ...REGION_PROVINCE];

/**
 * 공고가 선택한 공급지역에 속하는지. 공급지역 코드가 있으면 코드로만 판정한다.
 * 주소 글자 비교는 '경기도 광주시'를 광주광역시로 오인하므로 코드가 없을 때만, 주소의 첫 낱말(시·도, '서울시' 같은 줄임 포함)로만 비교한다.
 */
export function matchesRegions(item, selectedRegions) {
  const code = item.regionCode ? String(item.regionCode).trim() : '';
  const sido = String(item.supplyLocation || '').trim().split(/\s+/)[0] || '';
  return selectedRegions.some((reg) => (code ? code === reg.code : reg.matchNames.some((name) => sido.startsWith(name))));
}

// Citys.js의 계층형 데이터를 Autocomplete용 플랫(flat) 리스트로 변환 (시/도 + 시/군/구)
export const FLAT_CITY_OPTIONS = Citys.flatMap((c) => {
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

// 공급 유형 필터 칩 목록. 색·이름은 달력과 같은 RCEPT_SE_META를 그대로 쓴다(필터 색과 달력 일정 색이 어긋나지 않게).
export const SUPPLY_FILTER_ITEMS = Object.keys(RCEPT_SE_META).sort((a, b) => RCEPT_SE_META[a].order - RCEPT_SE_META[b].order)
  .map(id => ({ id, label: RCEPT_SE_META[id].label, color: RCEPT_SE_META[id].accent }));
