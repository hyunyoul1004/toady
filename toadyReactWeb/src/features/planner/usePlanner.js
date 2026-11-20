/**
 * 관심 공고(나의 청약) 데이터를 다루는 훅과 비회원 저장소.
 * - 로그인: 서버 /api/v1/me/favorites (React Query로 조회하고, 저장·삭제 뒤 invalidateQueries로 다시 받는다)
 * - 비로그인: 이 브라우저 localStorage('toady.planner.guest')를 zustand 스토어로 감싸 보관한다
 * 화면은 둘을 구분하지 않고 usePlanner()가 돌려주는 items·save·remove만 쓴다.
 * 쓰는 곳: MyPlanner, FavoriteCard, ResearchPanel(AnnouncementActions), TodayOverview, WeeklyPicks, push/PushBootstrap.
 */
import { trackAction } from '@/features/analytics/trackAction';
import { create } from 'zustand';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { ID_PATTERN, mergeGuestSummary, safeRead } from './plannerUtils';
import { favoritePromptDue, SIGNUP_PROMPT_EVENT } from '@/features/account/signupPrompt';

// 비회원 관심 공고 저장 키. 값은 [{ id, summary, note, checklist, stage }] 배열이다.
const KEY = 'toady.planner.guest';
// localStorage 값은 그대로 믿지 않는다. 공고 ID·단지명·일정 배열 형식이 맞는 것만 50개까지 쓴다.
function readGuest() {
  const data = safeRead({ getItem: key => localStorage.getItem(key) }, KEY, []);
  return Array.isArray(data) ? data.filter(x => ID_PATTERN.test(x?.id || '') && typeof x?.summary?.houseNm === 'string' && Array.isArray(x.summary.events)).slice(0,50) : [];
}
// zustand 스토어. 컴포넌트에서는 guest(s => s.items)처럼 훅으로 구독하고,
// 컴포넌트 밖에서는 guest.getState()·guest.setState()로 읽고 바꾼다. replace는 localStorage와 화면 상태를 함께 바꾼다.
const guest = create(set => ({ items: readGuest(), replace: items => {
  try { localStorage.setItem(KEY, JSON.stringify(items)); } catch { throw Error('기기 저장 공간을 사용할 수 없습니다. 로그인 후 저장해 주세요.'); }
  set({ items });
} }));
/** 로그인하지 않은 브라우저의 관심 공고 ID. 웹 푸시 구독 범위로 서버에 보낸다(메모·준비 상태는 보내지 않음). */
export function useGuestFavoriteIds() { const items = guest(s => s.items); return items.map(item => item.id); }
/** 로그인하지 않은 브라우저의 관심 공고(저장 당시 요약 포함). 로그인 뒤 계정으로 옮길지 물을 때 쓴다(SignupPrompts.GuestImportPrompt). */
export function useGuestFavorites() { return guest(s => s.items); }
// 다른 탭에서 관심 공고를 바꾸면 storage 이벤트가 온다. 다시 읽어 이 탭 화면도 맞춘다(key가 null이면 저장소 전체 삭제).
if (typeof window !== 'undefined') window.addEventListener('storage', event => { if (event.key === KEY || event.key === null) guest.setState({ items: readGuest() }); });

/**
 * 관심 공고 목록과 저장·삭제 함수. 로그인 여부(userSeq)에 따라 서버 또는 기기 저장소를 쓴다.
 * 돌려주는 scope는 계정이 바뀔 때 화면 상태(비교 선택·안내 문구)를 비우는 기준이다.
 */
