import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAnnouncementLocation } from '@/features/announcement/location/useAnnouncementLocation';
/**
 * 공고 상세 '동네 실거래' 탭의 데이터 훅(ResearchPanel에서 씀).
 * 1) 서버가 실거래 조회를 켜 두었는지(capabilities) 2) 공고 위치의 법정동 3) 그 동의 최근 거래, 세 단계를 차례로 받는다.
 * 정확한 주소(ADDRESS)로 확인된 법정동(10자리 코드)일 때만 거래를 조회한다. 후보·미확인 위치로 엉뚱한 동네 거래를 보여 주지 않기 위해서다.
 * React Query의 enabled로 앞 단계가 준비돼야 다음 요청이 나가게 이어 두었다.
 */
export function useNeighborhoodResearch(detail,tab,area) {
  const capabilities=useQuery({queryKey:['researchCapabilities'],queryFn:()=>apiClient.get('/api/v1/research/capabilities'),staleTime:300000,retry:1});
  const resolved=useAnnouncementLocation(detail,tab==='trades' && capabilities.data?.tradesEnabled===true);
  const precise=resolved.data?.status==='ADDRESS' && /^\d{10}$/.test(resolved.data?.bcode || '');
  const location={...resolved,data:precise?resolved.data:undefined,error:resolved.error || (resolved.data && !precise?Error('공고의 정확한 법정동을 확인하지 못했습니다. 확인되지 않은 동네의 거래는 표시하지 않습니다.'):null)};
  // queryKey에 법정동 코드와 면적을 넣어 조건이 바뀌면 새로 받고, 같은 조건이면 5분 동안 캐시를 쓴다.
  const trades=useQuery({queryKey:['researchTrades',location.data?.bcode,area],enabled:tab==='trades' && Boolean(location.data) && capabilities.data?.tradesEnabled===true,
    queryFn:()=>apiClient.get('/api/v1/research/transactions',{...location.data,area:area || undefined}),staleTime:300000,retry:false});
  return {capabilities,location,trades};
}
