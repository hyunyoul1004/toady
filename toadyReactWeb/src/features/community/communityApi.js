import apiClient from '@/api/client';

/**
 * 프로필 카드·쪽지·차단·신고 API. 서버는 규칙 위반도 화면에 보여 줄 문구(resultMessage)로 돌려준다.
 * run()은 성공 여부·문구·데이터로 바꿔, 화면이 예외 처리 없이 안내만 보여 주게 한다.
 * 쓰는 곳: CommunityLayers(프로필 카드·신고), MessagesPage(쪽지), MessageSettings(받는 범위·차단 관리), useCommunity.
 */
// React Query 캐시 키의 앞부분. 차단·쪽지처럼 서로 얽힌 값은 invalidateQueries({ queryKey: COMMUNITY_KEY })로 한꺼번에 새로 받는다.
export const COMMUNITY_KEY = ['community'];

export const communityApi = {
  card: userSeq => apiClient.get(`/api/v1/members/${userSeq}`),
  unread: () => apiClient.get('/api/v1/me/messages/unread').then(res => res?.data || { unread: 0 }),
  threads: page => apiClient.get('/api/v1/me/messages', { page }).then(res => res?.data || { items: [], hasMore: false }),
  /** 대화 하나의 최신 30통. before(쪽지 번호)를 주면 그보다 이전 쪽지를 받는다. */
  thread: (threadSeq, before) => apiClient.get(`/api/v1/me/messages/${threadSeq}`, before ? { before } : {}),
  /** 쪽지 보내기. contextId는 대화를 시작한 공고 ID(어느 공고 이야기에서 만났는지 보여 주는 용도). */
  send: (toUserSeq, content, contextId) => apiClient.post('/api/v1/me/messages', { toUserSeq, content, contextId: contextId || null }),
  leave: threadSeq => apiClient.delete(`/api/v1/me/messages/${threadSeq}`),
  settings: () => apiClient.get('/api/v1/me/messages/settings').then(res => res?.data || { policy: 'ALL' }),
  setPolicy: policy => apiClient.put('/api/v1/me/messages/settings', { policy }),
  blocks: () => apiClient.get('/api/v1/me/blocks').then(res => res?.data || { items: [] }),
  block: userSeq => apiClient.post('/api/v1/me/blocks', { userSeq }),
  unblock: userSeq => apiClient.delete(`/api/v1/me/blocks/${userSeq}`),
  report: (targetType, targetSeq, reason, detail) => apiClient.post('/api/v1/me/reports', { targetType, targetSeq, reason, detail: detail || null })
};

/** 서버 결과(성공·실패 문구)와 요청 제한·통신 오류를 화면 안내로 바꾼다. */
export async function run(request) {
  try {
    const res = await request();
    return { ok: res?.resultCode === 'SUCCESS', message: res?.resultMessage || '', data: res?.data };
  } catch (error) {
    return { ok: false, message: error.status === 429 ? '잠시 뒤에 다시 시도해 주세요. 짧은 시간에 너무 많이 시도했어요.' : (error.message || '처리하지 못했어요. 잠시 뒤 다시 시도해 주세요.') };
  }
}

// 규칙 함수는 communityRules.js에 있지만, 화면에서는 이 파일 하나만 불러오면 되게 다시 내보낸다.
export { REPORT_REASONS, RECEIVE_POLICIES, cardValue, parseCardValue, reportValue, parseReportValue, messageTime, mergeMessages, uniqueThreads, isCollapsedStory } from './communityRules.js';
