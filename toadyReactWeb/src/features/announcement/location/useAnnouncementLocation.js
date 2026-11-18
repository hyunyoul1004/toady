import { useQuery } from '@tanstack/react-query';
import { ensureKakaoLoaded } from '@/config/ensureKakaoLoaded';
import { resolveLocation } from './resolveLocation';

/**
 * 공고 한 건의 위치 판정 결과를 React Query로 캐시해 돌려주는 훅.
 * 지도와 실거래 조회가 동일한 주소·시군구 판정을 사용한다. 단지별 예외 데이터는 사용하지 않는다.
 * - 판정 자체는 resolveLocation(카카오 지도 SDK의 주소·장소 검색)이 한다. 결과 status는 ADDRESS / CANDIDATE / UNRESOLVED.
 * - 사용처: components/kakao/Kakaomap.jsx(지도), features/match/usePriceGap.js(분양가 vs 주변 시세),
 *   features/planner/useNeighborhoodResearch.js(동네 살펴보기)
 * - 같은 공고면 queryKey가 같아 세 곳이 한 번의 판정 결과를 함께 쓴다.
 * @param detail 공고 상세(subscriptSeq, hssplyAdres/supplyLocation, houseNm, operatorNotice)
 * @param enabled false면 판정하지 않는다(탭이 열리기 전 등)
 */
export function useAnnouncementLocation(detail,enabled=true) {
  return useQuery({
    // 키에 공급 주소와 운영자 확인 주소를 넣어, 관리자가 주소를 고치면 새로 판정되게 한다.
    queryKey:['announcementLocation',detail.subscriptSeq,detail.hssplyAdres || detail.supplyLocation,detail.operatorNotice?.locationAddress || ''],
    // 카카오 SDK가 늦게 뜨거나 첫 응답이 늦는 경우(모바일 첫 접속·느린 망)는 두 번까지 조금씩 더 기다려 다시 묻는다.
    // 주소 판정 결과(찾지 못함)는 오류가 아니므로 다시 묻지 않는다. 다시 묻기 전 쉬는 시간은 조금씩 늘린다.
    // retry는 (실패 횟수, 오류)를 받아 다시 물을지 정한다. retryDelay는 700ms, 1400ms처럼 점점 늘린다.
    // (한 번 묻는 응답 대기 시간은 아래 timeoutMs 4초로 같다.)
    enabled,staleTime:3600000,retry:(count,error)=>count<2 && /지연|SDK|지도/.test(error?.message || ''),retryDelay:count=>(count+1)*700,
    // 카카오 SDK를 먼저 불러온 뒤(이미 있으면 바로) 주소 판정을 한다. 오류는 다시 던져야 React Query가 isError·retry로 처리한다.
    queryFn:async()=>{
      try { const kakao=await ensureKakaoLoaded();return await resolveLocation(kakao.maps,detail,{timeoutMs:4000}); }
      catch(error) { if(import.meta.env?.DEV) console.warn('[위치 확인 실패]',error?.message || error); throw error; }
    }
  });
}
