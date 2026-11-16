import test from 'node:test';
import assert from 'node:assert/strict';
import { visibleNotices, dismissNotice, readDismissed } from '../src/features/account/siteNoticeRules.js';
import { shouldTrackSearch } from '../src/features/analytics/searchStatRules.js';

const memory = () => { const map = new Map(); return { getItem: k => map.get(k) ?? null, setItem: (k, v) => map.set(k, String(v)) }; };

test('사이트 공지는 첫 화면 전용·닫은 공지를 거른다', () => {
  const items = [{ seq: 1, message: '점검', placement: 'ALL' }, { seq: 2, message: '새 기능', placement: 'HOME' }, { seq: 3, message: '' }];
  assert.deepEqual(visibleNotices(items, '/', []).map(i => i.seq), [1, 2]);
  assert.deepEqual(visibleNotices(items, '/my', []).map(i => i.seq), [1]);
  const storage = memory();
  dismissNotice(1, storage);
  assert.deepEqual(readDismissed(storage), [1]);
  assert.deepEqual(visibleNotices(items, '/', readDismissed(storage)).map(i => i.seq), [2]);
  assert.deepEqual(visibleNotices(null, '/'), []);
});

test('검색어 통계는 확정된 검색어만, 숫자만 긴 값은 빼고, 10분에 한 번만 센다', () => {
  assert.equal(shouldTrackSearch(' 운정  신도시 ', 0), '운정 신도시');
  assert.equal(shouldTrackSearch('운정 신도시', 1000), null, '10분 안 같은 검색어');
  assert.equal(shouldTrackSearch('운정 신도시', 700000), '운정 신도시');
  assert.equal(shouldTrackSearch('010-1234-5678', 0), null);
  assert.equal(shouldTrackSearch('a', 0), null);
});
