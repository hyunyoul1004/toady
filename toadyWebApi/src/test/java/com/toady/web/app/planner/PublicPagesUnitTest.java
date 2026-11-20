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
    @Test void publicPagesShareAppHeaderFooterAndPalette() {
        String html = PublicHtml.page("https://www.toady.co.kr", "/weekly", "제목 <b>", "설명 & 요약", "https://www.toady.co.kr/weekly",
            "<nav aria-label='현재 위치'><a href='https://www.toady.co.kr/'>TOADY</a></nav><section class='card'>본문</section>").getBody();
        assertNotNull(html);
        // 상단: 로고·워드마크·주요 메뉴, 지금 페이지 표시, 빵부스러기는 그 아래 본문에 남는다.
        assertTrue(html.indexOf("<header class='site'>") < html.indexOf("<main>"), "헤더는 본문보다 먼저");
        assertTrue(html.contains("<a class='brand' data-app href='https://www.toady.co.kr/'><img src='https://www.toady.co.kr/brand/toady-mark.png' width='29' height='26' alt=''><span>TOADY</span></a>"));
        for (String link : List.of("/'>청약 캘린더", "/score'>가점 계산기", "/zupzup'>무순위", "/weekly'>이번 주"))
            assertTrue(html.contains("https://www.toady.co.kr" + link), link);
        assertTrue(html.contains("aria-current='page' href='https://www.toady.co.kr/weekly'"));
        assertEquals(1, html.split("aria-current=", -1).length - 1, "현재 페이지 표시는 하나");
        assertTrue(html.contains("<main><nav aria-label='현재 위치'>"));
        // 하단: 서비스 설명·데이터 출처·약관·문의
        assertTrue(html.indexOf("<footer class='site-foot'>") > html.indexOf("</main>"));
        assertTrue(html.contains("청약홈·국토교통부 공개 데이터"));
        assertTrue(html.contains("href='https://www.toady.co.kr/privacy'>개인정보처리방침</a>") && html.contains("href='https://www.toady.co.kr/terms'>이용약관</a>"));
        assertTrue(html.contains("mailto:dukkeoba@gmail.com"));
        // 제목·설명은 그대로 이스케이프되고, 유입처 스크립트는 문서 끝에 한 번만 둔다.
        assertTrue(html.contains("<title>제목 &lt;b&gt;</title>") && html.contains("content='설명 &amp; 요약'"));
        assertTrue(html.endsWith(PublicHtml.SOURCE_SCRIPT + "</body></html>\n"));
        assertEquals(1, html.split("<header", -1).length - 1); assertEquals(1, html.split("<footer", -1).length - 1);
        // 앱과 같은 파란 계열만 쓰고 예전 청록색은 남기지 않는다.
        assertTrue(PublicHtml.STYLE.contains("#2563eb") && PublicHtml.STYLE.contains("#eef4ff") && PublicHtml.STYLE.contains("#f8fafc"));
        for (String teal : List.of("#0b6e64", "#e7f4f1", "#b9dcd5", "#f4f8f7")) assertFalse(html.toLowerCase().contains(teal), teal);
        assertFalse(PublicHtml.header("https://www.toady.co.kr", "").contains("aria-current"), "공고·생활권 페이지는 메뉴에 해당 항목이 없다");
    }
    @Test void livingAreaMatchesAddressTermsAndSlugs() {
        assertEquals("unjeong", LivingAreas.match("경기도 파주시 파주운정3지구 주상복합 3블록(경기도 파주시 동패동 1673번지 일원)", "시티프라디움").orElseThrow().slug());
        assertEquals("wirye", LivingAreas.match("서울특별시 송파구 장지동 100", null).orElseThrow().slug());
        assertTrue(LivingAreas.match("서울특별시 강남구 역삼동 1", "역삼 아파트").isEmpty());
        // 같은 이름의 다른 지역은 생활권으로 보지 않는다(시·군 조건).
        assertTrue(LivingAreas.match("대구광역시 북구 검단동 100", "검단 푸르지오").isEmpty(), "대구 검단동은 인천 검단이 아니다");
        assertTrue(LivingAreas.match("경상북도 고령군 다산면 1", null).isEmpty(), "고령 다산면은 남양주 다산이 아니다");
        assertEquals("geomdan", LivingAreas.match("인천광역시 서구 불로동 1", "검단신도시 우미린").orElseThrow().slug());
        assertEquals("geomdan", LivingAreas.match("인천광역시 서구 백석동 1", "검단 아파트").orElseThrow().slug(), "인천 안의 '검단' 단지명은 인정");
        assertEquals("dasan", LivingAreas.match("경기도 남양주시 다산동 1", null).orElseThrow().slug());
        assertEquals("운정", LivingAreas.bySlug("unjeong").orElseThrow().label());
        assertTrue(LivingAreas.bySlug("운정").isEmpty());
        assertEquals(LivingAreas.all().size(), LivingAreas.all().stream().map(LivingAreas.Area::slug).distinct().count());
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
        assertTrue(RatePoll.shortage("(△1)") && RatePoll.shortage("△12") && RatePoll.shortage("(3)") && !RatePoll.shortage("3.1") && !RatePoll.shortage("-"));
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
    @Test void scoreRegionsUseSupplyRegionLikeTheRegionIndex() {
        // '광주시 오포읍'처럼 시·도 없이 시작하는 주소는 지역별 공개 페이지와 같이 공급지역명으로 시·도를 정한다.
        assertArrayEquals(new String[]{"경기", "광주시"}, ScoreBenchmarks.region("광주시 오포읍 신현리 1", "경기"));
        assertArrayEquals(new String[]{null, null}, ScoreBenchmarks.region("광주시 오포읍 신현리 1"), "공급지역명이 없으면 광주광역시로 넘겨짚지 않는다");
        var items = ScoreBenchmarks.items(List.<String[]>of(new String[]{"G", "1", "오포 단지", "광주시 오포읍 신현리 1", "2026-03-01", "50", "60", "경기"}));
        assertEquals("경기", items.getFirst().sido());
        assertEquals("광주시", items.getFirst().district());
        assertEquals(Regions.of("광주시 오포읍 신현리 1", "경기").map(Regions.Region::district).orElse(null), items.getFirst().district());
    }
    @Test void hiddenAnnouncementsMatchPathTypesAndAnnounceChangesOnce() {
        var overrides = org.mockito.Mockito.mock(com.toady.web.app.operation.AnnouncementOverrides.class);
        java.util.Set<String[]> none = java.util.Set.of(), one = java.util.Set.<String[]>of(new String[]{"H1", "N1"});
        org.mockito.Mockito.when(overrides.hiddenOf(org.mockito.ArgumentMatchers.anyString())).thenReturn(none);
        org.mockito.Mockito.when(overrides.hidden("PUBLIC_RENTAL", "H1", "N1")).thenReturn(true);
        List<Object> events = new java.util.ArrayList<>();
        var hidden = new HiddenAnnouncements(overrides, events::add);
        assertTrue(hidden.hiddenId("public-rental/H1/N1"), "공개 주소의 경로값을 운영 테이블 유형 이름으로 바꿔 확인한다");
        assertFalse(hidden.hiddenId("apt/H1/N1"));
        assertFalse(hidden.hiddenId("잘못된 값"));
        assertEquals(List.of("apt/H1/N1"), hidden.visible(List.of("public-rental/H1/N1", "apt/H1/N1"), id -> id), "숨긴 공고만 목록에서 뺀다");
        hidden.watch(); hidden.watch();
        assertTrue(events.isEmpty(), "처음 확인과 변화 없는 확인에는 알리지 않는다");
        org.mockito.Mockito.when(overrides.hiddenOf("APT")).thenReturn(one);
        hidden.watch(); hidden.watch(); hidden.watch();
        assertEquals(2, events.size(), "숨김 목록이 바뀌면 알리고, 계산 중이던 캐시 값을 정리하려고 다음 확인에 한 번만 더 알린다");
        assertInstanceOf(HiddenAnnouncements.Changed.class, events.getFirst());
        // 시작 기준값을 잡을 때 이미 숨긴 공고가 있으면 시작 중에 채워진 캐시를 비우도록 한 번 알린다.
        events.clear();
        var restarted = new HiddenAnnouncements(overrides, events::add);
        restarted.onReady(); restarted.watch(); restarted.watch();
        assertEquals(2, events.size(), "시작 때 숨긴 공고가 있으면 알리고(다음 확인에 한 번 더) 그 뒤로는 조용하다");
        assertFalse(new HiddenAnnouncements((com.toady.web.app.operation.AnnouncementOverrides) null, events::add).hiddenId("apt/H1/N1"), "숨김 기능이 없으면 아무것도 숨기지 않는다");
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
    @Test void scoreSearchCountsAnnouncementsWithoutRegionAndShowsAllPeriodCounts() {
        var today = LocalDate.of(2026, 11, 12);
        var items = ScoreBenchmarks.items(List.of(
            new String[]{"A", "1", "파주 최근", "경기도 파주시 1", "2026-05-01", "50", "70"},
            new String[]{"B", "1", "고양 7년 전", "경기도 고양시 덕양구 1", "2019-03-01", "30", "55"},
            new String[]{"C", "1", "경기 지구", "경기도 어느신도시 A1블록", "2026-04-01", "45", "60"},
            new String[]{"D", "1", "주소 미상", "어느 개발지구 B2블록", "2026-03-01", "40", "50"}));
        // 전국 4건 중 1건은 시·도를 모른다 → 시·도 합(3건)보다 1건 많다는 것을 알린다.
        assertEquals(1L, ScoreBenchmarks.search(items, today, null, null, null, 0, null, "recent").get("unknownRegion"));
        assertEquals(1L, ScoreBenchmarks.search(items, today, "경기", null, null, 0, null, "recent").get("unknownRegion"), "경기 3건 중 시·군·구 미확인 1건");
        assertEquals(0L, ScoreBenchmarks.search(items, today, "경기", "파주시", null, 0, null, "recent").get("unknownRegion"));
        assertNull(ScoreBenchmarks.search(items, today, null, null, "unjeong", 0, null, "recent").get("unknownRegion"), "생활권 검색은 지역 단계가 없다");
        var regions = ScoreBenchmarks.regions(items, today, 3);
        assertEquals(2L, regions.get(0).get("count")); assertEquals(3L, regions.get(0).get("allCount"));
        @SuppressWarnings("unchecked") var districts = (List<java.util.Map<String, Object>>) regions.get(0).get("districts");
        var goyang = districts.stream().filter(d -> "고양시".equals(d.get("name"))).findFirst().orElseThrow();
        assertEquals(0L, goyang.get("count"), "최근 3년에는 0건"); assertEquals(1L, goyang.get("allCount"), "전체 기간 건수를 함께 준다");
    }
    @Test void weekStatusSplitsTheWeeklyScheduleIntoOpenUpcomingAndClosed() {
        var status = new PublicCollectionPages.WeekStatus(25, 6, 2, 17);
        assertEquals(8, status.active());
        assertEquals("이번 주 일정 25건 · 접수 중 6 · 예정 2 · 마감 17", status.breakdown());
        assertEquals("접수 중·예정 8건(이번 주 일정 25건 중 마감 17건)", status.summary());
        assertEquals("최근 마감 · 최근 2개월 114건 중", PublicCollectionPages.closedHeading(114, 12), "목록 건수(12건)는 section이 붙인다");
        assertEquals("최근 마감 · 최근 2개월", PublicCollectionPages.closedHeading(5, 12), "다 보이면 일부라고 적지 않는다");
    }
    @Test void ratePollLimitsNewVotersPerNetworkButAllowsChangingAVote() {
        var poll = new RatePoll(null, null, java.time.Clock.systemUTC());
        for (int i = 0; i < RatePoll.VOTERS_PER_IP; i++) assertTrue(poll.voterAllowed("ip", "apt/A/1", "v" + i));
        assertFalse(poll.voterAllowed("ip", "apt/A/1", "new"), "같은 네트워크의 새 방문자 ID는 3개까지");
        assertTrue(poll.voterAllowed("ip", "apt/A/1", "v0"), "이미 투표한 방문자는 선택을 바꿀 수 있다");
        assertTrue(poll.voterAllowed("ip", "apt/B/1", "new"), "공고가 다르면 따로 센다");
    }

    @org.junit.jupiter.api.Test void moveInMonthIsNormalizedForThePlanner() {
        org.junit.jupiter.api.Assertions.assertEquals("2029-01", PlannerSummary.moveInMonth("202901"));
        org.junit.jupiter.api.Assertions.assertEquals("2029-01", PlannerSummary.moveInMonth("2029.01"));
        org.junit.jupiter.api.Assertions.assertNull(PlannerSummary.moveInMonth("202913"), "없는 달");
        org.junit.jupiter.api.Assertions.assertNull(PlannerSummary.moveInMonth(""));
        org.junit.jupiter.api.Assertions.assertNull(PlannerSummary.moveInMonth(null));
    }
    @Test void regionsUseExactProvinceNamesOrSupplyRegionWithoutPerComplexRules() {
        var paju = Regions.of("경기도 파주시 파주운정3지구 주상복합 3블록(경기도 파주시 동패동 1673번지 일원)").orElseThrow();
        assertEquals("경기도", paju.sido().name()); assertEquals("파주시", paju.district());
        assertEquals("/regions/" + Regions.segment("경기도") + "/" + Regions.segment("파주시"), paju.path());
        assertEquals("경기도 파주시", paju.label());
        assertEquals("수원시", Regions.of("경기 수원시 영통구 이의동 1").orElseThrow().district(), "구가 있는 시는 시 단위로 묶는다");
        assertEquals("강원특별자치도", Regions.of("강원도 춘천시 1").orElseThrow().sido().name(), "예전 이름도 지금 이름으로");
        assertNull(Regions.of("세종특별자치시 반곡동 14").orElseThrow().district());
        // 시·도 없이 '광주시'로 시작하면 광주광역시로 짐작하지 않고 공급지역명을 쓴다.
        assertTrue(Regions.of("광주시 오포읍 1", null).isEmpty());
        var gwangju = Regions.of("광주시 오포읍 1", "경기").orElseThrow();
        assertEquals("경기도", gwangju.sido().name()); assertEquals("광주시", gwangju.district());
        assertEquals("광주광역시", Regions.of("광주광역시 북구 1", "광주").orElseThrow().sido().name());
        assertEquals("충청북도", Regions.of("청주시 흥덕구 1", "충청북도").orElseThrow().sido().name());
        assertTrue(Regions.bySegment("경기").isPresent() && Regions.bySegment("경기도").isPresent() && Regions.bySegment("서울시").isPresent());
        assertTrue(Regions.bySegment("없는도").isEmpty() && Regions.bySegment("").isEmpty());
        assertEquals(17, Regions.ALL.size());
    }
    @Test void districtsComeOnlyFromTheRealDistrictListOfThatProvince() {
        // 지구·신도시 이름은 시·군·구가 아니다(시·도만 남긴다).
        for (String address : List.of("인천광역시 검단신도시 AA21블록", "경상남도 진주혁신도시 C-1블록", "경기도 평택고덕국제화지구 A-50블록",
                "경기도 위례신도시 A1-5블록", "경기 화성동탄2신도시 A-1블록", "서울특별시 마곡도시개발구 1")) {
            var region = Regions.of(address).orElseThrow();
            assertNull(region.district(), address);
        }
        assertEquals("서구", Regions.of("인천광역시 서구 원당동 1").orElseThrow().district());
        assertEquals("고성군", Regions.of("강원특별자치도 고성군 1").orElseThrow().district());
        assertEquals("군위군", Regions.of("대구광역시 군위군 1").orElseThrow().district());
        assertEquals("창원시", Regions.of("경남 창원시 성산구 1").orElseThrow().district());
        assertNull(Regions.of("광주시 오포읍 1", "광주").orElseThrow().district(), "광주광역시에는 광주시가 없다");
        var gyeonggi = Regions.strict("경기도").orElseThrow();
        assertTrue(Regions.district(gyeonggi, "파주시").isPresent() && Regions.district(gyeonggi, "강남구").isEmpty());
        assertEquals(17, Regions.DISTRICTS.size(), "모든 시·도에 목록이 있다");
        for (var sido : Regions.ALL) assertTrue(Regions.DISTRICTS.containsKey(sido.shortName()), sido.name());
        assertEquals(25, Regions.DISTRICTS.get("서울").size());
        assertEquals(31, Regions.DISTRICTS.get("경기").size());
        for (var names : Regions.DISTRICTS.values()) for (String name : names)
            assertTrue(name.matches("[가-힣]{1,5}(시|군|구)") && !name.endsWith("도시") && !name.endsWith("지구"), name);
    }
    @Test void resultCardUsesTheFetchedResultAndSkipsTheTableWhenHouseTypesFail() {
        var results = org.mockito.Mockito.mock(AnnouncementResults.class);
        org.mockito.Mockito.when(results.houseTypes("H1", "N1")).thenThrow(new IllegalStateException("db down"));
        var page = new PublicAnnouncementPage(null, null, "https://www.toady.co.kr", results, null);
        var published = new AnnouncementResults.Result("apt/H1/N1", true, 12.4, "54", "69", true);
        String card = page.resultCard("apt/H1/N1", published);
        assertTrue(card.contains("12.4 : 1") && card.contains("최저 54점"), card);
        assertFalse(card.contains("주택형별 1순위"), "주택형 조회가 실패하면 표만 뺀다");
        org.mockito.Mockito.verify(results, org.mockito.Mockito.never()).forIds(org.mockito.ArgumentMatchers.any());
        assertEquals("", page.resultCard("apt/H1/N1", null));
    }
    @Test void shortDateShowsYearOnlyOutsideTheCurrentYear() {
        var today = LocalDate.of(2026, 11, 18);
        assertEquals("12.08", PublicHtml.shortDate(LocalDate.of(2026, 12, 8), today));
        assertEquals("2025.12.08", PublicHtml.shortDate(LocalDate.of(2025, 12, 8), today));
        assertEquals("2027.01.05", PublicHtml.shortDate(LocalDate.of(2027, 1, 5), today));
        assertEquals("미제공", PublicHtml.shortDate(null, today));
    }
    @Test void structuredDataEscapesScriptEndAndListsBreadcrumbPositions() {
        var crumbs = List.of(new PublicHtml.Crumb("TOADY", "https://www.toady.co.kr/"), new PublicHtml.Crumb("</script>단지", null));
        String ld = PublicHtml.breadcrumbLd(crumbs, "https://www.toady.co.kr/announcements/apt/A/B");
        assertTrue(ld.startsWith("<script type='application/ld+json'>") && ld.contains("\"BreadcrumbList\"") && ld.contains("\"position\":2"));
        assertFalse(ld.substring(ld.indexOf('>') + 1, ld.length() - "</script>".length()).contains("<"), "본문의 <는 이스케이프한다");
        assertTrue(ld.contains("\\u003c/script\\u003e단지"));
        assertTrue(ld.contains("https://www.toady.co.kr/announcements/apt/A/B"), "마지막 항목은 현재 주소");
        String nav = PublicHtml.breadcrumb(crumbs);
        assertTrue(nav.contains("<a href='https://www.toady.co.kr/'>TOADY</a>") && nav.contains("<span>&lt;/script&gt;단지</span>"));
        assertTrue(PublicHtml.itemListLd("목록", List.of(new PublicHtml.Crumb("a", "https://x/1"))).contains("\"ItemList\""));
        assertEquals(1, PublicRegionPages.page(null)); assertEquals(3, PublicRegionPages.page("3"));
        assertEquals(0, PublicRegionPages.page("0")); assertEquals(0, PublicRegionPages.page("abc")); assertEquals(0, PublicRegionPages.page("10000"));
    }
    @Test void alwaysOpenEndIsShownAsAlwaysOpenInsteadOfAFakeDate() {
        var today = LocalDate.of(2026, 11, 20);
        LocalDate start = LocalDate.of(2026, 11, 12), never = LocalDate.of(9999, 12, 31);
        // 공통 문구: 상시 접수 표기 마감일은 '상시 접수', 보통 마감일은 그대로, collapse면 같은 날·없는 마감일은 시작일만.
        assertEquals("11.12~상시 접수", PublicHtml.receiptPeriod(start, never, "~", false, day -> PublicHtml.shortDate(day, today)));
        assertEquals("11.12~11.14", PublicHtml.receiptPeriod(start, LocalDate.of(2026, 11, 14), "~", false, day -> PublicHtml.shortDate(day, today)));
        assertEquals("2026-11-12", PublicHtml.receiptPeriod(start, start, " ~ ", true, PublicHtml::date));
        assertEquals("2026-11-12", PublicHtml.receiptPeriod(start, null, " ~ ", true, PublicHtml::date));
        assertEquals("2026-11-12 ~ 미제공", PublicHtml.receiptPeriod(start, null, " ~ ", false, PublicHtml::date));
        assertEquals("2026-11-12 ~ 상시 접수", PublicHtml.receiptPeriod(start, LocalDate.of(2100, 1, 1), " ~ ", true, PublicHtml::date));

        // 공고 페이지 설명과 같은 RSS 설명, 공유 이미지 문구
        var summary = new PlannerSummary.Summary("public-rental/H1/N1", "상시 임대 단지", "경기도 파주시 동패동 1", "100", "임대", start, never, null, "",
            List.of(), false, null, null);
        String feed = AnnouncementFeed.description(summary);
        assertTrue(feed.contains("~상시 접수") && !feed.contains("9999"), feed);
        assertEquals("2026-11-12 ~ 상시 접수", AnnouncementShareCard.receipt(summary));

        // 지역별 청약 목록 한 줄
        var entry = new PublicAnnouncementIndex.Entry("public-rental", "H1", "N1", "상시 임대 단지", "임대", "경기도 파주시 동패동 1", start, never, null, null, null, null);
        String region = PublicRegionPages.item("https://www.toady.co.kr", entry, today);
        assertTrue(region.contains("접수 11.12 ~ 상시 접수") && !region.contains("9999"), region);

        // 생활권·줍줍·이번 주 청약 페이지가 함께 쓰는 공고 목록 카드
        var source = new com.toady.web.app.announcement.dto.AnnouncementListItem("public-rental", "H1", "N1", "상시 임대 단지", "01", "임대", "410", "경기",
            "경기도 파주시 동패동 1", "", start.toString(), never.toString(), null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, "100", 0);
        String area = new PublicAreaPage(null, "https://www.toady.co.kr", null)
            .section("접수 중", List.of(new PublicAreaPage.Item(source, start, never, PublicAreaPage.Status.OPEN)), "");
        assertTrue(area.contains("접수 2026-11-12 ~ 상시 접수") && !area.contains("9999"), area);
    }
    @Test void areaCalendarDropsAlwaysOpenAnnouncementsAfterTheirReceiptCap() {
        var today = LocalDate.of(2026, 11, 20);
        LocalDate never = LocalDate.of(9999, 12, 31);
        // 시작 후 400일이 지나 끝난 상시 접수 공고: 원래 마감일(9999)로는 '최근 마감'이지만 인정 마지막 날(시작+400일)이 2주보다 오래전이라 뺀다.
        LocalDate oldStart = today.minusDays(com.toady.web.app.announcement.ReceiptWindow.MAX_OPEN_DAYS + 30);
        assertFalse(AreaCalendar.recent(new PublicAreaPage.Item(null, oldStart, never, PublicAreaPage.Status.CLOSED), today));
        // 400일 상한이 끝난 지 2주 안이면 다른 마감 공고처럼 남긴다.
        LocalDate recentStart = today.minusDays(com.toady.web.app.announcement.ReceiptWindow.MAX_OPEN_DAYS + 10);
        assertTrue(AreaCalendar.recent(new PublicAreaPage.Item(null, recentStart, never, PublicAreaPage.Status.CLOSED), today));
        // 보통 마감 공고는 마감 2주 이내만, 접수 중·예정은 항상 넣는다.
        assertTrue(AreaCalendar.recent(new PublicAreaPage.Item(null, today.minusDays(20), today.minusDays(14), PublicAreaPage.Status.CLOSED), today));
        assertFalse(AreaCalendar.recent(new PublicAreaPage.Item(null, today.minusDays(20), today.minusDays(15), PublicAreaPage.Status.CLOSED), today));
        assertTrue(AreaCalendar.recent(new PublicAreaPage.Item(null, today.minusDays(3), never, PublicAreaPage.Status.OPEN), today));
    }
}
