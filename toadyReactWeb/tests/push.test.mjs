import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { SPA_PATHS, appPath, base64UrlToBytes, detectPlatform, isSpaPath, pushMode, scopeKey } from '../src/features/push/pushSupport.js';

const win = (standalone = false) => ({ isSecureContext: true, PushManager: function () {}, Notification: function () {}, matchMedia: () => ({ matches: standalone }) });
const nav = (userAgent, extra = {}) => ({ userAgent, serviceWorker: {}, ...extra });
const IPHONE_17 = 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1';

test('iPhone needs the home screen app; Android/desktop browsers can enable push directly', () => {
  assert.equal(pushMode(detectPlatform(nav(IPHONE_17), win(false))), 'ios-home');
  assert.equal(pushMode(detectPlatform(nav(IPHONE_17), win(true))), 'ready');
  assert.equal(pushMode(detectPlatform(nav(IPHONE_17, { standalone: true }), win(false))), 'ready');
  assert.equal(pushMode(detectPlatform(nav(IPHONE_17.replace('17_5', '16_3')), win(true))), 'ios-old');
  assert.equal(pushMode(detectPlatform(nav(IPHONE_17.replace('Version/17.5', 'CriOS/120.0')), win(false))), 'ios-safari');
  // iPadOS 13+ 사파리는 맥 UA를 쓴다.
  assert.equal(pushMode(detectPlatform(nav('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15', { platform: 'MacIntel', maxTouchPoints: 5 }), win(false))), 'ios-home');
  assert.equal(pushMode(detectPlatform(nav('Mozilla/5.0 (Linux; Android 14) Chrome/124.0 Mobile Safari/537.36'), win(false))), 'ready');
  assert.equal(pushMode(detectPlatform(nav('Mozilla/5.0 (Linux; Android 14; wv) KAKAOTALK 10.0'), { ...win(false), PushManager: undefined })), 'in-app');
  assert.equal(pushMode(detectPlatform(nav('Mozilla/5.0 (Windows NT 10.0) Chrome/124.0'), { ...win(false), isSecureContext: false })), 'unsupported');
});

test('VAPID key decoding, same-origin routing and scope comparison', () => {
  const bytes = base64UrlToBytes('BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8');
  assert.equal(bytes.length, 65);
  assert.equal(bytes[0], 4);
  assert.equal(appPath('https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1', 'https://www.toady.co.kr'), '/?subscriptSeq=apt%2FP1%2FN1');
  assert.equal(appPath('https://evil.example/my', 'https://www.toady.co.kr'), null);
  assert.equal(appPath('/my', 'https://www.toady.co.kr'), '/my');
  // 순서만 다른 범위는 같은 것으로 보고, 회원은 기기 관심 공고 목록을 비교하지 않는다.
  assert.equal(scopeKey({ endpoint: 'e', userSeq: null, areas: ['b', 'a'], favoriteIds: ['y', 'x'] }), scopeKey({ endpoint: 'e', userSeq: null, areas: ['a', 'b'], favoriteIds: ['x', 'y'] }));
  assert.equal(scopeKey({ endpoint: 'e', userSeq: 7, favoriteIds: ['x'] }), scopeKey({ endpoint: 'e', userSeq: 7, favoriteIds: [] }));
  assert.notEqual(scopeKey({ endpoint: 'e', userSeq: null }), scopeKey({ endpoint: 'e', userSeq: 7 }));
});

async function serviceWorker(windows = []) {
  const listeners = {}, shown = [], opened = [], messages = [], navigated = [];
  const self = {
    location: new URL('https://www.toady.co.kr/sw.js'),
    addEventListener: (type, handler) => { listeners[type] = handler; },
    skipWaiting() {},
    registration: { showNotification: async (title, options) => { shown.push({ title, options }); } },
    clients: {
      claim: async () => {},
      matchAll: async () => windows.map(url => ({
        url, focus: async () => {}, postMessage: message => messages.push({ url, message }),
        navigate: async target => { navigated.push({ from: url, to: target }); return {}; }
      })),
      openWindow: async url => { opened.push(url); }
    }
  };
  vm.runInNewContext(await readFile(new URL('../public/sw.js', import.meta.url), 'utf8'), { self, URL });
  const fire = async (type, event) => { let wait; listeners[type]({ ...event, waitUntil: promise => { wait = promise; } }); await wait; };
  return { fire, shown, opened, messages, navigated };
}

