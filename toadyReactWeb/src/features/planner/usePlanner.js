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
      await apiClient.post(`/api/v1/me/favorites/${item.id}`, { note: item.note || '', checklist: item.checklist || 0, emailEnabled: Boolean(item.emailEnabled) });
      await client.invalidateQueries({ queryKey: key });
    } else {
      const current = guest.getState().items;
      if (!current.some(x => x.id === item.id) && current.length >= 50) throw Error('관심 공고는 50개까지 저장할 수 있습니다.');
      guest.getState().replace([{ ...item, emailEnabled: false }, ...current.filter(x => x.id !== item.id)]);
    }
    if (!existing) trackAction('favorite_add',item.id);
    if (item.emailEnabled && !existing?.emailEnabled) trackAction('alert_enable',item.id);
  }
  async function remove(id) {
    if ((useAuthStore.getState().isAuthenticated ? useAuthStore.getState().user?.userSeq : null) !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.');
    if (userSeq) { await apiClient.delete(`/api/v1/me/favorites/${id}`); await client.invalidateQueries({ queryKey: key }); }
    else guest.getState().replace(guest.getState().items.filter(x => x.id !== id));
  }
  async function importGuest() {
    // 명시적 클릭으로만 가져온다. 계정 전환 시 비공개 메모가 자동 업로드되지 않는다.
    for (const item of local) {
      if (useAuthStore.getState().user?.userSeq !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.');
      if (!items.some(x => x.id === item.id)) await save({ ...item, emailEnabled: false });
    }
  }
  async function disableEmails() { if (useAuthStore.getState().user?.userSeq !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.'); await apiClient.post('/api/v1/me/favorites/disable-emails', {}); await client.invalidateQueries({ queryKey: key }); }
  return { scope: userSeq || 'guest', items, guestCount: local.length, loggedIn: Boolean(userSeq), isLoading: Boolean(userSeq) && query.isLoading,
    error: userSeq ? query.error : null, refetch: query.refetch, save, remove, importGuest, disableEmails,
    guestRefreshError: guestSummaries.error, remindersEnabled: settings.data?.remindersEnabled === true };
}
