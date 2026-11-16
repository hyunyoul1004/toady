package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 새 공고 RSS: 모집공고일 최신순, 미래 공고 제외, 유형별 날짜 형식 통일, XML 이스케이프, 지워진 공고 건너뛰기. */
class AnnouncementFeedTest {
    private JdbcTemplate jdbc;
    private PlannerSummary summaries;
    private AnnouncementFeed feed;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:feed" + System.nanoTime() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        for (String table : new String[]{"TB_APT_LTTOT_PBLANC_DETAIL", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "TB_REMNDR_LTTOT_PBLANC_DETAIL",
                "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL"}) {
            jdbc.execute("CREATE TABLE " + table + " (HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), RCRIT_PBLANC_DE VARCHAR(10))");
        }
        summaries = mock(PlannerSummary.class);
        when(summaries.get(anyString(), anyString(), anyString())).thenAnswer(call -> summary(call.getArgument(0) + "/" + call.getArgument(1) + "/" + call.getArgument(2), "단지 " + call.getArgument(1)));
        feed = new AnnouncementFeed(jdbc, summaries, "https://www.toady.co.kr/", Clock.fixed(Instant.parse("2026-11-16T03:00:00Z"), ZoneId.of("Asia/Seoul")));
    }

    private static PlannerSummary.Summary summary(String id, String name) {
        return new PlannerSummary.Summary(id, name, "경기도 파주시 동패동 1", "300", "민영", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 27),
                "", List.of(), false, 45000L, 61000L);
    }

    @Test void newestAnnouncementsComeFirstAcrossTypesAndFutureOnesWaitUntilTheirDay() {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('A1','A1','2026-11-10'),('A2','A2','2026-11-16'),('A3','A3','2026-11-20')");
        jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL VALUES ('O1','O1','20261113')");
        jdbc.update("INSERT INTO TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL VALUES ('R1','R1',NULL)");
        List<AnnouncementFeed.Entry> recent = feed.recent();
        assertEquals(List.of("A2", "O1", "A1"), recent.stream().map(AnnouncementFeed.Entry::house).toList(), "모집공고일 최신순, 미래·날짜 없는 공고 제외");
    }

    @Test void titlesDoNotRepeatTheWordAnnouncement() {
        assertEquals("운정 더샵 청약 공고", AnnouncementFeed.title(" 운정 더샵 "));
        assertEquals("왕숙 A-2블록 잔여세대 추가입주자모집공고", AnnouncementFeed.title("왕숙 A-2블록 잔여세대 추가입주자모집공고"));
    }

    @Test void feedIsValidRssWithEscapedTextAndSkipsMissingAnnouncements() throws Exception {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL VALUES ('A1','A1','2026-11-12'),('GONE','GONE','2026-11-11')");
        when(summaries.get("apt", "A1", "A1")).thenReturn(summary("apt/A1/A1", "운정 <더샵> & 파크"));
        when(summaries.get(eq("apt"), eq("GONE"), eq("GONE"))).thenThrow(new EntityNotFoundException("gone"));
        String xml = feed.rss().getBody();
        var doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        var items = doc.getElementsByTagName("item");
        assertEquals(1, items.getLength(), "지워진 공고는 건너뛴다");
        assertEquals("운정 <더샵> & 파크 청약 공고", doc.getElementsByTagName("title").item(1).getTextContent());
        assertEquals("https://www.toady.co.kr/announcements/apt/A1/A1", doc.getElementsByTagName("link").item(1).getTextContent());
        assertEquals("Thu, 12 Nov 2026 09:00:00 +0900", doc.getElementsByTagName("pubDate").item(0).getTextContent());
        String description = doc.getElementsByTagName("description").item(1).getTextContent();
        assertTrue(description.contains("운정") && description.contains("4억 5,000만 ~ 6억 1,000만원") && description.contains("접수 11.20~11.21"), description);
    }
}
