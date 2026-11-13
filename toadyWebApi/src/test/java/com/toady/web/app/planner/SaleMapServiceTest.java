package com.toady.web.app.planner;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import com.toady.web.app.planner.research.AddressLocator;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SaleMapServiceTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    static AnnouncementListItem source(String no, String name, String address, String start, String end) {
        return new AnnouncementListItem("apt", no, no, name, "01", "APT", "410", "경기", address, "", start, end,
            null, null, null, null, null, null, null, null, null, null, null, null, "500", 0);
    }
    static EligibilityFeed.Item item(String id, String name, String address, String status, String moveIn) {
        return new EligibilityFeed.Item(id, "apt", name, address, "경기", status, "2026-11-02", "2026-11-04", null, null, null, null, null,
            null, null, moveIn, 500, false, false, false, false, EligibilityFeed.Special.NONE,
            List.of(new EligibilityFeed.Unit("084.9800A", new java.math.BigDecimal("84.98"), 100, 50, 79000L)), null);
    }

    @Test void regionNamesNarrowAnnouncementsBeforeLocating() {
        assertEquals(List.of("파주시"), SaleMapService.municipalityNames("경기 파주시"));
        assertEquals(List.of("수원시", "영통구"), SaleMapService.municipalityNames("경기 수원시 영통구"));
        assertEquals(List.of("제주시"), SaleMapService.municipalityNames("제주특별자치도 제주시"));
        assertEquals(List.of("세종"), SaleMapService.municipalityNames("세종특별자치시"));
        assertEquals(List.of(), SaleMapService.municipalityNames(null));
        assertTrue(SaleMapService.inRegion("경기도 수원시 영통구 이의동 1", List.of("수원시", "영통구")));
        assertFalse(SaleMapService.inRegion("경기도 수원시 권선구 1", List.of("수원시", "영통구")), "시·군·구 이름이 모두 있어야 한다");
    }

    @Test void pastCandidatesKeepFinishedAnnouncementsInTheDistrictOnly() {
        List<AnnouncementListItem> sources = List.of(
            source("2026000517", "3단지", "경기도 파주시 파주운정3지구 3블록(경기도 파주시 동패동 1673번지 일원)", "20261102", "20261104"),
            source("2026000600", "접수 중", "경기도 파주시 동패동 10", "20261113", "20261116"),
            source("2026000700", "고양 단지", "경기도 고양시 덕양구 1", "20260301", "20260303"),
            // 이름만 '파주'인 다른 지역 공고는 주소가 맞지 않아 뺀다.
            source("2026000800", "파주시티 고양", "경기도 고양시 일산동구 2", "20260301", "20260303"),
            source("2026000900", "접수일 없음", "경기도 파주시 목동동 3", null, null));
        List<AnnouncementListItem> kept = SaleMapService.pastCandidates(sources, List.of("파주시"), TODAY);
        assertEquals(List.of("3단지"), kept.stream().map(AnnouncementListItem::houseNm).toList());
    }

    @Test void moveInDecidesWhetherAFinishedSaleIsStillPreMoveIn() {
        YearMonth now = YearMonth.of(2026, 11);
        assertTrue(SaleMapService.beforeMoveIn("2029-03", now));
        assertTrue(SaleMapService.beforeMoveIn("2026-11", now), "이번 달 입주 예정은 아직 둔다");
        assertFalse(SaleMapService.beforeMoveIn("2026-10", now), "입주가 지난 단지는 매매 실거래로 본다");
        assertTrue(SaleMapService.beforeMoveIn(null, now), "입주 예정월 미제공");
    }

    @Test void mapShowsOpenAndFinishedSalesAndListsBlockOnlyAddressesSeparately() throws Exception {
        EligibilityFeed feed = mock(EligibilityFeed.class);
        AnnouncementSearchService search = mock(AnnouncementSearchService.class);
        AddressLocator locator = mock(AddressLocator.class);
        when(locator.enabled()).thenReturn(true);
        String open = "경기도 파주시 동패동 10", done = "경기도 파주시 파주운정3지구 3블록(경기도 파주시 동패동 1673번지 일원)",
            block = "경기도 파주시 파주운정3지구 A99블록", moved = "경기도 파주시 목동동 5";
        when(feed.load(TODAY)).thenReturn(Map.of("items", List.of(item("apt/1/1", "청약 중 단지", open, "OPEN", "2029-01"),
            item("apt/9/9", "다른 시 단지", "경기도 고양시 덕양구 1", "OPEN", "2029-01"))));
        when(search.searchAll(any(), eq(List.of("apt")))).thenReturn(new PageImpl<>(List.of(
            source("2", "3단지", done, "20261102", "20261104"), source("3", "블록 단지", block, "20260601", "20260603"),
            source("4", "입주 끝난 단지", moved, "20240101", "20240103"), source("1", "청약 중 단지", open, "20261113", "20261116"))));
        when(feed.items(anyList(), anyMap())).thenAnswer(call -> {
            List<AnnouncementListItem> sources = call.getArgument(0);
            Map<String, String> status = call.getArgument(1);
            return sources.stream().map(s -> item("apt/" + s.houseManageNo() + "/" + s.pblancNo(), s.houseNm(), s.hssplyAdres(),
                status.get("apt/" + s.houseManageNo() + "/" + s.pblancNo()), s.houseNm().contains("입주 끝난") ? "2026-02" : "2029-03")).toList();
        });
        when(locator.locate(open)).thenReturn(Optional.of(new AddressLocator.Location("4148011700", "동패동", open, 37.72, 126.73)));
        when(locator.locate(done)).thenReturn(Optional.of(new AddressLocator.Location("4148011700", "동패동", done, 37.717, 126.725)));
        when(locator.locate(block)).thenReturn(Optional.empty());

        SaleMapService service = new SaleMapService(feed, search, locator, Clock.fixed(TODAY.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
        SaleMapService.Result result = service.sales("4148011700", "경기 파주시");
        assertEquals(0, result.pending());
        assertEquals(List.of("청약 중 단지:OPEN", "3단지:DONE"), result.sales().stream().map(s -> s.houseNm() + ":" + s.status()).toList(),
            "접수 중 공고는 한 번만, 접수를 마친 공고는 분양 완료로, 입주가 지난 공고는 빼고");
        assertEquals("2029-03", result.sales().get(1).moveIn());
        assertEquals("4148011700", result.sales().get(1).bcode(), "상세 탭과 같은 주변 시세 비교를 부를 수 있게 법정동을 함께 준다");
        assertEquals("동패동", result.sales().get(1).dong());
        assertEquals(List.of("블록 단지"), result.unlocated().stream().map(SaleMapService.Unlocated::houseNm).toList(), "지구·블록 주소는 지도에 놓지 않고 따로 알린다");
        verify(locator, never()).locate(moved);
        service.close();
    }

    @Test void supplyAreaIsReadFromTheAnnouncementModel() {
        assertEquals(new java.math.BigDecimal("112.1593"), EligibilityFeed.decimal("112.1593 "));
        assertNull(EligibilityFeed.decimal("-"));
        assertNull(EligibilityFeed.decimal(null));
        assertNull(EligibilityFeed.decimal("0.0"), "10㎡ 미만은 공급면적으로 보지 않는다");
    }
}