test('service worker shows the payload and opens only same-origin detail pages', async () => {
  const sw = await serviceWorker();
  await sw.fire('push', { data: { json: () => ({ title: '내일 접수 마감 · 운정', body: '본문', url: '/?subscriptSeq=apt%2FP1%2FN1', tag: 'toady-digest', actions: [{ action: 'my', title: '나의 청약', url: '/my' }] }) } });
  assert.equal(sw.shown[0].title, '내일 접수 마감 · 운정');
  assert.equal(sw.shown[0].options.tag, 'toady-digest');
  assert.equal(sw.shown[0].options.icon, '/icons/icon-192.png');
  const notification = { data: sw.shown[0].options.data, close() {} };
  await sw.fire('notificationclick', { notification, action: '' });
  assert.deepEqual(sw.opened, ['https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1']);
  await sw.fire('notificationclick', { notification, action: 'my' });
  assert.equal(sw.opened[1], 'https://www.toady.co.kr/my');
  await sw.fire('notificationclick', { notification: { data: { url: 'https://evil.example/' }, close() {} }, action: '' });
  assert.equal(sw.opened[2], 'https://www.toady.co.kr/');

  // 이미 열린 TOADY 창이 있으면 새 창 대신 앱 라우터로 이동시킨다.
  const open = await serviceWorker(['https://www.toady.co.kr/my']);
  await open.fire('notificationclick', { notification, action: '' });
  assert.deepEqual(open.opened, []);
  assert.equal(JSON.stringify(open.messages[0].message), JSON.stringify({ type: 'toady-push-open', url: 'https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1' }));
});

test('notification click: SPA window + SPA target uses the router, otherwise the window navigates', async () => {
  const click = async (windows, url) => {
    const sw = await serviceWorker(windows);
    await sw.fire('notificationclick', { notification: { data: { url }, close() {} }, action: '' });
    return sw;
  };
  // 서버 HTML 생활권 페이지로 가는 알림은 열린 앱 창이 있어도 창을 직접 이동시킨다.
  let sw = await click(['https://www.toady.co.kr/my'], '/areas/unjeong');
  assert.deepEqual(sw.messages, []);
  assert.deepEqual(sw.navigated, [{ from: 'https://www.toady.co.kr/my', to: 'https://www.toady.co.kr/areas/unjeong' }]);
  assert.deepEqual(sw.opened, []);
  // 열린 창이 서버 페이지면 앱 라우터가 없으므로 앱 경로도 창 이동으로 처리한다.
  sw = await click(['https://www.toady.co.kr/weekly'], '/?subscriptSeq=apt%2FP1%2FN1');
  assert.deepEqual(sw.messages, []);
  assert.equal(sw.navigated[0].to, 'https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1');
  // 앱 창 + 앱 경로(끝 슬래시 포함)는 메시지로 라우터 이동.
  sw = await click(['https://www.toady.co.kr/score/'], '/my');
  assert.equal(sw.messages.length, 1);
  assert.deepEqual(sw.navigated, []);
});

test('SPA path classification matches the service worker list', async () => {
  const origin = 'https://www.toady.co.kr';
  assert.equal(isSpaPath('/?subscriptSeq=apt%2FP1%2FN1&tabType=0', origin), true);
  assert.equal(isSpaPath('/my', origin), true);
  assert.equal(isSpaPath('/my/', origin), true);
  assert.equal(isSpaPath('/areas/unjeong', origin), false);
  assert.equal(isSpaPath('/announcements/apt/P1/N1', origin), false);
  assert.equal(isSpaPath('/weekly', origin), false);
  assert.equal(isSpaPath('https://evil.example/my', origin), false);
  const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  const list = JSON.parse(source.match(/const SPA_PATHS = (\[[^\]]*\])/)[1].replace(/'/g, '"'));
  assert.deepEqual(list, SPA_PATHS);
});

test('manifest declares same-origin 192/512 PNG icons and standalone display', async () => {
  const manifest = JSON.parse(await readFile(new URL('../public/manifest.webmanifest', import.meta.url), 'utf8'));
  assert.equal(manifest.display, 'standalone');
  assert.deepEqual(manifest.icons.map(icon => icon.sizes).sort(), ['192x192', '512x512', '512x512']);
  for (const icon of manifest.icons) {
    assert.ok(icon.src.startsWith('/icons/'));
    const png = await readFile(new URL(`../public${icon.src}`, import.meta.url));
    assert.equal(png.subarray(1, 4).toString(), 'PNG');
    assert.equal(png.readUInt32BE(16) + 'x' + png.readUInt32BE(20), icon.sizes);
  }
});

test('push click marks only an opened detail, with the marker of that screen', async () => {
  const { detailMarker } = await import('../src/features/push/pushSupport.js');
  assert.equal(detailMarker('/match/zupzup'), null, '상세 없는 화면에는 표식을 붙이지 않는다(탭 다시 누르기가 이전 화면으로 빠지지 않게)');
  assert.deepEqual(detailMarker('/?subscriptSeq=apt%2F1%2F1'), { detailOverlay: true });
  assert.deepEqual(detailMarker('/my?subscriptSeq=apt%2F1%2F1'), { plannerDetail: true });
  assert.equal(detailMarker('/faq?subscriptSeq=x'), null);
});

test('closing a detail goes back past the inline layers stacked on it', async () => {
  const { detailCloseSteps } = await import('../src/utils/overlay.util.js');
  assert.equal(detailCloseSteps({ detailOverlay: true }), 1);
  assert.equal(detailCloseSteps({ detailOverlay: true, overlayLayers: [{ id: 'price-map-complex', value: 'apt|1' }] }), 2);
  assert.equal(detailCloseSteps(null), 1);
});
