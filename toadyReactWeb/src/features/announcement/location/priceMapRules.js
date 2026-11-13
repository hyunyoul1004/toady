/**
 * 위치 탭 '주변 실거래가' 지도의 필터·표시 규칙(화면 없이 테스트한다). 금액 단위는 만원.
 * 서버(ComplexPriceService)가 단지별로 묶어 준 최근 신고 거래 안에서만 고르고 계산한다. 호가·시세 추정은 하지 않는다.
 */
export const PYEONG = 3.305785;
/**
 * 공급면적 ÷ 전용면적 추정 비율. 실거래 자료에는 전용면적만 있어 분양 광고의 '평형'(공급면적 기준)을 이 비율로 추정한다.
 * 전용 59㎡ ≈ 24평형, 84㎡ ≈ 34평형, 114㎡ ≈ 46평형. 단지마다 실제 공급면적과 1~2평 다를 수 있다.
 */
export const SUPPLY_RATIO = 1.33;
/** 전용면적(㎡) → 추정 공급 평형(정수). */
export const supplyPyeong = area => Math.round(Number(area) * SUPPLY_RATIO / PYEONG);
/** 추정 공급 평형 → 전용면적(㎡, 정수). 저장된 옛 '전용 평' 필터를 옮길 때 쓴다. */
export const areaForSupplyPyeong = pyeong => Math.round(Number(pyeong) * PYEONG / SUPPLY_RATIO);

export const PERIODS = [{ value: 3, label: '최근 3개월' }, { value: 6, label: '최근 6개월' }, { value: 12, label: '최근 1년' }];
/** 전용면적 구간(㎡, 반올림 값 기준). */
export const AREA_BANDS = [
  { value: 'all', label: '전체 면적', test: () => true },
  { value: 'small', label: '60㎡ 이하', test: area => area <= 60 },
  { value: 'mid', label: '60~85㎡', test: area => area > 60 && area <= 85 },
  { value: 'large', label: '85㎡ 초과', test: area => area > 85 }
];
export const PRICE_BANDS = [
  { value: 'all', label: '전체 가격', test: () => true },
  { value: 'u5', label: '5억 이하', test: price => price <= 50000 },
  { value: '5-10', label: '5~10억', test: price => price > 50000 && price <= 100000 },
  { value: '10-15', label: '10~15억', test: price => price > 100000 && price <= 150000 },
  { value: 'o15', label: '15억 초과', test: price => price > 150000 }
];
export const AGE_BANDS = [
  { value: 'all', label: '전체', years: null },
  { value: '5', label: '5년 이내(신축)', years: 5 },
  { value: '10', label: '10년 이내', years: 10 },
  { value: '20', label: '20년 이내', years: 20 }
];
export const PRICE_MODES = [{ value: 'latest', label: '거래가' }, { value: 'pyeong', label: '평당가' }];
/** 면적 표시 단위. 평은 전용면적에서 추정한 공급 평형(분양 광고의 '34평형'과 같은 기준)이다. 필터 값 자체는 항상 전용 ㎡로 저장한다. */
export const AREA_UNITS = [{ value: 'm2', label: '전용 ㎡' }, { value: 'pyeong', label: '평형(공급 추정)' }];
export const SORTS = [
  { value: 'distance', label: '가까운 순' }, { value: 'price', label: '높은 가격 순' },
  { value: 'recent', label: '최근 거래 순' }, { value: 'count', label: '거래 많은 순' }
];
/** 면적 범위 슬라이더(전용 ㎡). 양 끝(0·165)은 '최소'·'최대'로 제한 없음. */
export const AREA_RANGE = { min: 0, max: 165, marks: [0, 33, 66, 99, 132, 165] };
/** 평형 단위일 때 눈금: 양 끝과 20·30·40·50평형에 해당하는 전용 ㎡. */
export const AREA_MARKS_PYEONG = [0, ...[20, 30, 40, 50].map(areaForSupplyPyeong), 165];
/** 매매가 범위 슬라이더(만원, 500만 단위). 양 끝(0·40억)은 '최소'·'최대'로 제한 없음. */
export const PRICE_RANGE = { min: 0, max: 400000, step: 500, marks: [0, 100000, 200000, 300000, 400000] };
export const DEFAULT_FILTERS = { months: 12, areaMin: 0, areaMax: 165, priceMin: 0, priceMax: 400000, age: 'all', mode: 'latest', unit: 'm2', sort: 'distance', query: '' };

