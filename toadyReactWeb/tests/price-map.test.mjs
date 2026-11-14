import test from 'node:test';
import assert from 'node:assert/strict';
import {
  shortPrice, fullPrice, perPyeong, representative, filterComplexes, normalizeFilters, bubbleText, ageTone, supplyForBand, distanceMeters, distanceText
, travelMinutes, measureText, northOf, isAddressLike, areaText, areaBandLabel, AREA_BANDS, areaRangeText, isAreaFiltered, priceRangeText, isPriceFiltered, rangePrice, isStaleMonth, monthPrice, monthLabel, splitComplexes, outsideText, mapSummary, PYEONG_HINT, supplyPyeong, areaForSupplyPyeong, migrateStoredFilters, AREA_MARKS_PYEONG, saleStatusLabel, saleStatusOf, moveInText, mergeSales, groupTier, groupRows, inView, GROUP_LEVELS, unitAreaLabel, sameParcel, saleAreaText, supplyForBand as supplyBand, displayTier, defaultAreaRange, DENSE_IN_VIEW, nearbyText, isSameComplex, groupSaleUnits, saleGroupText
} from '../src/features/announcement/location/priceMapRules.js';

const complex = (name, buildYear, areas, lat = 37.72, lng = 126.75, dong = '동패동') => ({ key: name, name, dong, jibun: '1', buildYear, lat, lng, count: areas.reduce((s, a) => s + a.count, 0), areas });
const area = (value, latest, count, latestDate = '2026-10-01') => ({ area: value, latest, count, latestDate, latestFloor: 10, median: latest, min: latest, max: latest });

test('금액 표기: 지도용 짧은 표기와 목록용 전체 표기', () => {
  assert.equal(shortPrice(78000), '7.8억');
  assert.equal(shortPrice(120000), '12억');
  assert.equal(shortPrice(125400), '12.5억');
  assert.equal(shortPrice(8500), '8,500만');
  assert.equal(shortPrice(0), '—');
  assert.equal(fullPrice(78500), '7억 8,500만원');
  assert.equal(fullPrice(120000), '12억원');
  assert.equal(fullPrice(9800), '9,800만원');
  assert.equal(perPyeong(80000, 84), Math.round(80000 / 84 * 3.305785));
});

test('대표 거래는 고른 면적 구간에서 거래가 가장 많은 면적의 최근 거래이고, 구간에 거래가 없으면 뺀다', () => {
  const c = complex('가', '2020', [area(59, 52000, 1), area(84, 78000, 3), area(114, 110000, 2)]);
  assert.equal(representative(c, 'all').area, 84);
  assert.equal(representative(c, 'all').count, 6, '구간 안 전체 거래 수');
  assert.equal(representative(c, 'small').latest, 52000);
  assert.equal(representative(c, 'large').area, 114);
  assert.equal(representative(complex('나', '2020', [area(59, 1, 1)]), 'mid'), null);
});

test('필터: 면적·가격대·연식·이름 검색을 함께 적용하고 정렬한다', () => {
  const origin = { lat: 37.72, lng: 126.75 };
  const list = [
    complex('새 아파트', '2023', [area(84, 98000, 2, '2026-10-20')], 37.721, 126.751),
    complex('구축 단지', '2001', [area(84, 45000, 5, '2026-09-01')], 37.73, 126.76),
    complex('대형 단지', '2015', [area(134, 160000, 1, '2026-08-01')], 37.70, 126.74)
  ];
  const year = 2026;
  assert.deepEqual(filterComplexes(list, { areaMin: 60, areaMax: 85 }, { origin, year }).map(r => r.name), ['새 아파트', '구축 단지'], '가까운 순, 면적 범위 밖 단지 제외');
  assert.deepEqual(filterComplexes(list, { priceMin: 50000, priceMax: 100000 }, { origin, year }).map(r => r.name), ['새 아파트']);
  assert.deepEqual(filterComplexes(list, { age: '10' }, { origin, year }).map(r => r.name), ['새 아파트']);
  assert.deepEqual(filterComplexes(list, { sort: 'price' }, { origin, year }).map(r => r.name), ['대형 단지', '새 아파트', '구축 단지']);
  assert.deepEqual(filterComplexes(list, { sort: 'recent' }, { origin, year }).map(r => r.name), ['새 아파트', '구축 단지', '대형 단지']);
  assert.deepEqual(filterComplexes(list, { sort: 'count' }, { origin, year }).map(r => r.name)[0], '구축 단지');
  assert.deepEqual(filterComplexes(list, { query: '구축 단' }, { origin, year }).map(r => r.name), ['구축 단지'], '띄어쓰기와 무관한 이름 검색');
  assert.deepEqual(filterComplexes(list, { query: '동패' }, { origin, year }).length, 3, '동 이름으로도 찾는다');
});

