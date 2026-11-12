import { create } from 'zustand';
import apiClient from '@/api/client';
import { anonymousVisitorId, trackAction } from '@/features/analytics/trackAction';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { base64UrlToBytes, detectPlatform, pushMode, scopeKey } from './pushSupport';

const SCOPE_KEY = 'toady.push.scope';
/** 서버 기록이 사라져 같은 구독을 한 번 다시 저장한 주소. 또 사라지면 만료된 구독으로 보고 새로 만든다. */
const RELOST_KEY = 'toady.push.relost';
export const SYNCED_KEY = 'toady.push.synced';
const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };
const write = (key, value) => { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 저장소 차단 시 이번 화면에서만 유지 */ } };

function platform() {
  if (typeof window === 'undefined') return { mode: 'unsupported' };
  const detected = detectPlatform(navigator, window);
  return { ...detected, mode: pushMode(detected) };
}
function permission() { try { return typeof Notification === 'undefined' ? 'default' : Notification.permission; } catch { return 'default'; } }
async function registration() {
  return (await navigator.serviceWorker.getRegistration('/')) || navigator.serviceWorker.register('/sw.js', { scope: '/' });
}
function keys(subscription) {
  const json = subscription.toJSON();
  return { endpoint: json.endpoint, keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth } };
}

/**
 * 이 브라우저의 웹 푸시 상태. 권한 요청은 enable()에서만, 즉 사용자가 버튼을 눌렀을 때만 한다.
 * scope: 관심 공고 전날 일정(favorites)과 새 공고를 받을 생활권(areas). 비회원 관심 공고 ID는 PushBootstrap이 넣는다.
 */
/** 기존 구독을 지우고 새로 만든다. 권한이 없거나 브라우저가 거절하면 null(화면은 꺼진 상태로 둔다). */
async function renewSubscription(reg, subscription, publicKey) {
  try {
    await subscription.unsubscribe();
    if (!publicKey || permission() !== 'granted') return null;
    return await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(publicKey) });
  } catch { return null; }
}

