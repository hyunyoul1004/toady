import test from 'node:test';
import assert from 'node:assert/strict';
import { ALL_REGIONS, matchesRegions } from '../src/views/main/announcementFilters.js';

const only = (...codes) => ALL_REGIONS.filter((r) => codes.includes(r.code));

test('공급지역 코드가 있으면 코드로만 판정해 경기도 광주시를 광주광역시로 오인하지 않는다', () => {
  const gyeonggiGwangju = { regionCode: '410', supplyLocation: '경기도 광주시 오포읍 신현리 1' };
  assert.equal(matchesRegions(gyeonggiGwangju, only('500')), false);
  assert.equal(matchesRegions(gyeonggiGwangju, only('410')), true);
  assert.equal(matchesRegions({ regionCode: '500', supplyLocation: '광주광역시 북구 1' }, only('500')), true);
});

test('코드가 없으면 주소 첫 낱말(시·도)로만 비교한다', () => {
  assert.equal(matchesRegions({ supplyLocation: '경기도 광주시 1' }, only('500')), false);
  assert.equal(matchesRegions({ supplyLocation: '경기도 광주시 1' }, only('410')), true);
  assert.equal(matchesRegions({ supplyLocation: '광주광역시 남구 1' }, only('500')), true);
  assert.equal(matchesRegions({ supplyLocation: '서울시 강남구 1' }, only('100')), true);
  assert.equal(matchesRegions({ supplyLocation: '' }, only('100')), false);
});
