import { create } from 'zustand';
import apiClient from '@/api/client';
import { anonymousVisitorId, trackAction } from '@/features/analytics/trackAction';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { base64UrlToBytes, detectPlatform, pushMode, scopeKey } from './pushSupport';

// localStorage 키. scope: 마지막으로 저장한 구독 범위, synced: 서버와 맞춘 범위의 비교 키(PushBootstrap이 다시 보낼지 판단).
const SCOPE_KEY = 'toady.push.scope';
/** 서버 기록이 사라져 같은 구독을 한 번 다시 저장한 주소. 또 사라지면 만료된 구독으로 보고 새로 만든다. */
const RELOST_KEY = 'toady.push.relost';
export const SYNCED_KEY = 'toady.push.synced';
const read = (key, fallback) => { try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; } };
const write = (key, value) => { try { localStorage.setItem(key, JSON.stringify(value)); } catch { /* 저장소 차단 시 이번 화면에서만 유지 */ } };

/** 지금 기기 판정에 알림 가능 방식(mode)을 붙인다. */
function platform() {
  if (typeof window === 'undefined') return { mode: 'unsupported' };
  const detected = detectPlatform(navigator, window);
  return { ...detected, mode: pushMode(detected) };
}
function permission() { try { return typeof Notification === 'undefined' ? 'default' : Notification.permission; } catch { return 'default'; } }
/** 등록된 서비스 워커를 찾고, 없으면 지금 등록한다(PushBootstrap이 먼저 등록하지만 실패했을 때를 대비). */
async function registration() {
  return (await navigator.serviceWorker.getRegistration('/')) || navigator.serviceWorker.register('/sw.js', { scope: '/' });
}
/** 구독 객체에서 서버에 보낼 값만 꺼낸다. endpoint: 브라우저 회사 푸시 서버 주소, p256dh·auth: 알림 내용을 암호화하는 키. */
function keys(subscription) {
  const json = subscription.toJSON();
  return { endpoint: json.endpoint, keys: { p256dh: json.keys?.p256dh, auth: json.keys?.auth } };
}

/** 기존 구독을 지우고 새로 만든다. 권한이 없거나 브라우저가 거절하면 null(화면은 꺼진 상태로 둔다). */
async function renewSubscription(reg, subscription, publicKey) {
  try {
    await subscription.unsubscribe();
    if (!publicKey || permission() !== 'granted') return null;
    return await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(publicKey) });
  } catch { return null; }
}

/**
 * 이 브라우저의 웹 푸시 상태. 권한 요청은 enable()에서만, 즉 사용자가 버튼을 눌렀을 때만 한다.
 * scope: 관심 공고 전날 일정(favorites)과 새 공고를 받을 생활권(areas). 비회원 관심 공고 ID는 PushBootstrap이 넣는다.
 *
 * 상태 값: status('loading' → 'ready'), platform(기기 판정 + mode), permission(브라우저 알림 권한),
 * enabled(서버가 푸시 기능을 켰는지), publicKey(VAPID 공개키), endpoint·subscribed(이 기기 구독), member(회원 구독인지).
 * 쓰는 곳: PushBootstrap(init·자동 저장), PushPrompt·PushSettingsDialog(켜기·끄기·테스트), usePushAttention(점 표시).
 * API: GET /api/v1/push/config, POST /api/v1/push/subscription/lookup, PUT /api/v1/push/subscription,
 *      POST /api/v1/push/unsubscribe, POST /api/v1/push/test
 * zustand create의 set은 상태 일부를 합쳐 넣고, get은 지금 상태를 읽는다(async 함수 안에서도 최신 값을 읽을 수 있다).
 */
