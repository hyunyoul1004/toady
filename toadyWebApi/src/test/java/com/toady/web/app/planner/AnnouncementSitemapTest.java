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
    private AnnouncementSitemap sitemap;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:sitemap" + System.nanoTime() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), RCRIT_PBLANC_DE VARCHAR(10), RCEPT_ENDDE VARCHAR(10))");
        for (String table : new String[]{"TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "TB_REMNDR_LTTOT_PBLANC_DETAIL", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL"}) {
            jdbc.execute("CREATE TABLE " + table + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), RCRIT_PBLANC_DE VARCHAR(10), SUBSCRPT_RCEPT_ENDDE VARCHAR(10))");
        }
        // 기준일 2026-11-16(KST), 최근 365일 → 2025-11-16 이후로 끝나는 공고만
        Clock clock = Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Seoul"));
        sitemap = new AnnouncementSitemap(jdbc, "https://www.toady.co.kr/", 365, clock);
    }

    @Test void onlyRecentAndUpcomingAnnouncementsAreListed() {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('2017001518','2017001518','2017-12-20','2018-01-05')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('2025000100','2025000100','2025-11-01','2025-11-16')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('2026000900','2026000900','2026-11-20','2026-12-02')");
        // 접수 마감이 비어 있으면 모집공고일로 본다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('2026000901','2026000901','2026-10-01',NULL)");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('2024000001','2024000001','2024-01-01','')");
        String xml = sitemap.entries("apt", 0).getBody();
        assertTrue(xml.contains("/announcements/apt/2025000100/2025000100<") && xml.contains("/2026000900/") && xml.contains("/2026000901/"), xml);
        assertFalse(xml.contains("2017001518") || xml.contains("2024000001"), "몇 년 지난 공고는 사이트맵에서 뺀다");
        assertTrue(xml.contains("https://www.toady.co.kr/announcements/"), "도메인 끝 슬래시는 한 번만");
    }

    @Test void compactDateFormatsAreComparedTheSameWay() {
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL VALUES ('O1','Q1','20261110','20261118')");
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL VALUES ('O2','Q2','20200110','20200118')");
        String xml = sitemap.entries("optional", 0).getBody();
        assertTrue(xml.contains("/optional/O1/Q1<") && !xml.contains("/O2/"), xml);
    }

    @Test void indexListsOnlyTypesThatStillHaveRecentAnnouncements() {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('A1','A1','2026-11-01','2026-11-20')");
        jdbc.update("INSERT INTO TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL VALUES ('U1','U1','2019-01-01','2019-01-10')");
        String index = sitemap.index().getBody();
        assertTrue(index.contains("/sitemaps/apt/0.xml") && index.contains("/sitemaps/areas.xml"), index);
        assertFalse(index.contains("/sitemaps/urban/"), "최근 공고가 없는 유형은 색인에서 뺀다");
    }
}
