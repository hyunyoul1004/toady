import { useAuthStore } from '@/features/auth/store/useAuthStore';

function identity(state) {
  return state.isAuthenticated ? `${state.user?.sub || ''}/${state.user?.userSeq || ''}` : null;
}

/** 계정 변경·로그아웃 시 진행 중인 이전 조회를 취소하고 활성 화면을 새 세션으로 재조회한다. */
export function bindAuthQueryCache(queryClient) {
  return useAuthStore.subscribe((next, previous) => {
    if (identity(next) !== identity(previous)) {
      void queryClient.resetQueries().catch(error => console.error('[Auth] 조회 초기화 실패:', error));
    }
  });
}
