import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import { cardValue, parseCardValue, reportValue, parseReportValue, messageTime } from '../src/features/community/communityRules.js';

test('profile card layer value keeps the member and the announcement the talk started from', () => {
  assert.deepEqual(parseCardValue(cardValue(12, 'apt/2026000001/2026000001')), { userSeq: 12, contextId: 'apt/2026000001/2026000001' });
  assert.deepEqual(parseCardValue(cardValue(7)), { userSeq: 7, contextId: null });
  assert.equal(parseCardValue('abc'), null);
  assert.equal(parseCardValue('-3'), null);
  assert.equal(parseCardValue(null), null);
});

test('report layer value accepts only known targets and carries the author for the block offer', () => {
  assert.deepEqual(parseReportValue(reportValue('COMMENT', 31, 9)), { type: 'COMMENT', seq: 31, author: 9 });
  assert.deepEqual(parseReportValue(reportValue('USER', 9)), { type: 'USER', seq: 9, author: null });
  assert.equal(parseReportValue('POST:1'), null);
  assert.equal(parseReportValue('MESSAGE:x'), null);
});

test('message times read as time today, month.day this year and full date before that', () => {
  const now = new Date(2026, 10, 16, 21, 0);
  assert.equal(messageTime(new Date(2026, 10, 16, 9, 5).toISOString(), now), '09:05');
  assert.equal(messageTime(new Date(2026, 2, 3, 9, 5).toISOString(), now), '3.3');
  assert.equal(messageTime(new Date(2025, 11, 31, 9, 5).toISOString(), now), '2025.12.31');
  assert.equal(messageTime('nope', now), '');
});

test('push clicks open the message box inside the app', async () => {
  const sw = await fs.readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  const support = await fs.readFile(new URL('../src/features/push/pushSupport.js', import.meta.url), 'utf8');
  for (const source of [sw, support]) assert.match(source, /'\/messages'/);
});

import { mergeMessages, uniqueThreads, isCollapsedStory } from '../src/features/community/communityRules.js';

test('messages merge by number without gaps or duplicates when older pages meet a newer poll', () => {
  const m = seq => ({ messageSeq: seq, content: `#${seq}` });
  // 이전 쪽지(1~3)를 불러온 뒤, 새 쪽지가 와서 최신 범위가 5~7로 밀린 경우에도 4가 남아 있어야 한다(이미 쌓아 둔 값).
  const shown = mergeMessages([m(1), m(2), m(3), m(4), m(5), m(6)], [m(5), m(6), m(7)]);
  assert.deepEqual(shown.map(x => x.messageSeq), [1, 2, 3, 4, 5, 6, 7]);
  assert.deepEqual(mergeMessages([m(3)], [m(1), m(3)]).map(x => x.messageSeq), [1, 3]);
  assert.deepEqual(mergeMessages(undefined, undefined), []);
});

test('thread pages are joined without showing the same conversation twice', () => {
  const pages = [{ items: [{ threadSeq: 9 }, { threadSeq: 8 }] }, { items: [{ threadSeq: 8 }, { threadSeq: 7 }] }, null];
  assert.deepEqual(uniqueThreads(pages).map(x => x.threadSeq), [9, 8, 7]);
});

test('blocked authors are collapsed but never my own story or one I chose to reveal', () => {
  const blocked = new Set([5]);
  assert.equal(isCollapsedStory({ createUserSeq: 5, subscriptCommentSeq: 1 }, { myUserSeq: 3, blocked, revealed: new Set() }), true);
  assert.equal(isCollapsedStory({ createUserSeq: 5, subscriptCommentSeq: 1 }, { myUserSeq: 3, blocked, revealed: new Set([1]) }), false);
  assert.equal(isCollapsedStory({ createUserSeq: 3, subscriptCommentSeq: 2 }, { myUserSeq: 3, blocked: new Set([3]), revealed: new Set() }), false);
  assert.equal(isCollapsedStory({ createUserSeq: 4, subscriptCommentSeq: 3 }, { myUserSeq: null, blocked, revealed: new Set() }), false);
});

test('message and block caches are reset when the account changes', async () => {
  const source = await fs.readFile(new URL('../src/features/auth/store/bindAuthQueryCache.js', import.meta.url), 'utf8');
  const roots = /USER_QUERY_ROOTS = new Set\(\[([^\]]+)\]\)/.exec(source)?.[1] || '';
  assert.match(roots, /'community'/, '계정을 바꾸면 이전 계정의 쪽지·차단 캐시를 지운다');
});
