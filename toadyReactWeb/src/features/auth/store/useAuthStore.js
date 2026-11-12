/**
 * @file useAuthStore.js
 * @description Zustand 기반의 사용자 인증 전역 상태 스토어
 * - JWT Access Token 및 사용자 프로필 관리
 * - 로그인/로그아웃 상태 영속화 (localStorage 동기화)
 */
import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';

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
   * 로그아웃 시 인증 정보를 제거합니다.
   */
  renewToken: (requestToken, accessToken) => {
    // Ignore late responses after logout, another login or a newer renewal.
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

  logout: () => {
    localStorage.removeItem('user');
    set({
      accessToken: null,
      user: null,
      isAuthenticated: false
    });
  }
}));

// 401 이벤트 리스너 등록
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === 'user' || event.key === null) useAuthStore.getState().syncFromStorage();
  });
  window.addEventListener('auth-logout', () => {
    useAuthStore.getState().logout();
  });
}

export default useAuthStore;