test('저장된 필터는 지금 규칙에 맞는 값만 복원한다', () => {
  assert.deepEqual(normalizeFilters({ months: 24, area: 'huge', mode: 'pyeong', query: 1 }),
    { months: 12, areaMin: 0, areaMax: 165, priceMin: 0, priceMax: 400000, age: 'all', mode: 'pyeong', unit: 'm2', sort: 'distance', query: '' });
  assert.equal(normalizeFilters(null).months, 12, '거래 기간은 최근 1년(+이전 거래)으로 고정');
  assert.equal(normalizeFilters({ months: '3' }).months, 12, '예전에 저장한 기간 값은 무시한다');
});

test('말풍선·연식 색·분양가 대표값·거리', () => {
  const row = { rep: { latest: 78000, area: 84, pyeong: 3070 } };
  assert.deepEqual(bubbleText(row, 'latest'), { price: '7.8억', sub: '84㎡' });
  assert.deepEqual(bubbleText(row, 'pyeong'), { price: '3,070만', sub: '3.3㎡당' });
  assert.equal(ageTone('2023', 2026), 'new');
  assert.equal(ageTone('2017', 2026), 'mid');
  assert.equal(ageTone('1999', 2026), 'old');
  assert.equal(ageTone('', 2026), 'unknown');
  const units = [{ area: 59.9, amountManwon: 50000 }, { area: 84.9, amountManwon: 70000 }, { area: 84.5, amountManwon: 69000 }, { area: 5, amountManwon: 1 }];
  assert.deepEqual(supplyForBand(units, 'all'), { area: 85, amount: 69000, pyeong: Math.round(69000 / 84.5 * 3.305785), supplyArea: null, type: '' }, '84㎡에 가장 가까운 주택형');
  assert.equal(supplyForBand(units, 'small').amount, 50000);
  assert.equal(supplyForBand(units, 'large'), null);
  assert.ok(Math.abs(distanceMeters({ lat: 37.5, lng: 127 }, { lat: 37.51, lng: 127 }) - 1112) < 5);
  assert.equal(distanceText(840), '840m');
  assert.equal(distanceText(1530), '1.5km');
  assert.equal(distanceMeters(null, { lat: 1, lng: 1 }), Infinity);
});

test('거리 재기 표기와 이동 시간', () => {
  assert.equal(measureText(0), '0m');
  assert.equal(measureText(432.6), '433m');
  assert.equal(measureText(1234), '1.2km');
  assert.equal(measureText(1000), '1km');
  assert.equal(measureText(15600), '16km');
  assert.deepEqual(travelMinutes(1340), { walk: 20, bike: 5 });
  assert.deepEqual(travelMinutes(20), { walk: 1, bike: 1 });
  assert.deepEqual(travelMinutes(0), { walk: 0, bike: 0 });
  const north = northOf({ lat: 37.5, lng: 127 }, 1000);
  assert.ok(Math.abs(distanceMeters({ lat: 37.5, lng: 127 }, north) - 1000) < 2);
});

test('검색어가 주소인지 단지 이름인지 구분한다', () => {
  for (const text of ['동패동 400-7', '경기도 파주시 동패동 1673', '운정로 123', '문산읍 당동리 산12', '목동동 1100 ']) assert.equal(isAddressLike(text), true, text);
  for (const text of ['한울마을1단지', '센트럴 2차', '운정', '동패', '힐스테이트 3']) assert.equal(isAddressLike(text), false, text);
});


