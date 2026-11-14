import { useSyncExternalStore } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { ACCOUNT_KEY, accountApi } from './accountApi';

/** 로그인한 회원의 내 정보 요약(캐릭터·닉네임 등). 내 정보 화면과 같은 캐시를 쓴다. */
export function useMyAccount() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  return useQuery({ queryKey: [...ACCOUNT_KEY, userSeq], queryFn: accountApi.summary, enabled: userSeq != null, staleTime: 60000 });
}

/** 내 프로필 캐릭터 키(없거나 로그아웃이면 null) */
export function useMyProfileIcon() {
  return useMyAccount().data?.profileIcon || null;
}

/**
 * 받은 답글. 마지막으로 본 답글 번호는 이 기기에 회원별로 기억한다(다른 기기에서 본 것은 따로 센다).
 * 5분마다·창으로 돌아올 때 새로 확인한다. unread는 본 뒤 바로 0이 되도록 기억한 번호와 함께 계산한다.
 */
const SEEN_KEY = userSeq => `toady.replies.seen.${userSeq}`;
const seenListeners = new Set();
/** 이 기기에서 마지막으로 본 답글 번호(없으면 null) */
export function readSeen(userSeq) {
  try { const value = Number(localStorage.getItem(SEEN_KEY(userSeq))); return Number.isFinite(value) && value > 0 ? value : null; } catch { return null; }
}
export function markRepliesSeen(userSeq, latestSeq) {
  if (userSeq == null || !latestSeq) return;
  const before = readSeen(userSeq);
  if (before && before >= latestSeq) return;
  try { localStorage.setItem(SEEN_KEY(userSeq), String(latestSeq)); } catch { /* 기억하지 못하면 다음에 다시 새 답글로 보인다 */ }
  seenListeners.forEach(listener => listener());
}
function subscribeSeen(listener) { seenListeners.add(listener); return () => seenListeners.delete(listener); }

export function useMyReplies() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const seen = useSyncExternalStore(subscribeSeen, () => (userSeq == null ? null : readSeen(userSeq)), () => null);
  const query = useQuery({
    queryKey: [...ACCOUNT_KEY, 'replies', userSeq],
    queryFn: () => accountApi.replies(readSeen(userSeq)),
    enabled: userSeq != null,
    staleTime: 60000,
    refetchInterval: 300000,
    refetchOnWindowFocus: true
  });
  const data = query.data;
  const unread = !data || (seen && data.latestSeq && seen >= data.latestSeq) ? 0 : Number(data.unread || 0);
  return { ...query, unread, seen, markSeen: () => markRepliesSeen(userSeq, data?.latestSeq) };
}
