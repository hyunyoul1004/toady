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
  return { area: Math.round(best.area), amount: best.amountManwon, pyeong: perPyeong(best.amountManwon, best.area) };
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

/** 분양가와 나란히 보여 줄 주변 거래 범위(m)와 같은 면적으로 보는 차이(㎡). */
export const NEARBY_METERS = 1500, SAME_AREA = 3;
/**
 * 공고 위치 반경 안 단지들의 같은 면적(±3㎡) 최근 거래가 중간값. 판정 없이 숫자만 준다.
 * 단지마다 그 면적의 '최근 거래' 한 건만 쓴다(거래 많은 단지가 값을 끌고 가지 않게). 2곳 미만이면 null.
 */
export function nearbyMedian(complexes = [], area, origin, meters = NEARBY_METERS) {
  if (!Number.isFinite(area) || !origin) return null;
  const values = complexes
    .filter(complex => distanceMeters(origin, complex) <= meters)
    .map(complex => (complex.areas || []).filter(item => Math.abs(item.area - area) <= SAME_AREA).sort((a, b) => String(b.latestDate).localeCompare(String(a.latestDate)))[0])
    .filter(Boolean).map(monthPrice).sort((a, b) => a - b);
  if (values.length < 2) return null;
  const middle = Math.floor(values.length / 2);
  return { median: values.length % 2 ? values[middle] : Math.round((values[middle - 1] + values[middle]) / 2), complexes: values.length };
}

/** 지도 분양 말풍선의 상태 이름. OPEN: 청약 중, UPCOMING: 청약 예정, DONE: 접수를 마쳤고 아직 입주 전(실거래 없음). */
export const SALE_STATUS = { OPEN: '청약 중', UPCOMING: '청약 예정', DONE: '분양 완료' };
export const saleStatusLabel = status => SALE_STATUS[status] || '분양';
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