test('면적은 전용 ㎡ 또는 추정 공급 평형으로 보여 준다', () => {
  assert.equal(areaText(84.97, 'm2'), '85㎡');
  assert.equal(areaText(84.97, 'pyeong'), '34평형');
  assert.equal(areaText(59.9, 'pyeong'), '24평형');
  assert.equal(areaText(0, 'pyeong'), '—');
  assert.equal(areaBandLabel(AREA_BANDS[2], 'pyeong'), '24~34평형');
  assert.equal(areaBandLabel(AREA_BANDS[2], 'm2'), '60~85㎡');
  assert.deepEqual(bubbleText({ rep: { latest: 78000, area: 84, pyeong: 3070 } }, 'latest', 'pyeong'), { price: '7.8억', sub: '34평형' });
  assert.deepEqual(bubbleText({ rep: { latest: 78000, area: 84, pyeong: 3070 } }, 'pyeong', 'pyeong'), { price: '3,070만', sub: '평당' });
});

test('면적 범위(네이버식 슬라이더): 양 끝은 제한 없음, 순서가 바뀌면 바로잡는다', () => {
  assert.equal(areaRangeText({ areaMin: 0, areaMax: 165 }), '전체');
  assert.equal(areaRangeText({ areaMin: 66, areaMax: 83 }), '66㎡~83㎡');
  assert.equal(areaRangeText({ areaMin: 0, areaMax: 83 }, 'pyeong'), '최소~33평형');
  assert.equal(areaRangeText({ areaMin: 66, areaMax: 165 }, 'pyeong'), '27평형~최대');
  assert.deepEqual([normalizeFilters({ areaMin: 99, areaMax: 33 }).areaMin, normalizeFilters({ areaMin: 99, areaMax: 33 }).areaMax], [33, 99]);
  assert.deepEqual([normalizeFilters({ areaMin: -5, areaMax: 900 }).areaMin, normalizeFilters({ areaMin: -5, areaMax: 900 }).areaMax], [0, 165]);
  assert.equal(isAreaFiltered({ areaMin: 0, areaMax: 165 }), false);
  assert.equal(isAreaFiltered({ areaMin: 33, areaMax: 165 }), true);
  const c = complex('가', '2020', [area(59, 52000, 1), area(84, 78000, 3), area(114, 110000, 2)]);
  assert.equal(representative(c, { areaMin: 100, areaMax: 165 }).area, 114, '최대 쪽은 165㎡ 넘는 면적도 포함');
  assert.equal(representative(c, { areaMin: 0, areaMax: 60 }).area, 59);
  assert.equal(supplyForBand([{ area: 59.9, amountManwon: 50000 }, { area: 84.9, amountManwon: 70000 }], { areaMin: 80, areaMax: 90 }).amount, 70000);
});

test('매매가 범위(네이버식 슬라이더) 글자와 정규화', () => {
  assert.equal(priceRangeText({ priceMin: 0, priceMax: 400000 }), '전체');
  assert.equal(priceRangeText({ priceMin: 0, priceMax: 29000 }), '~2억 9,000만');
  assert.equal(priceRangeText({ priceMin: 50000, priceMax: 400000 }), '5억~');
  assert.equal(priceRangeText({ priceMin: 50000, priceMax: 100000 }), '5억~10억');
  assert.equal(rangePrice(9500), '9,500만');
  assert.deepEqual([normalizeFilters({ priceMin: 29123, priceMax: 10 }).priceMin, normalizeFilters({ priceMin: 29123, priceMax: 10 }).priceMax], [0, 29000], '500만 단위, 순서 바로잡기');
  assert.equal(isPriceFiltered({ priceMin: 0, priceMax: 400000 }), false);
  assert.equal(isPriceFiltered({ priceMin: 0, priceMax: 29000 }), true);
});

test('대표 금액은 마지막 거래월 평균이고, 1년 넘은 값은 언제 값인지 표시한다', () => {
  const c = complex('가', '2020', [{ area: 84, latest: 80000, count: 4, latestDate: '2026-10-20', latestFloor: 3, median: 75000, min: 1, max: 1, month: '2026-10', monthAvg: 76000, monthCount: 3 }]);
  const rep = representative(c, 'all');
  assert.equal(rep.latest, 76000, '마지막 거래월 평균');
  assert.equal(rep.lastTrade, 80000);
  assert.equal(rep.monthCount, 3);
  assert.equal(monthPrice({ latest: 5 }), 5, '옛 응답은 마지막 거래가로');
  assert.equal(isStaleMonth('2025-11', '2026-11-14'), true);
  assert.equal(isStaleMonth('2025-12', '2026-11-14'), false);
  assert.equal(monthLabel('2026-10', 2026), '10월');
  assert.equal(monthLabel('2025-03', 2026), '25년 3월');
  assert.deepEqual(bubbleText({ rep: { latest: 42000, area: 85, pyeong: 1, month: '2025-03' } }, 'latest', 'm2', '2026-11-14'), { price: '4.2억', sub: '85㎡ · 25.3월' });
  assert.deepEqual(bubbleText({ rep: { latest: 42000, area: 85, pyeong: 1, month: '2026-10' } }, 'latest', 'm2', '2026-11-14'), { price: '4.2억', sub: '85㎡' });
});

