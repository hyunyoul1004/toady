/**
 * @file useAuthStore.js
 * @description Zustand 기반의 사용자 인증 전역 상태 스토어
 * - JWT Access Token 및 사용자 프로필 관리
 * - 로그인/로그아웃 상태 영속화 (localStorage 동기화)
 */
import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';

/**
 * 로컬 스토리지에서 기존 인증 정보를 로드합니다.
 */
function getInitialAuthState() {
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

export const useAuthStore = create((set) => ({
  ...getInitialAuthState(),

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
  window.addEventListener('auth-logout', () => {
    useAuthStore.getState().logout();
  });
}

export default useAuthStore;
