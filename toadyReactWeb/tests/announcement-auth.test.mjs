import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';
import { QueryClient, QueryObserver } from '@tanstack/react-query';

function token(exp, sub = 'member@example.test', userSeq = 1) {
  return `e30.${Buffer.from(JSON.stringify({ sub, userSeq, exp })).toString('base64url')}.signature`;
}

async function harness(fetch, initialStorage = {}) {
  const storage = new Map(Object.entries(initialStorage));
  const listeners = new Map();
  const context = vm.createContext({
    console, URLSearchParams, Event,
    fetch,
    localStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value),
      removeItem: key => storage.delete(key)
    },
    window: {
      addEventListener: (name, fn) => listeners.set(name, fn),
      dispatchEvent: event => listeners.get(event.type)?.(event)
    }
  });
  const mocks = {
    config: { default: { apiUrl: '' } },
    zustand: { create },
    'jwt-decode': { jwtDecode },
    '@/utils/date.util': { formatDate: value => value, formatDateRange: (start, end) => `${start || ''}~${end || ''}` }
  };
  const cache = new Map();
  async function load(specifier) {
    if (cache.has(specifier)) return cache.get(specifier);
    let module;
    if (mocks[specifier]) {
      const values = mocks[specifier];
      module = new vm.SyntheticModule(Object.keys(values), function () {
        for (const [key, value] of Object.entries(values)) this.setExport(key, value);
      }, { context });
    } else {
      const path = specifier.replace(/^@\//, 'src/');
      module = new vm.SourceTextModule(await fs.readFile(new URL(`../${path}.js`, import.meta.url), 'utf8'), { context });
    }
    cache.set(specifier, module);
    return module;
  }
  const api = await load('@/features/announcement/api/announcementApi');
  await api.link(load);
  await api.evaluate();
  const auth = await load('@/features/auth/api/authApi');
  await auth.link(load); await auth.evaluate();
  const binding = await load('@/features/auth/store/bindAuthQueryCache');
  await binding.link(load); await binding.evaluate();
  return {
    auth: auth.namespace.default,
    bindCache: binding.namespace.bindAuthQueryCache,
    dispatchStorage: key => listeners.get('storage')?.({ key }),
    api: api.namespace.default,
    getCompetitionType: api.namespace.getCompetitionType,
    client: cache.get('@/api/client').namespace,
    store: cache.get('@/features/auth/store/useAuthStore').namespace.useAuthStore,
    storage
  };
}

function response(data, renewed) {
  return new Response(JSON.stringify(data), {
    status: 200,
    headers: renewed ? { Authorization: `Bearer ${renewed}` } : {}
  });
}

function row(id) {
  return { type: 'urban', houseManageNo: String(id), pblancNo: String(id), rceptBgnde: '2026-11-10' };
}

test('calendar fetches all pages and keeps all 201 announcements', async () => {
  const pages = [];
  const h = await harness(async url => {
    const page = Number(new URL(url, 'http://localhost').searchParams.get('page'));
    pages.push(page);
    const count = page === 2 ? 1 : 100;
    return response({ data: { content: Array.from({ length: count }, (_, i) => row(page * 100 + i)), page, size: 100, totalPages: 3, totalElements: 201 } });
  });
  const result = await h.api.getAnnouncements({ startStr: '2026-11-01', endStr: '2026-11-30' });
  assert.deepEqual(pages, [0, 1, 2]);
  assert.equal(result.data.resultList.length, 201);
});

test('explicit page request stays paginated', async () => {
  let calls = 0;
  const h = await harness(async () => {
    calls += 1;
    return response({ data: { content: [row(100)], totalPages: 3 } });
  });
  await h.api.getAnnouncements({ page: 1 });
  assert.equal(calls, 1);
});