const pick = (list, value) => list.find(item => item.value === value) || list[0];
/** 면적 범위를 0~165 안의 정수로 맞추고, 최소가 최대보다 크면 바꾼다. */
function areaRange(min, max) {
  const clamp = (value, fallback) => (Number.isFinite(Number(value)) ? Math.min(AREA_RANGE.max, Math.max(AREA_RANGE.min, Math.round(Number(value)))) : fallback);
  const low = clamp(min, AREA_RANGE.min), high = clamp(max, AREA_RANGE.max);
  return low <= high ? { areaMin: low, areaMax: high } : { areaMin: high, areaMax: low };
}
/** 매매가 범위를 0~40억 안의 500만 단위로 맞춘다. */
function priceRange(min, max) {
  const clamp = (value, fallback) => (Number.isFinite(Number(value)) ? Math.min(PRICE_RANGE.max, Math.max(PRICE_RANGE.min, Math.round(Number(value) / PRICE_RANGE.step) * PRICE_RANGE.step)) : fallback);
  const low = clamp(min, PRICE_RANGE.min), high = clamp(max, PRICE_RANGE.max);
  return low <= high ? { priceMin: low, priceMax: high } : { priceMin: high, priceMax: low };
}
/** 슬라이더용 금액 글자: 290,000 → '29억', 29,000 → '2억 9,000만', 0 → '0'. */
export function rangePrice(manwon) {
  const value = Math.round(Number(manwon) || 0);
  if (value <= 0) return '0';
  const eok = Math.floor(value / 10000), rest = value % 10000;
  if (!eok) return `${rest.toLocaleString('ko-KR')}만`;
  return rest ? `${eok}억 ${rest.toLocaleString('ko-KR')}만` : `${eok}억`;
}
/** 매매가 범위 글자(네이버 방식): '전체', '~2억 9,000만', '5억~', '5억~10억'. */
export function priceRangeText(filters) {
  const { priceMin, priceMax } = priceRange(filters?.priceMin, filters?.priceMax);
  const low = priceMin > PRICE_RANGE.min, high = priceMax < PRICE_RANGE.max;
  if (!low && !high) return '전체';
  return `${low ? rangePrice(priceMin) : ''}~${high ? rangePrice(priceMax) : ''}`;
}
export const isPriceFiltered = filters => !(Number(filters?.priceMin) <= PRICE_RANGE.min && Number(filters?.priceMax) >= PRICE_RANGE.max);
/** 면적 조건: 'all'·'small' 같은 구간 이름 또는 { areaMin, areaMax } 범위. */
function areaMatcher(spec) {
  if (spec && typeof spec === 'object') {
    const { areaMin, areaMax } = areaRange(spec.areaMin, spec.areaMax);
    return area => (areaMin <= AREA_RANGE.min || area >= areaMin) && (areaMax >= AREA_RANGE.max || area <= areaMax);
  }
  return pick(AREA_BANDS, spec).test;
}
/** 면적 범위 글자: '전체', '66㎡~83㎡', '최소~25평', '20평~최대'. */
export function areaRangeText(filters, unit = 'm2') {
  const { areaMin, areaMax } = areaRange(filters?.areaMin, filters?.areaMax);
  if (areaMin <= AREA_RANGE.min && areaMax >= AREA_RANGE.max) return '전체';
  if (unit === 'pyeong') {
    // 추정 공급 평형: '32~50평형', '최소~25평형', '20평형~최대'.
    const low = areaMin <= AREA_RANGE.min ? '최소' : String(supplyPyeong(areaMin)), high = areaMax >= AREA_RANGE.max ? '최대' : `${supplyPyeong(areaMax)}평형`;
    return `${low}${high === '최대' && low !== '최소' ? '평형' : ''}~${high}`;
  }
  const low = areaMin <= AREA_RANGE.min ? '최소' : areaText(areaMin, unit), high = areaMax >= AREA_RANGE.max ? '최대' : areaText(areaMax, unit);
  return `${low}~${high}`;
}
/** 평형 단위 면적 필터에서 보여 주는 안내. */
export const PYEONG_HINT = `평형은 전용면적×${SUPPLY_RATIO}로 추정한 공급 평형이에요(전용 84㎡ ≈ 34평형, 59㎡ ≈ 24평형). 단지마다 실제 공급면적과 1~2평 다를 수 있어요.`;
/**
 * 저장된 필터 옮기기: 예전에는 평 단위 슬라이더가 '전용 평'이었다(전용 106㎡ = 32평).
 * 같은 숫자(32평)를 이제 공급 평형으로 읽도록 전용 ㎡ 값을 바꾼다. 양 끝(제한 없음)은 그대로 둔다. ㎡ 단위였으면 바꾸지 않는다.
 */
