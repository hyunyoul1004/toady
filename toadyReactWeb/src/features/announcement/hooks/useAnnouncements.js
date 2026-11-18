/**
 * @file useAnnouncements.js
 * @description TanStack Query (React Query v5) 기반의 공고 및 댓글 커스텀 훅
 * - 자동 캐싱, 백그라운드 리페칭, 로딩/에러 상태 관리
 * - 댓글 등록/수정/삭제 시 쿼리 무효화(invalidateQueries)를 통한 실시간 동기화
 * - 서버 통신은 api/announcementApi.js가 하고, 여기서는 캐시 키·갱신 시점만 정한다.
 * - 사용처: views/main/Main.jsx(목록·상세·가점), views/main/subscriptRate/SubscriptRate.jsx(경쟁률·가점·특별공급),
 *   views/main/subscriptComment/SubscriptComment.jsx(댓글)
 *
 * React Query 짧은 정리
 * - queryKey: 캐시의 이름표. 키가 같으면 여러 화면이 같은 데이터를 함께 쓰고, 키가 바뀌면 새로 불러온다.
 * - staleTime: 이 시간 동안은 '신선한' 데이터로 보고 다시 요청하지 않는다.
 * - enabled: false면 요청하지 않는다(필요한 값이 아직 없을 때 등).
 * - invalidateQueries: 그 키(로 시작하는) 캐시를 낡았다고 표시해, 화면에 떠 있으면 바로 다시 불러온다.
 */
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import announcementApi, { parseSubscriptId } from '../api/announcementApi';
import { announcementListKeyParams } from '../api/announcementParams';
import { formatDate } from '@/utils/date.util';

/**
 * Query Keys. 배열 앞부분이 같으면 한꺼번에 무효화할 수 있다
 * (예: lists로 무효화하면 조건이 다른 목록 캐시가 모두 낡음 처리된다).
 */
export const announcementKeys = {
  all: ['announcements'],
  lists: ['announcements', 'list'],
  // 화면 조건 전체가 아니라 실제 서버 요청 파라미터로 키를 만든다(citys 등 화면 전용 조건 제외).
  list: (params) => ['announcements', 'list', announcementListKeyParams(params, formatDate)],
  detail: (id) => ['announcements', 'detail', id],
  comments: (id) => ['announcements', 'comments', id],
  competitionRates: (id) => ['announcements', 'competitionRates', id],
  scores: (id) => ['announcements', 'scores', id],
  specialSupplyStatus: (id) => ['announcements', 'specialSupplyStatus', id]
};

/**
 * 분양 공고 목록 조회 훅
 * @param {Record<string, any>} params 검색/필터/페이징 조건
 */
export function useAnnouncementsQuery(params, enabled = true) {
  return useQuery({
    queryKey: announcementKeys.list(params),
    queryFn: () => announcementApi.getAnnouncements(params),
    // 달력에서 날짜 범위가 세팅되기 전(초기값 '')에는 요청하지 않음 → 중복 호출 방지
    enabled: enabled && Boolean(params?.startStr && params?.endStr),
    staleTime: 1000 * 60 * 3, // 3분간 캐시 유지
    // 월·조건을 바꾸는 동안 이전 결과를 유지해 달력이 비었다가 다시 그려지지 않게 한다.
    placeholderData: keepPreviousData,
    refetchOnWindowFocus: false
  });
}

/**
 * 공고 상세 정보 조회 훅
 * @param {string|object} subscriptId 식별자 ('type/houseManageNo/pblancNo')
 * @param {boolean} [enabled=true] 쿼리 실행 여부
 */
export function useAnnouncementDetailQuery(subscriptId, enabled = true) {
  // 형식이 틀린 id는 parseSubscriptId가 houseManageNo를 비워 돌려주므로 아래 isValid가 false가 되어 요청하지 않는다.
  const { type, houseManageNo, pblancNo } = parseSubscriptId(subscriptId);
  const isValid = Boolean(houseManageNo);

  return useQuery({
    queryKey: announcementKeys.detail(`${type}/${houseManageNo}/${pblancNo}`),
    queryFn: () => announcementApi.getAnnouncementDetail({ type, houseManageNo, pblancNo }),
    enabled: enabled && isValid,
    staleTime: 1000 * 60 * 5,
    refetchOnWindowFocus: false
  });
}

/**
 * 공고 댓글 목록 조회 훅
 * @param {string|object} subscriptId 식별자
 * @param {boolean} [enabled=true]
 */
export function useCommentsQuery(subscriptId, enabled = true) {
  const { type, houseManageNo, pblancNo } = parseSubscriptId(subscriptId);
  const idKey = `${type}/${houseManageNo}/${pblancNo}`;
  const isValid = Boolean(houseManageNo);

  return useQuery({
    queryKey: announcementKeys.comments(idKey),
    queryFn: () => announcementApi.getComments({ type, houseManageNo, pblancNo }),
    // staleTime을 두지 않아(기본 0) 댓글 칸을 다시 열 때마다 최신 댓글을 받아 온다.
    enabled: enabled && isValid,
    refetchOnWindowFocus: false
  });
}

