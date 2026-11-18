import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import { SPA_PATHS, appPath, base64UrlToBytes, detectPlatform, isSpaPath, needsPushAttention, promptSnoozed, PROMPT_SNOOZE_MS, pushMode, scopeKey } from '../src/features/push/pushSupport.js';
import { pushOpenSubject } from '../src/features/analytics/pageViews.js';
import { kakaoTagged } from '../src/utils/kakaoShare.js';

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
  // 어떤 알림으로 들어왔는지 src 꼬리표를 붙인다(앱이 첫 기록 뒤 지운다).
  assert.deepEqual(sw.opened, ['https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1&src=push.digest']);
  await sw.fire('notificationclick', { notification, action: 'my' });
  assert.equal(sw.opened[1], 'https://www.toady.co.kr/my?src=push.digest');
  await sw.fire('notificationclick', { notification: { data: { url: 'https://evil.example/' }, close() {} }, action: '' });
  assert.equal(sw.opened[2], 'https://www.toady.co.kr/?src=push.other');

  // 이미 열린 TOADY 창이 있으면 새 창 대신 앱 라우터로 이동시킨다.
  const open = await serviceWorker(['https://www.toady.co.kr/my']);
  await open.fire('notificationclick', { notification, action: '' });
  assert.deepEqual(open.opened, []);
  assert.equal(JSON.stringify(open.messages[0].message), JSON.stringify({ type: 'toady-push-open', url: 'https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1&src=push.digest' }));
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
  assert.deepEqual(sw.navigated, [{ from: 'https://www.toady.co.kr/my', to: 'https://www.toady.co.kr/areas/unjeong?src=push.other' }]);
  assert.deepEqual(sw.opened, []);
  // 열린 창이 서버 페이지면 앱 라우터가 없으므로 앱 경로도 창 이동으로 처리한다.
  sw = await click(['https://www.toady.co.kr/weekly'], '/?subscriptSeq=apt%2FP1%2FN1');
  assert.deepEqual(sw.messages, []);
  assert.equal(sw.navigated[0].to, 'https://www.toady.co.kr/?subscriptSeq=apt%2FP1%2FN1&src=push.other');
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
  assert.equal(isSpaPath('/announcements/apt/P1/N1', origin), true, '공고 주소는 사람에게 앱 상세 화면이다');
  assert.equal(isSpaPath('/weekly', origin), false);
  assert.equal(isSpaPath('https://evil.example/my', origin), false);
  const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  const list = JSON.parse(source.match(/const SPA_PATHS = (\[[^\]]*\])/)[1].replace(/'/g, '"'));
  assert.deepEqual(list, SPA_PATHS);
});

