import { useQuery } from '@tanstack/react-query';
import apiClient from '@/api/client';
import { useAnnouncementLocation } from '@/features/announcement/location/useAnnouncementLocation';
export function useNeighborhoodResearch(detail,tab,area) {
  const capabilities=useQuery({queryKey:['researchCapabilities'],queryFn:()=>apiClient.get('/api/v1/research/capabilities'),staleTime:300000,retry:1});
  const resolved=useAnnouncementLocation(detail,tab==='trades' && capabilities.data?.tradesEnabled===true);
  const precise=resolved.data?.status==='ADDRESS' && /^\d{10}$/.test(resolved.data?.bcode || '');
  const location={...resolved,data:precise?resolved.data:undefined,error:resolved.error || (resolved.data && !precise?Error('공고의 정확한 법정동을 확인하지 못했습니다. 확인되지 않은 동네의 거래는 표시하지 않습니다.'):null)};
  const trades=useQuery({queryKey:['researchTrades',location.data?.bcode,area],enabled:tab==='trades' && Boolean(location.data) && capabilities.data?.tradesEnabled===true,
    queryFn:()=>apiClient.get('/api/v1/research/transactions',{...location.data,area:area || undefined}),staleTime:300000,retry:false});
  return {capabilities,location,trades};
}
