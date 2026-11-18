/** 공고 주소 → 시·도/시·군·구 판정과 지역별 청약 묶기. 쓰는 곳: RegionsPage, views/main/Main.jsx, useAnnouncementSearch.js. */
import { isDistrictOf } from './districts.js';
import Citys from '../../views/main/Citys.js';

/**
 * 공고 주소 → 캘린더 검색과 같은 지역 목록(Citys)의 시·도/시·군·구.
 * 시·도는 주소 첫 낱말이 정확한 시·도 이름(또는 줄임말)이면 그것을, 아니면 공급지역명(region)을, 그래도 없으면 주소 앞 두 글자로 찾는다.
 * 시·군·구는 그 시·도의 이름 중 주소에 낱말 단위로 들어 있는 가장 긴 이름(예: '창원시 성산구'가 '창원시'보다 먼저)을 쓴다.
 * 공고별 예외 규칙은 두지 않는다.
 */
const SHORT = { 충청북도: '충북', 충청남도: '충남', 전북특별자치도: '전북', 전라북도: '전북', 전라남도: '전남', 경상북도: '경북', 경상남도: '경남' };
const shortOf = name => SHORT[name] || name.slice(0, 2);
// 줄임말('경기') → Citys 항목. 시·도를 빨리 찾기 위한 표.
const CITY_BY_SHORT = new Map(Citys.map(city => [shortOf(city.cityName), city]));

const headOf = text => String(text || '').trim().split(/\s+/)[0] || '';
/** 정확한 시·도 이름이거나 줄임말 그 자체('서울', '충북')일 때만 인정한다. */
function strictCity(head) {
  return Citys.find(city => city.cityName === head) || CITY_BY_SHORT.get(SHORT[head] || head) || null;
}
/** 공급지역명('경기', '서울특별시')은 앞 두 글자로도 찾는다. */
function looseCity(head) {
  return strictCity(head) || (head.length >= 2 ? CITY_BY_SHORT.get(head.slice(0, 2)) : null) || null;
}

/** 공고 주소(와 공급지역명) → { cityName, districtName }. 시·도를 모르면 null. */
export function regionOf(address, region) {
  // 주소 첫 낱말이 '광주시 오포읍'처럼 시·도가 아닌 시·군이면 앞 두 글자로 추정하지 않고 공급지역명을 먼저 본다.
  const city = strictCity(headOf(address)) || looseCity(headOf(region)) || looseCity(headOf(address));
  if (!city) return null;
  // 앞뒤에 공백을 붙여 낱말 단위로 비교한다(' 중구 '처럼). '강남구' 안의 '남구'가 걸리지 않게 한다.
  const words = ` ${String(address || '').replace(/\s+/g, ' ').trim()} `;
  const district = [...(city.districts || [])]
    .sort((a, b) => b.districtName.length - a.districtName.length)
    .find(item => item.districtName !== city.cityName && words.includes(` ${item.districtName} `));
  return { cityName: city.cityName, districtName: district?.districtName || null };
}

/**
 * 캘린더에서 열 달: 가장 이른 접수 시작 달. 캘린더는 접수 시작일이 그 달 조회 범위에 있는 공고만 가져오므로,
 * 지난달에 시작한 접수 중 공고도 시작 달로 열어야 보인다(서버 생활권 링크 PublicAreaPage.Groups.month와 같은 규칙).
 */
function firstMonth(entry, item) {
  const month = String(item.start || '').slice(0, 7);
  if (!/^\d{4}-\d{2}$/.test(month)) return;
  if (!entry.month || month < entry.month) entry.month = month;
}
/**
 * 접수 중·예정 공고를 시·도 → 시·군·구로 묶는다. 공고가 많은 시·도부터, 같은 수면 접수 중이 많은 순.
 * 시·군·구를 알 수 없는 공고는 시·도 건수에만 넣는다. 시·도도 모르면 other로 센다.
 */
export function groupByRegion(items = [], today = '') {
  const cities = new Map();
  let other = 0;
  for (const item of items) {
    const found = regionOf(item.address, item.region);
    if (!found) { other += 1; continue; }
    const city = cities.get(found.cityName) || { cityName: found.cityName, total: 0, open: 0, districts: new Map() };
    city.total += 1;
    if (item.status === 'OPEN') city.open += 1;
    firstMonth(city, item);
    if (found.districtName) {
      const district = city.districts.get(found.districtName) || { districtName: found.districtName, total: 0, open: 0 };
      district.total += 1;
      if (item.status === 'OPEN') district.open += 1;
      firstMonth(district, item);
      city.districts.set(found.districtName, district);
    }
    cities.set(found.cityName, city);
  }
  const order = (a, b) => b.total - a.total || b.open - a.open;
  const list = [...cities.values()].map(city => ({ ...city, districts: [...city.districts.values()].sort((a, b) => order(a, b) || a.districtName.localeCompare(b.districtName, 'ko')) }))
    .sort((a, b) => order(a, b) || a.cityName.localeCompare(b.cityName, 'ko'));
  return { cities: list, other };
}

