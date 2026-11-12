package com.toady.web.app.planner;

import java.time.LocalDate;
import java.util.*;

/** 화면·메일·공유 페이지에서 같은 날짜의 순위/지역 일정을 반복하지 않는다. 원본 접수 기간은 요약에 유지한다. */
final class ScheduleEvents {
    private ScheduleEvents() { }
    static List<PlannerSummary.Event> normalize(List<PlannerSummary.Event> events) {
        Map<LocalDate, Set<String>> days = new TreeMap<>();
        for (var event : events) {
            if (event.date() != null && event.label() != null && !event.label().isBlank())
                days.computeIfAbsent(event.date(), key -> new LinkedHashSet<>()).add(event.label().trim());
        }
        List<PlannerSummary.Event> result = new ArrayList<>();
        days.forEach((date, values) -> {
            List<String> labels = new ArrayList<>(values);
            if (labels.stream().anyMatch(label -> label.matches("^(특별공급|[12]순위).*")))
                labels.removeIf(label -> label.equals("접수 시작") || label.equals("접수 마감"));
            Set<String> merged = new LinkedHashSet<>();
            labels.stream().filter(label -> !label.matches("^[12]순위 (해당지역|경기지역|기타지역)$")).forEach(merged::add);
            for (String rank : List.of("1", "2")) {
                List<String> areas = List.of("해당", "경기", "기타").stream()
                        .filter(area -> labels.contains(rank + "순위 " + area + "지역")).toList();
                if (!areas.isEmpty()) merged.add(rank + "순위 (" + String.join("·", areas) + "지역)");
            }
            result.add(new PlannerSummary.Event(String.join(" · ", merged), date));
        });
        return List.copyOf(result);
    }
}
