package com.toady.web.app.planner.research;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ComplexPriceServiceTest {
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:complex;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP TABLE IF EXISTS TB_APT_COMPLEX_LOCATION");
        jdbc.execute("CREATE TABLE TB_APT_COMPLEX_LOCATION (COMPLEX_KEY VARCHAR(120) PRIMARY KEY, LATITUDE DECIMAL(10,7), LONGITUDE DECIMAL(10,7), STATUS VARCHAR(12) NOT NULL, CHECKED_DT TIMESTAMP NOT NULL)");
    }

    private static String item(String name, String dong, String jibun, String amount, String area, int floor, String year, String month, int day) {
        return "<item><aptNm>" + name + "</aptNm><umdNm>" + dong + "</umdNm><jibun>" + jibun + "</jibun><dealAmount>" + amount + "</dealAmount><excluUseAr>" + area
            + "</excluUseAr><floor>" + floor + "</floor><buildYear>" + year + "</buildYear><dealYear>" + month.substring(0, 4) + "</dealYear><dealMonth>" + month.substring(4)
            + "</dealMonth><dealDay>" + day + "</dealDay><cdealType></cdealType></item>";
    }
    private static byte[] molit(String month) {
        String items = item("한빛마을", "동패동", "1673", "75,000", "84.97", 12, "2018", month, 3)
            + item("한빛마을", "동패동", "1673", "52,000", "59.92", 5, "2018", month, 9)
            + item("한빛마을(개명)", "동패동", "1673", "78,000", "84.91", 20, "2018", month, 20)
            + item("산자락", "동패동", "산12-3", "30,000", "59.00", 2, "1999", month, 1)
            + item("지번없음", "야당동", "", "40,000", "70.00", 3, "2005", month, 2);
        return ("<response><header><resultCode>000</resultCode></header><body><totalCount>5</totalCount><items>" + items + "</items></body></response>").getBytes(StandardCharsets.UTF_8);
    }
    /** 카카오 응답: 요청 지번과 같은 결과 하나, 다른 시군구의 같은 지번 하나(걸러져야 함). */
    private static byte[] kakao(String query) {
        if (query.contains("1673")) return ("{\"documents\":[{\"address\":{\"b_code\":\"1168010100\",\"main_address_no\":\"1673\",\"sub_address_no\":\"\",\"mountain_yn\":\"N\",\"region_3depth_name\":\"동패동\",\"x\":\"127.0\",\"y\":\"37.5\"}},"
            + "{\"address\":{\"b_code\":\"4148012800\",\"main_address_no\":\"1673\",\"sub_address_no\":\"0\",\"mountain_yn\":\"N\",\"region_3depth_name\":\"동패동\",\"x\":\"126.7581\",\"y\":\"37.7203\"}}]}").getBytes(StandardCharsets.UTF_8);
        // 산12-3인데 산이 아닌 12-3만 있다 → 확정하지 않는다.
        return "{\"documents\":[{\"address\":{\"b_code\":\"4148012800\",\"main_address_no\":\"12\",\"sub_address_no\":\"3\",\"mountain_yn\":\"N\",\"region_3depth_name\":\"동패동\",\"x\":\"126.7\",\"y\":\"37.7\"}}]}".getBytes(StandardCharsets.UTF_8);
    }

    @Test void groupsTradesByJibunAndPlacesOnlyExactlyMatchedComplexes() {
        AtomicInteger kakaoCalls = new AtomicInteger();
        ResearchTransport transport = (uri, headers) -> {
            if (uri.getHost().equals("dapi.kakao.com")) {
                kakaoCalls.incrementAndGet();
                String query = java.net.URLDecoder.decode(uri.getRawQuery().split("query=")[1], StandardCharsets.UTF_8);
                assertTrue(query.startsWith("경기 파주시 동패동 ") || query.startsWith("동패동 "), "시·군·구 이름을 붙여 묻고, 못 찾으면 동·지번만으로 한 번 더: " + query);
                return kakao(query);
            }
            return molit(uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6));
        };
        var research = new NeighborhoodResearchService(transport, "key");
        var locator = new ComplexLocator(transport, jdbc, "kakao");
        var service = new ComplexPriceService(research, locator);

        var result = service.complexes("4148012800", "경기 파주시", 3);
        assertEquals("READY", result.status());
        assertEquals(3, result.months());
        assertEquals(3, result.complexes().size(), "같은 지번은 이름이 바뀌어도 한 단지");
        var hanbit = result.complexes().get(0);
        assertEquals("한빛마을(개명)", hanbit.name(), "가장 최근 신고 이름");
        assertEquals(9, hanbit.count());
        assertEquals(37.7203, hanbit.lat(), 1e-6, "다른 시·군·구의 같은 지번이 아니라 같은 시·군·구 결과를 쓴다");
        assertEquals(List.of(60, 85), hanbit.areas().stream().map(ComplexPriceService.AreaPrice::area).toList());
        assertEquals(78000, hanbit.areas().get(1).latest(), "면적별 가장 최근 거래");
        var area85 = hanbit.areas().get(1);
        // 이번 달(가장 최근 달)의 85㎡ 거래는 75,000·78,000 두 건 → 평균 76,500
        assertEquals(2, area85.monthCount(), "마지막 거래가 있었던 달의 거래만 센다");
        assertEquals(76500, area85.monthAvg());
        assertEquals(area85.latestDate().substring(0, 7), area85.month());
        assertTrue(hanbit.recent().size() <= ComplexPriceService.RECENT_PER_COMPLEX);

        var mountain = result.complexes().stream().filter(c -> c.jibun().startsWith("산")).findFirst().orElseThrow();
        assertNull(mountain.lat(), "산 지번이 산 아닌 결과와만 맞으면 지도에 놓지 않는다");
        var noJibun = result.complexes().stream().filter(c -> c.jibun().isBlank()).findFirst().orElseThrow();
        assertNull(noJibun.lat(), "지번이 없으면 목록에만");
        assertEquals(0, result.pending());

        // 결과는 테이블에 남아 다시 묻지 않는다(새 인스턴스도). 찾지 못한 산 지번은 동·지번만으로 한 번 더 물어 3회.
        assertEquals(3, kakaoCalls.get());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM TB_APT_COMPLEX_LOCATION", Integer.class));
        var fresh = new ComplexPriceService(new NeighborhoodResearchService(transport, "key"), new ComplexLocator(transport, jdbc, "kakao"));
        assertEquals(37.7203, fresh.complexes("4148012800", "경기 파주시", 3).complexes().get(0).lat(), 1e-6);
        assertEquals(3, kakaoCalls.get(), "저장된 좌표와 '찾지 못함'은 다시 묻지 않는다");
    }

    @Test void complexesWithoutTradesInTheLastYearAreAddedFromOlderMonths() {
        java.time.YearMonth now = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
        String lastYearEdge = now.minusMonths(12).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        String older = now.minusMonths(20).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        java.util.concurrent.atomic.AtomicInteger molitCalls = new java.util.concurrent.atomic.AtomicInteger();
        ResearchTransport transport = (uri, headers) -> {
            if (uri.getHost().equals("dapi.kakao.com")) return "{\"documents\":[]}".getBytes(StandardCharsets.UTF_8);
            molitCalls.incrementAndGet();
            String month = uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6);
            String items = month.equals(lastYearEdge)
                ? item("옛단지", "야당동", "77", "41,000", "84.90", 3, "2001", month, 5) + item("옛단지", "야당동", "77", "43,000", "84.95", 9, "2001", month, 25)
                : month.equals(older) ? item("옛단지", "야당동", "77", "30,000", "84.90", 1, "2001", month, 5) + item("더옛단지", "야당동", "88", "20,000", "59.00", 2, "1990", month, 1)
                : item("요즘단지", "동패동", "1", "70,000", "84.00", 5, "2019", month, 2);
            int count = items.split("<item>").length - 1;
            return ("<response><header><resultCode>000</resultCode></header><body><totalCount>" + count + "</totalCount><items>" + items + "</items></body></response>").getBytes(StandardCharsets.UTF_8);
        };
        var service = new ComplexPriceService(new NeighborhoodResearchService(transport, "key"), new ComplexLocator(transport, jdbc, "kakao"));
        var result = service.complexes("4148012800", null, 12);
        assertFalse(result.partial(), "가짜 제공처는 빨라서 3년치를 한 번에 받는다");
        assertEquals(36, molitCalls.get(), "최근 1년 12번 + 그 이전 2년 24번");
        var names = result.complexes().stream().map(ComplexPriceService.Complex::name).toList();
        assertTrue(names.containsAll(List.of("요즘단지", "옛단지", "더옛단지")), names.toString());
        var old = result.complexes().stream().filter(c -> c.name().equals("옛단지")).findFirst().orElseThrow();
        assertEquals(42000, old.areas().get(0).monthAvg(), "1년 전 단지도 마지막 거래월(12개월 전) 평균");
        assertEquals(2, old.areas().get(0).monthCount());
        assertEquals(now.minusMonths(12).toString(), old.areas().get(0).month());
        assertEquals(36, molitCalls.get() + 0, "다시 불러도 3년치를 다시 받지 않는다");
        service.complexes("4148012800", null, 12);
        assertEquals(36, molitCalls.get(), "완성된 결과는 캐시에서 준다");
    }

    @Test void worksWithoutTheLocationTableAndWithoutKakaoKey() {
        jdbc.execute("DROP TABLE TB_APT_COMPLEX_LOCATION");
        ResearchTransport transport = (uri, headers) -> uri.getHost().equals("dapi.kakao.com") ? kakao("1673") : molit(uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6));
        var withKey = new ComplexPriceService(new NeighborhoodResearchService(transport, "key"), new ComplexLocator(transport, jdbc, "kakao"));
        assertNotNull(withKey.complexes("4148012800", null, 6).complexes().get(0).lat(), "테이블이 없어도 메모리에 두고 지도에 놓는다");
        var noKey = new ComplexPriceService(new NeighborhoodResearchService(transport, "key"), new ComplexLocator(transport, jdbc, ""));
        var result = noKey.complexes("4148012800", null, 6);
        assertEquals("READY", result.status());
        assertEquals(0, result.pending(), "카카오 키가 없으면 기다리게 하지 않는다");
        assertNull(result.complexes().get(0).lat());
    }

    @Test void slowGeocodingIsReportedAsPendingAndContinuesInBackground() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        ResearchTransport transport = (uri, headers) -> {
            if (uri.getHost().equals("dapi.kakao.com")) { release.await(10, TimeUnit.SECONDS); return kakao("1673"); }
            return molit(uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6));
        };
        var locator = new ComplexLocator(transport, jdbc, "kakao");
        locator.locate(List.of(new ComplexLocator.Target("41480|동패동|1673", "41480", "동패동", "1673", "동패동 1673")), System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(locator.pending("41480|동패동|1673"), "시간 안에 못 끝나면 확인 중으로 남는다");
        release.countDown();
        for (int i = 0; i < 50 && locator.pending("41480|동패동|1673"); i++) Thread.sleep(50);
        assertTrue(locator.known(List.of("41480|동패동|1673")).get("41480|동패동|1673").isPresent(), "백그라운드에서 끝나면 다음 조회에 반영된다");
        assertEquals(Map.of(), locator.known(List.of()));
    }

    @Test void sameLegalDongComplexesAreLocatedFirst() {
        java.util.function.BiFunction<String,String,java.util.Map.Entry<String,java.util.List<NeighborhoodResearchService.Trade>>> entry=(key,dongCode)->
            java.util.Map.entry(key,java.util.List.of(new NeighborhoodResearchService.Trade(key,"동",dongCode,new java.math.BigDecimal("84"),3,"2020",java.time.LocalDate.of(2026,10,1),50000,"1")));
        var ordered=ComplexPriceService.nearFirst(java.util.List.of(entry.apply("busy","11000"),entry.apply("near","11700"),entry.apply("other","12000"),entry.apply("near2","11700")),"11700");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("near","near2","busy","other"),ordered.stream().map(java.util.Map.Entry::getKey).toList());
    }

    @Test void aWrongRegionNameCannotPoisonStoredLocations() {
        ResearchTransport transport = (uri, headers) -> {
            if (uri.getHost().equals("dapi.kakao.com")) {
                String query = java.net.URLDecoder.decode(uri.getRawQuery().split("query=")[1], StandardCharsets.UTF_8);
                // 엉뚱한 지역 이름이 붙은 질의는 결과가 없고, 동·지번만의 질의는 제 위치를 찾는다고 흉내 낸다.
                return query.startsWith("서울") ? "{\"documents\":[]}".getBytes(StandardCharsets.UTF_8) : kakao("경기 파주시 " + query);
            }
            return molit(uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6));
        };
        var service = new ComplexPriceService(new NeighborhoodResearchService(transport, "key"), new ComplexLocator(transport, jdbc, "kakao"));
        var result = service.complexes("4148012800", "서울 종로구", 3);
        assertEquals(37.7203, result.complexes().get(0).lat(), 1e-6, "region이 틀려도 동·지번으로 다시 찾아 '찾지 못함'을 저장하지 않는다");
    }
}
