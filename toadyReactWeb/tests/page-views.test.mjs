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
