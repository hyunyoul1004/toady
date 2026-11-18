package com.toady.web.app.planner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** 사이트맵은 최근·앞으로의 공고만 알린다. 유형마다 접수 마감 컬럼과 날짜 형식이 달라도 같은 기준으로 고른다. */
class AnnouncementSitemapTest {
    private JdbcTemplate jdbc;
    private PublicAnnouncementIndex index;
    private AnnouncementSitemap sitemap;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:sitemap" + System.nanoTime() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        for (var source : PublicAnnouncementIndex.SOURCES.values()) {
            jdbc.execute("CREATE TABLE " + source.table() + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), HOUSE_NM VARCHAR(200), HOUSE_SECD_NM VARCHAR(100), "
                + source.address() + " VARCHAR(256), SUBSCRPT_AREA_CODE_NM VARCHAR(100), " + source.start() + " VARCHAR(10), " + source.end() + " VARCHAR(10), "
                + "RCRIT_PBLANC_DE VARCHAR(10), PRZWNER_PRESNATN_DE VARCHAR(10), REG_DT TIMESTAMP)");
            for (String result : source.results()) jdbc.execute("CREATE TABLE IF NOT EXISTS " + result + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), REG_DT TIMESTAMP)");
        }
        // 기준일 2026-11-16(KST), 최근 365일 → 2025-11-16 이후로 끝나는 공고만
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        index = new PublicAnnouncementIndex(jdbc, 365, clock);
        sitemap = new AnnouncementSitemap(index, "https://www.toady.co.kr/");
    }
    private void apt(String house, String announced, String end, String address) {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,RCRIT_PBLANC_DE) VALUES (?,?,?,?,?,?,?)",
            house, house, "단지 " + house, address, end, end, announced);
    }

    @Test void onlyRecentAndUpcomingAnnouncementsAreListed() {
        apt("2017001518", "2017-12-20", "2018-01-05", "서울특별시 강남구 1");
        apt("2025000100", "2025-11-01", "2025-11-16", "서울특별시 강남구 1");
        apt("2026000900", "2026-11-20", "2026-12-02", "서울특별시 강남구 1");
        // 접수 마감이 비어 있으면 모집공고일로 본다.
        apt("2026000901", "2026-10-01", null, "서울특별시 강남구 1");
        apt("2024000001", "2024-01-01", "", "서울특별시 강남구 1");
        String xml = sitemap.entries("apt", 0).getBody();
        assertTrue(xml.contains("/announcements/apt/2025000100/2025000100<") && xml.contains("/2026000900/") && xml.contains("/2026000901/"), xml);
        assertFalse(xml.contains("2017001518") || xml.contains("2024000001"), "몇 년 지난 공고는 사이트맵에서 뺀다");
        assertTrue(xml.contains("https://www.toady.co.kr/announcements/"), "도메인 끝 슬래시는 한 번만");
    }

    @Test void compactDateFormatsAreComparedTheSameWay() {
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,RCRIT_PBLANC_DE,SUBSCRPT_RCEPT_ENDDE) VALUES ('O1','Q1','20261110','20261118')");
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,RCRIT_PBLANC_DE,SUBSCRPT_RCEPT_ENDDE) VALUES ('O2','Q2','20200110','20200118')");
        String xml = sitemap.entries("optional", 0).getBody();
        assertTrue(xml.contains("/optional/O1/Q1<") && !xml.contains("/O2/"), xml);
    }

    @Test void indexListsOnlyTypesThatStillHaveRecentAnnouncements() {
        apt("A1", "2026-11-01", "2026-11-20", "경기도 파주시 동패동 1");
        jdbc.update("INSERT INTO TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,RCRIT_PBLANC_DE,SUBSCRPT_RCEPT_ENDDE) VALUES ('U1','U1','2019-01-01','2019-01-10')");
        String sitemapIndex = sitemap.index().getBody();
        assertTrue(sitemapIndex.contains("/sitemaps/apt/0.xml") && sitemapIndex.contains("/sitemaps/areas.xml") && sitemapIndex.contains("/sitemaps/regions.xml"), sitemapIndex);
        assertFalse(sitemapIndex.contains("/sitemaps/urban/"), "최근 공고가 없는 유형은 색인에서 뺀다");
    }

    @Test void lastmodIsTheLatestOfCollectionAndPublishedResults() {
        apt("A1", "2026-10-01", "2026-10-10", "경기도 파주시 동패동 1");
        apt("A2", "2026-10-02", "2026-10-12", "경기도 파주시 야당동 1");
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET REG_DT=TIMESTAMP '2026-10-01 09:00:00'");
        // A1은 경쟁률(10/15)·당첨가점(10/20)이 나중에 들어왔다. 결과가 들어온 날이 lastmod다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_CMPET VALUES ('A1','A1',TIMESTAMP '2026-10-15 18:00:00')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_SCORE VALUES ('A1','A1',TIMESTAMP '2026-10-20 18:00:00')");
        String xml = sitemap.entries("apt", 0).getBody();
        assertTrue(xml.contains("/apt/A1/A1</loc><lastmod>2026-10-20</lastmod>"), xml);
        assertTrue(xml.contains("/apt/A2/A2</loc><lastmod>2026-10-01</lastmod>"), xml);
        // 지역 페이지는 담긴 공고 중 가장 늦은 날, 정식 시·도 이름(퍼센트 인코딩) 주소를 쓴다.
        String regions = sitemap.regions().getBody();
        assertTrue(regions.contains("/regions/" + Regions.segment("경기도") + "/" + Regions.segment("파주시") + "</loc><lastmod>2026-10-20</lastmod>"), regions);
        assertTrue(regions.contains("/regions</loc>"), regions);
        String areas = sitemap.areas().getBody();
        assertTrue(areas.contains("/areas/unjeong</loc><lastmod>2026-10-20</lastmod>"), areas);
        assertTrue(areas.contains("/score</loc></url>"), "계산기 페이지에는 lastmod를 붙이지 않는다");
    }

    @Test void districtPagesOnlyForRealDistrictsAndEmptyProvincesAreNoindex() {
        // '시·구'로 끝나도 신도시·혁신도시·지구 이름은 시·군·구가 아니다. 시·도에만 넣는다.
        apt("A1", "2026-10-01", "2026-10-10", "인천광역시 검단신도시 AA21블록");
        apt("A2", "2026-10-02", "2026-10-12", "경상남도 진주혁신도시 C-1블록");
        apt("A3", "2026-10-03", "2026-10-13", "경기도 평택고덕국제화지구 A-50블록");
        apt("A4", "2026-10-04", "2026-10-14", "경기도 파주시 동패동 1");
        String regions = sitemap.regions().getBody();
        assertFalse(regions.contains(Regions.segment("검단신도시")) || regions.contains(Regions.segment("진주혁신도시")) || regions.contains(Regions.segment("평택고덕국제화지구")), regions);
        assertTrue(regions.contains("/regions/" + Regions.segment("인천광역시") + "</loc>") && regions.contains("/regions/" + Regions.segment("경상남도") + "</loc>"), "시·도에는 넣는다");
        assertTrue(regions.contains("/regions/" + Regions.segment("경기도") + "/" + Regions.segment("파주시") + "</loc>"), regions);
        assertFalse(regions.contains("/regions/" + Regions.segment("제주특별자치도")), "공고가 없는 시·도는 사이트맵에서 뺀다");

        PublicHtml.clearCache();
        var pages = new PublicRegionPages(index, "https://www.toady.co.kr");
        assertEquals(404, pages.district("인천광역시", "검단신도시", null).getStatusCode().value());
        assertEquals(404, pages.district("경기도", "평택고덕국제화지구", null).getStatusCode().value());
        assertEquals(404, pages.district("서울특별시", "파주시", null).getStatusCode().value(), "다른 시·도의 시·군·구");
        assertEquals(200, pages.district("경기", "파주시", null).getStatusCode().value());
        String incheon = pages.sido("인천광역시", null).getBody();
        assertTrue(incheon.contains("단지 A1") && !incheon.contains(Regions.segment("검단신도시")), "시·도 페이지에 공고는 두되 가짜 시·군·구 링크는 없다");
        assertFalse(incheon.contains("noindex"));
        var jeju = pages.sido("제주", null);
        assertEquals(200, jeju.getStatusCode().value());
        assertTrue(jeju.getBody().contains("<meta name='robots' content='noindex'>"), "공고가 없는 시·도는 색인하지 않는다");
        PublicHtml.clearCache();
    }

    @Test void indexWorksWithoutResultTablesOrRegistrationColumn() {
        JdbcTemplate bare = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:sitemapbare" + System.nanoTime() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        for (var source : PublicAnnouncementIndex.SOURCES.values())
            bare.execute("CREATE TABLE " + source.table() + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), HOUSE_NM VARCHAR(200), HOUSE_SECD_NM VARCHAR(100), "
                + source.address() + " VARCHAR(256), SUBSCRPT_AREA_CODE_NM VARCHAR(100), " + source.start() + " VARCHAR(10), " + source.end() + " VARCHAR(10), "
                + "RCRIT_PBLANC_DE VARCHAR(10), PRZWNER_PRESNATN_DE VARCHAR(10))");
        bare.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,RCRIT_PBLANC_DE,RCEPT_ENDDE) VALUES ('A1','A1','2026-11-01','2026-11-20')");
        var fallback = new AnnouncementSitemap(new PublicAnnouncementIndex(bare, 365, Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Seoul"))), "https://www.toady.co.kr");
        assertTrue(fallback.entries("apt", 0).getBody().contains("/apt/A1/A1</loc><lastmod>2026-11-01</lastmod>"), "수집 시각이 없으면 모집공고일");
    }
}
