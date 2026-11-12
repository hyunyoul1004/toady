import Citys from '../../views/main/Citys.js';

/**
 * 공고 주소 → 캘린더 검색과 같은 지역 목록(Citys)의 시·도/시·군·구.
 * 시·도는 주소 첫 낱말이 정확한 시·도 이름(또는 줄임말)이면 그것을, 아니면 공급지역명(region)을, 그래도 없으면 주소 앞 두 글자로 찾는다.
 * 시·군·구는 그 시·도의 이름 중 주소에 낱말 단위로 들어 있는 가장 긴 이름(예: '창원시 성산구'가 '창원시'보다 먼저)을 쓴다.
 * 공고별 예외 규칙은 두지 않는다.
 */
const SHORT = { 충청북도: '충북', 충청남도: '충남', 전북특별자치도: '전북', 전라북도: '전북', 전라남도: '전남', 경상북도: '경북', 경상남도: '경남' };
const shortOf = name => SHORT[name] || name.slice(0, 2);
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

export function regionOf(address, region) {
  // 주소 첫 낱말이 '광주시 오포읍'처럼 시·도가 아닌 시·군이면 앞 두 글자로 추정하지 않고 공급지역명을 먼저 본다.
  const city = strictCity(headOf(address)) || looseCity(headOf(region)) || looseCity(headOf(address));
  if (!city) return null;
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
  if (found?.districtName) return found.districtName === target.districtName;
  return hasWord(location, target.districtName) || hasWord(address, target.districtName);
}
