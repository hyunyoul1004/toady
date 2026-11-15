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
