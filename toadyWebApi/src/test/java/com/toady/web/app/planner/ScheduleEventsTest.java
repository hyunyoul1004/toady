package com.toady.web.app.planner;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleEventsTest {
    private static PlannerSummary.Event event(String label,String date) {return new PlannerSummary.Event(label,LocalDate.parse(date));}
    @Test void sameDateAndRegionEventsAreMergedWithoutLosingOtherDates() {
        var events=List.of(event("접수 시작","2026-11-16"),event("특별공급 시작","2026-11-16"),
            event("1순위 해당지역","2026-11-17"),event("1순위 기타지역","2026-11-17"),event("1순위 기타지역","2026-11-17"),
            event("접수 마감","2026-11-18"),event("2순위 해당지역","2026-11-18"),event("2순위 기타지역","2026-11-18"),
            event("당첨자 발표","2026-11-24"),event("계약 시작","2027-01-25"),event("계약 마감","2027-01-29"));
        var result=ScheduleEvents.normalize(events);
        assertEquals(6,result.size());
        assertEquals("특별공급 시작",result.get(0).label());
        assertEquals("1순위 (해당·기타지역)",result.get(1).label());
        assertEquals("2순위 (해당·기타지역)",result.get(2).label());
        assertEquals("계약 마감",result.get(5).label());
        assertEquals(result,ScheduleEvents.normalize(result));
    }
    @Test void genericDatesRemainWhenDetailedScheduleIsNotAvailable() {
        var result=ScheduleEvents.normalize(List.of(event("접수 시작","2026-11-16"),event("접수 마감","2026-11-18")));
        assertEquals(2,result.size());assertEquals("접수 시작",result.get(0).label());
    }
}
