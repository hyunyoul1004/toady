package com.toady.web.app.planner;

import java.time.LocalDate;
import java.util.*;

/**
 * 공고 일정(Event) 목록을 날짜별로 묶어 정리하는 도우미. PlannerSummary.get()이 마지막에 부른다.
 * 화면·메일·공유 페이지에서 같은 날짜의 순위/지역 일정을 반복하지 않는다. 원본 접수 기간은 요약에 유지한다.
 * 예: 같은 날 '1순위 해당지역'·'1순위 기타지역' → '1순위 (해당·기타지역)' 한 줄로 합친다.
 */
final class ScheduleEvents {
    private ScheduleEvents() { }
    /**
     * 날짜 순(TreeMap)으로 라벨을 모은 뒤, 같은 날 특별공급·순위 일정이 있으면 겹치는 '접수 시작/마감'은 뺀다.
     * 결과는 날짜당 Event 하나이고, 라벨은 ' · '로 이어 붙인다.
     */
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