test('조건 밖 단지는 이유와 함께 따로 돌려준다(거래 없음과 위치 미확인을 구분)', () => {
  // '32~50평형'(추정 공급 평형) = 전용 80~124㎡. 전용 84㎡(34평형) 단지는 들어오고, 59㎡(24평형)만 거래된 단지는 조건 밖으로 남는다.
  const origin = { lat: 37.72, lng: 126.75 };
  const year = 2026;
  const list = [
    complex('대형 있음', '2020', [area(84, 70000, 9), area(110, 77000, 2)], 37.721, 126.751),
    complex('국민평형', '2022', [area(59, 52000, 3), area(84.4, 68000, 5)], 37.722, 126.752),
    complex('소형만', '2021', [area(59, 45000, 7), area(60, 46000, 2)], 37.7225, 126.7525),
    complex('구축 대형', '1998', [area(120, 39000, 1)], 37.723, 126.753),
    complex('위치 미확인', '2021', [area(114, 90000, 1)], null, null)
  ];
  const range = { areaMin: areaForSupplyPyeong(32), areaMax: areaForSupplyPyeong(50), unit: 'pyeong' };
  assert.deepEqual([range.areaMin, range.areaMax], [80, 124]);
  const { rows, outside } = splitComplexes(list, range, { origin, year });
  assert.deepEqual(rows.map(r => r.name), ['대형 있음', '국민평형', '구축 대형', '위치 미확인'], '34평형(전용 84㎡) 단지가 32~50평형에 들어온다');
  assert.equal(rows.find(r => r.name === '국민평형').rep.area, 84.4);
  assert.deepEqual(outside.map(r => [r.name, r.reason]), [['소형만', 'area']]);
  assert.equal(outside[0].rep, null);
  assert.equal(outsideText(outside[0], 'pyeong'), '고른 면적의 거래 없음 · 거래 면적 24평형');
  assert.equal(outsideText(outside[0], 'm2'), '고른 면적의 거래 없음 · 거래 면적 59·60㎡');
  assert.deepEqual(mapSummary(rows, outside), { matched: 4, placed: 3, unplaced: 1, outside: 1, outsidePlaced: 1 }, '위치 미확인은 조건 밖과 따로 센다');

  const byAge = splitComplexes(list, { ...range, age: '10' }, { origin, year });
  assert.deepEqual(byAge.outside.map(r => [r.name, r.reason]), [['소형만', 'area'], ['구축 대형', 'age']]);
  assert.equal(outsideText(byAge.outside[1]), '준공 연식 조건 밖');
  const byPrice = splitComplexes(list, { priceMin: 60000, priceMax: 400000 }, { origin, year });
  assert.deepEqual(byPrice.outside.map(r => [r.name, r.reason]), [['소형만', 'price'], ['구축 대형', 'price']]);
  assert.deepEqual(splitComplexes(list, { ...range, query: '소형' }, { origin, year }).outside.map(r => r.name), ['소형만']);
  assert.equal(splitComplexes(list, { ...range, query: '없는이름' }, { origin, year }).outside.length, 0, '이름 검색에 안 맞는 단지는 어느 쪽에도 없다');
});

