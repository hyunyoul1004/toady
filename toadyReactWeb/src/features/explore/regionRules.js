/** 공고 주소 → 시·도/시·군·구 판정과 지역별 청약 묶기. 쓰는 곳: RegionsPage, views/main/Main.jsx, useAnnouncementSearch.js, announcementFilters.js. */
import { SIDOS, SIDO_ALIASES, canonicalDistrict, districtOf } from './districts.js';

/**
 * 서버 Regions.of(주소, 공급지역명)와 같은 규칙(같은 주소 샘플로 양쪽을 대조한다: toadyWebApi/src/test/resources/region-samples.tsv).
 * - 괄호 안 글자는 지운 뒤 낱말로 나눈다.
 * - 첫 낱말이 정확한 시·도 이름·줄임말·예전 이름이면 그 시·도, 시·군·구는 두 번째 낱말이 그 시·도의 실제 시·군·구(districts.js)일 때만.
 *   '창원시 성산구'는 창원시, '화성특례시'는 화성시, '검단신도시' 같은 지구 이름은 시·군·구로 보지 않는다.
 * - '전남광주통합특별시 북구'처럼 통합특별시 표기는 다음 낱말로 옛 시·도(광주광역시/전라남도)를 정한다.
 * - 첫 낱말이 지구·사업 이름이면 괄호 안 정식 주소로 다시 판정한다.
 * - 그래도 아니면('광주시 오포읍') 공급지역명으로 시·도를 정하고 첫 낱말을 시·군·구로 본다. 그래도 모르면 null(미확인).
 * 공고별 예외 규칙은 두지 않는다.
 */
const SIDO_BY_SHORT = new Map(SIDOS.map(sido => [sido.shortName, sido]));
const headOf = text => String(text || '').trim().split(/\s+/)[0] || '';
/** 정확한 시·도 이름·줄임말·예전 이름일 때만 그 시·도(서버 Regions.strict). */
function strictSido(token) {
  const text = String(token || '').trim();
  if (!text) return null;
  return SIDOS.find(sido => sido.name === text) || SIDO_BY_SHORT.get(SIDO_ALIASES[text] || text) || null;
}
// '충청북도'·'경상남도'처럼 줄임말로 시작하지 않는 이름은 앞부분으로 따로 맞춘다(서버 Regions.loose).
const LOOSE_PREFIX = [['충청북', '충북'], ['충청남', '충남'], ['전라북', '전북'], ['전라남', '전남'], ['경상북', '경북'], ['경상남', '경남']];
/** 공급지역명('경기', '서울특별시')처럼 시·도 분류 값은 앞부분으로도 찾는다. */
function looseSido(token) {
  const exact = strictSido(token);
  if (exact) return exact;
  const text = String(token || '').trim();
  const prefix = LOOSE_PREFIX.find(([head]) => text.startsWith(head));
  if (prefix) return strictSido(prefix[1]);
  return text.length < 2 ? null : SIDOS.find(sido => text.startsWith(sido.shortName)) || null;
}
/** 시·도 정식 이름 → 줄임말('경기도' → '경기'). 공급지역 체크박스(시·도 단위)가 쓴다. */
export const sidoShortName = cityName => SIDOS.find(sido => sido.name === cityName)?.shortName || null;

const MERGED_SUFFIX = '통합특별시';
/** '전남광주통합특별시'처럼 시·도 줄임말을 이어 붙인 통합특별시 이름 → 옛 시·도 목록(서버 Regions.merged). 아니면 빈 목록. */
export function mergedSidos(token) {
  const text = String(token || '').trim();
  if (!text.endsWith(MERGED_SUFFIX)) return [];
  let rest = text.slice(0, -MERGED_SUFFIX.length);
  const found = [];
  while (rest) {
    const next = SIDOS.find(sido => rest.startsWith(sido.shortName));
    if (!next) return [];
    found.push(next);
    rest = rest.slice(next.shortName.length);
  }
  return found.length >= 2 ? found : [];
}
const words = text => { const list = String(text ?? '').trim().split(/\s+/).filter(Boolean); return list.length ? list : ['']; };
/** 첫 낱말이 시·도(또는 통합특별시)인 주소만 판정한다(서버 Regions.headed). 아니면 null. */
function headed(tokens, region) {
  const [head, next] = tokens;
  const sido = strictSido(head);
  if (sido) return { cityName: sido.name, districtName: next ? districtOf(sido.name, next) : null };
  const merged = mergedSidos(head);
  if (!merged.length) return null;
  // 통합특별시: 다음 낱말이 어느 옛 시·도의 시·군·구인지로 정한다(광주 자치구와 전남 시·군은 이름이 겹치지 않는다).
  for (const candidate of merged) {
    const districtName = next ? districtOf(candidate.name, next) : null;
    if (districtName) return { cityName: candidate.name, districtName };
  }
  const fromRegion = region == null ? null : looseSido(headOf(region));
  return fromRegion && merged.includes(fromRegion) ? { cityName: fromRegion.name, districtName: null } : null;
}
/**
 * 공고 주소(와 공급지역명) → { cityName, districtName }. 시·도를 모르면 null.
 * 순서(서버 Regions.of와 같음): ① 주소 첫 낱말(시·도·통합특별시) ② 괄호 안 정식 주소('김포 풍무역세권 C5블록 (경기도 김포시 …)') ③ 공급지역명.
 */