export const usePushStore = create((set, get) => ({
  status: 'loading', platform: platform(), permission: permission(), enabled: false, publicKey: null,
  endpoint: null, subscribed: false, member: false, scope: read(SCOPE_KEY, { favorites: true, areas: [] }), favoriteIds: [],
  error: null,

  /** 앱 시작 때 한 번(PushBootstrap). 서버 설정을 받고, 이 기기에 남은 구독이 서버에도 있는지 확인한다. 권한은 묻지 않는다. */
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
    // 1) 브라우저 권한 창. 'granted'(허용)·'denied'(차단)·'default'(그냥 닫음) 중 하나가 온다.
    const result = await Notification.requestPermission();
    set({ permission: result });
    // 권한 거절은 다시 되돌리기 어려워 따로 센다(알림을 묻는 시점·문구가 맞는지 보는 지표).
    if (result === 'denied') trackAction('push_denied', 'tool/push');
    if (result !== 'granted') throw Error(result === 'denied' ? '알림이 차단되어 있어요. 브라우저 설정에서 TOADY 알림을 허용해 주세요.' : '알림 권한을 허용해야 받을 수 있어요.');
    // 2) 서비스 워커가 활성화될 때까지 기다린다(구독은 서비스 워커에 붙는다).
    const reg = await registration();
    await navigator.serviceWorker.ready;
    let subscription = await reg.pushManager.getSubscription();
    // 서버에 없는 기존 구독(만료로 지워졌을 수 있음)은 버리고 새로 만든다.
    if (subscription && !state.subscribed) subscription = await renewSubscription(reg, subscription, state.publicKey);
    // 3) 구독. userVisibleOnly: 받은 알림은 반드시 화면에 보여 준다는 약속(브라우저가 요구). applicationServerKey: VAPID 공개키.
    if (!subscription) subscription = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: base64UrlToBytes(state.publicKey) });
    set({ endpoint: subscription.endpoint });
    // 4) 구독 정보와 받을 범위를 서버에 저장한다.
    await get().save({ ...state.scope, ...scope });
    trackAction('push_enable', 'tool/push');
  },

  /** 범위를 저장한다. 로그인 상태면 서버가 회원 관심 공고로 연결한다. */
  async save(scope) {
    const reg = await navigator.serviceWorker.getRegistration('/');
    const subscription = reg && await reg.pushManager.getSubscription();
    if (!subscription) { set({ subscribed: false, endpoint: null }); return; }
    const next = { favorites: scope.favorites !== false, areas: [...new Set(scope.areas || [])].slice(0, 6) };
    // 익명 방문자 ID는 로그인하지 않은 구독에만 싣는다. 로그인 토큰과 함께 보내면 익명 통계가 회원과 연결될 수 있다.
    let visitorId;
    if (!useAuthStore.getState().isAuthenticated) { try { visitorId = anonymousVisitorId(); } catch { visitorId = undefined; } }
    const server = await apiClient.put('/api/v1/push/subscription', { ...keys(subscription), visitorId, ...next, favoriteIds: get().favoriteIds.slice(0, 50) });
    write(SCOPE_KEY, next);
    const auth = useAuthStore.getState();
    try { localStorage.setItem(SYNCED_KEY, scopeKey({ endpoint: subscription.endpoint, userSeq: auth.isAuthenticated ? auth.user?.userSeq ?? null : null, ...next, favoriteIds: get().favoriteIds })); } catch { /* 무시 */ }
    set({ subscribed: true, endpoint: subscription.endpoint, member: server?.member === true, scope: { favorites: server?.favorites !== false, areas: server?.areas || next.areas } });
  },

  /** 이 기기의 알림 끄기: 서버 기록을 지우고 브라우저 구독도 해제한다. 서버 요청이 실패해도 브라우저 쪽은 끈다. */
  async disable() {
    const reg = await navigator.serviceWorker.getRegistration('/');
    const subscription = reg && await reg.pushManager.getSubscription();
    if (subscription) {
      await apiClient.post('/api/v1/push/unsubscribe', { endpoint: subscription.endpoint }).catch(() => {});
      await subscription.unsubscribe().catch(() => {});
      trackAction('push_disable', 'tool/push');
    }
    set({ subscribed: false, endpoint: null });
  },

  /** 테스트 알림 보내기. announcementId가 있으면 알림을 눌렀을 때 그 공고로 간다. */
  async sendTest(announcementId) {
    const { endpoint } = get();
    if (!endpoint) throw Error('먼저 알림을 켜 주세요.');
    try { await apiClient.post('/api/v1/push/test', { endpoint, ...(announcementId ? { announcementId } : {}) }); }
    catch (error) {
      // 서버에 구독이 없거나(404) 만료됐으면(410) 화면도 꺼진 상태로 맞추고, 브라우저의 죽은 구독도 지워 다음 '알림 켜기'가 새로 만들게 한다.
      if (error.status === 404 || error.status === 410) {
        set({ subscribed: false, endpoint: null });
        const reg = await navigator.serviceWorker.getRegistration('/').catch(() => null);
        const subscription = reg && await reg.pushManager.getSubscription().catch(() => null);
        if (subscription) await subscription.unsubscribe().catch(() => {});
      }
      throw error;
    }
  }
}));