export function usePlanner() {
  const userSeq = useAuthStore(s => s.isAuthenticated ? s.user?.userSeq : null);
  const local = guest(s => s.items);
  const client = useQueryClient();
  // React Query: queryKey가 같으면 여러 화면이 같은 캐시를 나눠 쓴다. userSeq를 키에 넣어 계정마다 캐시가 따로 생긴다.
  // enabled가 false면(비로그인) 요청하지 않는다. staleTime 30초 동안은 캐시를 신선하다고 보고 다시 받지 않는다.
  // 서버 응답은 { data, resultCode } 꼴(ResData)이라 .data를 꺼낸다.
  const key = ['planner', userSeq];
  const query = useQuery({ queryKey: key, enabled: Boolean(userSeq), queryFn: async () => (await apiClient.get('/api/v1/me/favorites')).data || [], staleTime: 30000 });
  // 메일 알림 기능이 켜져 있는지 알려 주는 서버 설정. 로그인과 상관없이 받는다.
  const settings = useQuery({ queryKey: ['plannerSettings'], queryFn: () => apiClient.get('/api/v1/planner/settings'), staleTime: 300000 });
  // 비회원 공고는 저장 당시 요약이라 일정이 바뀌었을 수 있다. ID 목록으로 최신 요약을 받아 덮어 보여 준다(기기 저장값은 그대로).
  // 원본이 사라진 공고는 서버 요약 대신 저장해 둔 단지명·일정에 unavailable 표시만 더한다(mergeGuestSummary).
  // 키에 ID를 정렬해 넣어 같은 공고 묶음이면 순서가 달라도 같은 캐시를 쓴다.
  const guestSummaries = useQuery({ queryKey:['guestSummaries',local.map(x=>x.id).sort().join(',')], enabled:!userSeq && local.length>0,
    queryFn:()=>apiClient.post('/api/v1/planner/summaries',{ids:local.map(x=>x.id)},{auth:false}), staleTime:300000, retry:1 });
  const latest = new Map((Array.isArray(guestSummaries.data) ? guestSummaries.data : []).map(s=>[s.id,s]));
  const items = userSeq ? (query.data || []) : local.map(item=>({...item,summary:mergeGuestSummary(item.summary, latest.get(item.id))}));
  /** 공고 저장(새로 저장, 메모·체크리스트·단계 수정 모두). 저장 직전에 로그인 계정이 바뀌었으면(다른 탭 로그아웃 등) 멈춘다. */
  // options.quiet: 가입 권유 신호를 보내지 않는다(관심 해제 되돌리기처럼 새로 고른 저장이 아닐 때).
  async function save(item, options = {}) {
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
      // 관심 공고 3개째 저장이면 가입 권유를 띄우라고 알린다(SignupPromptHost가 받고, 미뤄 둔 순간이면 띄우지 않는다).
      const isNew = !current.some(x => x.id === item.id);
      if (!options.quiet && favoritePromptDue({ isNew, countAfter: guest.getState().items.length })) {
        try { window.dispatchEvent(new CustomEvent(SIGNUP_PROMPT_EVENT, { detail: { moment: 'favorite', count: guest.getState().items.length } })); } catch { /* 권유는 저장과 무관 */ }
      }
    }
    if (!existing) trackAction('favorite_add',item.id);
    if (item.emailEnabled && !existing?.emailEnabled) trackAction('alert_enable',item.id);
  }
  /** 관심 공고 해제. 메모·체크리스트·단계도 함께 지워진다. */
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
  /** 이 회원의 모든 관심 공고 메일 알림을 끈다. */
  async function disableEmails() { if (useAuthStore.getState().user?.userSeq !== userSeq) throw Error('계정이 변경되었습니다. 다시 시도해 주세요.'); await apiClient.post('/api/v1/me/favorites/disable-emails', {}); await client.invalidateQueries({ queryKey: key }); }
  return { scope: userSeq || 'guest', items, guestCount: local.length, loggedIn: Boolean(userSeq), isLoading: Boolean(userSeq) && query.isLoading,
    error: userSeq ? query.error : null, refetch: query.refetch, save, remove, importGuest, disableEmails,
    guestRefreshError: guestSummaries.error, remindersEnabled: settings.data?.remindersEnabled === true };
}
