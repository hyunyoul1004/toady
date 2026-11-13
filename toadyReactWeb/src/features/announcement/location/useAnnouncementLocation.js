import { useQuery } from '@tanstack/react-query';
import { ensureKakaoLoaded } from '@/config/ensureKakaoLoaded';
import { resolveLocation } from './resolveLocation';

/** 지도와 실거래 조회가 동일한 주소·시군구 판정을 사용한다. 단지별 예외 데이터는 사용하지 않는다. */
export function useAnnouncementLocation(detail,enabled=true) {
  return useQuery({
    queryKey:['announcementLocation',detail.subscriptSeq,detail.hssplyAdres || detail.supplyLocation],
    // 카카오 SDK가 늦게 뜨는 경우(모바일 첫 접속 등)만 한 번 더 기다린다. 주소 판정 결과는 다시 묻지 않는다.
    enabled,staleTime:3600000,retry:(count,error)=>count<1 && /지연|SDK/.test(error?.message || ''),retryDelay:500,
    queryFn:async()=>{const kakao=await ensureKakaoLoaded();return resolveLocation(kakao.maps,detail);}
  });
}
