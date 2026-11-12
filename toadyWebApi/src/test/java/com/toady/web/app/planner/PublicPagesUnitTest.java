package com.toady.web.app.planner;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 공개 페이지 문구·생활권 판정·구독 캘린더 형식은 DB 없이 같은 규칙을 지켜야 한다. */
class PublicPagesUnitTest {
    @Test void pricesUseManwonUnitsWithoutImplyingTheLowestPrice() {
        assertEquals("4억 2,000만원", PublicHtml.manwon(42000));
        assertEquals("5,000만원", PublicHtml.manwon(5000));
        assertEquals("6억원", PublicHtml.manwon(60000));
        assertEquals("5억 8,900만 ~ 9억 9,400만원", PublicHtml.priceRange(58900L, 99400L));
        assertEquals("6억 ~ 7억원", PublicHtml.priceRange(60000L, 70000L));
        assertEquals("6억원", PublicHtml.priceRange(60000L, 60000L));
        assertEquals("", PublicHtml.priceRange(null, null));
        assertEquals(58900L, PlannerSummary.manwon("58,900"));
        assertNull(PlannerSummary.manwon("0"));
        assertNull(PlannerSummary.manwon("미정"));
    }
    @Test void livingAreaMatchesAddressTermsAndSlugs() {
        assertEquals("unjeong", LivingAreas.match("경기도 파주시 파주운정3지구 주상복합 3블록(경기도 파주시 동패동 1673번지 일원)", "시티프라디움").orElseThrow().slug());
        assertEquals("wirye", LivingAreas.match("서울특별시 송파구 장지동 100", null).orElseThrow().slug());
        assertTrue(LivingAreas.match("서울특별시 강남구 역삼동 1", "역삼 아파트").isEmpty());
        assertEquals("운정", LivingAreas.bySlug("unjeong").orElseThrow().label());
        assertTrue(LivingAreas.bySlug("운정").isEmpty());
        assertEquals(LivingAreas.ALL.size(), LivingAreas.ALL.stream().map(LivingAreas.Area::slug).distinct().count());
    }
    @Test void calendarFeedFoldsLinesEscapesTextAndKeepsStableIds() {
        var date = LocalDate.of(2026, 12, 1);
        var summary = new PlannerSummary.Summary("apt/H1/N1", "아주 긴 이름의 운정 테스트 단지, 1블록; 후속사업 ".repeat(3), "경기도 파주시 동패동", "", "", date, date, date, "",
            List.of(new PlannerSummary.Event("특별공급 시작", date)), false, null, null);
        String ics = CalendarIcs.build(List.of(summary, PlannerSummary.Summary.missing("apt/X/Y")), "https://www.toady.co.kr", Instant.parse("2026-11-12T00:00:00Z"));
        assertTrue(ics.endsWith("END:VCALENDAR\r\n"));
        for (String line : ics.split("\r\n")) assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 75, line);
        assertEquals(1, ics.split("BEGIN:VEVENT", -1).length - 1, "원본이 없는 공고는 일정을 만들지 않는다");
        assertTrue(ics.contains("UID:apt-H1-N1-20261201@toady.co.kr"));
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20261201") && ics.contains("DTEND;VALUE=DATE:20261202"));
        assertTrue(ics.contains("TRIGGER:-PT15H"));
        String unfolded = ics.replace("\r\n ", "");
        assertTrue(unfolded.contains("운정 테스트 단지\\, 1블록\\; 후속사업"));
    }
    @Test void ratePollBucketsAndPublishedTopRate() {
        assertEquals(0, RatePoll.bucket(0.4)); assertEquals(1, RatePoll.bucket(1)); assertEquals(3, RatePoll.bucket(29.99)); assertEquals(5, RatePoll.bucket(512.3));
        assertEquals(12.5, RatePoll.topRate(List.of("3.1", "12.5", "(△2)", "", "1,000a")));
        assertNull(RatePoll.topRate(List.of("-", "(△1)")));
        assertEquals(6, RatePoll.LABELS.size());
    }
    @Test void weekRangeUsesMondayToSundayInKorea() {
        var week = PublicCollectionPages.Week.of(LocalDate.of(2026, 11, 12));
        assertEquals(LocalDate.of(2026, 11, 9), week.monday()); assertEquals(LocalDate.of(2026, 11, 15), week.sunday());
        assertTrue(week.overlaps(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 9)));
        assertFalse(week.overlaps(LocalDate.of(2026, 11, 16), null));
        assertEquals(LocalDate.of(2026, 11, 16), week.next().monday());
    }
    @Test void scoreSummaryIgnoresUnpublishedValuesAndKeepsNewestFirst() {
        var body = ScoreBenchmarks.summarize(List.of(
            new String[]{"A", "1", "운정 A", "경기도 파주시 다율동 1", "2026-10-01", "50", "70"},
            new String[]{"A", "1", "운정 A", "경기도 파주시 다율동 1", "2026-10-01", "0", "-"},
            new String[]{"B", "1", "강남 B", "서울특별시 강남구 1", "20261105", "60", "84"},
            new String[]{"C", "1", "빈 공고", "서울특별시 강남구 2", "2026-11-01", "", "90"}), Instant.parse("2026-11-12T00:00:00Z"));
        @SuppressWarnings("unchecked") var recent = (List<ScoreBenchmarks.Item>) body.get("recent");
        assertEquals(List.of("apt/B/1", "apt/A/1"), recent.stream().map(ScoreBenchmarks.Item::id).toList(), "84점 초과·0점·빈 값만 있는 공고는 뺀다");
        assertEquals(50.0, recent.get(1).low()); assertEquals(70.0, recent.get(1).high()); assertEquals("unjeong", recent.get(1).areaSlug());
    }
    @Test void areaSubscribeLinksUseWebcalAndGoogle() {
        var area = LivingAreas.bySlug("dongtan").orElseThrow();
        assertEquals("https://www.toady.co.kr/calendar/areas/dongtan.ics", AreaCalendar.feedUrl("https://www.toady.co.kr/", area));
        String links = AreaCalendar.subscribeLinks("https://www.toady.co.kr", area);
        assertTrue(links.contains("webcal://www.toady.co.kr/calendar/areas/dongtan.ics"));
        assertTrue(links.contains("calendar.google.com/calendar/r?cid=webcal%3A%2F%2Fwww.toady.co.kr%2Fcalendar%2Fareas%2Fdongtan.ics"));
    }
    @Test void regionUsesProvinceAndDistrictPrefixes() {
        assertArrayEquals(new String[]{"경기", "파주시"}, ScoreBenchmarks.region("경기도 파주시 파주운정3지구 주상복합 3블록(경기도 파주시 동패동 1673번지 일원)"));
        assertArrayEquals(new String[]{"서울", "강남구"}, ScoreBenchmarks.region("서울특별시 강남구 역삼동 1"));
        assertArrayEquals(new String[]{"전북", "전주시"}, ScoreBenchmarks.region("전북특별자치도 전주시 완산구 1"));
        assertArrayEquals(new String[]{"세종", null}, ScoreBenchmarks.region("세종특별자치시 반곡동 14"));
        assertArrayEquals(new String[]{null, null}, ScoreBenchmarks.region("주소 미상"));
    }
    @Test void searchWidensPeriodWhenRecentDataIsScarceAndSuggestsProvince() {
        var today = LocalDate.of(2026, 11, 12);
        var items = ScoreBenchmarks.items(List.of(
            new String[]{"A", "1", "파주 최근", "경기도 파주시 1", "2026-05-01", "50", "70"},
            new String[]{"B", "1", "파주 4년 전", "경기도 파주시 2", "2022-09-01", "40", "60"},
            new String[]{"C", "1", "파주 7년 전", "경기도 파주시 3", "2019-03-01", "30", "55"},
            new String[]{"D", "1", "수원", "경기도 수원시 영통구 1", "2026-01-01", "65", "80"}));
        var paju = ScoreBenchmarks.search(items, today, "경기", "파주시", null, 3, null, "recent");
        assertEquals(true, paju.get("expanded"), "최근 3년 1건뿐이면 기간을 넓힌다");
        assertEquals(0, paju.get("years"));
        assertEquals(3, paju.get("total"));
        @SuppressWarnings("unchecked") var stats = (java.util.Map<String, Object>) paju.get("stats");
        assertEquals(40.0, stats.get("medianLow"));
        var fiveYears = ScoreBenchmarks.search(items, today, "경기", "파주시", null, 5, null, "low");
        assertEquals(0, fiveYears.get("years"), "5년을 골라도 3건이 안 되면 전체로 넓힌다");
        @SuppressWarnings("unchecked") var sorted = (List<ScoreBenchmarks.Item>) fiveYears.get("items");
        assertEquals("apt/C/1", sorted.get(0).id(), "최저가점 낮은 순 정렬");
        var all = ScoreBenchmarks.search(items, today, "경기", null, null, 3, null, "recent");
        assertEquals("경기", all.get("scope")); assertEquals(5, all.get("years")); assertEquals(3, all.get("total"), "최근 3년 2건이라 5년(3건)으로 넓히고 거기서 멈춘다");
        assertEquals(false, ScoreBenchmarks.search(items, today, "경기", null, null, 0, null, "recent").get("expanded"), "전체 기간을 고르면 넓히지 않는다");
        var none = ScoreBenchmarks.search(items, today, "경기", "고양시", null, 3, null, "recent");
        assertEquals(0, none.get("total"));
        assertEquals(java.util.Map.of("sido", "경기", "count", 4L), none.get("suggestion"));
        assertEquals(1, ScoreBenchmarks.search(items, today, null, null, null, 0, "수원", "recent").get("total"));
        var regions = ScoreBenchmarks.regions(items);
        assertEquals("경기", regions.get(0).get("sido"));
        assertEquals(4L, regions.get(0).get("count"));
    }
}
