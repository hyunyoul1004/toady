import test from 'node:test';
import assert from 'node:assert/strict';
import { announcementListKeyParams, buildAnnouncementQueryParams } from '../src/features/announcement/api/announcementParams.js';

const all = { rceptSe01: 'Y', rceptSe02: 'Y', rceptSe03: 'Y', rceptSe04: 'Y', rceptSe05: 'Y', rceptSe06: 'Y', rceptSe07: 'Y', rceptSe08: 'Y' };
const range = { startStr: '2026-10-25', endStr: '2026-12-14' };

test('server params map rceptSe toggles to types and drop client-only search areas', () => {
  assert.deepEqual(buildAnnouncementQueryParams({ ...all, ...range, citys: [{ key: 'area_운정' }] }),
    { startDate: '2026-10-25', endDate: '2026-12-14', page: 0, size: 100 });
  const unranked = { ...all, rceptSe01: 'N', rceptSe02: 'N', rceptSe03: 'N', rceptSe04: 'N', rceptSe05: 'N', ...range };
  assert.equal(buildAnnouncementQueryParams(unranked).types, 'remaining,optional');
  assert.equal(buildAnnouncementQueryParams({ ...range, types: 'apt', keyword: '운정', regionCode: '41' }).types, 'apt');
  assert.equal(buildAnnouncementQueryParams({ ...range, keyword: '운정', regionCode: '41' }).keyword, '운정');
  assert.equal(buildAnnouncementQueryParams({ startStr: '20261025' }, value => `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6)}`).startDate, '2026-10-25');
});

test('list query key is shared when only client-side filters change', () => {
  const base = announcementListKeyParams({ ...all, ...range, citys: [] });
  // 검색 지역만 바뀐 경우
  assert.deepEqual(announcementListKeyParams({ ...all, ...range, citys: [{ key: 'area_운정' }] }), base);
  // 특별공급만 끈 경우도 APT 전체를 받아 화면에서 거르므로 서버 요청이 같다.
  assert.deepEqual(announcementListKeyParams({ ...all, ...range, rceptSe01: 'N' }), base);
  assert.notDeepEqual(announcementListKeyParams({ ...all, ...range, rceptSe05: 'N' }), base);
  assert.notDeepEqual(announcementListKeyParams({ ...all, startStr: '2026-11-24', endStr: '2027-01-14' }), base);
  // 페이지를 지정한 조회와 전체 페이지 수집은 결과가 달라 키를 나눈다.
  assert.equal(base.allPages, true);
  assert.equal(announcementListKeyParams({ ...all, ...range, page: 0 }).allPages, false);
});
