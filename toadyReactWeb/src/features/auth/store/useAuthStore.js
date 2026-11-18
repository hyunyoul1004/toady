/**
 * @file useAuthStore.js
 * @description Zustand 기반의 사용자 인증 전역 상태 스토어
 * - JWT Access Token 및 사용자 프로필 관리
 * - 로그인/로그아웃 상태 영속화 (localStorage 동기화)
 *
 * zustand의 create((set, get) => ({ 상태와 함수 }))로 전역 스토어를 만든다. 반환값 useAuthStore는 훅이면서 스토어 객체다.
 * - 컴포넌트 안: useAuthStore(state => state.user)처럼 selector로 필요한 값만 꺼내면 그 값이 바뀔 때만 다시 그린다.
 * - 컴포넌트 밖(이벤트·API 클라이언트 등): useAuthStore.getState().logout()처럼 바로 읽고 부른다.
 * - set(부분 객체)은 기존 상태에 합쳐진다. get()은 지금 상태를 읽는다.
 * zustand의 persist 미들웨어는 쓰지 않고, 'user' 키에 { accessToken }만 직접 저장한다. 프로필은 토큰(JWT)을 풀어서 얻는다.
 * api/client.js는 getState().accessToken으로 토큰을 꺼내 요청에 싣는다. 로그인 계정이 바뀌면 bindAuthQueryCache가 React Query 캐시를 정리한다.
 */
import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';
import config from 'config';
import { clearAllDrafts } from '@/utils/draftStore';

/**
 * 서버에 로그아웃을 알린다(POST /api/v1/auth/logout). 서버는 이 회원의 토큰 버전을 올려 이미 발급한 토큰을 모두 무효로 만든다.
 * 응답을 기다리지 않고(fire-and-forget), 실패해도 화면의 로그아웃은 그대로 진행한다.
 * api/client.js가 이 스토어를 불러 쓰므로 순환 참조를 피하려고 fetch를 직접 쓴다. 401 응답도 무시한다(이미 끊긴 토큰).
 */
