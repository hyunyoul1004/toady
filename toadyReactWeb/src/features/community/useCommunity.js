import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/features/auth/store/useAuthStore';
import { COMMUNITY_KEY, communityApi } from './communityApi';

/** 안 읽은 쪽지 수. 2분마다·창으로 돌아올 때 새로 확인한다(로그인했을 때만). */
export function useMessageUnread() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const query = useQuery({
    queryKey: [...COMMUNITY_KEY, 'unread', userSeq],
    queryFn: communityApi.unread,
    enabled: userSeq != null,
    staleTime: 30000,
    refetchInterval: 120000,
    refetchOnWindowFocus: true
  });
  return userSeq == null ? 0 : Number(query.data?.unread || 0);
}

/** 내가 차단한 회원 번호 묶음. 이야기 목록에서 차단한 회원의 글을 접는 데 쓴다. */
export function useBlockedSet() {
  const userSeq = useAuthStore(state => state.user?.userSeq ?? null);
  const query = useQuery({
    queryKey: [...COMMUNITY_KEY, 'blocks', userSeq],
    queryFn: communityApi.blocks,
    enabled: userSeq != null,
    staleTime: 300000
  });
  const items = query.data?.items;
  return useMemo(() => new Set((items || []).map(item => Number(item.userSeq))), [items]);
}
