import test from 'node:test';
import assert from 'node:assert/strict';
import { announcementShareText, scoreShareShort, scoreShareText } from '../src/features/planner/shareUtils.js';
import { shareClipboardText, shareLink, toolShareText } from '../src/utils/share.util.js';

test('announcement share text is a single line with the tab name and TOADY', () => {
  assert.equal(announcementShareText({ houseNm: '운정 A단지', tab: 'detail' }), '운정 A단지 청약 일정·분양 정보 · TOADY');
  assert.equal(announcementShareText({ houseNm: '운정 A단지', tab: 'map' }), '운정 A단지 위치·입지 · TOADY');
  assert.equal(announcementShareText({ houseNm: '운정 A단지', tab: 'comment' }), '운정 A단지 청약 이야기 · TOADY');
  assert.equal(announcementShareText({ houseNm: '', tab: 'unknown' }), '청약 공고 청약 일정·분양 정보 · TOADY');
  const rows = [{ lwetScore: '52', topScore: '69' }, { lwetScore: '0', topScore: '90' }];
  const score = scoreShareShort(rows);
  assert.equal(score, '당첨가점 최저 52점 / 최고 69점');
  assert.equal(announcementShareText({ houseNm: '운정 A단지', tab: 'rate', score }), '운정 A단지 청약 경쟁률 · TOADY · 당첨가점 최저 52점 / 최고 69점');
  assert.equal(announcementShareText({ houseNm: '운정 A단지', tab: 'detail', score }), '운정 A단지 청약 일정·분양 정보 · TOADY', '경쟁률 탭에서만 가점을 붙인다');
  assert.ok(!announcementShareText({ houseNm: '운정 A단지', tab: 'rate', score }).includes('\n'));
  // 미리보기 설명용 긴 문구도 같은 계산을 쓴다.
  assert.equal(scoreShareText(rows), '공개 당첨가점 · 최저 52점 / 최고 69점 (공개 주택형·지역 전체 기준)');
  assert.equal(scoreShareShort([]), '');
});

test('share falls back to copying text and link on separate lines and omits an empty title', async () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  const calls = [];
  try {
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { clipboard: { writeText: async value => calls.push(['copy', value]) } } });
    assert.equal(await shareLink({ text: '한 줄', url: 'https://www.toady.co.kr/x' }), 'copied');
    assert.deepEqual(calls.pop(), ['copy', '한 줄\nhttps://www.toady.co.kr/x']);
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { share: async data => calls.push(['share', data]) } });
    assert.equal(await shareLink({ text: '한 줄', url: 'https://www.toady.co.kr/x' }), 'shared');
    assert.deepEqual(calls.pop(), ['share', { text: '한 줄', url: 'https://www.toady.co.kr/x' }]);
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { share: async () => { const error = new Error('닫음'); error.name = 'AbortError'; throw error; } } });
    assert.equal(await shareLink({ text: '한 줄', url: 'u' }), 'cancelled');
  } finally {
    if (original) Object.defineProperty(globalThis, 'navigator', original); else delete globalThis.navigator;
  }
  assert.equal(shareClipboardText('', 'u'), 'u');
  assert.equal(toolShareText(' 내 청약 가점 40점 '), '내 청약 가점 40점 · TOADY');
});
