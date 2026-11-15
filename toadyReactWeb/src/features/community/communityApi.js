import apiClient from '@/api/client';

/**
 * 프로필 카드·쪽지·차단·신고 API. 서버는 규칙 위반도 화면에 보여 줄 문구(resultMessage)로 돌려준다.
 * run()은 성공 여부·문구·데이터로 바꿔, 화면이 예외 처리 없이 안내만 보여 주게 한다.
 */
export const COMMUNITY_KEY = ['community'];

export const communityApi = {
  card: userSeq => apiClient.get(`/api/v1/members/${userSeq}`),
  unread: () => apiClient.get('/api/v1/me/messages/unread').then(res => res?.data || { unread: 0 }),
  threads: page => apiClient.get('/api/v1/me/messages', { page }).then(res => res?.data || { items: [], hasMore: false }),
  thread: (threadSeq, before) => apiClient.get(`/api/v1/me/messages/${threadSeq}`, before ? { before } : {}),
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

export { REPORT_REASONS, RECEIVE_POLICIES, cardValue, parseCardValue, reportValue, parseReportValue, messageTime, mergeMessages, uniqueThreads, isCollapsedStory } from './communityRules.js';