export const usePushStore = create((set, get) => ({
  status: 'loading', platform: platform(), permission: permission(), enabled: false, publicKey: null,
  endpoint: null, subscribed: false, member: false, scope: read(SCOPE_KEY, { favorites: true, areas: [] }), favoriteIds: [],
  error: null,

  async init() {
    const current = platform();
    set({ platform: current, permission: permission() });
    let config;
    try { config = await apiClient.get('/api/v1/push/config'); } catch { set({ status: 'ready', enabled: false }); return; }
    set({ enabled: config?.enabled === true, publicKey: config?.publicKey || null });
    if (!config?.enabled || current.mode !== 'ready') { set({ status: 'ready' }); return; }
    try {
      const reg = await navigator.serviceWorker.getRegistration('/');
      const subscription = reg && await reg.pushManager.getSubscription();
      if (!subscription || permission() !== 'granted') { set({ status: 'ready', subscribed: false, endpoint: null }); return; }
      const server = await apiClient.post('/api/v1/push/subscription/lookup', { endpoint: subscription.endpoint });
      set({ endpoint: subscription.endpoint, status: 'ready' });
      if (server?.subscribed) write(RELOST_KEY, null);
      if (server?.subscribed) set({ subscribed: true, member: server.member === true, scope: { favorites: server.favorites !== false, areas: server.areas || [] } });
      else {
        // 서버 기록이 사라졌다. 처음이면(서버 정리·이관 등) 같은 구독을 다시 저장해 복구한다.
        // 다시 저장했던 같은 주소가 또 사라졌다면 푸시 서버가 만료(410)로 거절하는 구독이므로, 같은 주소를 또 쓰지 않고
        // '꺼짐'으로 보여 준다. 새 구독은 사용자가 '알림 켜기'를 누를 때(enable, 사용자 동작 안) 만든다.
        // 페이지를 열 때 몰래 지우고 다시 만들지 않는다(사파리는 사용자 동작 없이 새 구독을 만들 수 없다).
        if (read(RELOST_KEY, null) === subscription.endpoint) { set({ subscribed: false }); return; }
        write(RELOST_KEY, subscription.endpoint);
        await get().save(get().scope);
      }
    } catch { set({ status: 'ready' }); }
  },

  setFavoriteIds(ids) { set({ favoriteIds: ids }); },

  /** 반드시 클릭 처리기 안에서 호출한다. 사파리는 사용자 동작 안에서만 권한 창을 띄운다. */
  async enable(scope = {}) {
    const state = get();
    if (state.platform.mode !== 'ready' || !state.enabled) throw Error('이 환경에서는 알림을 켤 수 없어요.');
    const result = await Notification.requestPermission();
    set({ permission: result });
    if (result !== 'granted') throw Error(result === 'denied' ? '알림이 차단되어 있어요. 브라우저 설정에서 TOADY 알림을 허용해 주세요.' : '알림 권한을 허용해야 받을 수 있어요.');
    const reg = await registration();
    await navigator.serviceWorker.ready;
    let subscription = await reg.pushManager.getSubscription();
    // 서버에 없는 기존 구독(만료로 지워졌을 수 있음)은 버리고 새로 만든다.
    if (subscription && !state.subscribed) subscription = await renewSubscription(reg, subscription, state.publicKey);
    if (!subscription) subscription = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(state.publicKey) });
    set({ endpoint: subscription.endpoint });
    await get().save({ ...state.scope, ...scope });
    trackAction('push_enable', 'tool/push');
  },

  /** 범위를 저장한다. 로그인 상태면 서버가 회원 관심 공고로 연결한다. */
  async save(scope) {
    const reg = await navigator.serviceWorker.getRegistration('/');
    const subscription = reg && await reg.pushManager.getSubscription();
    if (!subscription) { set({ subscribed: false, endpoint: null }); return; }
    const next = { favorites: scope.favorites !== false, areas: [...new Set(scope.areas || [])].slice(0, 6) };
    let visitorId; try { visitorId = anonymousVisitorId(); } catch { visitorId = undefined; }
    const server = await apiClient.put('/api/v1/push/subscription', { ...keys(subscription), visitorId, ...next, favoriteIds: get().favoriteIds.slice(0, 50) });
    write(SCOPE_KEY, next);
    const auth = useAuthStore.getState();
    try { localStorage.setItem(SYNCED_KEY, scopeKey({ endpoint: subscription.endpoint, userSeq: auth.isAuthenticated ? auth.user?.userSeq ?? null : null, ...next, favoriteIds: get().favoriteIds })); } catch { /* 무시 */ }
    set({ subscribed: true, endpoint: subscription.endpoint, member: server?.member === true, scope: { favorites: server?.favorites !== false, areas: server?.areas || next.areas } });
  },

  async disable() {
    const reg = await navigator.serviceWorker.getRegistration('/');
    const subscription = reg && await reg.pushManager.getSubscription();
    if (subscription) {
      await apiClient.post('/api/v1/push/unsubscribe', { endpoint: subscription.endpoint }).catch(() => {});
      await subscription.unsubscribe().catch(() => {});
    }
    set({ subscribed: false, endpoint: null });
  },

  async sendTest(announcementId) {
    const { endpoint } = get();
    if (!endpoint) throw Error('먼저 알림을 켜 주세요.');
    try { await apiClient.post('/api/v1/push/test', { endpoint, ...(announcementId ? { announcementId } : {}) }); }
    catch (error) {
      // 만료된 구독이면 서버가 지웠으므로 화면도 꺼진 상태로 맞추고, 브라우저의 죽은 구독도 지워 다음 '알림 켜기'가 새로 만들게 한다.
      if (/만료/.test(error.message)) {
        set({ subscribed: false, endpoint: null });
        const reg = await navigator.serviceWorker.getRegistration('/').catch(() => null);
        const subscription = reg && await reg.pushManager.getSubscription().catch(() => null);
        if (subscription) await subscription.unsubscribe().catch(() => {});
      }
      throw error;
    }
  }
}));