export function migrateStoredFilters(raw) {
  if (!raw || typeof raw !== 'object' || raw.areaBasis === 'supply' || raw.unit !== 'pyeong') return raw;
  const move = (value, edge) => (!Number.isFinite(Number(value)) || Number(value) <= AREA_RANGE.min || Number(value) >= AREA_RANGE.max ? value
    : Math.max(AREA_RANGE.min, Math.min(edge, areaForSupplyPyeong(Math.round(Number(value) / PYEONG)))));
  return { ...raw, areaMin: move(raw.areaMin, AREA_RANGE.max), areaMax: move(raw.areaMax, AREA_RANGE.max), areaBasis: 'supply' };
}
export const isAreaFiltered = filters => !(Number(filters?.areaMin) <= AREA_RANGE.min && Number(filters?.areaMax) >= AREA_RANGE.max);
/** 저장해 둔 필터를 지금 규칙에 맞는 값만 남겨 복원한다(옛 값·잘못된 값은 기본값). */
export function normalizeFilters(raw) {
  const value = raw && typeof raw === 'object' ? raw : {};
  return {
    // 거래 기간은 최근 1년으로 고정한다(거래가 드문 단지도 마지막 거래월 평균을 보여 주려고).
    months: 12,
    ...areaRange(value.areaMin, value.areaMax),
    ...priceRange(value.priceMin, value.priceMax),
    age: pick(AGE_BANDS, value.age).value,
    mode: pick(PRICE_MODES, value.mode).value,
    unit: pick(AREA_UNITS, value.unit).value,
    sort: pick(SORTS, value.sort).value,
    query: typeof value.query === 'string' ? value.query.slice(0, 30) : ''
  };
}

/** 지도 말풍선용 짧은 금액: 78,000 → '7.8억', 120,000 → '12억', 8,500 → '8,500만'. */
export function shortPrice(manwon) {
  const value = Math.round(Number(manwon) || 0);
  if (value <= 0) return '—';
  if (value < 10000) return `${value.toLocaleString('ko-KR')}만`;
  const eok = value / 10000;
  return `${eok >= 10 ? Math.round(eok * 10) / 10 : Math.round(eok * 10) / 10}억`.replace(/\.0억$/, '억');
}
/** 목록·상세용 금액: 78,500 → '7억 8,500만원', 120,000 → '12억원'. */
export function fullPrice(manwon) {
  const value = Math.round(Number(manwon) || 0);
  if (value <= 0) return '—';
  const eok = Math.floor(value / 10000), rest = value % 10000;
  if (!eok) return `${rest.toLocaleString('ko-KR')}만원`;
  return rest ? `${eok}억 ${rest.toLocaleString('ko-KR')}만원` : `${eok}억원`;
}
/** 면적 글자: ㎡는 전용면적 정수, 평은 추정 공급 평형(예: 84.9㎡ → '34평형'). */
export function areaText(area, unit = 'm2') {
  const value = Number(area);
  if (!Number.isFinite(value) || value <= 0) return '—';
  return unit === 'pyeong' ? `${supplyPyeong(value)}평형` : `${Math.round(value)}㎡`;
}
/** 면적 구간 이름(평 단위면 추정 공급 평형으로 바꿔 보여 준다). */
export function areaBandLabel(band, unit = 'm2') {
  if (unit !== 'pyeong') return band.label;
  return { all: '전체 면적', small: '24평형 이하', mid: '24~34평형', large: '34평형 초과' }[band.value] || band.label;
}
/** 3.3㎡(1평)당 가격(만원, 반올림). */
export function perPyeong(manwon, area) {
  return area > 0 ? Math.round(manwon / area * PYEONG) : 0;
}