/** 댓글이 이미 지워졌거나 관리자가 숨겼으면(404) 화면의 목록이 낡은 것이므로 다시 불러온다. */
function refreshOnMissing(queryClient) {
  return (error, variables) => {
    if (error?.status !== 404) return;
    const { type, houseManageNo, pblancNo } = parseSubscriptId(variables);
    const idKey = `${type}/${houseManageNo}/${pblancNo}`;
    queryClient.invalidateQueries({ queryKey: announcementKeys.comments(idKey) });
    queryClient.invalidateQueries({ queryKey: announcementKeys.detail(idKey) });
  };
}

/**
 * 댓글 등록 Mutation 훅
 * useMutation은 서버 데이터를 바꾸는 요청에 쓴다. mutate(params)로 실행하고,
 * 성공하면(onSuccess) 댓글 목록·상세(댓글 수)·공고 목록(댓글 수 표시) 캐시를 무효화해 다시 불러오게 한다.
 */
export function useAddCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.addComment(params),
    onError: refreshOnMissing(queryClient),
    onSuccess: (_, variables) => {
      const { type, houseManageNo, pblancNo } = parseSubscriptId(variables);
      const idKey = `${type}/${houseManageNo}/${pblancNo}`;
      // 해당 공고의 댓글 목록 캐시 무효화 (즉시 재조회)
      queryClient.invalidateQueries({ queryKey: announcementKeys.comments(idKey) });
      queryClient.invalidateQueries({ queryKey: announcementKeys.detail(idKey) });
      queryClient.invalidateQueries({ queryKey: announcementKeys.lists });
    }
  });
}



/**
 * 댓글 수정 Mutation 훅. 댓글 수는 그대로라 댓글 목록만 다시 불러온다.
 */
export function useUpdateCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.updateComment(params),
    onError: refreshOnMissing(queryClient),
    onSuccess: (_, variables) => {
      const { type, houseManageNo, pblancNo } = parseSubscriptId(variables);
      const idKey = `${type}/${houseManageNo}/${pblancNo}`;
      queryClient.invalidateQueries({ queryKey: announcementKeys.comments(idKey) });
    }
  });
}

/**
 * 댓글 삭제 Mutation 훅. 댓글 수가 줄어드므로 등록과 같은 캐시들을 무효화한다.
 */
export function useDeleteCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.deleteComment(params),
    onError: refreshOnMissing(queryClient),
    onSuccess: (_, variables) => {
      const { type, houseManageNo, pblancNo } = parseSubscriptId(variables);
      const idKey = `${type}/${houseManageNo}/${pblancNo}`;
      queryClient.invalidateQueries({ queryKey: announcementKeys.comments(idKey) });
      queryClient.invalidateQueries({ queryKey: announcementKeys.detail(idKey) });
      queryClient.invalidateQueries({ queryKey: announcementKeys.lists });
    }
  });
}

/**
 * 청약 경쟁률 조회 훅
 * @param {string|object} subscriptId 식별자 ('type/houseManageNo/pblancNo')
 * @param {boolean} [enabled=true]
 */
export function useCompetitionRatesQuery(subscriptId, enabled = true) {
  const { type, houseManageNo, pblancNo } = parseSubscriptId(subscriptId);
  const idKey = `${type}/${houseManageNo}/${pblancNo}`;
  const isValid = Boolean(houseManageNo && pblancNo);

  return useQuery({
    queryKey: announcementKeys.competitionRates(idKey),
    queryFn: () => announcementApi.getCompetitionRates({ type, houseManageNo, pblancNo }),
    enabled: enabled && isValid,
    staleTime: 1000 * 60 * 5,
    refetchOnWindowFocus: false
  });
}

/**
 * APT 당첨 가점 조회 훅
 * @param {string|object} subscriptId 식별자
 * @param {boolean} [enabled=true]
 */
export function useAptScoresQuery(subscriptId, enabled = true) {
  // 가점은 APT 공고에만 있다. 다른 유형이면 enabled가 false가 되어 요청하지 않는다.
  const { type, houseManageNo, pblancNo } = parseSubscriptId(subscriptId);
  const idKey = `apt/${houseManageNo}/${pblancNo}`;
  const isApt = !type || type === 'apt';
  const isValid = Boolean(houseManageNo && pblancNo && isApt);

  return useQuery({
    queryKey: announcementKeys.scores(idKey),
    queryFn: () => announcementApi.getAptScores({ houseManageNo, pblancNo }),
    enabled: enabled && isValid,
    staleTime: 1000 * 60 * 5,
    refetchOnWindowFocus: false
  });
}

/**
 * APT 특별공급 신청현황 조회 훅
 * @param {string|object} subscriptId 식별자
 * @param {boolean} [enabled=true]
 */
export function useAptSpecialSupplyStatusQuery(subscriptId, enabled = true) {
  const { type, houseManageNo, pblancNo } = parseSubscriptId(subscriptId);
  const idKey = `apt/${houseManageNo}/${pblancNo}`;
  const isApt = !type || type === 'apt';
  const isValid = Boolean(houseManageNo && pblancNo && isApt);

  return useQuery({
    queryKey: announcementKeys.specialSupplyStatus(idKey),
    queryFn: () => announcementApi.getAptSpecialSupplyStatus({ houseManageNo, pblancNo }),
    enabled: enabled && isValid,
    staleTime: 1000 * 60 * 5,
    refetchOnWindowFocus: false
  });
}


