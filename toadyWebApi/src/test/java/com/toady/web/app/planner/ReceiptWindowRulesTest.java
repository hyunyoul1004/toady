package com.toady.web.app.planner;

import com.toady.web.app.announcement.ApplyDays;
import com.toady.web.app.announcement.ReceiptWindow;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * '접수 중'·'접수 예정' 범위(ReceiptWindow)가 앱 지역별 청약(active-announcements)·생활권·줍줍 페이지(classify)와
 * /regions(PublicAnnouncementIndex.Entry)에서 같게 적용되는지 확인한다.
 */
class ReceiptWindowRulesTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    static AnnouncementListItem item(String id, LocalDate start, LocalDate end) {
        return new AnnouncementListItem("public-rental", id, id, id, "01", "임대", "100", "서울", "서울특별시 1", "",
            start.toString(), end == null ? null : end.toString(), null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, "10", 0);
    }
    static PublicAnnouncementIndex.Entry entry(LocalDate start, LocalDate end) {
        return new PublicAnnouncementIndex.Entry("public-rental", "H", "N", "단지", "임대", "서울특별시 1", start, end, null, null, null, null);
    }

    @Test void receiptWindowUsesTheSamePeriodCapAsApplyDays() {
        assertEquals(ApplyDays.MAX_PERIOD_DAYS, ReceiptWindow.MAX_OPEN_DAYS);
        // 시작 300일 전, 마감 다음 달: 접수 중(예전 active-announcements는 120일까지만 봐서 빠졌다).
        assertTrue(ReceiptWindow.open(TODAY.minusDays(300), TODAY.plusDays(30), TODAY));
        // 시작 401일 전, 마감이 99991231처럼 먼 공고: 400일까지만 인정해 접수 중으로 보지 않는다.
        assertFalse(ReceiptWindow.open(TODAY.minusDays(401), LocalDate.of(9999, 12, 31), TODAY));
        assertTrue(ReceiptWindow.open(TODAY.minusDays(400), LocalDate.of(9999, 12, 31), TODAY));
        // 접수 예정은 180일 안에 시작하는 공고까지.
        assertTrue(ReceiptWindow.upcoming(TODAY.plusDays(ReceiptWindow.UPCOMING_DAYS), TODAY));
        assertFalse(ReceiptWindow.upcoming(TODAY.plusDays(ReceiptWindow.UPCOMING_DAYS + 1), TODAY));
    }

    @Test void publicListsAndRegionPagesClassifyTheSameWay() {
        List<AnnouncementListItem> sources = List.of(
            item("LONG", TODAY.minusDays(300), TODAY.plusDays(30)),
            item("BROKEN", TODAY.minusDays(401), LocalDate.of(9999, 12, 31)),
            item("SOON", TODAY.plusDays(100), TODAY.plusDays(101)),
            item("FAR", TODAY.plusDays(200), TODAY.plusDays(201)));
        var groups = PublicAreaPage.classify(sources, TODAY, Integer.MAX_VALUE);
        assertEquals(List.of("LONG"), groups.open().stream().map(i -> i.source().houseManageNo()).toList());
        assertEquals(List.of("SOON"), groups.upcoming().stream().map(i -> i.source().houseManageNo()).toList(), "180일보다 먼 예정 공고는 넣지 않는다");
        assertEquals(List.of("BROKEN"), groups.closed().stream().map(i -> i.source().houseManageNo()).toList());

        // /regions(PublicAnnouncementIndex)도 같은 판정이다.
        assertTrue(entry(TODAY.minusDays(300), TODAY.plusDays(30)).active(TODAY));
        assertEquals(PublicAnnouncementIndex.Status.OPEN, entry(TODAY.minusDays(300), TODAY.plusDays(30)).status(TODAY));
        assertFalse(entry(TODAY.minusDays(401), LocalDate.of(9999, 12, 31)).active(TODAY));
        assertEquals(PublicAnnouncementIndex.Status.CLOSED, entry(TODAY.minusDays(401), LocalDate.of(9999, 12, 31)).status(TODAY));
        assertTrue(entry(TODAY.plusDays(100), TODAY.plusDays(101)).active(TODAY));
        assertFalse(entry(TODAY.plusDays(200), TODAY.plusDays(201)).active(TODAY), "/regions 건수도 180일 안에 시작하는 예정 공고까지만 센다");
        // 지역 목록·건수·사이트맵(listed)은 접수 중·예정 또는 마감만. 180일보다 먼 예정 공고는 어디에도 세지 않는다.
        assertFalse(entry(TODAY.plusDays(200), TODAY.plusDays(201)).listed(TODAY));
        assertTrue(entry(TODAY.minusDays(20), TODAY.minusDays(18)).listed(TODAY));
        assertTrue(entry(TODAY.plusDays(100), TODAY.plusDays(101)).listed(TODAY));
    }
}