/**
 * 고른 면적 구간에서 이 단지를 대표하는 값: 거래가 가장 많은 면적(같으면 최근 거래 면적)의 최근 거래.
 * 구간에 거래가 없으면 null(지도·목록에서 뺀다).
 */
export function representative(complex, areaSpec = 'all') {
  const test = areaMatcher(areaSpec);
  const areas = (complex?.areas || []).filter(item => test(item.area));
  if (!areas.length) return null;
  const best = [...areas].sort((a, b) => b.count - a.count || String(b.latestDate).localeCompare(String(a.latestDate)) || b.area - a.area)[0];
  // 대표 금액은 그 면적의 '마지막 거래가 있었던 달'의 평균이다(서버 monthAvg). 옛 응답이면 마지막 거래가로 대신한다.
  const price = monthPrice(best);
  return { ...best, lastTrade: best.latest, latest: price, pyeong: perPyeong(price, best.area), month: best.month || String(best.latestDate || '').slice(0, 7),
    monthCount: best.monthCount || 1, count: areas.reduce((sum, item) => sum + item.count, 0) };
}

/** 면적별 대표 금액: 마지막 거래월 평균(없으면 마지막 거래가). */
export function monthPrice(item) {
  return Number.isFinite(item?.monthAvg) && item.monthAvg > 0 ? item.monthAvg : item?.latest;
}
/** 마지막 거래월이 1년보다 오래됐는지(지도에서 흐리게 표시). today: 'YYYY-MM-DD'. */
export function isStaleMonth(month, today) {
  const [y, m] = String(month || '').split('-').map(Number), [ty, tm] = String(today || '').split('-').map(Number);
  if (!y || !m || !ty || !tm) return false;
  return (ty * 12 + tm) - (y * 12 + m) >= 12;
}
/** '2026-11' → '11월', 다른 해면 '25년 12월'. */
export function monthLabel(month, year = new Date().getFullYear()) {
  const [y, m] = String(month || '').split('-').map(Number);
  if (!y || !m) return '';
  return y === year ? `${m}월` : `${String(y).slice(2)}년 ${m}월`;
}
/** 두 좌표 사이 거리(m). */
export function distanceMeters(a, b) {
  if (![a?.lat, a?.lng, b?.lat, b?.lng].every(Number.isFinite)) return Infinity;
  const rad = Math.PI / 180, dLat = (b.lat - a.lat) * rad, dLng = (b.lng - a.lng) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}
export function distanceText(meters) {
  if (!Number.isFinite(meters)) return '';
  return meters < 1000 ? `${Math.round(meters / 10) * 10}m` : `${(meters / 1000).toFixed(1)}km`;
}

const compact = value => String(value || '').replace(/\s+/g, '').toLowerCase();

/**
 * 필터를 적용한 단지 목록. 각 항목에 rep(대표 거래)·distance(공고 위치에서의 거리)를 붙여 정렬한다.
 * year: 연식 기준 연도(한국 기준 올해).
 */
export function filterComplexes(complexes = [], filters = DEFAULT_FILTERS, options = {}) {
  return splitComplexes(complexes, filters, options).rows;
}