test('calendar rejects incomplete data if a later page fails', async () => {
  let calls = 0;
  const h = await harness(async () => {
    if (++calls > 1) throw new Error('page unavailable');
    return response({ data: { content: [row(1)], totalPages: 2 } });
  });
  await assert.rejects(h.api.getAnnouncements(), /page unavailable/);
});

test('renewed JWT updates both storage and Zustand', async () => {
  const old = token(1000), renewed = token(2000);
  const h = await harness(async () => response({}, renewed));
  h.store.getState().login(old);
  await h.client.apiClient.get('/example');
  assert.equal(h.store.getState().accessToken, renewed);
  assert.equal(JSON.parse(h.storage.get('user')).accessToken, renewed);
});

test('late renewal cannot restore a logged-out or different session', async () => {
  const old = token(1000);
  const h = await harness(async () => response({}));
  h.store.getState().login(old);
  h.store.getState().logout();
  await h.client.handleResponse(response({}, token(2000)), old);
  assert.equal(h.store.getState().accessToken, null);
  const other = token(3000, 'other@example.test', 2);
  h.store.getState().login(other);
  await h.client.handleResponse(response({}, token(4000)), old);
  assert.equal(h.store.getState().accessToken, other);
});

test('stale unauthorized response does not clear a newer session', async () => {
  const old = token(1000), current = token(2000);
  const h = await harness(async () => response({}));
  h.store.getState().login(current);
  await assert.rejects(h.client.handleResponse(new Response('{}', { status: 401 }), old));
  assert.equal(h.store.getState().accessToken, current);
});


test('comments collect older parents with a monotonic cursor', async () => {
  const cursors = [];
  const h = await harness(async url => {
    const before = new URL(url, 'http://localhost').searchParams.get('beforeCommentSeq');
    cursors.push(before);
    return response({ data: { content: before ? [{ commentSeq: 1, parentCommentSeq: null }] : Array.from({ length: 100 }, (_, i) => ({ commentSeq: 101 - i, parentCommentSeq: 1 })), totalPages: before ? 1 : 2, totalElements: before ? 1 : 101 } });
  });
  const result = await h.api.getComments({ type: 'apt', houseManageNo: '1', pblancNo: '1' });
  assert.deepEqual(cursors, [null, '2']);
  assert.equal(result.data.subscriptCommentList.length, 101);
  assert.equal(result.data.totalElements, 101);
});

test('comments reject partial results when an older page fails', async () => {
  let calls = 0;
  const h = await harness(async () => {
    if (++calls > 1) throw new Error('comments unavailable');
    return response({ data: { content: [{ commentSeq: 10 }], totalPages: 2 } });
  });
  await assert.rejects(h.api.getComments({ type: 'apt', houseManageNo: '1', pblancNo: '1' }), /comments unavailable/);
});


test('APT detail uses API reception start and end fields', async () => {
  const h = await harness(async () => response({ data: { announcement: {
    rceptBgnde: '2026-11-01', rceptEndde: '2026-11-04',
    gnrlRnk1CrspareaRcptde: '2026-11-02', gnrlRnk1CrspareaEndde: '2026-11-03',
    gnrlRnk1EtcAreaRcptde: '2026-11-03', gnrlRnk1EtcAreaEndde: '2026-11-04',
    gnrlRnk2CrspareaRcptde: '2026-11-04', gnrlRnk2CrspareaEndde: '2026-11-04'
  }, models: [] } }));
  const detail = (await h.api.getAnnouncementDetail({ type: 'apt', houseManageNo: '1', pblancNo: '1' })).data.subscriptDetail;
  assert.equal(detail.onePriorityArea, '2026-11-02~2026-11-03');
  assert.equal(detail.oneOtherAreas, '2026-11-03~2026-11-04');
  assert.equal(detail.twoPriorityArea, '2026-11-04~2026-11-04');
  assert.equal(detail.subscriptDate, '2026-11-01~2026-11-04');
});

