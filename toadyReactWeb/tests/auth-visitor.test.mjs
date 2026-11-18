import test from 'node:test';
import assert from 'node:assert/strict';
import { passwordProblem } from '../src/features/auth/passwordRule.js';
import { ensureVisitorId, createPageViewTracker } from '../src/features/analytics/pageViews.js';

test('password rule matches the server pattern (letters plus a digit or symbol, 6 or more)', () => {
  assert.equal(passwordProblem('abc123'), '');
  assert.equal(passwordProblem('abcde!'), '');
  assert.equal(passwordProblem('비밀번호abc'), '', '서버 \\W처럼 한글도 특수문자로 본다');
  assert.notEqual(passwordProblem('abcdef'), '', '영문만');
  assert.notEqual(passwordProblem('abcde_'), '', '밑줄은 특수문자가 아니다');
  assert.notEqual(passwordProblem('123456'), '', '숫자만');
  assert.notEqual(passwordProblem('ab1'), '', '6자 미만');
  assert.notEqual(passwordProblem(''), '');
});

test('visitor ID page reuses the stored ID, creates one when missing and never sends a page view', async () => {
  const data = new Map();
  const storage = { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value) };
  const id = '0f8fad5b-d9cb-469f-a165-70867728950e';
  data.set('toady.analytics.visitor', id);
  assert.equal(ensureVisitorId(storage, () => 'unused'), id);
  data.set('toady.analytics.visitor', 'broken');
  const created = ensureVisitorId(storage, () => '7c9e6679-7425-40de-944b-e07fc1f90ae7');
  assert.equal(created, '7c9e6679-7425-40de-944b-e07fc1f90ae7');
  assert.equal(data.get('toady.analytics.visitor'), created);
  assert.equal(ensureVisitorId({ getItem: () => { throw Error(); }, setItem: () => {} }, () => id), null);
  // 이 화면 경로는 조회 기록 대상이 아니다.
  const calls = [];
  const tracker = createPageViewTracker({ apiUrl: '', storage, randomUUID: () => id, fetcher: async (...args) => { calls.push(args); return { ok: true, status: 200 }; } });
  await tracker('/my-visitor-id');
  assert.equal(calls.length, 0);
});

test('password reset resend wait applies only to the same email', async () => {
  const { resendWaitLeft, RESEND_WAIT_MS } = await import('../src/features/auth/passwordRule.js');
  const sent = { email: 'me@toady.co.kr', at: 1000 };
  assert.equal(resendWaitLeft(sent, ' Me@Toady.co.kr ', 1000 + 60000), RESEND_WAIT_MS - 60000, '공백·대소문자만 다르면 같은 이메일');
  assert.equal(resendWaitLeft(sent, 'me@toady.co.k', 1000 + 60000), 0, '오타를 고친 다른 이메일은 바로');
  assert.equal(resendWaitLeft(sent, 'me@toady.co.kr', 1000 + RESEND_WAIT_MS), 0, '4분 지나면 다시');
  assert.equal(resendWaitLeft(null, 'me@toady.co.kr', 0), 0);
});

test('drafts are cleared by prefix only', async () => {
  const { clearAllDrafts, draftKey } = await import('../src/utils/draftStore.js');
  const data = new Map([[draftKey('dm', 7, 'thread-1'), '쓰던 글'], ['toady.other', 'keep']]);
  const storage = { get length() { return data.size; }, key: index => [...data.keys()][index] ?? null, removeItem: key => data.delete(key) };
  clearAllDrafts(storage);
  assert.deepEqual([...data.keys()], ['toady.other']);
  assert.equal(draftKey('dm', null, 'thread-1'), null, '회원이 없으면 저장하지 않는다');
});

test('home logo replaces history only when already on the bare home path', async () => {
  const { replaceHomeLink } = await import('../src/components/layout/navItems.js');
  assert.equal(replaceHomeLink({ pathname: '/', search: '' }), true);
  assert.equal(replaceHomeLink({ pathname: '/', search: '?subscriptSeq=apt/1/2' }), false);
  assert.equal(replaceHomeLink({ pathname: '/score', search: '' }), false);
});