/**
 * 거래가 있는 단지를 조건에 맞는 단지(rows)와 조건 밖 단지(outside)로 나눈다. 이름 검색에 맞지 않는 단지는 어느 쪽에도 넣지 않는다.
 * outside 항목의 reason: 'area'(고른 면적의 거래 없음) · 'age'(연식 조건 밖) · 'price'(가격 조건 밖).
 * 지도는 조건 밖 단지를 작은 회색 점으로 보여 '거래가 없는 것'과 '위치를 찾지 못한 것'을 구분한다.
 */
export function splitComplexes(complexes = [], filters = DEFAULT_FILTERS, { origin = null, year = new Date().getFullYear() } = {}) {
  const f = normalizeFilters(filters);
  const age = pick(AGE_BANDS, f.age), query = compact(f.query);
  const priceOk = value => (f.priceMin <= PRICE_RANGE.min || value >= f.priceMin) && (f.priceMax >= PRICE_RANGE.max || value <= f.priceMax);
  const ageOk = row => !age.years || (/^\d{4}$/.test(row.buildYear || '') && year - Number(row.buildYear) <= age.years);
  const rows = [], outside = [];
  complexes.filter(complex => !query || compact(complex.name).includes(query) || compact(complex.dong).includes(query)).forEach(complex => {
    const row = { ...complex, rep: representative(complex, f), distance: distanceMeters(origin, complex) };
    const reason = !row.rep ? 'area' : !ageOk(row) ? 'age' : !priceOk(row.rep.latest) ? 'price' : null;
    if (reason) outside.push({ ...row, rep: null, reason }); else rows.push(row);
  });
  const sorters = {
    distance: (a, b) => a.distance - b.distance || b.rep.count - a.rep.count,
    price: (a, b) => (f.mode === 'pyeong' ? b.rep.pyeong - a.rep.pyeong : b.rep.latest - a.rep.latest),
    recent: (a, b) => String(b.rep.latestDate).localeCompare(String(a.rep.latestDate)),
    count: (a, b) => b.rep.count - a.rep.count
  };
  rows.sort(sorters[f.sort] || sorters.distance);
  outside.sort((a, b) => a.distance - b.distance);
  return { rows, outside };
}

const OUTSIDE_TEXT = { area: '고른 면적의 거래 없음', age: '준공 연식 조건 밖', price: '매매가 조건 밖' };
/** 조건 밖 단지 설명: '고른 면적의 거래 없음 · 거래 면적 59·84㎡'. */
export function outsideText(row, unit = 'm2') {
  const areas = [...new Set((row?.areas || []).map(item => (unit === 'pyeong' ? supplyPyeong(item.area) : Math.round(item.area))))].sort((a, b) => a - b);
  const traded = areas.length ? ` · 거래 면적 ${areas.join('·')}${unit === 'pyeong' ? '평형' : '㎡'}` : '';
  return `${OUTSIDE_TEXT[row?.reason] || '조건에 맞는 거래 없음'}${row?.reason === 'area' ? traded : ''}`;
}

/**
 * 지도 아래 상태 줄의 숫자: 조건에 맞는 단지, 그중 지도에 놓은 단지, 위치를 확인하지 못한 단지(지번 없음·주소 불일치), 조건 밖 단지.
 * pending(좌표 확인 중)은 위치 미확인과 따로 센다.
 */
export function mapSummary(rows = [], outside = []) {
  const placed = rows.filter(row => Number.isFinite(row.lat) && Number.isFinite(row.lng)).length;
  return { matched: rows.length, placed, unplaced: rows.length - placed, outside: outside.length,
    outsidePlaced: outside.filter(row => Number.isFinite(row.lat) && Number.isFinite(row.lng)).length };
}

