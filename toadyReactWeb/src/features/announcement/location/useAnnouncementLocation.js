import { useQuery } from '@tanstack/react-query';
import { ensureKakaoLoaded } from '@/config/ensureKakaoLoaded';
import { resolveLocation } from './resolveLocation';

/** 지도와 실거래 조회가 동일한 주소·시군구 판정을 사용한다. 단지별 예외 데이터는 사용하지 않는다. */
export function useAnnouncementLocation(detail,enabled=true) {
  return useQuery({
    queryKey:['announcementLocation',detail.subscriptSeq,detail.hssplyAdres || detail.supplyLocation],
    enabled,staleTime:3600000,retry:false,
    queryFn:async()=>{const kakao=await ensureKakaoLoaded();return resolveLocation(kakao.maps,detail);}
  });
}
