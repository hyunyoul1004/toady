package com.toady.web.app.planner.research;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NeighborhoodResearchServiceTest {
    private static byte[] response(String month, String dong, String cancellation) {
        return ("<response><header><resultCode>000</resultCode></header><body><totalCount>1</totalCount><items><item>"+
            "<aptNm>테스트 아파트</aptNm><umdNm>"+dong+"</umdNm><dealAmount>58,900</dealAmount><excluUseAr>84.95</excluUseAr>"+
            "<floor>12</floor><buildYear>2020</buildYear><dealYear>"+month.substring(0,4)+"</dealYear><dealMonth>"+month.substring(4)+
            "</dealMonth><dealDay>1</dealDay><cdealType>"+cancellation+"</cdealType></item></items></body></response>").getBytes(StandardCharsets.UTF_8);
    }
    @Test void basicApiUsesSingleKeyEncodingCachesDistrictAndFiltersDongAndArea() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{
            calls.incrementAndGet();assertEquals("/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade",uri.getPath());
            assertTrue(uri.getRawQuery().contains("serviceKey=a%2Bb%2F%3D"));
            String month=uri.getQuery().split("DEAL_YMD=")[1].substring(0,6);
            return response(month,"동패동","");
        },"a%2Bb%2F%3D");
        var result=service.trades("4148012800","동패동",new BigDecimal("84"));
        assertEquals("READY",result.status());assertEquals(3,result.total());assertEquals(58900,result.items().getFirst().amountManwon());
        assertEquals("EMPTY",service.trades("4148012900","다른동").status());
        assertEquals("EMPTY",service.trades("4148012800","동패동",new BigDecimal("60")).status());
        assertEquals(3,calls.get());assertEquals(result.checkedAt(),service.trades("4148012800","동패동").checkedAt());
    }
    @Test void cancelledTradesCountForPaginationButAreNotDisplayed() throws Exception {
        var page=NeighborhoodResearchService.parseTrades(response("202610","동패동","Y"));
        assertEquals(1,page.rawCount());assertTrue(page.trades().isEmpty());
    }
    @Test void providerFailuresAreNotReportedAsEmptyAndAreNotRetriedImmediately() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{calls.incrementAndGet();return "<response><resultCode>30</resultCode></response>".getBytes();},"key");
        assertEquals("UNAVAILABLE",service.trades("4148012800","동패동").status());
        // 실패는 '거래 없음'으로 바꾸지 않고, 같은 시군구·월은 몇 분간 다시 부르지 않아 일일 쿼터를 지킨다.
        assertEquals("UNAVAILABLE",service.trades("4148012800","동패동").status());assertEquals(1,calls.get());
        assertEquals("UNAVAILABLE",service.trades("9948012800","동패동").status(),"없는 시·도 코드는 외부 호출 없이 거절한다");assertEquals(1,calls.get());
    }
    @Test void incompletePaginationAndWrongMonthsNeverReturnPartialResults() {
        var service=new NeighborhoodResearchService((uri,headers)->{
            String month=uri.getQuery().split("DEAL_YMD=")[1].substring(0,6);
            String xml=new String(response(month,"동패동",""),StandardCharsets.UTF_8);
            if(uri.getQuery().contains("pageNo=2")) xml=xml.replaceAll("<item>.*?</item>","");
            return xml.replace("<totalCount>1</totalCount>","<totalCount>2</totalCount>").getBytes(StandardCharsets.UTF_8);
        },"key");
        assertEquals("UNAVAILABLE",service.trades("4148012800","동패동").status());
        var wrongMonth=new NeighborhoodResearchService((uri,headers)->response("200001","동패동",""),"key");
        assertEquals("UNAVAILABLE",wrongMonth.trades("4148012800","동패동").status());
    }
    @Test void noKeyDoesNotCallProviderAndExternalEntitiesAreRejected() {
        var service=new NeighborhoodResearchService((uri,headers)->{throw new AssertionError("Unexpected outbound request");},"");
        assertEquals("NOT_CONFIGURED",service.trades("4148012800","동패동").status());
        assertThrows(Exception.class,()->NeighborhoodResearchService.parseTrades("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///etc/passwd'>]><x>&external;</x>".getBytes()));
    }
}