/** 말풍선 글자: 가격과 보조 문구(면적 또는 '평당'). */
export function bubbleText(row, mode = 'latest', unit = 'm2', today = '') {
  if (!row?.rep) return { price: '—', sub: '' };
  // 1년 넘게 거래가 없던 단지는 언제 값인지 함께 적는다(예: '25.3월').
  const when = today && isStaleMonth(row.rep.month, today) ? ` · ${String(row.rep.month).slice(2, 4)}.${Number(String(row.rep.month).slice(5, 7))}월` : '';
  return mode === 'pyeong' ? { price: shortPrice(row.rep.pyeong), sub: (unit === 'pyeong' ? '평당' : '3.3㎡당') + when } : { price: shortPrice(row.rep.latest), sub: areaText(row.rep.area, unit) + when };
}
/** 준공 연차에 따른 말풍선 색 구분: new(5년 이내) · mid(10년 이내) · old · unknown. */
export function ageTone(buildYear, year = new Date().getFullYear()) {
  if (!/^\d{4}$/.test(buildYear || '')) return 'unknown';
  const age = year - Number(buildYear);
  return age <= 5 ? 'new' : age <= 10 ? 'mid' : 'old';
}

/** 공고의 같은 면적 구간 최고 공급금액(분양가) — 지도에서 주변 거래가와 나란히 보여 준다. */
export function supplyForBand(unitPrices = [], areaSpec = 'all') {
  const test = areaMatcher(areaSpec);
  const units = unitPrices.filter(unit => Number.isFinite(unit.amountManwon) && unit.amountManwon > 0 && unit.area >= 10 && test(Math.round(unit.area)));
  if (!units.length) return null;
  // 국민평형(84㎡)에 가장 가까운 주택형을 대표로 쓴다.
  const best = [...units].sort((a, b) => Math.abs(a.area - 84) - Math.abs(b.area - 84) || b.amountManwon - a.amountManwon)[0];
  return { area: Math.round(best.area), amount: best.amountManwon, pyeong: perPyeong(best.amountManwon, best.area),
    supplyArea: Number(best.supplyArea) >= 10 ? Number(best.supplyArea) : null, type: best.type || '' };
}
/** 분양 말풍선의 면적 글자: 평 단위면 실제 공급면적의 평형(없으면 추정), ㎡ 단위면 전용 ㎡. */
export function saleAreaText(supply, unit = 'm2') {
  if (!supply) return '';
  return unit === 'pyeong' && supply.supplyArea ? `${Math.round(supply.supplyArea / PYEONG)}평형` : areaText(supply.area, unit);
}

/** 걷기 약 4km/h(분당 67m), 자전거 약 16km/h(분당 267m) 기준의 대략적인 이동 시간(분, 최소 1분). */
export function travelMinutes(meters) {
  if (!Number.isFinite(meters) || meters <= 0) return { walk: 0, bike: 0 };
  return { walk: Math.max(1, Math.round(meters / 67)), bike: Math.max(1, Math.round(meters / 267)) };
}
/** 거리 재기 표시: 1km 미만은 m(정수), 이상은 km 소수 한 자리(10km 이상은 정수). */
export function measureText(meters) {
  if (!Number.isFinite(meters) || meters <= 0) return '0m';
  if (meters < 1000) return `${Math.round(meters)}m`;
  const km = meters / 1000;
  return `${km >= 10 ? Math.round(km) : km.toFixed(1)}km`.replace('.0km', 'km');
}
/** 반경 표시 단계(m). */
export const RADII = [500, 1000];
/** 원 위쪽 끝(북쪽) 좌표 — 반경 이름표 위치. */
export function northOf(point, meters) {
  return { lat: point.lat + meters / 111320, lng: point.lng };
}

/**
 * 검색어가 주소(동·리·가·로·길 + 번지)처럼 보이는지. 주소면 단지 이름 거르기에 쓰지 않고 '그 위치로 지도 이동'에 쓴다.
 * 예: '동패동 400-7', '운정로 123' → 주소 / '한울마을1단지', '센트럴 2차' → 단지 이름.
 */
export function isAddressLike(text) {
  return /(동|리|가|로|길)\s*(산\s*)?\d+(-\d+)?(\s|$)/.test(String(text || '').trim());
}


