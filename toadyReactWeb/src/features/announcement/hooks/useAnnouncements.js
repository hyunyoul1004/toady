/**
 * @file useAnnouncements.js
 * @description TanStack Query (React Query v5) 기반의 공고 및 댓글 커스텀 훅
 * - 자동 캐싱, 백그라운드 리페칭, 로딩/에러 상태 관리
 * - 댓글 등록/수정/삭제 시 쿼리 무효화(invalidateQueries)를 통한 실시간 동기화
 */
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import announcementApi, { parseSubscriptId } from '../api/announcementApi';
import { announcementListKeyParams } from '../api/announcementParams';
import { formatDate } from '@/utils/date.util';

/** Query Keys */
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
    enabled: enabled && isValid,
    refetchOnWindowFocus: false
  });
}

/**
 * 댓글 등록 Mutation 훅
 */
export function useAddCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.addComment(params),
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
 * 댓글 수정 Mutation 훅
 */
export function useUpdateCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.updateComment(params),
    onSuccess: (_, variables) => {
      const { type, houseManageNo, pblancNo } = parseSubscriptId(variables);
      const idKey = `${type}/${houseManageNo}/${pblancNo}`;
      queryClient.invalidateQueries({ queryKey: announcementKeys.comments(idKey) });
    }
  });
}

/**
 * 댓글 삭제 Mutation 훅
 */
export function useDeleteCommentMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (params) => announcementApi.deleteComment(params),
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


