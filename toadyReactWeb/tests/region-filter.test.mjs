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

test('주소·공급지역명이 있으면 지역별 청약과 같은 시·도 판정(regionOf)을 쓰고, 체크박스는 시·도 단위 그대로다', () => {
  const hwaseong = { hssplyAdres: '경기도 화성특례시 효행구 봉담읍 1', regionName: '경기', regionCode: '410' };
  assert.equal(matchesRegions(hwaseong, only('410')), true);
  assert.equal(matchesRegions(hwaseong, only('100', '400')), false);
  // 주소가 시·군으로 시작하면 공급지역명으로 시·도를 정한다(경기 광주시를 광주광역시로 오인하지 않음).
  assert.equal(matchesRegions({ hssplyAdres: '광주시 오포읍 1', regionName: '경기' }, only('410')), true);
  assert.equal(matchesRegions({ hssplyAdres: '광주시 오포읍 1', regionName: '경기' }, only('500')), false);
  // 옛 시·도 이름·줄임말도 같은 시·도.
  assert.equal(matchesRegions({ hssplyAdres: '전라북도 전주시 완산구 1', regionName: '전북' }, only('560')), true);
  assert.equal(matchesRegions({ hssplyAdres: '인천광역시 제물포구 도화동 1', regionName: '인천' }, only('400')), true);
});

test('일반구가 괄호 안에 있는 주소도 일반구 검색에 맞는다', async () => {
  const { matchesRegionTarget } = await import('../src/features/explore/regionRules.js');
  const target = { cityName: '경기도', districtName: '고양시 덕양구' };
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기도 고양시(덕양구) 향동동 1', regionName: '경기' }, target), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기도 고양시 덕양구(향동지구)', regionName: '경기' }, target), true);
  assert.equal(matchesRegionTarget({ hssplyAdres: '경기도 고양시 일산동구 장항동', regionName: '경기' }, target), false);
});