/**
 * 지도 분양 말풍선의 상태 이름. 앱의 다른 화면(관심 공고·준비 패널)과 같은 '접수 중·접수 예정·접수 종료'를 쓴다.
 * DONE은 접수를 마쳤고 아직 입주 전인 공고다('분양 완료'는 '다 팔림'으로 읽힐 수 있어 쓰지 않는다).
 * 지금 보고 있는 공고도 같은 규칙(saleStatusOf)으로 상태를 붙이고, '이 공고' 표시는 따로 단다.
 */
export const SALE_STATUS = { OPEN: '접수 중', UPCOMING: '접수 예정', DONE: '접수 종료' };
export const saleStatusLabel = status => SALE_STATUS[status] || '분양 공고';
const isoDay = value => { const digits = String(value || '').replace(/\D/g, ''); return digits.length >= 8 ? `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}` : ''; };
/** 접수 시작·마감일과 오늘('YYYY-MM-DD')로 상태를 정한다(서버 분류와 같은 규칙: 마감일이 없으면 시작일 하루). 날짜를 모르면 null. */
export function saleStatusOf(start, end, today) {
  const from = isoDay(start), to = isoDay(end) || from;
  if (!from || !today) return null;
  return today < from ? 'UPCOMING' : today <= to ? 'OPEN' : 'DONE';
}
/** '2029-03' → '29.3 입주 예정', 없으면 ''. */
export function moveInText(moveIn) {
  const [y, m] = String(moveIn || '').split('-').map(Number);
  return y && m ? `${String(y).slice(2)}.${m} 입주 예정` : '';
}
/**
 * 여러 시·군·구 응답의 분양 공고를 합친다. 지금 보고 있는 공고는 빼고, 같은 공고는 한 번만 둔다.
 * unlocated(지번으로 위치를 확정하지 못한 공고)도 같은 방식으로 합치되, 지도에 놓인 공고는 뺀다.
 */
export function mergeSales(responses = [], currentId = '') {
  const sales = new Map(), unlocated = new Map();
  responses.forEach(data => (data?.sales || []).forEach(sale => { if (sale.id !== currentId && !sales.has(sale.id)) sales.set(sale.id, sale); }));
  responses.forEach(data => (data?.unlocated || []).forEach(item => { if (item.id !== currentId && !sales.has(item.id) && !unlocated.has(item.id)) unlocated.set(item.id, item); }));
  return { sales: [...sales.values()], unlocated: [...unlocated.values()] };
}

/**
 * 지도 축소 단계별 표시 방식. 카카오 레벨이 클수록 넓게 본다.
 * single: 단지별 말풍선(레벨 5 이하) · dong: 법정동 묶음(6~8) · district: 시·군·구 묶음(9 이상).
 */
export const GROUP_LEVELS = { dong: 6, district: 9 };
export function groupTier(level) {
  return level >= GROUP_LEVELS.district ? 'district' : level >= GROUP_LEVELS.dong ? 'dong' : 'single';
}
/** 화면 범위(view: {swLat, swLng, neLat, neLng}) 안에 있는지. pad: 범위 바깥으로 더 볼 비율(옮길 때 빈 곳이 안 보이게). */
export function inView(point, view, pad = 0.3) {
  if (!view || !Number.isFinite(point?.lat) || !Number.isFinite(point?.lng)) return Boolean(!view && Number.isFinite(point?.lat));
  const dLat = (view.neLat - view.swLat) * pad, dLng = (view.neLng - view.swLng) * pad;
  return point.lat >= view.swLat - dLat && point.lat <= view.neLat + dLat && point.lng >= view.swLng - dLng && point.lng <= view.neLng + dLng;
}
const median = values => {
  const sorted = [...values].sort((a, b) => a - b), middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2);
};
/**
 * 지도에 놓인 단지(rows)를 법정동 또는 시·군·구로 묶는다. 단지 key의 앞부분(시군구코드|법정동)을 쓴다.
 * price: 묶음 안 단지 대표 가격(거래가 또는 평당가)의 중간값. lat·lng: 단지 좌표 평균. bounds: 누르면 이 범위로 확대한다.
 * names: 시군구코드(5자리) → 시·군·구 이름('파주시').
 */