test('urban detail preserves its API address for display and map lookup', async () => {
  const h = await harness(async () => response({ data: { announcement: { hssplyHshldco: '서울특별시 실제주소 1' }, models: [] } }));
  const detail = (await h.api.getAnnouncementDetail({ type: 'urban', houseManageNo: '1', pblancNo: '1' })).data.subscriptDetail;
  assert.equal(detail.hssplyAdres, '서울특별시 실제주소 1');
  assert.equal(detail.supplyLocation, '서울특별시 실제주소 1');
});

test('cancelled resupply uses its competition endpoint while keeping remaining detail type', async () => {
  const urls = [];
  const h = await harness(async url => { urls.push(url); return response({ data: [] }); });
  const detail = { type: 'remaining', houseSecd: '06', houseManageNo: '1', pblancNo: '2' };
  const type = h.getCompetitionType(detail);
  assert.equal(type, 'cancel-resupply');
  await h.api.getCompetitionRates({ ...detail, type });
  assert.match(urls[0], /\/cancel-resupply\/announcements\/1\/2\/competition-rates$/);
  assert.equal(detail.type, 'remaining');
  assert.equal(h.getCompetitionType({ type: 'remaining', houseSecd: '04' }), 'remaining');
  assert.equal(h.getCompetitionType({ type: 'apt' }), 'apt');
});


test('public authentication calls omit expired JWT and preserve the current session', async () => {
  const requests = [];
  const h = await harness(async (url, options) => { requests.push({ url, headers: options.headers }); return response({ resultCode: 'SUCCESS' }); });
  const old = token(1); h.store.getState().login(old);
  await h.auth.login({ userEmail: 'member@example.test', userPassword: 'pass' });
  await h.auth.register({}); await h.auth.verifyEmail({});
  await h.auth.resendAccountVerification('member@example.test');
  await h.auth.requestPasswordReset({}); await h.auth.confirmPasswordChange({});
  assert.equal(requests.length, 6);
  for (const request of requests) {
    assert.equal(request.headers.Authorization, undefined);
    assert.equal(request.headers['X-Requested-With'],'XMLHttpRequest');
  }
  assert.equal(h.store.getState().accessToken, old);
  await h.client.apiClient.post('/protected', {});
  assert.equal(requests.at(-1).headers.Authorization, `Bearer ${old}`);
});

test('verification errors keep the original failure without posting to a missing legacy endpoint', async () => {
  const urls = [];
  const h = await harness(async url => { urls.push(url); return new Response(JSON.stringify({ resultMessage: 'temporary outage' }), { status: 503 }); });
  await assert.rejects(h.auth.verifyEmail({}), /temporary outage/);
  assert.deepEqual(urls, ['/webApi/certify/certify-check']);
});

test('storage account changes synchronize UI, request headers and cached query data', async () => {
  const requests = [];
  const h = await harness(async (url, options) => { requests.push(options.headers.Authorization); return response({}); });
  const cache = new QueryClient();
  const old = token(1000), next = token(2000, 'other@example.test', 2);
  h.store.getState().login(old);
  const unbind = h.bindCache(cache);
  cache.setQueryData(['planner'], { name: 'old account' });
  cache.setQueryData(['announcements', 'list', { startDate: '2026-11-01' }], { data: { resultList: [1] } });
  const before = h.store.getState().user;
  // 같은 토큰으로 다시 동기화하면 새 user 객체를 만들지 않는다(구독 화면 재렌더 방지).
  h.dispatchStorage('user');
  assert.equal(h.store.getState().user, before);
  h.storage.set('user', JSON.stringify({ accessToken: next })); h.dispatchStorage('user');
  assert.equal(h.store.getState().user.userSeq, 2);
  assert.equal(cache.getQueryData(['planner']), undefined);
  // 공용 공고 목록은 비우지 않고 다시 받아오도록 표시만 한다.
  assert.deepEqual(cache.getQueryData(['announcements', 'list', { startDate: '2026-11-01' }]), { data: { resultList: [1] } });
  assert.equal(cache.getQueryState(['announcements', 'list', { startDate: '2026-11-01' }]).isInvalidated, true);
  await h.client.apiClient.post('/protected', {});
  assert.equal(requests[0], `Bearer ${next}`);
  cache.setQueryData(['planner'], { name: 'new account' });
  const renewed = token(3000, 'other@example.test', 2);
  h.storage.set('user', JSON.stringify({ accessToken: renewed })); h.dispatchStorage('user');
  assert.equal(cache.getQueryData(['planner']).name, 'new account');
  h.storage.delete('user'); h.dispatchStorage('user');
  assert.equal(h.store.getState().isAuthenticated, false);
  assert.equal(cache.getQueryData(['planner']), undefined);
  unbind(); cache.clear();
});

