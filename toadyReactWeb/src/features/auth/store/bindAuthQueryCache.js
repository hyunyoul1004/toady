import { useAuthStore } from '@/features/auth/store/useAuthStore';

/** 로그인 계정을 구분하는 문자열(비로그인이면 null). 이 값이 바뀌었을 때만 캐시를 정리한다. */
function identity(state) {
  return state.isAuthenticated ? `${state.user?.sub || ''}/${state.user?.userSeq || ''}` : null;
}

/**
 * 로그인 계정에 따라 결과가 달라지는 조회의 첫 키.
 * planner(관심 공고)·plannerSettings(알림 설정)·calendarFeed(개인 캘린더 주소)·ratePoll(내 투표 여부)
 * community(쪽지·차단·프로필 카드의 쪽지 상태): 다른 계정의 대화가 잠깐이라도 보이지 않게 함께 비운다.
 */
const USER_QUERY_ROOTS = new Set(['planner', 'plannerSettings', 'calendarFeed', 'ratePoll', 'community']);

/** 계정별 조회인지 판정한다. 공고 댓글은 작성자 표시·권한이 계정에 따라 달라 함께 비운다. */
export function isUserScopedQuery(queryKey = []) {
  const [root, kind] = queryKey;
  return USER_QUERY_ROOTS.has(root) || (root === 'announcements' && kind === 'comments');
}

/**
 * 계정 변경·로그아웃 시 계정별 조회는 이전 계정 데이터를 지우고(reset) 진행 중인 요청을 취소한다.
 * 공고 목록·상세 같은 공용 조회는 화면을 비우지 않고 다시 받아오기만 한다(invalidate, 진행 중 요청은 취소 후 재조회).
 *
 * React Query는 queryKey(배열)별로 서버 응답을 캐시해 둔다. 로그아웃해도 캐시는 저절로 지워지지 않으므로,
 * 여기서 직접 정리하지 않으면 다른 사람이 같은 브라우저로 로그인했을 때 이전 계정의 관심 공고가 잠깐 보일 수 있다.
 * - resetQueries: 캐시 값을 처음 상태로 되돌린다(화면에 남은 데이터가 사라진다).
 * - invalidateQueries: 값은 두고 '오래됨' 표시만 해서, 보고 있는 화면이면 바로 다시 받아 온다.
 *
 * App.jsx가 앱 시작 때 한 번 부른다. React 컴포넌트가 아니므로 훅 대신 zustand의 useAuthStore.subscribe로
 * 스토어 변화를 직접 듣는다(다음 상태·이전 상태를 받는다). 돌려주는 함수를 부르면 듣기를 멈춘다.
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
