import { trackAction } from '@/features/analytics/trackAction';
import { create } from 'zustand';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { ID_PATTERN, safeRead } from './plannerUtils';

const KEY = 'toady.planner.guest';
function readGuest() {
  const data = safeRead({ getItem: key => localStorage.getItem(key) }, KEY, []);
  return Array.isArray(data) ? data.filter(x => ID_PATTERN.test(x?.id || '') && typeof x?.summary?.houseNm === 'string' && Array.isArray(x.summary.events)).slice(0,50) : [];
}
const guest = create(set => ({ items: readGuest(), replace: items => {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { throw Error('기기 저장 공간을 사용할 수 없습니다. 로그인 후 저장해 주세요.'); }
  set({ items });
} }));
/** 로그인하지 않은 브라우저의 관심 공고 ID. 웹 푸시 구독 범위로 서버에 보낸다(메모·준비 상태는 보내지 않음). */
export function useGuestFavoriteIds() { const items = guest(s => s.items); return items.map(item => item.id); }
if (typeof window !== 'undefined') window.addEventListener('storage', event => { if (event.key === KEY || event.key === null) guest.setState({ items: readGuest() }); });

export function usePlanner() {
  const userSeq = useAuthStore(s => s.isAuthenticated ? s.user?.userSeq : null);
  const local = guest(s => s.items);
  const client = useQueryClient();
  const key = ['planner', userSeq];
  const query = useQuery({ queryKey: key, enabled: Boolean(userSeq), queryFn: async () => (await apiClient.get('/api/v1/me/favorites')).data || [], staleTime: 30000 });
  const settings = useQuery({ queryKey: ['plannerSettings'], queryFn: () => apiClient.get('/api/v1/planner/settings'), staleTime: 300000 });
  const guestSummaries = useQuery({ queryKey:['guestSummaries',local.map(x=>x.id).sort().join(',')], enabled:!userSeq && local.length>0,
    queryFn:()=>apiClient.post('/api/v1/planner/summaries',{ids:local.map(x=>x.id)},{auth:false}), staleTime:300000, retry:1 });
  const latest = new Map((Array.isArray(guestSummaries.data) ? guestSummaries.data : []).map(s=>[s.id,s]));
  const items = userSeq ? (query.data || []) : local.map(item=>({...item,summary:latest.get(item.id) || item.summary}));
  async function save(item) {
    if ((useAuthStore.getState().isAuthenticated ? useAuthStore.getState().user?.userSeq : null) !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.');
    if (!ID_PATTERN.test(item.id)) throw Error('공고 식별자를 확인해 주세요.');
    const existing = items.find(x => x.id === item.id);
    if (userSeq) {
      // stage를 보내지 않으면 서버가 기존 단계를 유지한다(공고 저장·메모 수정).
      await apiClient.post(`/api/v1/me/favorites/${item.id}`, { note: item.note || '', checklist: item.checklist || 0, emailEnabled: Boolean(item.emailEnabled), ...(item.stage !== undefined ? { stage: item.stage } : {}) });
      await client.invalidateQueries({ queryKey: key });
    } else {
      const current = guest.getState().items;
      if (!current.some(x => x.id === item.id) && current.length >= 50) throw Error('관심 공고는 50개까지 저장할 수 있습니다.');
      // 이미 저장한 공고는 제자리에서 바꾸고(단계·메모를 바꿀 때 카드가 맨 위로 튀지 않게), 새 공고만 맨 앞에 둔다.
      // 원본을 찾을 수 없게 된 공고는 처음 저장한 단지명·일정을 지키도록 기존 요약을 그대로 둔다.
      const saved = current.find(x => x.id === item.id);
      const next = { ...item, emailEnabled: false, ...(item.summary?.unavailable && saved?.summary ? { summary: saved.summary } : {}) };
      guest.getState().replace(current.some(x => x.id === item.id) ? current.map(x => (x.id === item.id ? next : x)) : [next, ...current]);
    }
    if (!existing) trackAction('favorite_add',item.id);
    if (item.emailEnabled && !existing?.emailEnabled) trackAction('alert_enable',item.id);
  }
  async function remove(id) {
    if ((useAuthStore.getState().isAuthenticated ? useAuthStore.getState().user?.userSeq : null) !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.');
    if (userSeq) { await apiClient.delete(`/api/v1/me/favorites/${id}`); await client.invalidateQueries({ queryKey: key }); }
    else guest.getState().replace(guest.getState().items.filter(x => x.id !== id));
  }
  /**
   * 명시적 클릭으로만 가져온다. 계정 전환 시 비공개 메모가 자동 업로드되지 않는다.
   * 원본이 사라진 공고·한도 초과로 한 건이 실패해도 나머지는 계속 가져오고 결과를 돌려준다.
   */
  async function importGuest() {
    const result = { imported: 0, failed: 0, skipped: 0, lastError: null };
    for (const item of local) {
      if (useAuthStore.getState().user?.userSeq !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.');
      if (items.some(x => x.id === item.id)) continue;
      if (item.summary?.unavailable) { result.skipped += 1; continue; }
      try { await save({ ...item, emailEnabled: false }); result.imported += 1; }
      catch (error) {
        // 원본 공고가 사라진 항목(404)은 실패가 아니라 제외로 센다.
        if (error.status === 404) result.skipped += 1;
        else { result.failed += 1; result.lastError = error.message; }
      }
    }
    return result;
  }
  async function disableEmails() { if (useAuthStore.getState().user?.userSeq !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.'); await apiClient.post('/api/v1/me/favorites/disable-emails', {}); await client.invalidateQueries({ queryKey: key }); }
  return { scope: userSeq || 'guest', items, guestCount: local.length, loggedIn: Boolean(userSeq), isLoading: Boolean(userSeq) && query.isLoading,
    error: userSeq ? query.error : null, refetch: query.refetch, save, remove, importGuest, disableEmails,
    guestRefreshError: guestSummaries.error, remindersEnabled: settings.data?.remindersEnabled === true };
}
