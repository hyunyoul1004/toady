package com.toady.web.app.planner;

import com.toady.web.app.announcement.service.AnnouncementSearchService;
import com.toady.web.app.operation.AnnouncementOverrides;
import com.toady.web.app.planner.research.AddressLocator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 리뷰 후속 규칙: 결과 알림 대상 기간, 운영자 확인 주소의 위치 판정(시세 비교·분양 지도), 경쟁률 투표 정답 확정 시점,
 * 숨긴 공고의 결과·시세·투표 API 제외, 시세 비교 면적 고르기.
 */
class ConfirmedLocationAndResultRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    // ---- 1. 결과 알림 대상 기간 ----
    @Test void resultWindowIncludesAnnouncementsWhoseFirstRankEndedBeforeTheClosingDay() {
        AnnouncementSearchService search = mock(AnnouncementSearchService.class);
        when(search.searchAll(any(), eq(List.of("apt")))).thenReturn(new PageImpl<>(List.of(
            SaleMapServiceTest.source("A", "접수 중(1순위 끝남)", "경기도 의정부시 의정부동 1", "20261116", "20261118"),
            SaleMapServiceTest.source("B", "마감 10일", "경기도 의정부시 의정부동 2", "20261105", "20261107"),
            SaleMapServiceTest.source("C", "마감 40일", "경기도 의정부시 의정부동 3", "20261005", "20261008"),
            SaleMapServiceTest.source("D", "접수 전", "경기도 의정부시 의정부동 4", "20261120", "20261123"))));
        var page = new PublicAreaPage(search, "https://www.toady.co.kr", null);
        assertEquals(List.of("apt/A/A", "apt/B/B"), page.aptResultWindow(TODAY, 30));
    }

    // ---- 2. 운영자 확인 주소 ----
    static final String ROW = "{\"address_type\":\"REGION_ADDR\",\"x\":\"127.04\",\"y\":\"37.73\",\"address_name\":\"%1$s\",\"address\":{\"address_name\":\"%1$s\",\"b_code\":\"%2$s\",\"region_3depth_name\":\"%3$s\"}}";
    /** 조회어(지번)마다 정해 둔 결과를 돌려주는 카카오 흉내. 모르는 조회어는 빈 결과. */
    static AddressLocator locator(Map<String, String> rows, List<String> calls) {
        return new AddressLocator((uri, headers) -> {
            String query = java.net.URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8).replaceFirst("^size=10&query=", "");
            calls.add(query);
            String row = rows.get(query);
            return ("{\"documents\":[" + (row == null ? "" : row) + "]}").getBytes(StandardCharsets.UTF_8);
        }, "test-key");
    }

    @Test void operatorAddressIsVerifiedFirstAndFallsBackToTheSupplyAddress() throws Exception {
        String block = "경기도 의정부시 의정부동 일원 의정부역세권 A1블록";
        String operator = "경기도 의정부시 의정부동 100-1";
        Map<String, String> rows = Map.of(
            operator, ROW.formatted(operator, "4115010100", "의정부동"),
            "경기도 양주시 옥정동 1", ROW.formatted("경기도 양주시 옥정동 1", "4163010100", "옥정동"),
            "경기도 의정부시 의정부동 7", ROW.formatted("경기도 의정부시 의정부동 7", "4115010100", "의정부동"));
        List<String> calls = new ArrayList<>();
        // 지구·블록 주소만 있으면 미확인이지만, 운영자 확인 주소가 지번까지 맞으면 그 위치로 확정한다.
        assertTrue(locator(rows, calls).locate(block).isEmpty());
        var found = locator(rows, calls).locate(block, operator).orElseThrow();
        assertEquals("4115010100", found.bcode());
        assertEquals(operator, found.address());
        // 운영자 주소가 공급 주소와 다른 시·군이면 쓰지 않는다(잘못 넣은 주소로 엉뚱한 곳에 확정하지 않게).
        assertTrue(locator(rows, calls).locate(block, "경기도 양주시 옥정동 1").isEmpty());
        // 운영자 주소를 확인하지 못하면 공급 주소 지번으로 이어진다.
        var supply = locator(rows, calls).locate("경기도 의정부시 의정부동 7", "경기도 의정부시 의정부동 999").orElseThrow();
        assertEquals("경기도 의정부시 의정부동 7", supply.address());
    }

    static AnnouncementOverrides overrides(Map<String, AnnouncementOverrides.Override> values) {
        AnnouncementOverrides overrides = mock(AnnouncementOverrides.class);
        when(overrides.get(anyString(), anyString(), anyString())).thenAnswer(call -> values.get(call.getArgument(0) + "/" + call.getArgument(1) + "/" + call.getArgument(2)));
        when(overrides.hidden(anyString(), anyString(), anyString())).thenAnswer(call -> {
            var value = values.get(call.getArgument(0) + "/" + call.getArgument(1) + "/" + call.getArgument(2));
            return value != null && value.hidden();
        });
        return overrides;
    }

    @Test void confirmedAddressesUseThePublicIdOfTheAnnouncement() {
        var confirmed = new ConfirmedAddresses(overrides(Map.of("PUBLIC_RENTAL/H1/N1", new AnnouncementOverrides.Override(false, null, "서울특별시 강동구 고덕동 1"))));
        assertEquals("서울특별시 강동구 고덕동 1", confirmed.of("public-rental/H1/N1"));
        assertNull(confirmed.of("apt/H1/N1"));
        assertNull(confirmed.of("broken"));
        assertNull(ConfirmedAddresses.NONE.of("apt/H1/N1"));
    }

    @Test void saleMapPlacesBlockAddressAtTheOperatorConfirmedParcel() throws Exception {
        EligibilityFeed feed = mock(EligibilityFeed.class);
        AnnouncementSearchService search = mock(AnnouncementSearchService.class);
        AddressLocator locator = mock(AddressLocator.class);
        when(locator.enabled()).thenReturn(true);
        String block = "경기도 파주시 파주운정3지구 A99블록", operator = "경기도 파주시 동패동 1673";
        when(feed.cached(SaleMapServiceTest.TODAY)).thenReturn(Map.of("items", List.of(SaleMapServiceTest.item("apt/7/7", "블록 단지", block, "OPEN", "2029-01"))));
        when(search.searchAll(any(), anyList())).thenReturn(new PageImpl<>(List.of()));
        when(locator.locate(block, operator)).thenReturn(Optional.of(new AddressLocator.Location("4148011700", "동패동", operator, 37.71, 126.72)));
        SaleMapService service = new SaleMapService(feed, search, locator, Clock.fixed(SaleMapServiceTest.TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
        service.useConfirmed(new ConfirmedAddresses(overrides(Map.of("APT/7/7", new AnnouncementOverrides.Override(false, null, operator)))));
        var result = service.sales("4148011700", "경기 파주시");
        assertEquals(List.of("블록 단지"), result.sales().stream().map(SaleMapService.Sale::houseNm).toList(), "운영자 확인 주소로 지도에 놓는다");
        assertTrue(result.unlocated().isEmpty());
        verify(locator, never()).locate(block, null);
        service.close();
    }

    // ---- 3. 경쟁률 투표 정답 확정 ----
    static PlannerSummary.Summary poll(LocalDate start, LocalDate end, List<PlannerSummary.Event> events) {
        return new PlannerSummary.Summary("apt/V/1", "투표 단지", "", "", "", start, end, null, "", ScheduleEvents.normalize(events), false, null, null);
    }

    @Test void pollAnswerWaitsUntilAllFirstRankRegionsAreIn() {
        // 1순위 해당지역 11/17, 기타지역 11/18(마감 11/18).
        var split = poll(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), List.of(
            new PlannerSummary.Event("1순위 해당지역", LocalDate.of(2026, 11, 17)), new PlannerSummary.Event("1순위 기타지역", LocalDate.of(2026, 11, 18))));
        Collection<Set<String>> onlyLocal = List.of(Set.of("01"), Set.of("01"));
        assertFalse(RatePoll.rankOneSettled(split, onlyLocal, LocalDate.of(2026, 11, 17)), "해당지역 결과만으로는 확정하지 않는다");
        assertFalse(RatePoll.rankOneSettled(split, onlyLocal, LocalDate.of(2026, 11, 18)), "기타지역 접수일 당일도 아직");
        assertTrue(RatePoll.rankOneSettled(split, List.of(Set.of("01", "03")), LocalDate.of(2026, 11, 18)), "기타지역 행까지 들어오면 확정");
        assertTrue(RatePoll.rankOneSettled(split, onlyLocal, LocalDate.of(2026, 11, 19)), "마지막 1순위 접수일 다음 날 이후 확정");
        // 같은 날 해당·경기·기타지역 접수(경기 공고): 세 지역 행이 모두 있어야 당일 확정.
        var sameDay = poll(LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 18), List.of(
            new PlannerSummary.Event("1순위 해당지역", TODAY), new PlannerSummary.Event("1순위 경기지역", TODAY), new PlannerSummary.Event("1순위 기타지역", TODAY)));
        assertFalse(RatePoll.rankOneSettled(sameDay, List.of(Set.of("01", "02")), TODAY));
        assertTrue(RatePoll.rankOneSettled(sameDay, List.of(Set.of("01", "02", "03")), TODAY));
        // 지역별 접수일이 없는 옛 자료는 들어온 결과를 그대로 확정한다.
        assertTrue(RatePoll.rankOneSettled(poll(TODAY, TODAY.plusDays(2), List.of()), List.of(Set.of("01")), TODAY));
    }

    // ---- 4. 숨긴 공고 제외 ----
    static HiddenAnnouncements hidden() {
        return new HiddenAnnouncements(overrides(Map.of("APT/H/1", new AnnouncementOverrides.Override(true, null))), event -> { });
    }

    @Test void hiddenAnnouncementsAreLeftOutOfResultsGapsAndPolls() {
        // 결과: 숨긴 공고만 물으면 DB를 읽지 않고 빈 목록(jdbc가 null이라 읽으면 실패한다).
        var results = new AnnouncementResults(null);
        results.useHidden(hidden());
        assertEquals(List.of(), results.results(new AnnouncementResults.Request(List.of("apt/H/1"))));

        // 시세 비교: 숨긴 공고는 저장소에 묻지 않는다.
        PriceGapStore store = mock(PriceGapStore.class);
        when(store.forIds(anyCollection())).thenReturn(Map.of());
        var gaps = new PriceGapSnapshots(null, store, null, null, null);
        gaps.useHidden(hidden());
        assertEquals(List.of(), gaps.lookup(new PriceGapSnapshots.Request(List.of("apt/H/1"))));
        verify(store, never()).forIds(anyCollection());
        gaps.lookup(new PriceGapSnapshots.Request(List.of("apt/H/1", "apt/V/1")));
        verify(store).forIds(List.of("apt/V/1"));

        // 투표: 없는 공고처럼 404(요약을 읽지 않는다).
        PlannerSummary summaries = mock(PlannerSummary.class);
        var poll = new RatePoll(null, summaries, Clock.systemUTC());
        poll.useHidden(hidden());
        assertEquals(404, poll.get("H", "1", null).getStatusCode().value());
        verify(summaries, never()).get(anyString(), anyString(), anyString());
    }

    // ---- 5. 시세 비교 면적 ----
    @Test void comparisonAreasKeepEightyFourWhenThereAreManySmallTypes() {
        List<Integer> many = List.of(21, 26, 29, 33, 36, 39, 44, 46, 49, 51, 55, 59, 74, 84, 101, 114);
        List<Integer> picked = PriceGapSnapshots.comparisonAreas(many, PriceGapSnapshots.MAX_AREAS);
        assertEquals(PriceGapSnapshots.MAX_AREAS, picked.size());
        assertTrue(picked.containsAll(List.of(59, 84, 114)), "면적대 대표(59·84·114)는 빠지지 않는다: " + picked);
        assertEquals(picked.stream().sorted().toList(), picked, "면적 오름차순");
        assertEquals(List.of(59, 84), PriceGapSnapshots.comparisonAreas(List.of(84, 59), 8), "상한보다 적으면 전부");
    }
}
