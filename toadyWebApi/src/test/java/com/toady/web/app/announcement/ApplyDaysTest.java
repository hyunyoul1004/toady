package com.toady.web.app.announcement;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 신청 가능일 규칙. 웹 tests/apply-days.test.mjs가 같은 예시(2026-11-19)로 같은 결과를 확인한다. */
class ApplyDaysTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 19);

    /** APT 한 건. rank1·rank2는 해당지역·경기·기타지역 접수일 순서. */
    static AnnouncementListItem apt(String id, String start, String end, String specialStart, String specialEnd, String[] rank1, String[] rank2) {
        return new AnnouncementListItem("apt", id, id, id, "01", "민영", "100", "서울", "서울특별시 1", "",
            start, end, specialStart, specialEnd,
            rank1[0], rank1[0], rank1[1], rank1[1], rank1[2], rank1[2],
            rank2[0], rank2[0], rank2[1], rank2[1], rank2[2], rank2[2], "100", 0);
    }
    static AnnouncementListItem other(String type, String id, String start, String end) {
        return new AnnouncementListItem(type, id, id, id, "04", "무순위", "100", "서울", "서울특별시 1", "",
            start, end, null, null, null, null, null, null, null, null, null, null, null, null, null, null, "10", 0);
    }
    static final String[] NONE = { null, null, null };

    /** 2026-11-19 실제 사례: 접수 기간 중인 공고는 5건이지만 그날 신청할 수 있는 공고는 4건이다. */
    static List<AnnouncementListItem> sample() {
        return List.of(
            apt("JUNGHWA", "2026-11-16", "2026-11-19", "2026-11-16", "2026-11-16",
                new String[] { "2026-11-17", "2026-11-18", "2026-11-18" }, new String[] { "2026-11-19", "2026-11-19", "2026-11-19" }),
            apt("SINGIL", "2026-11-16", "2026-11-24", null, null,
                new String[] { "2026-11-16", "2026-11-16", "2026-11-16" }, new String[] { "2026-11-24", "2026-11-24", "2026-11-24" }),
            other("remaining", "YAMOK", "2026-11-17", "2026-11-23"),
            other("remaining", "ESTERA", "2026-11-18", "2026-11-19"),
            other("optional", "RIATZ", "2026-11-18", "2026-11-20"));
    }

    @Test void countsOnlyAnnouncementsThatCanBeAppliedForToday() {
        var applicable = sample().stream().filter(item -> ApplyDays.on(item, TODAY)).map(AnnouncementListItem::houseManageNo).toList();
        assertEquals(List.of("JUNGHWA", "YAMOK", "ESTERA", "RIATZ"), applicable);
    }

    @Test void aptUsesSpecialAndRankDaysOnly() {
        var junghwa = sample().get(0);
        assertEquals(List.of("2026-11-16", "2026-11-17", "2026-11-18", "2026-11-19"), ApplyDays.strings(junghwa));
        var singil = sample().get(1);
        assertEquals(List.of("2026-11-16", "2026-11-24"), ApplyDays.strings(singil), "접수 기간 중이라도 순위 접수일이 아니면 신청일이 아니다");
        assertEquals(LocalDate.of(2026, 11, 24), ApplyDays.next(singil, TODAY));
        assertNull(ApplyDays.next(junghwa, TODAY.plusDays(1)));
    }

    @Test void aptWithoutSpecialColumnsTreatsEarlierStartAsSpecialDay() {
        var item = apt("A", "2026-11-09", "2026-11-12", null, null,
            new String[] { "2026-11-10", null, "2026-11-11" }, new String[] { "2026-11-12", null, null });
        assertEquals(List.of("2026-11-09", "2026-11-10", "2026-11-11", "2026-11-12"), ApplyDays.strings(item));
    }

    @Test void aptWithoutRankDaysUsesWholePeriod() {
        var item = apt("B", "20261117", "20261119", null, null, NONE, NONE);
        assertEquals(List.of("2026-11-17", "2026-11-18", "2026-11-19"), ApplyDays.strings(item));
    }

    @Test void otherTypesUseEveryDayOfThePeriodIncludingWeekends() {
        var item = other("public-rental", "C", "2026-11-20", "2026-11-23");
        assertEquals(List.of("2026-11-20", "2026-11-21", "2026-11-22", "2026-11-23"), ApplyDays.strings(item));
        assertEquals(List.of("2026-11-20"), ApplyDays.strings(other("urban", "D", "2026-11-20", null)), "종료일이 없으면 시작일 하루");
        assertEquals(List.of(), ApplyDays.strings(other("urban", "E", null, "2026-11-20")), "시작일이 없으면 신청일을 알 수 없다");
        assertEquals(List.of("2026-11-20"), ApplyDays.strings(other("urban", "F", "2026-11-20", "2026-11-01")), "종료일이 앞서면 시작일 하루");
    }
}