test('추정 공급 평형 변환 경계(전용×1.33)', () => {
  assert.equal(supplyPyeong(59), 24);
  assert.equal(supplyPyeong(84), 34);
  assert.equal(supplyPyeong(114), 46);
  // 32평형 경계: 전용 78㎡는 31평형, 79㎡부터 32평형. 50평형 경계: 124㎡는 50평형, 125㎡는 50평형, 126㎡는 51평형.
  assert.equal(supplyPyeong(78), 31);
  assert.equal(supplyPyeong(79), 32);
  assert.equal(supplyPyeong(125), 50);
  assert.equal(supplyPyeong(126), 51);
  assert.equal(supplyPyeong(areaForSupplyPyeong(32)), 32);
  assert.equal(supplyPyeong(areaForSupplyPyeong(50)), 50);
  assert.deepEqual(AREA_MARKS_PYEONG.slice(1, -1).map(supplyPyeong), [20, 30, 40, 50]);
  assert.equal(areaRangeText({ areaMin: 80, areaMax: 124 }, 'pyeong'), '32~50평형');
  assert.equal(areaRangeText({ areaMin: 80, areaMax: 165 }, 'pyeong'), '32평형~최대');
  assert.equal(areaRangeText({ areaMin: 80, areaMax: 124 }, 'm2'), '80㎡~124㎡');
  assert.match(PYEONG_HINT, /추정한 공급 평형.*84㎡ ≈ 34평형/);
});

test('저장된 옛 전용 평 필터는 같은 숫자의 공급 평형으로 옮긴다', () => {
  // 예전 '32평~50평'(전용 106~164㎡) → 이제 '32~50평형'(전용 80~124㎡).
  const moved = migrateStoredFilters({ unit: 'pyeong', areaMin: 106, areaMax: 164 });
  assert.equal(areaRangeText(moved, 'pyeong'), '32~50평형');
  assert.equal(moved.areaBasis, 'supply');
  assert.deepEqual(migrateStoredFilters({ unit: 'pyeong', areaMin: 0, areaMax: 165 }).areaMax, 165, '제한 없음은 그대로');
  assert.deepEqual(migrateStoredFilters({ unit: 'm2', areaMin: 106, areaMax: 164 }), { unit: 'm2', areaMin: 106, areaMax: 164 }, '㎡ 단위는 그대로');
  const already = { unit: 'pyeong', areaMin: 80, areaMax: 124, areaBasis: 'supply' };
  assert.equal(migrateStoredFilters(already), already, '이미 옮긴 값은 다시 옮기지 않는다');
});

test('분양 말풍선: 접수 중·예정·종료를 구분하고, 위치 미확정 공고는 따로 모은다', () => {
  assert.equal(saleStatusLabel('OPEN'), '접수 중');
  assert.equal(saleStatusLabel('UPCOMING'), '접수 예정');
  assert.equal(saleStatusLabel('DONE'), '접수 종료');
  assert.equal(saleStatusLabel('X'), '분양 공고');
  assert.equal(moveInText('2029-03'), '29.3 입주 예정');
  assert.equal(moveInText(null), '');
  const a = { sales: [{ id: 'apt/1/1', status: 'OPEN' }, { id: 'apt/2/2', status: 'DONE' }, { id: 'apt/cur/cur', status: 'DONE' }], unlocated: [{ id: 'apt/3/3', status: 'DONE' }] };
  const b = { sales: [{ id: 'apt/2/2', status: 'DONE' }, { id: 'apt/3/3', status: 'DONE' }], unlocated: [{ id: 'apt/4/4', status: 'UPCOMING' }, { id: 'apt/cur/cur' }] };
  const merged = mergeSales([a, b, undefined], 'apt/cur/cur');
  assert.deepEqual(merged.sales.map(sale => sale.id), ['apt/1/1', 'apt/2/2', 'apt/3/3'], '지금 공고는 빼고 같은 공고는 한 번만');
  assert.deepEqual(merged.unlocated.map(item => item.id), ['apt/4/4'], '다른 지역 응답에서 지도에 놓인 공고는 미확정 목록에서 뺀다');
});

