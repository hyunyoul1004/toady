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
