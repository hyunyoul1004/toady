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
    /** 광주·전남 통합(전남광주통합특별시) 뒤 법정동 코드는 12로 시작한다. 막으면 진도·광주 공고의 주변 시세가 늘 '불러오지 못함'이 된다. */
    @Test void unifiedGwangjuJeonnamCodeIsQueried() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{
            calls.incrementAndGet();assertTrue(uri.getQuery().contains("LAWD_CD=12860"));
            return response(uri.getQuery().split("DEAL_YMD=")[1].substring(0,6),"진도읍 교동리","");
        },"key");
        assertEquals("READY",service.trades("1286025024","진도읍 교동리").status());
        assertTrue(calls.get()>0);
        assertEquals("UNAVAILABLE",service.trades("9986025024","진도읍 교동리").status(),"없는 시·도 코드는 여전히 외부 호출 없이 거절");
        int before=calls.get();
        assertEquals("UNSUPPORTED",service.districtTrades("9986025024",3,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)).status(),"지도는 지원하지 않는 지역을 일시 오류와 구분한다");
        assertEquals(before,calls.get());
    }
    @Test void priceGapUsesSixMonthsDongFirstThenDistrictAndReusesCache() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{
            calls.incrementAndGet();
            String month=uri.getQuery().split("DEAL_YMD=")[1].substring(0,6);
            return response(month,"동패동","");
        },"key");
        var gap=service.priceGap("4148012800","동패동",java.util.List.of(new BigDecimal("84"),new BigDecimal("59")));
        assertEquals("READY",gap.status());
        assertEquals(6,calls.get(),"최근 6개월을 시·군·구 단위로 한 번씩 부른다");
        var area84=gap.areas().get(0);
        assertEquals("DONG",area84.scope());assertEquals(6,area84.count());assertEquals(58900,area84.median());
        assertEquals(6,area84.newCount(),"2020년 준공은 10년 이내 거래로 따로 센다");
        assertEquals(0,gap.areas().get(1).count(),"59㎡ 안팎 거래가 없으면 0건");
        var other=service.priceGap("4148012900","다른동",java.util.List.of(new BigDecimal("84")));
        assertEquals("DISTRICT",other.areas().get(0).scope(),"같은 동 거래가 부족하면 시·군·구로 넓힌다");
        assertEquals(6,calls.get(),"같은 시·군·구·월은 캐시를 다시 쓴다");
        assertEquals(6,gap.months());assertFalse(gap.partial(),"6개월을 모두 받으면 부분 결과가 아니다");
        assertEquals(58900,area84.q1());assertEquals(58900,area84.q3());
        assertEquals(58900,NeighborhoodResearchService.median(java.util.List.of(58900L)));
        assertEquals(20,NeighborhoodResearchService.quantile(java.util.List.of(10L,20L,30L,40L,50L),0.25));
        assertEquals(40,NeighborhoodResearchService.quantile(java.util.List.of(10L,20L,30L,40L,50L),0.75));
        assertEquals(18,NeighborhoodResearchService.quantile(java.util.List.of(10L,20L,30L,40L),0.25));
        assertEquals(15,NeighborhoodResearchService.median(java.util.List.of(10L,20L)));
    }
    @Test void cancelledTradesCountForPaginationButAreNotDisplayed() throws Exception {
        var page=NeighborhoodResearchService.parseTrades(response("202610","동패동","Y"));
        assertEquals(1,page.rawCount());assertTrue(page.trades().isEmpty());
    }
    @Test void providerFailuresAreNotReportedAsEmptyAndAreNotRetriedImmediately() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{calls.incrementAndGet();return "<response><resultCode>30</resultCode></response>".getBytes();},"key");
        assertEquals("UNAVAILABLE",service.trades("4148012800","동패동").status());
        // 실패는 '거래 없음'으로 바꾸지 않는다. 제공처가 거절한 응답은 다시 불러도 같아 재시도하지 않고, 같은 시군구·월은 잠시(30초) 다시 부르지 않는다.
        assertEquals("UNAVAILABLE",service.trades("4148012800","동패동").status());assertEquals(1,calls.get());
        assertEquals("UNAVAILABLE",service.trades("9948012800","동패동").status(),"없는 시·도 코드는 외부 호출 없이 거절한다");assertEquals(1,calls.get());
    }
    @Test void quotaOrRateLimitRejectionPausesAllRegionsAndMonths() {
        AtomicInteger calls=new AtomicInteger();
        var quota=new NeighborhoodResearchService((uri,headers)->{calls.incrementAndGet();return "<response><header><resultCode>22</resultCode></header></response>".getBytes();},"key");
        assertEquals("UNAVAILABLE",quota.trades("4148012800","동패동").status());
        int afterFirst=calls.get();
        // 일일 한도 초과 뒤에는 다른 시·군·구·다른 달도 외부 호출 없이 바로 실패한다(차단기).
        assertEquals("UNAVAILABLE",quota.trades("1168010100","역삼동").status());
        quota.districtTrades("2611010100",12,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
        assertEquals(afterFirst,calls.get());
        assertTrue(quota.molitPaused());

        AtomicInteger limited=new AtomicInteger();
        var tooMany=new NeighborhoodResearchService((uri,headers)->{limited.incrementAndGet();throw new ResearchTransport.ProviderStatus(429);},"key");
        tooMany.districtTrades("4148012800",12,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5));
        assertEquals(1,limited.get(),"429 한 번 뒤 남은 달은 부르지 않는다");
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

    /** 제공처 일시 오류: 한 번은 다시 시도하고, 계속 실패한 달만 빼고 받은 달로 돌려준다(시·군·구 전체를 막지 않는다). */
    @Test void aFailingMonthIsRetriedOnceThenSkippedWithoutBlockingTheDistrict() {
        java.time.YearMonth now=java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
        String flaky=now.minusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String broken=now.minusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        java.util.Map<String,AtomicInteger> calls=new java.util.concurrent.ConcurrentHashMap<>();
        var service=new NeighborhoodResearchService((uri,headers)->{
            String month=uri.getQuery().split("DEAL_YMD=")[1].substring(0,6);
            int n=calls.computeIfAbsent(month,k->new AtomicInteger()).incrementAndGet();
            if(month.equals(flaky) && n==1) throw new java.io.IOException("throttled");
            // 한도·인증 오류가 아닌 제공처 오류(예: 99)는 그 달만 빠지고 다른 달은 계속 부른다.
            if(month.equals(broken)) return "<response><header><resultCode>99</resultCode></header></response>".getBytes(StandardCharsets.UTF_8);
            return response(month,"동패동","");
        },"key");
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        var district=service.districtTrades("4148012800",6,deadline);
        assertEquals("READY",district.status());
        assertEquals(5,district.months(),"계속 실패한 한 달만 빠진다");
        assertTrue(district.partial(),"빠진 달이 있으면 partial이라 화면이 잠시 뒤 다시 부른다");
        assertEquals(2,calls.get(flaky).get(),"일시 오류는 한 번 다시 시도해 받는다");
        assertEquals(1,calls.get(broken).get(),"제공처가 거절한 응답(코드 22 등)은 다시 시도하지 않는다");
        int brokenCalls=calls.get(broken).get();
        assertEquals("READY",service.districtTrades("4148012800",6,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10)).status());
        assertEquals(brokenCalls,calls.get(broken).get(),"실패한 달은 잠깐(30초) 다시 묻지 않는다");
        var gap=service.priceGap("4148012800","동패동",java.util.List.of(new BigDecimal("84")));
        assertEquals("READY",gap.status(),"분양가 비교도 실패한 달만 빼고 계산한다");
        assertTrue(gap.partial());
    }
    @Test void oneMalformedRowDoesNotDropTheWholeMonth() throws Exception {
        String xml="<response><header><resultCode>000</resultCode></header><body><totalCount>2</totalCount><items>"
            +"<item><aptNm>정상 아파트</aptNm><umdNm>동패동</umdNm><dealAmount>58,900</dealAmount><excluUseAr>84.95</excluUseAr><floor>12</floor><buildYear>2020</buildYear><dealYear>2026</dealYear><dealMonth>10</dealMonth><dealDay>1</dealDay></item>"
            +"<item><aptNm>층 없음</aptNm><umdNm>동패동</umdNm><dealAmount>40,000</dealAmount><excluUseAr>59.9</excluUseAr><floor></floor><buildYear>2020</buildYear><dealYear>2026</dealYear><dealMonth>10</dealMonth><dealDay>2</dealDay></item>"
            +"</items></body></response>";
        var page=NeighborhoodResearchService.parseTrades(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(2,page.rawCount(),"쪽 수 계산은 원래 줄 수로");
        assertEquals(java.util.List.of("정상 아파트"),page.trades().stream().map(NeighborhoodResearchService.Trade::apartment).toList());
    }
    @Test void tooManyRequestsIsNotRetried() {
        AtomicInteger calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{calls.incrementAndGet();throw new ResearchTransport.ProviderStatus(429);},"key");
        var district=service.districtTrades("4148012800",3,System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(10));
        assertEquals("UNAVAILABLE",district.status());
        assertEquals(1,calls.get(),"429는 다시 시도하지 않고, 차단기가 열려 남은 달도 부르지 않는다");
        assertTrue(new ResearchTransport.ProviderStatus(503).retryable());
    }

    /** 지도를 옮겨 여러 시·군·구를 한꺼번에 불러도 국토부 호출은 동시에 3개까지만, 간격을 두고 나간다(429 초당 한도 예방). */
    @Test void providerCallsArePacedWhenManyDistrictsLoadAtOnce() throws Exception {
        AtomicInteger running=new AtomicInteger(),peak=new AtomicInteger(),calls=new AtomicInteger();
        var service=new NeighborhoodResearchService((uri,headers)->{
            int now=running.incrementAndGet();peak.accumulateAndGet(now,Math::max);calls.incrementAndGet();
            try { Thread.sleep(40); } finally { running.decrementAndGet(); }
            return response(uri.getQuery().split("DEAL_YMD=")[1].substring(0,6),"동패동","");
        },"key");
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(5);
        var districts=java.util.List.of("4148012800","2824511000","2823710100","4128510100","1168010100");
        var futures=districts.stream().map(code -> pool.submit(() -> service.districtTrades(code,2,deadline))).toList();
        for(var future:futures) assertEquals("READY",future.get().status());
        pool.shutdown();
        assertEquals(10,calls.get());
        assertTrue(peak.get()<=NeighborhoodResearchService.MOLIT_SLOTS,"동시 호출 "+peak.get()+"개");
    }
    /** 차례를 기다리다 응답 시간 한도를 넘기면 그 달은 실패로 남기지 않고 받은 달까지만 돌려준다(화면이 이어 부른다). */
    @Test void waitingForATurnPastTheDeadlineReturnsAPartialResult() {
        var service=new NeighborhoodResearchService((uri,headers)->response(uri.getQuery().split("DEAL_YMD=")[1].substring(0,6),"동패동",""),"key");
        long before=NeighborhoodResearchService.MOLIT_SPACING_MS;
        NeighborhoodResearchService.MOLIT_SPACING_MS=400;
        try {
            var district=service.districtTrades("4148012800",12,System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(900));
            assertTrue(district.partial(),"한도 안에 다 받지 못했으면 partial");
            assertTrue(district.months()>=1 && district.months()<12,"받은 달 "+district.months());
            assertFalse(service.molitPaused(),"속도 조절 대기는 제공처 거절이 아니다");
        } finally { NeighborhoodResearchService.MOLIT_SPACING_MS=before; }
    }
}