test('넓게 보면 법정동·시군구로 묶고, 묶음 가격은 대표 가격의 중간값', () => {
  assert.equal(groupTier(5), 'single');
  assert.equal(groupTier(GROUP_LEVELS.dong), 'dong');
  assert.equal(groupTier(8), 'dong');
  assert.equal(groupTier(GROUP_LEVELS.district), 'district');
  const row = (key, dong, latest, pyeong, lat, lng) => ({ key, dong, rep: { latest, pyeong }, lat, lng });
  const rows = [
    row('41480|동패동|1', '동패동', 60000, 2500, 37.71, 126.72),
    row('41480|동패동|2', '동패동', 80000, 3000, 37.72, 126.73),
    row('41480|동패동|3', '동패동', 70000, 2800, 37.73, 126.74),
    row('41480|조리읍 봉일천리|5', '조리읍 봉일천리', 30000, 1500, 37.74, 126.80),
    row('41285|식사동|9', '식사동', 90000, 3500, 37.68, 126.81),
    { key: '41480|동패동|7', dong: '동패동', rep: { latest: 1, pyeong: 1 }, lat: null, lng: null },
    { key: '41480|동패동|8', dong: '동패동', rep: null, lat: 37.7, lng: 126.7 }
  ];
  const dong = groupRows(rows, 'dong', { names: { 41480: '파주시', 41285: '일산동구' } });
  assert.deepEqual(dong.map(g => [g.label, g.count, g.price]), [['동패동', 3, 70000], ['봉일천리', 1, 30000], ['식사동', 1, 90000]], '위치 없는 단지·대표 거래 없는 단지는 빼고 중간값');
  assert.deepEqual(dong[0].bounds, { swLat: 37.71, swLng: 126.72, neLat: 37.73, neLng: 126.74 });
  assert.ok(Math.abs(dong[0].lat - 37.72) < 1e-9);
  const district = groupRows(rows, 'district', { mode: 'pyeong', names: { 41480: '파주시', 41285: '일산동구' } });
  assert.deepEqual(district.map(g => [g.label, g.count, g.price]), [['파주시', 4, 2650], ['일산동구', 1, 3500]], '평당가 모드면 평당가 중간값');
  assert.equal(groupRows(rows, 'district')[1].label, '시·군·구', '이름을 모르면 일반 이름');
});

test('단지별 말풍선은 화면 근처만 만든다', () => {
  const view = { swLat: 37.70, swLng: 126.70, neLat: 37.72, neLng: 126.74 };
  assert.equal(inView({ lat: 37.71, lng: 126.72 }, view), true);
  assert.equal(inView({ lat: 37.725, lng: 126.72 }, view), true, '가장자리 바깥 30%까지는 미리 만든다');
  assert.equal(inView({ lat: 37.80, lng: 126.72 }, view), false);
  assert.equal(inView({ lat: 37.80, lng: 126.72 }, null), true, '범위를 아직 모르면 모두');
  assert.equal(inView({ lat: null, lng: null }, view), false);
});

test('큰 시·군·구(단지 3,000곳)도 묶음 계산이 가볍다', () => {
  const many = Array.from({ length: 3000 }, (_, i) => ({ key: `41110|동${i % 40}|${i}`, dong: `동${i % 40}`, rep: { latest: 50000 + i, pyeong: 2000 }, lat: 37 + (i % 100) / 1000, lng: 127 + (i % 77) / 1000 }));
  const started = performance.now();
  const groups = groupRows(many, 'dong');
  assert.equal(groups.length, 40);
  assert.ok(performance.now() - started < 200, '200ms 안');
});

test('이 공고도 다른 분양 말풍선과 같은 규칙으로 접수 상태를 정한다', () => {
  // 시티프라디움 3단지: 2026-11-02~04 접수 → 11-14에는 4단지와 같은 '접수 종료'.
  assert.equal(saleStatusOf('2026-11-02', '2026-11-04', '2026-11-14'), 'DONE');
  assert.equal(saleStatusOf('20261102', '20261104', '2026-11-04'), 'OPEN', '마감일 당일까지 접수 중');
  assert.equal(saleStatusOf('2026-11-02', '2026-11-04', '2026-11-02'), 'OPEN');
  assert.equal(saleStatusOf('2026-11-20', '2026-11-22', '2026-11-14'), 'UPCOMING');
  assert.equal(saleStatusOf('2026-11-14', null, '2026-11-14'), 'OPEN', '마감일이 없으면 시작일 하루');
  assert.equal(saleStatusOf('2026-11-13', '', '2026-11-14'), 'DONE');
  assert.equal(saleStatusOf(null, null, '2026-11-14'), null, '날짜를 모르면 상태를 붙이지 않는다');
  assert.equal(saleStatusLabel(saleStatusOf('2026-11-02', '2026-11-04', '2026-11-14')), saleStatusLabel('DONE'));
});

