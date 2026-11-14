import { useQuery } from '@tanstack/react-query';
import { ensureKakaoLoaded } from '@/config/ensureKakaoLoaded';
import { resolveLocation } from './resolveLocation';

/** 지도와 실거래 조회가 동일한 주소·시군구 판정을 사용한다. 단지별 예외 데이터는 사용하지 않는다. */
export function useAnnouncementLocation(detail,enabled=true) {
  return useQuery({
    queryKey:['announcementLocation',detail.subscriptSeq,detail.hssplyAdres || detail.supplyLocation],
    // 카카오 SDK가 늦게 뜨거나 첫 응답이 늦는 경우(모바일 첫 접속·느린 망)는 두 번까지 조금씩 더 기다려 다시 묻는다.
    // 주소 판정 결과(찾지 못함)는 오류가 아니므로 다시 묻지 않는다. 다시 물을 때는 응답 대기 시간도 늘린다.
    enabled,staleTime:3600000,retry:(count,error)=>count<2 && /지연|SDK|지도/.test(error?.message || ''),retryDelay:count=>(count+1)*700,
    queryFn:async()=>{
      try { const kakao=await ensureKakaoLoaded();return await resolveLocation(kakao.maps,detail,{timeoutMs:4000}); }
      catch(error) { if(import.meta.env?.DEV) console.warn('[위치 확인 실패]',error?.message || error); throw error; }
    }
  });
}