/** 캘린더 검색어(검색 목록의 label과 같은 형식): 시·도만이면 '경기도', 시·군·구까지면 '경기도 파주시'. */
export function searchLabel(cityName, districtName) {
  return districtName ? `${cityName} ${districtName}` : cityName;
}

/** 예전 이름·흔한 표기(서버 Regions.ALIASES와 같은 목록). '광주시'는 경기 광주시와 헷갈리므로 넣지 않는다. */
const CITY_ALIASES = { 서울시: '서울', 부산시: '부산', 대구시: '대구', 인천시: '인천', 대전시: '대전', 울산시: '울산', 세종시: '세종', 강원도: '강원', 전라북도: '전북', 제주도: '제주' };
/**
 * 지역별 청약 주소(/regions/{시·도}[/{시·군·구}])의 조각 → 캘린더 검색 대상(검색 목록 FLAT_CITY_OPTIONS와 같은 모양).
 * 서버 공개 페이지(Regions.java)와 같은 기준: 시·도는 정확한 이름·줄임말·예전 이름만, 시·군·구는 그 시·도의 이름으로만 거른다.
 * 검색 목록(Citys)에 없는 시·군·구도 글자 검색이 아니라 같은 시·도 안의 낱말 비교(matchesRegionTarget)로 거른다.
 * 시·도를 모르면 null, 그 시·도의 실제 시·군·구(districts.js, 서버와 같은 목록)가 아니면 시·도만 돌려준다.
 */
export function regionTargetOf(sidoSegment, districtSegment) {
  const head = String(sidoSegment || '').trim();
  const city = head && (strictCity(head) || strictCity(CITY_ALIASES[head] || ''));
  if (!city) return null;
  const district = String(districtSegment || '').trim();
  // 실제 시·군·구 이름만 받는다. '검단신도시'·'평택고덕국제화지구' 같은 지구·신도시 이름이면 시·도만 돌려준다.
  if (district && isDistrictOf(city.cityName, district)) {
    const label = searchLabel(city.cityName, district);
    return { type: 'district', key: label, label, cityName: city.cityName, districtName: district };
  }
  return { type: 'city', key: city.cityName, label: city.cityName, cityName: city.cityName };
}
/** 검색 대상의 지역별 청약 주소('/regions/경기도/파주시', 각 조각은 퍼센트 인코딩). */
export function regionPath(target) {
  if (!target?.cityName) return '/regions';
  return `/regions/${encodeURIComponent(target.cityName)}${target.districtName ? `/${encodeURIComponent(target.districtName)}` : ''}`;
}

// 괄호·쉼표를 공백으로 바꾼 뒤 낱말 단위로 들어 있는지 본다.
const hasWord = (text, word) => ` ${String(text || '').replace(/[(),]/g, ' ').replace(/\s+/g, ' ').trim()} `.includes(` ${word} `);
/**
 * 캘린더 검색의 시·도/시·군·구 대상과 공고가 맞는지. 글자 포함이 아니라 같은 시·도 안의 낱말 단위로 비교한다
 * ('부산 남구'가 '서울 강남구'에, '인천 중구'가 '서울 중구'에 걸리지 않게). 지역별 청약 건수와 같은 규칙(regionOf)이다.
 */
export function matchesRegionTarget(item, target) {
  const address = item.hssplyAdres || '', location = item.supplyLocation || '';
  const found = regionOf(address, item.regionName || location) || regionOf(location, item.regionName);
  const sameCity = found ? found.cityName === target.cityName : hasWord(`${location} ${address}`, target.cityName);
  if (!target.districtName) return sameCity;
  if (!sameCity) return false;
  // 일반구가 있는 시는 검색 목록에서 '청주시 상당구'처럼 구까지 붙어 나온다. 시 단위 주소(/regions/충청북도/청주시)는 그 구들도 포함한다.
  if (found?.districtName) return found.districtName === target.districtName || found.districtName.startsWith(`${target.districtName} `);
  return hasWord(location, target.districtName) || hasWord(address, target.districtName);
}