export function groupRows(rows = [], tier = 'dong', { mode = 'latest', names = {} } = {}) {
  const groups = new Map();
  rows.filter(row => row.rep && Number.isFinite(row.lat) && Number.isFinite(row.lng)).forEach(row => {
    const [lawd5 = '', dong = ''] = String(row.key || '').split('|');
    const key = tier === 'district' ? lawd5 : `${lawd5}|${dong || row.dong}`;
    const label = tier === 'district' ? (names[lawd5] || '시·군·구') : String(dong || row.dong || '').split(' ').pop();
    if (!groups.has(key)) groups.set(key, { key, label, members: [] });
    groups.get(key).members.push(row);
  });
  return [...groups.values()].map(({ key, label, members }) => {
    const lats = members.map(row => row.lat), lngs = members.map(row => row.lng);
    return {
      key, label, count: members.length,
      price: median(members.map(row => (mode === 'pyeong' ? row.rep.pyeong : row.rep.latest))),
      lat: lats.reduce((a, b) => a + b, 0) / lats.length, lng: lngs.reduce((a, b) => a + b, 0) / lngs.length,
      bounds: { swLat: Math.min(...lats), swLng: Math.min(...lngs), neLat: Math.max(...lats), neLng: Math.max(...lngs) }
    };
  }).sort((a, b) => b.count - a.count || a.label.localeCompare(b.label));
}

/**
 * 분양 주택형 이름과 면적 글자. 평형은 모집공고의 실제 공급면적(supplyArea)으로 계산하고, 없을 때만 추정이라고 밝힌다.
 * 같은 84㎡라도 84.96㎡·84.50㎡처럼 전용을 소수 둘째 자리까지 써서 주택형을 구분한다(타입 문자 A·B가 있으면 붙인다).
 * areaUnit: 'm2'면 전용 ㎡가 앞, 'pyeong'이면 평형이 앞.
 */
export function unitAreaLabel(unit = {}, areaUnit = 'm2') {
  const area = Number(unit.area), supply = Number(unit.supplyArea);
  const letter = String(unit.type || '').trim().match(/[A-Za-z]+$/)?.[0] || '';
  const exclusive = Number.isFinite(area) && area > 0 ? `${area.toFixed(2).replace(/\.?0+$/, '')}㎡${letter ? ` ${letter}` : ''}` : (unit.label || '주택형');
  const pyeong = Number.isFinite(supply) && supply >= 10 ? `${Math.round(supply / PYEONG)}평형` : Number.isFinite(area) && area > 0 ? `약 ${supplyPyeong(area)}평형(추정)` : '';
  return areaUnit === 'pyeong' ? { main: pyeong || exclusive, sub: pyeong ? `전용 ${exclusive}` : '' } : { main: `전용 ${exclusive}`, sub: pyeong };
}

const lastWord = value => String(value || '').trim().split(/\s+/).pop();
const parcelOf = value => String(value || '').replace(/\s+/g, '').replace(/번지$/, '');
/**
 * 실거래 단지가 공고와 같은 필지인지: 법정동(마지막 단어)과 지번(본번-부번, 산 여부)이 같을 때만.
 * 무순위·잔여세대처럼 이미 지어진 단지의 공고에서 '이 단지 최근 거래'를 함께 보여 줄 때 쓴다. 근처라는 이유로 같다고 보지 않는다.
 * location: { dong, address: '경기 파주시 동패동 2295' }.
 */
export function sameParcel(complex, location) {
  if (!complex?.jibun || !location?.address) return false;
  // 주소의 마지막 지번과 그 앞 단어(동·리)를 쓴다('… 조리읍 봉일천리 산 12' → 봉일천리 / 산12).
  const words = String(location.address).trim().split(/\s+/);
  let jibun = words.pop();
  if (words[words.length - 1] === '산') jibun = words.pop() + jibun;
  const dong = words.pop();
  return /^(산)?\d+(-\d+)?$/.test(parcelOf(jibun)) && parcelOf(jibun) === parcelOf(complex.jibun) && lastWord(dong) === lastWord(complex.dong);
}
