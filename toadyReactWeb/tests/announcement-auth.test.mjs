import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';

function token(exp, sub = 'member@example.test', userSeq = 1) {
  return `e30.${Buffer.from(JSON.stringify({ sub, userSeq, exp })).toString('base64url')}.signature`;
}

async function harness(fetch) {
  const storage = new Map();
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
    '@/utils/date.util': { formatDate: value => value, formatDateRange: () => '' }
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
  return {
    api: api.namespace.default,
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