export function regionOf(address, region) {
  const text = String(address ?? '');
  const outside = words(text.replace(/\([^)]*\)/g, ' '));
  const found = headed(outside, region);
  // 시·도까지만 정해졌으면('인천 검단신도시 AB13블록 (인천광역시 서구 …)') 괄호 안 정식 주소에서 같은 시·도의 시·군·구를 찾는다.
  if (found && found.districtName) return found;
  for (const [, inner] of text.matchAll(/\(([^)]*)\)/g)) {
    const nested = headed(words(inner), region);
    if (!nested) continue;
    if (!found) return nested;
    if (nested.cityName === found.cityName && nested.districtName) return nested;
  }
  if (found) return found;
  // 주소가 시·군으로 시작하면(예: '광주시 오포읍') 공급지역명으로 시·도를 정하고 첫 낱말을 시·군·구로 쓴다.
  const head = outside[0];
  // 통합특별시인데 옛 시·도를 못 정했으면 다른 시·도로 확정하지 않는다(공급지역명이 통합 대상이 아니면 미확인).
  if (mergedSidos(head).length) return null;
  const fromRegion = region == null ? null : looseSido(headOf(region));
  return fromRegion ? { cityName: fromRegion.name, districtName: districtOf(fromRegion.name, head) } : null;
}

/**
 * 캘린더에서 열 달: 가장 이른 접수 시작 달(서버 생활권 링크 PublicAreaPage.Groups.month와 같은 규칙).
 * 접수 중 공고가 시작일 줄(진한 줄)로 보이는 달을 연다. 이번 달에는 연한 '접수 중' 줄로 이어서 보인다.
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

/**
 * 지역별 청약 주소(/regions/{시·도}[/{시·군·구}])의 조각 → 캘린더 검색 대상(검색 목록 FLAT_CITY_OPTIONS와 같은 모양).
 * 서버 공개 페이지(Regions.java)와 같은 기준: 시·도는 정확한 이름·줄임말·예전 이름만, 시·군·구는 그 시·도의 실제 이름(특례시 표기는 '시')으로만 거른다.
 * 시·도를 모르면 null, 그 시·도의 실제 시·군·구(districts.js, 서버와 같은 목록)가 아니면 시·도만 돌려준다.
 */
export function regionTargetOf(sidoSegment, districtSegment) {
  const sido = strictSido(sidoSegment);
  if (!sido) return null;
  // 실제 시·군·구 이름만 받는다. '검단신도시'·'평택고덕국제화지구' 같은 지구·신도시 이름이면 시·도만 돌려준다.
  const district = districtOf(sido.name, districtSegment);
  if (district) {
    const label = searchLabel(sido.name, district);
    return { type: 'district', key: label, label, cityName: sido.name, districtName: district };
  }
  return { type: 'city', key: sido.name, label: sido.name, cityName: sido.name };
}
/** 검색 대상의 지역별 청약 주소('/regions/경기도/파주시', 각 조각은 퍼센트 인코딩). */
export function regionPath(target) {
  if (!target?.cityName) return '/regions';
  return `/regions/${encodeURIComponent(target.cityName)}${target.districtName ? `/${encodeURIComponent(target.districtName)}` : ''}`;
}

// 주소에 낱말 단위로 들어 있는지 본다. 괄호는 공백으로 바꿔 '고양시(덕양구)'의 구 이름도 찾는다.
const hasWord = (text, word) => ` ${String(text || '').replace(/[()]/g, ' ').replace(/,/g, ' ').replace(/\s+/g, ' ').trim()} `.includes(` ${word} `);
/**
 * 캘린더 검색의 시·도/시·군·구 대상과 공고가 맞는지. 지역별 청약 건수·서버 공개 페이지와 같은 판정(regionOf = Regions.of(주소, 공급지역명))만 쓴다.
 * 글자 포함·단지명으로는 맞추지 않는다('부산 남구'가 '서울 강남구'에, 단지명에 지역 이름이 든 다른 지역 공고가 걸리지 않게).
 * 시·군·구를 확인할 수 없는 공고는 시·도 검색에서만 보인다(서버도 시·도 페이지에만 둔다).
 * 검색 목록(Citys)의 일반구 항목('청주시 상당구')은 판정된 시(청주시)가 같고 주소에 그 구 낱말이 있을 때만 맞는다.
 */
export function matchesRegionTarget(item, target) {
  if (!target?.cityName) return false;
  const found = regionOf(item.hssplyAdres ?? item.address, item.regionName ?? item.region);
  if (!found || found.cityName !== target.cityName) return false;
  // 검색 목록의 '세종특별자치시 세종특별자치시'처럼 시·도와 같은 이름의 항목은 시·도 검색으로 본다.
  if (!target.districtName || target.districtName === target.cityName) return true;
  if (!found.districtName) return false;
  if (found.districtName === target.districtName) return true;
  const [cityPart, ...rest] = String(target.districtName).split(/\s+/);
  return rest.length > 0 && found.districtName === canonicalDistrict(cityPart) && hasWord(item.hssplyAdres ?? item.address, rest.join(' '));
}