test('지역별 청약 주소(/regions/시·도/시·군·구)는 앱 안의 지역 캘린더이고, 서비스 워커와 같은 식을 쓴다', async () => {
  const { REGION_PATH } = await import('../src/features/push/pushSupport.js');
  const origin = 'https://www.toady.co.kr';
  assert.equal(isSpaPath('/regions/%EA%B2%BD%EA%B8%B0%EB%8F%84/%ED%8C%8C%EC%A3%BC%EC%8B%9C', origin), true);
  assert.equal(isSpaPath(`/regions/${encodeURIComponent('전북특별자치도')}/`, origin), true);
  assert.equal(isSpaPath('/regions/a/b/c', origin), false);
  const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  assert.equal(source.match(/const REGION_PATH = (\/.*\/);/)[1], String(REGION_PATH));
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

test('공고 주소 위에 로그인 창을 열면 목록·상세·로그인 순으로 쌓는다', async () => {
  const { detailMarker, detailListBase, openLoginOver } = await import('../src/features/push/pushSupport.js');
  assert.deepEqual(detailMarker('/announcements/apt/1/1?tabType=comment'), { detailOverlay: true });
  assert.equal(detailListBase('/announcements/apt/1/1?tabType=comment'), '/');
  assert.equal(detailListBase('/my?subscriptSeq=apt%2F1%2F1&tabType=rate&x=1'), '/my?x=1');
  const calls = [];
  openLoginOver((to, options) => calls.push([to, options]), '/announcements/apt/1/1?tabType=comment');
  assert.deepEqual(calls.map(([to]) => to), ['/', '/announcements/apt/1/1?tabType=comment', '/announcements/apt/1/1?tabType=comment']);
  assert.equal(calls[0][1].replace, true);
  assert.deepEqual(calls[1][1].state, { detailOverlay: true });
  assert.deepEqual(calls[2][1].state, { detailOverlay: true, overlayLayers: [{ id: 'layout', value: 'login' }] });
  const plain = [];
  openLoginOver((to, options) => plain.push([to, options]), '/my');
  assert.deepEqual(plain.map(([to, o]) => [to, Boolean(o.replace)]), [['/my', true], ['/my', false]]);
});

test('공고 주소는 앱 안 경로이고, 서비스 워커와 같은 식을 쓴다', async () => {
  const { ANNOUNCEMENT_PATH, isSpaPath, detailMarker, detailListBase } = await import('../src/features/push/pushSupport.js');
  const origin = 'https://www.toady.co.kr';
  assert.equal(isSpaPath('/announcements/apt/2026000576/2026000576?tabType=rate', origin), true);
  assert.equal(isSpaPath('/announcements/evil/1/1', origin), false);
  assert.equal(isSpaPath('/weekly', origin), false);
  const source = await readFile(new URL('../public/sw.js', import.meta.url), 'utf8');
  assert.equal(source.match(/const ANNOUNCEMENT_PATH = (\/.*\/);/)[1], String(ANNOUNCEMENT_PATH));
  assert.deepEqual(detailMarker('/showSubscriptDetail?subscriptSeq=apt%2F1%2F1'), { detailOverlay: true });
  assert.equal(detailListBase('/showSubscriptDetail?subscriptSeq=apt%2F1%2F1'), '/');
});

test('notification clicks are tagged by kind so the app can count opens per notification', async () => {
  const sw = await serviceWorker();
  for (const [tag, source] of [['toady-result', 'push.result'], ['dm-12', 'push.message'], ['notice', 'push.notice'], ['notice-test', 'push.notice'], ['toady-test', 'push.other']]) {
    await sw.fire('notificationclick', { notification: { data: { url: '/my', tag }, close() {} }, action: '' });
    assert.equal(new URL(sw.opened.at(-1)).searchParams.get('src'), source, tag);
    assert.ok(pushOpenSubject(source), `${source} must be a known push source in pageViews.js`);
  }
  assert.equal(pushOpenSubject('push.result'), 'tool/push-result');
  assert.equal(pushOpenSubject('kakao.com'), null);
  assert.equal(pushOpenSubject(null), null);
});

test('the notification dot shows only when push can be turned on and settings were never opened', () => {
  const base = { status: 'ready', mode: 'ready', enabled: true, permission: 'default', subscribed: false };
  assert.equal(needsPushAttention(base, false), true);
  assert.equal(needsPushAttention(base, true), false, '설정을 한 번 열어 봤으면 점을 지운다');
  assert.equal(needsPushAttention({ ...base, subscribed: true }, false), false);
  assert.equal(needsPushAttention({ ...base, permission: 'denied' }, false), false, '거절했으면 켤 수 없으니 재촉하지 않는다');
  assert.equal(needsPushAttention({ ...base, status: 'loading' }, false), false);
  assert.equal(needsPushAttention({ ...base, mode: 'ios-home' }, false), true, '아이폰 사파리는 홈 화면 추가 안내로 이어진다');
  assert.equal(needsPushAttention({ ...base, mode: 'in-app' }, false), false);
});

test('a dismissed notification card comes back after 30 days', () => {
  const now = 1_800_000_000_000;
  assert.equal(promptSnoozed(String(now - 1000), now), true);
  assert.equal(promptSnoozed(String(now - PROMPT_SNOOZE_MS - 1), now), false);
  assert.equal(promptSnoozed('1', now), false, '예전 형식(영구 닫기)은 한 번 다시 보여 준다');
  assert.equal(promptSnoozed(null, now), false);
});

test('kakao shares carry a source tag so KakaoTalk visits are counted', () => {
  assert.equal(kakaoTagged('https://www.toady.co.kr/announcements/apt/1/1?tabType=rate'), 'https://www.toady.co.kr/announcements/apt/1/1?tabType=rate&src=kakao.com');
  assert.equal(kakaoTagged('not a url'), 'not a url');
});

test('홈 화면 추가: 이미 앱으로 열었거나 방법이 없는 PC 브라우저에서는 메뉴를 숨긴다', async () => {
  const { installMode, showInstallEntry } = await import('../src/features/install/installSupport.js');
  const base = { ios: false, safari: false, inApp: false, standalone: false };
  assert.equal(installMode({ ...base, standalone: true }, { canPrompt: true }), 'installed');
  assert.equal(installMode(base, { canPrompt: true }), 'prompt');
  assert.equal(installMode({ ...base, ios: true, safari: true }), 'ios');
  assert.equal(installMode({ ...base, ios: true, inApp: true }), 'ios-safari');
  assert.equal(installMode({ ...base, inApp: true }, { android: true }), 'in-app');
  assert.equal(installMode(base, { android: true }), 'android');
  assert.equal(installMode(base), 'none');
  assert.equal(installMode(base, { promptDismissed: true }), 'desktop');
  assert.equal(installMode(base, { android: true, promptDismissed: true }), 'android');
  assert.equal(showInstallEntry('installed'), false);
  assert.equal(showInstallEntry('none'), false);
  assert.equal(showInstallEntry('android'), true);
});

test('홈 화면 추가 안내는 안드로이드 브라우저마다 다른 순서를 고른다', async () => {
  const { androidBrowser } = await import('../src/features/install/installSupport.js');
  assert.equal(androidBrowser('Mozilla/5.0 (Linux; Android 14; SM-S918N) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/26.0 Chrome/122.0 Mobile Safari/537.36'), 'samsung');
  assert.equal(androidBrowser('Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36'), 'chrome');
  assert.equal(androidBrowser('Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Whale/3.28 Mobile Safari/537.36'), 'whale');
  assert.equal(androidBrowser('Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0'), 'firefox');
  assert.equal(androidBrowser('Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36 EdgA/131.0'), 'edge');
});

test('서비스 워커는 페이지 이동만 네트워크로 받고, 끊기면 안내 화면을 보여 준다(그 밖의 요청은 손대지 않음)', async () => {
  const listeners = {};
  const self = { location: new URL('https://www.toady.co.kr/sw.js'), addEventListener: (type, handler) => { listeners[type] = handler; }, skipWaiting() {}, clients: { claim: async () => {} } };
  const fetch = async () => { throw new TypeError('offline'); };
  vm.runInNewContext(await readFile(new URL('../public/sw.js', import.meta.url), 'utf8'), { self, URL, fetch, Response });
  let responded = null;
  listeners.fetch({ request: { mode: 'navigate', method: 'GET' }, respondWith: value => { responded = value; } });
  const page = await responded;
  assert.equal(page.status, 503);
  assert.match(await page.text(), /인터넷 연결이 끊겼어요/);
  responded = null;
  listeners.fetch({ request: { mode: 'cors', method: 'GET' }, respondWith: value => { responded = value; } });
  assert.equal(responded, null);
});
