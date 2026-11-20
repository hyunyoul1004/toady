import test from 'node:test';
import assert from 'node:assert/strict';
import {
  SIGNUP_MOMENTS, SIGNUP_SNOOZE_MS, favoritePromptDue, favoritePromptText, resultSoon, resultSoonText, promptSnoozed, snoozePrompt,
  trackPromptView, trackPromptClick, importCandidates, importBucket, importAsked, markImportAsked
} from '../src/features/account/signupPrompt.js';

const memory = () => { const data = {}; return { getItem: k => data[k] ?? null, setItem: (k, v) => { data[k] = String(v); } }; };

test('관심 공고 3개째 새로 저장할 때만 권한다(기존 공고 수정은 아님)', () => {
  assert.equal(favoritePromptDue({ isNew: true, countAfter: 2 }), false);
  assert.equal(favoritePromptDue({ isNew: true, countAfter: 3 }), true);
  assert.equal(favoritePromptDue({ isNew: false, countAfter: 3 }), false, '메모·단계 수정');
  assert.equal(favoritePromptDue({ isNew: true, countAfter: 7 }), true, '3개를 넘겨 처음 만나도 한 번은 권한다(미룸은 따로 본다)');
  assert.match(favoritePromptText(3), /관심 공고 3개가 이 브라우저에만/);
});

test('발표 임박: 오늘·내일 발표인 관심 공고, 결과를 기록했거나 원본이 사라진 공고는 뺀다', () => {
  const item = (id, winnerDate, extra = {}) => ({ id, stage: 0, summary: { houseNm: id, winnerDate }, ...extra });
  const today = '2026-11-20';
  assert.equal(resultSoon([item('a', '2026-11-22')], today), null, '모레');
  assert.equal(resultSoon([item('a', '2026-11-19')], today), null, '어제');
  assert.deepEqual(resultSoon([item('a', '2026-11-21'), item('b', '2026-11-20')], today).item.id, 'b', '오늘 발표가 먼저');
  assert.equal(resultSoon([item('a', '2026-11-20', { stage: 3 })], today), null, '당첨 기록');
  assert.equal(resultSoon([item('a', '2026-11-20', { stage: 4 })], today), null, '미당첨 기록');
  assert.equal(resultSoon([{ id: 'a', summary: { winnerDate: '2026-11-20', unavailable: true } }], today), null);
  assert.equal(resultSoon([item('a', '')], today), null);
  assert.match(resultSoonText(resultSoon([item('힐스테이트', '2026-11-21')], today)), /힐스테이트 당첨자 발표가 내일예요/);
});

test('한 순간은 30일 미루고, 다른 순간은 따로 본다', () => {
  const store = memory();
  const now = Date.parse('2026-11-20T00:00:00Z');
  assert.equal(promptSnoozed('favorite', now, store), false);
  snoozePrompt('favorite', now, store);
  assert.equal(promptSnoozed('favorite', now + 1000, store), true);
  assert.equal(promptSnoozed('result', now + 1000, store), false);
  assert.equal(promptSnoozed('favorite', now + SIGNUP_SNOOZE_MS + 1, store), false, '30일 뒤 다시');
  assert.equal(promptSnoozed('favorite', now, { getItem: () => '{oops' }), false, '깨진 저장값');
  assert.doesNotThrow(() => snoozePrompt('favorite', now, { getItem: () => null, setItem: () => { throw Error('blocked'); } }));
});

test('노출·클릭은 탭에서 순간당 한 번만 세고, 측정 이름은 서버 허용 목록과 같다', () => {
  const sent = [], track = (a, s) => sent.push([a, s]), store = memory();
  trackPromptView(track, 'favorite', store);
  trackPromptView(track, 'favorite', store);
  trackPromptView(track, 'story', store);
  trackPromptView(track, 'unknown', store);
  trackPromptClick(track, 'story', store);
  trackPromptClick(track, 'story', store);
  assert.deepEqual(sent, [['signup_prompt_view', 'tool/signup-favorite'], ['signup_prompt_view', 'tool/signup-story'], ['signup_prompt_click', 'tool/signup-story']]);
  assert.deepEqual(Object.values(SIGNUP_MOMENTS).sort(), ['tool/signup-calendar', 'tool/signup-favorite', 'tool/signup-result', 'tool/signup-story']);
});

test('이어받기: 계정에 없고 원본이 있는 기기 공고만, 개수는 구간으로만 센다, 같은 묶음은 다시 묻지 않는다', () => {
  const guest = [{ id: 'apt/1/1' }, { id: 'apt/2/2' }, { id: 'apt/3/3', summary: { unavailable: true } }];
  assert.deepEqual(importCandidates(guest, [{ id: 'apt/1/1' }]).map(x => x.id), ['apt/2/2']);
  assert.equal(importBucket(0), null);
  assert.equal(importBucket(2), 'tool/import-1-2');
  assert.equal(importBucket(5), 'tool/import-3-5');
  assert.equal(importBucket(12), 'tool/import-6');
  const store = memory();
  assert.equal(importAsked(7, ['b', 'a'], store), false);
  markImportAsked(7, ['b', 'a'], store);
  assert.equal(importAsked(7, ['a', 'b'], store), true, '순서와 무관');
  assert.equal(importAsked(7, ['a', 'b', 'c'], store), false, '공고가 늘면 다시 묻는다');
  assert.equal(importAsked(8, ['a', 'b'], store), false, '다른 계정');
});