test('a late response cannot overwrite another tabs session before its storage event arrives', async () => {
  const h = await harness(async () => response({}));
  const old = token(1000), next = token(2000, 'other@example.test', 2);
  h.store.getState().login(old);
  h.storage.set('user', JSON.stringify({ accessToken: next }));
  await h.client.handleResponse(response({}, token(3000)), old);
  assert.equal(h.store.getState().accessToken, next);
  assert.equal(JSON.parse(h.storage.get('user')).accessToken, next);
  await assert.rejects(h.client.handleResponse(new Response('{}', { status: 401 }), old));
  assert.equal(h.store.getState().accessToken, next);
});


test('account switch cancels old cached queries so a late result cannot replace the new profile', async () => {
  const h = await harness(async () => response({}));
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  h.store.getState().login(token(1000));
  const unbind = h.bindCache(cache);
  let finishOld;
  const observer = new QueryObserver(cache, { queryKey: ['planner'], queryFn: () => {
    const userSeq = h.store.getState().user.userSeq;
    return userSeq === 1 ? new Promise(resolve => { finishOld = resolve; }) : Promise.resolve({ userSeq });
  } });
  const unsubscribe = observer.subscribe(() => {});
  assert.equal(typeof finishOld, 'function');
  h.storage.set('user', JSON.stringify({ accessToken: token(2000, 'other@example.test', 2) }));
  h.dispatchStorage('user');
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(cache.getQueryData(['planner']).userSeq, 2);
  finishOld({ userSeq: 1 });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(cache.getQueryData(['planner']).userSeq, 2);
  unsubscribe(); unbind(); cache.clear();
});


test('startup treats an expired stored JWT as logged out and clears it', async () => {
  const expired = await harness(async () => response({}), { user: JSON.stringify({ accessToken: token(1000) }) });
  assert.equal(expired.store.getState().isAuthenticated, false);
  assert.equal(expired.storage.has('user'), false);
  const future = Math.floor(Date.now() / 1000) + 3600;
  const valid = await harness(async () => response({}), { user: JSON.stringify({ accessToken: token(future) }) });
  assert.equal(valid.store.getState().isAuthenticated, true);
  assert.equal(valid.store.getState().user.exp, future);
});

test('map SDK loading has a deadline even after the delayed load function appears', async () => {
  const source = await fs.readFile(new URL('../src/config/ensureKakaoLoaded.js', import.meta.url), 'utf8');
  const { ensureKakaoLoaded } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const target = {};
  let called = false;
  const result = assert.rejects(ensureKakaoLoaded(target, 150), /지연/);
  setTimeout(() => { target.kakao = { maps: { load: () => { called = true; } } }; }, 10);
  await result;
  assert.equal(called, true);
});

test('map SDK success resolves after initialization', async () => {
  const source = await fs.readFile(new URL('../src/config/ensureKakaoLoaded.js', import.meta.url), 'utf8');
  const { ensureKakaoLoaded } = await import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`);
  const maps = { load: callback => { maps.Map = function Map() {}; callback(); } };
  const kakao = { maps };
  assert.equal(await ensureKakaoLoaded({ kakao }, 100), kakao);
});