test('분양 주택형은 실제 공급면적으로 평형을 쓰고, 같은 84㎡도 전용 소수로 구분한다', () => {
  const a = { area: 84.9594, supplyArea: 112.1593, type: '084.9594' }, b = { area: 84.4998, supplyArea: 111.8, type: '084.4998B' };
  assert.deepEqual(unitAreaLabel(a, 'm2'), { main: '전용 84.96㎡', sub: '34평형' });
  assert.deepEqual(unitAreaLabel(b, 'm2'), { main: '전용 84.5㎡ B', sub: '34평형' });
  assert.deepEqual(unitAreaLabel(a, 'pyeong'), { main: '34평형', sub: '전용 84.96㎡' });
  assert.deepEqual(unitAreaLabel({ area: 60.9995, supplyArea: 83.0332 }, 'pyeong').main, '25평형', '공급 83.03㎡ = 25평형');
  assert.deepEqual(unitAreaLabel({ area: 84.9 }, 'pyeong'), { main: '약 34평형(추정)', sub: '전용 84.9㎡' }, '공급면적이 없을 때만 추정이라고 밝힌다');
  const supply = supplyBand([{ area: 84.9594, supplyArea: 112.1593, amountManwon: 79800 }], 'all');
  assert.equal(saleAreaText(supply, 'pyeong'), '34평형');
  assert.equal(saleAreaText(supply, 'm2'), '85㎡');
});

test('같은 필지 판정은 법정동과 지번이 모두 같을 때만', () => {
  const location = { address: '경기 파주시 동패동 2295', dong: '동패동' };
  assert.equal(sameParcel({ dong: '동패동', jibun: '2295' }, location), true);
  assert.equal(sameParcel({ dong: '동패동', jibun: '2295-1' }, location), false, '부번이 다르면 다른 필지');
  assert.equal(sameParcel({ dong: '목동동', jibun: '2295' }, location), false);
  assert.equal(sameParcel({ dong: '조리읍 봉일천리', jibun: '산 12' }, { address: '경기 파주시 조리읍 봉일천리 산 12', dong: '조리읍' }), true, '읍·면 아래 리와 산 지번');
  assert.equal(sameParcel({ dong: '동패동', jibun: '' }, location), false);
  assert.equal(sameParcel({ dong: '동패동', jibun: '2295' }, { address: '경기 파주시 동패동' }), false, '지번 없는 주소는 같다고 보지 않는다');
});

test('밀집 지역은 레벨 5에서도 법정동으로 묶고, 레벨 4 이하는 늘 단지별', () => {
  const view = { swLat: 37.0, swLng: 127.0, neLat: 37.1, neLng: 127.1 };
  const many = Array.from({ length: DENSE_IN_VIEW + 1 }, (_, i) => ({ lat: 37.01 + i / 1000, lng: 127.05 }));
  assert.equal(displayTier(5, many, view), 'dong');
  assert.equal(displayTier(5, many.slice(0, DENSE_IN_VIEW), view), 'single');
  assert.equal(displayTier(4, many, view), 'single', '묶음을 눌러 들어온 레벨 4는 다시 묶지 않는다');
  assert.equal(displayTier(5, many.map(p => ({ ...p, lat: p.lat + 1 })), view), 'single', '화면 밖 단지는 세지 않는다');
  assert.equal(displayTier(7, [], view), 'dong');
  assert.equal(displayTier(5, many, null), 'single');
});

test('면적 기본값은 이 공고 주택형 범위', () => {
  assert.deepEqual(defaultAreaRange([{ area: 60.9995 }, { area: 84.96 }, { area: 115.2 }]), { areaMin: 55, areaMax: 121 });
  assert.deepEqual(defaultAreaRange([{ area: 39.3 }]), { areaMin: 34, areaMax: 45 });
  assert.deepEqual(defaultAreaRange([]), { areaMin: 0, areaMax: 165 }, '주택형 정보가 없으면 전체');
  assert.deepEqual(defaultAreaRange([{ area: 12 }, { area: 163 }]), { areaMin: 7, areaMax: 165 }, '최대 쪽 끝 근처는 제한 없음으로');
});

test('가까운 시설은 직선거리와 걷기 시간을 함께 밝힌다', () => {
  assert.deepEqual(nearbyText({ kind: 'STATION', name: '운정중앙역 경의중앙선', distance: 420 }), { label: '지하철역', short: '역', name: '운정중앙역 경의중앙선', distance: '직선 420m · 걸어서 약 6분' });
  assert.equal(nearbyText({ kind: 'SCHOOL', name: '물향기초등학교', distance: 1350 }).distance, '직선 1.4km · 걸어서 약 20분');
  assert.equal(nearbyText({ kind: 'PARK', name: 'x', distance: 1 }), null);
});

