package com.toady.web.app.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.AnnouncementResults;
import com.toady.web.app.planner.PlannerSummary;
import com.toady.web.app.planner.PublicAreaPage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 결과 즉시 알림 대상 기간: 접수 마감일이 아니라 '접수 시작 ~ 마감 30일'로 본다.
 * 특별공급 11/16 → 1순위 11/17 → 마감 11/18인 공고는 11/17 저녁에 나온 1순위 결과를 마감일까지 기다리지 않고 알린다.
 */
class PushResultWindowTest {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 20, 15);

    static PlannerSummary.Summary summary(String id, LocalDate start, LocalDate end) {
        return new PlannerSummary.Summary(id, "결과 단지 " + id, "경기도 의정부시 의정부동 1", "100", "민영", start, end, end.plusDays(7), "", List.of(), false, null, null);
    }

    @Test void firstRankResultBeforeTheClosingDayIsAnnouncedRightAway() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AnnouncementResults results = mock(AnnouncementResults.class);
        when(results.forIds(anyCollection())).thenAnswer(call -> {
            Collection<String> ids = call.getArgument(0);
            Map<String, AnnouncementResults.Result> out = new LinkedHashMap<>();
            for (String id : ids) out.put(id, new AnnouncementResults.Result(id, true, 12.5, "", "", false));
            return out;
        });
        PushDigest digest = new PushDigest(null, null, jdbc, null, null, null, null, new ObjectMapper(), results, null, null,
            Clock.fixed(NOW.atZone(KST).toInstant(), KST));
        LocalDate today = NOW.toLocalDate();
        List<PlannerSummary.Summary> favorites = List.of(
            // 1순위 접수일 저녁, 마감은 내일 → 알린다(예전에는 마감일까지 대상이 아니었다).
            summary("apt/OPEN/1", LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18)),
            // 접수 전 공고는 결과가 있을 수 없으니 보지 않는다.
            summary("apt/LATER/1", LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 23)),
            // 마감 30일이 지난 공고는 새 소식으로 보내지 않는다.
            summary("apt/OLD/1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3)));
        // 결과 종류별 기록은 이미 시작된 상태(기준 시각 아님)로 둔다.
        Set<String> started = new HashSet<>(List.of(PushDigest.RATE_SLUG, PushDigest.SCORE_SLUG));
        var lines = digest.resultLines(favorites, today, NOW.minusHours(2), NOW, new HashMap<>(), new HashMap<>(), started);
        assertEquals(List.of("apt/OPEN/1|경쟁률 발표"), lines.stream().map(line -> line.announcementId() + "|" + line.label()).toList());
        verify(results).forIds(List.of("apt/OPEN/1"));
    }

    @Test void announcementListAndSubscriberLinesShareTheSameWindow() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        assertTrue(PublicAreaPage.resultWindow(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), today, 30), "접수 중(1순위 결과가 나올 수 있음)");
        assertTrue(PublicAreaPage.resultWindow(today, today, today, 30), "접수 당일");
        assertFalse(PublicAreaPage.resultWindow(today.plusDays(1), today.plusDays(3), today, 30), "접수 전");
        assertTrue(PublicAreaPage.resultWindow(LocalDate.of(2026, 10, 15), today.minusDays(30), today, 30), "마감 30일째까지");
        assertFalse(PublicAreaPage.resultWindow(LocalDate.of(2026, 10, 14), today.minusDays(31), today, 30), "마감 31일째부터 제외");
        assertTrue(PublicAreaPage.resultWindow(null, today, today, 30), "시작일이 없으면 마감일로 대신한다");
        assertFalse(PublicAreaPage.resultWindow(null, null, today, 30));
    }
}
