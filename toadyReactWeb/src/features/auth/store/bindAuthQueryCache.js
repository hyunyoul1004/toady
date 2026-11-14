import { useAuthStore } from '@/features/auth/store/useAuthStore';

function identity(state) {
  return state.isAuthenticated ? `${state.user?.sub || ''}/${state.user?.userSeq || ''}` : null;
}

/**
 * 로그인 계정에 따라 결과가 달라지는 조회의 첫 키.
 * planner(관심 공고)·plannerSettings(알림 설정)·calendarFeed(개인 캘린더 주소)·ratePoll(내 투표 여부)
 */
const USER_QUERY_ROOTS = new Set(['planner', 'plannerSettings', 'calendarFeed', 'ratePoll']);

/** 계정별 조회인지 판정한다. 공고 댓글은 작성자 표시·권한이 계정에 따라 달라 함께 비운다. */
function isUserScopedQuery(queryKey = []) {
  const [root, kind] = queryKey;
  return USER_QUERY_ROOTS.has(root) || (root === 'announcements' && kind === 'comments');
}

/**
 * 계정 변경·로그아웃 시 계정별 조회는 이전 계정 데이터를 지우고(reset) 진행 중인 요청을 취소한다.
 * 공고 목록·상세 같은 공용 조회는 화면을 비우지 않고 다시 받아오기만 한다(invalidate, 진행 중 요청은 취소 후 재조회).
 */
export function bindAuthQueryCache(queryClient) {
  return useAuthStore.subscribe((next, previous) => {
    if (identity(next) !== identity(previous)) {
      void Promise.all([
        queryClient.resetQueries({ predicate: query => isUserScopedQuery(query.queryKey) }),
        queryClient.invalidateQueries({ predicate: query => !isUserScopedQuery(query.queryKey) })
      ]).catch(error => console.error('[Auth] 조회 초기화 실패:', error));
    }
  });
}