test('같은 지번이라도 재건축 전 옛 아파트는 이 단지로 보지 않는다', () => {
  const location = { address: '경북 구미시 형곡동 141-11' };
  const old = { dong: '형곡동', jibun: '141-11', buildYear: '1986' }, built = { dong: '형곡동', jibun: '141-11', buildYear: '2025' };
  assert.equal(isSameComplex(old, location, { type: 'apt', year: 2026 }), false, '신축 분양 + 옛 준공 단지');
  assert.equal(isSameComplex(built, location, { type: 'apt', year: 2026 }), true, '최근 준공 단지');
  assert.equal(isSameComplex(old, location, { type: 'remaining', year: 2026 }), true, '무순위는 이미 지어진 단지의 공고');
  assert.equal(isSameComplex({ ...built, jibun: '141-12' }, location, { type: 'remaining', year: 2026 }), false);
});

test('같은 전용면적 주택형은 한 줄로 묶고, 3.3㎡당은 공급면적 기준을 밝힌다', () => {
  const units = ['A', 'B', 'C', 'D', 'E', 'F'].map((letter, i) => ({ type: `049.9${i}00${letter}`, area: 49.9, supplyArea: 68.4, amountManwon: 31000 + i * 100 }))
    .concat([{ type: '059.9800', area: 59.98, supplyArea: 82.1, amountManwon: 39050 }]);
  const groups = groupSaleUnits(units);
  assert.equal(groups.length, 2);
  assert.deepEqual([groups[0].area, groups[0].count, groups[0].minAmount, groups[0].maxAmount, groups[0].perBasis], [50, 6, 31000, 31500, 'supply']);
  assert.deepEqual(saleGroupText(groups[0], 'm2'), { main: '전용 50㎡', sub: '21평형 · A~F 6개 타입', price: '3.1억~3.2억', per: `공급 3.3㎡당 ${Math.round(31000 / 68.4 * 3.305785).toLocaleString('ko-KR')}만~${Math.round(31500 / 68.4 * 3.305785).toLocaleString('ko-KR')}만` });
  assert.equal(saleGroupText(groups[0], 'pyeong').main, '21평형');
  assert.equal(saleGroupText(groups[1], 'm2').main, '전용 59.98㎡', '한 타입이면 그대로');
  assert.match(saleGroupText(groupSaleUnits([{ area: 84.9, amountManwon: 60000 }])[0]).per, /^전용 3.3㎡당/, '공급면적이 없으면 전용 기준이라고 밝힌다');
});

test('map search suggestions rank dongs, sales and complexes and skip unlocated ones', async () => {
  const { suggestPlaces } = await import('../src/features/announcement/location/priceMapRules.js');
  const complexes = [
    { key: 'a', name: '작전현대1차', dong: '작전동', lat: 37.53, lng: 126.73, buildYear: '1990' },
    { key: 'b', name: '현대작전타운', dong: '작전동', lat: 37.54, lng: 126.74 },
    { key: 'c', name: '작전위치없음', dong: '작전동', lat: null, lng: null },
    { key: 'd', name: '부평자이', dong: '부평동', lat: 37.5, lng: 126.7 }
  ];
  const sales = [{ id: 'apt/1', houseNm: '작전역 새단지', status: 'OPEN', lat: 37.535, lng: 126.735 }];
  const items = suggestPlaces(complexes, sales, '작전', { statusLabel: () => '접수 중' });
  assert.deepEqual(items.map(item => item.kind + ':' + item.label), ['dong:작전동', 'sale:작전역 새단지', 'complex:작전현대1차', 'complex:현대작전타운']);
  // 법정동은 그 동 단지들의 가운데(위치를 아는 단지만)로 옮긴다.
  assert.equal(items[0].lat, (37.53 + 37.54) / 2);
  assert.equal(items[0].detail, '실거래 단지 2곳');
  assert.equal(items[1].detail, '분양 · 접수 중');
  assert.equal(items[1].key, 'sale:apt/1');
  assert.deepEqual(suggestPlaces(complexes, sales, '  '), []);
  assert.equal(suggestPlaces(complexes, sales, '작전', { limit: 2 }).length, 2);
});
