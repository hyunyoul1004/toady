import test from 'node:test';
import assert from 'node:assert/strict';
import { createPageViewTracker } from '../src/features/analytics/pageViews.js';

function harness(blocked = false) {
  const data = new Map();
  const calls = [];
  let time = 0, seq = 0;
  const tracker = createPageViewTracker({ apiUrl: '',
    storage: { getItem: key => { if (blocked) throw Error(); return data.get(key); },
      setItem: (key, value) => { if (blocked) throw Error(); data.set(key, value); } },
    randomUUID: () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`,
    now: () => time,
    fetcher: async (url, options) => { calls.push({ url, ...options }); return { ok: calls.length > 1, status: 503 }; }
  });
  return { tracker, calls, data, advance: value => { time += value; } };
}

test('retry reuses event ID, omits credentials and never sends query secrets', async () => {
  const h = harness();
  await h.tracker('/certify?code=secret');
  assert.equal(h.calls.length, 0);
  await h.tracker('/certify');
  assert.equal(h.calls.length, 2);
  assert.equal(h.calls[0].body, h.calls[1].body);
  assert.equal(h.calls[0].credentials, 'omit');
  assert.equal(h.calls[0].headers.Authorization, undefined);
  assert.deepEqual(Object.keys(JSON.parse(h.calls[0].body)).sort(), ['eventId', 'path', 'sessionId', 'visitorId']);
});

test('visitor persists and session changes after 30 minutes of inactivity', async () => {
  const h = harness();
  await h.tracker('/');
  const first = JSON.parse(h.calls.at(-1).body);
  h.advance(1000);
  await h.tracker('/login');
  assert.equal(JSON.parse(h.calls.at(-1).body).sessionId, first.sessionId);
  h.advance(30 * 60 * 1000);
  await h.tracker('/');
  const last = JSON.parse(h.calls.at(-1).body);
  assert.equal(last.visitorId, first.visitorId);
  assert.notEqual(last.sessionId, first.sessionId);
  assert.notEqual(last.eventId, first.eventId);
});

test('blocked storage does not prevent tracking or stable in-memory identity', async () => {
  const h = harness(true);
  await h.tracker('/');
  const first = JSON.parse(h.calls.at(-1).body);
  await h.tracker('/login');
  assert.equal(JSON.parse(h.calls.at(-1).body).visitorId, first.visitorId);
  assert.equal(JSON.parse(h.calls.at(-1).body).sessionId, first.sessionId);
});

test('referrer host is limited to an external domain and passes through TOADY public pages only', async () => {
  const { externalReferrer } = await import('../src/features/analytics/pageViews.js');
  assert.equal(externalReferrer('https://www.google.com/search?q=%EC%9A%B4%EC%A0%95', '', 'www.toady.co.kr'), 'www.google.com');
  assert.equal(externalReferrer('https://m.search.naver.com/search.naver?query=x', '?src=www.google.com', 'www.toady.co.kr'), 'm.search.naver.com',
    'a src left in a shared link must not override the real external source');
  assert.equal(externalReferrer('https://www.toady.co.kr/areas/unjeong', '?area=unjeong&src=m.search.naver.com', 'www.toady.co.kr'), 'm.search.naver.com');
  assert.equal(externalReferrer('https://toady.co.kr/announcements/apt/1/1', '?src=evil.toady.co.kr', 'www.toady.co.kr'), null);
  assert.equal(externalReferrer('https://www.toady.co.kr/', '?src=<script>', 'www.toady.co.kr'), null);
  assert.equal(externalReferrer('', '?src=www.google.com', 'www.toady.co.kr'), null, 'direct visits cannot claim a source');
  assert.equal(externalReferrer('http://localhost:3000/', '?src=www.google.com', 'localhost'), 'www.google.com');
  assert.equal(externalReferrer('http://localhost:3000/', '', 'www.toady.co.kr'), null, 'single-label hosts are not recorded');
});

test('referrer is sent once with the first new session of a page load', async () => {
  const data = new Map(), calls = [];
  let time = 0, seq = 0, asked = 0;
  const tracker = createPageViewTracker({ apiUrl: '', storage: { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value) },
    randomUUID: () => `00000000-0000-4000-8000-${String(++seq).padStart(12, '0')}`, now: () => time,
    referrerHost: () => { asked++; return 'www.google.com'; },
    fetcher: async (url, options) => { calls.push(JSON.parse(options.body)); return { ok: true, status: 204 }; } });
  await tracker('/');
  await tracker('/my');
  time += 31 * 60 * 1000;
  await tracker('/');
  assert.equal(calls[0].referrerHost, 'www.google.com');
  assert.equal(calls[1].referrerHost, undefined);
  assert.equal(calls[2].referrerHost, undefined, 'an expired session in the same tab must not reuse the old referrer');
  assert.equal(asked, 1);
});

test('a returning session in the same 30 minutes keeps its original attribution', async () => {
  const data = new Map([['toady.analytics.session', JSON.stringify({ id: '00000000-0000-4000-8000-000000000999', lastSeen: 0 })],
    ['toady.analytics.visitor', '00000000-0000-4000-8000-000000000998']]);
  const calls = [];
  const tracker = createPageViewTracker({ apiUrl: '', storage: { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value) },
    randomUUID: () => '00000000-0000-4000-8000-000000000001', now: () => 1000, referrerHost: () => 'www.google.com',
    fetcher: async (url, options) => { calls.push(JSON.parse(options.body)); return { ok: true, status: 204 }; } });
  await tracker('/');
  assert.equal(calls[0].referrerHost, undefined);
});