function revokeOnServer(accessToken) {
  if (!accessToken || typeof fetch !== 'function') return;
  try {
    fetch(`${config.apiUrl}/api/v1/auth/logout`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${accessToken}` },
      // 로그아웃 직후 페이지를 옮겨도 요청이 끊기지 않게 한다
      keepalive: true
    }).catch(() => { /* 네트워크 실패는 무시 */ });
  } catch { /* fetch를 쓸 수 없는 환경 */ }
}

/** 저장된 로그인 정보의 토큰만 읽는다(없거나 깨졌으면 null). */
function readStoredToken() {
  try {
    const raw = localStorage.getItem('user');
    return raw ? JSON.parse(raw)?.accessToken || null : null;
  } catch {
    return null;
  }
}

/**
 * 로컬 스토리지에서 기존 인증 정보를 로드합니다.
 * @param {boolean} [dropExpired=false] 앱 시작 시에는 만료(exp)가 지난 토큰을 로그아웃 상태로 보고 지운다.
 *   실행 중 동기화에서는 시계 오차로 멀쩡한 세션을 끊지 않도록 서버의 401 응답에 맡긴다.
 */
function getInitialAuthState(dropExpired = false) {
  try {
    const raw = localStorage.getItem('user');
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed?.accessToken) {
        let userProfile = null;
        try {
          userProfile = jwtDecode(parsed.accessToken);
        } catch (e) {
          console.warn('[useAuthStore] JWT 토큰 디코딩 실패:', e);
        }
        if (!userProfile?.sub || userProfile.userSeq == null) throw new Error('유효하지 않은 저장된 로그인 정보');
        if (dropExpired && typeof userProfile.exp === 'number' && userProfile.exp * 1000 <= Date.now()) {
          // 만료된 토큰으로 로그인된 것처럼 보였다가 첫 요청에서 갑자기 로그아웃되는 일을 막는다.
          try { localStorage.removeItem('user'); } catch { /* 저장소 접근 불가 */ }
          return { accessToken: null, user: null, isAuthenticated: false };
        }
        return {
          accessToken: parsed.accessToken,
          user: userProfile,
          isAuthenticated: Boolean(parsed.accessToken)
        };
      }
    }
  } catch (e) {
    console.error('[useAuthStore] 초기 인증 로드 실패:', e);
  }

  return {
    accessToken: null,
    user: null,
    isAuthenticated: false
  };
}

export const useAuthStore = create((set, get) => ({
  ...getInitialAuthState(true),

  /**
   * 다른 탭의 로그인 변경을 반영한다. 토큰이 그대로면 새 user 객체를 만들지 않아 구독 화면이 다시 그려지지 않는다.
   */
  syncFromStorage: () => {
    if (readStoredToken() === get().accessToken) return;
    set(getInitialAuthState());
  },

  /**
   * 로그인 성공 시 토큰과 프로필 정보를 저장합니다.
   * @param {string} accessToken JWT 액세스 토큰
   */
  login: (accessToken) => {
    let userProfile = null;
    try {
      userProfile = jwtDecode(accessToken);
    } catch (e) {
      console.warn('[useAuthStore] 로그인 JWT 디코딩 실패:', e);
    }

    if (!userProfile?.sub || userProfile.userSeq == null) throw new Error('유효하지 않은 로그인 응답입니다. 다시 로그인해 주세요.');
    localStorage.setItem('user', JSON.stringify({ accessToken }));
    set({
      accessToken,
      user: userProfile,
      isAuthenticated: true
    });
  },

  /**
   * 토큰 연장 응답으로 받은 새 토큰을 반영한다(api/client.js가 부른다).
   * @param {string} requestToken 연장을 요청할 때 쓰던 토큰
   * @param {string} accessToken 서버가 새로 준 토큰
   * 같은 계정이고 만료 시각이 더 늦을 때만 바꾼다.
   */
  renewToken: (requestToken, accessToken) => {
    // 로그아웃·다른 계정 로그인·더 최근 연장 뒤에 늦게 도착한 응답은 버린다.
    useAuthStore.getState().syncFromStorage();
    if (useAuthStore.getState().accessToken !== requestToken) return;
    let user;
    try {
      user = jwtDecode(accessToken);
      const current = useAuthStore.getState().user;
      if (user.sub !== current?.sub || user.userSeq !== current?.userSeq) return;
      if ((user.exp || 0) <= (current?.exp || 0)) return;
    } catch {
      return;
    }
    localStorage.setItem('user', JSON.stringify({ accessToken }));
    set({ accessToken, user, isAuthenticated: true });
  },

  /**
   * 로그아웃 시 인증 정보를 제거합니다.
   * 회원이 직접 로그아웃하면 서버에도 알려 다른 곳에 남은 같은 토큰을 쓸 수 없게 한다(모든 기기에서 로그아웃).
   * @param {{ server?: boolean, clearDrafts?: boolean }} [options] server=false면 서버에 알리지 않는다(401로 이미 끊긴 세션 정리 등).
   * clearDrafts: 이 탭의 쓰던 글 초안(sessionStorage 'toady.draft.')을 지울지. 기본은 회원이 직접 로그아웃할 때(server를 끄지 않았을 때)만 지운다.
   * 401 만료 경로(server:false)는 지우지 않아, 다시 로그인하면 쓰던 쪽지를 이어 쓸 수 있다.
   */
  logout: (options) => {
    const notifyServer = !(options && options.server === false);
    if (notifyServer) revokeOnServer(get().accessToken);
    if (options?.clearDrafts ?? notifyServer) clearAllDrafts();
    localStorage.removeItem('user');
    set({
      accessToken: null,
      user: null,
      isAuthenticated: false
    });
  }
}));

// 전역 이벤트 리스너(모듈을 처음 불러올 때 한 번 등록).
// - storage: 다른 탭에서 로그인·로그아웃하면 브라우저가 이 탭에 알려 준다. 같은 계정 상태로 맞춘다.
// - auth-logout: API가 401(로그인 만료)을 돌려주면 api/client.js가 보내는 이벤트. 로그아웃 처리한다.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === 'user' || event.key === null) useAuthStore.getState().syncFromStorage();
  });
  window.addEventListener('auth-logout', () => {
    // 서버가 이미 토큰을 거부했으므로(401) 다시 알리지 않는다.
    useAuthStore.getState().logout({ server: false });
  });
}

export default useAuthStore;
