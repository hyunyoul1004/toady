package com.toady.web.app.planner;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** RFC 5545 구독용 캘린더. 하루 종일 일정에 전날 오전 9시 알림을 붙여 메일 없이도 알림을 받게 한다. */
final class CalendarIcs {
    private CalendarIcs() { }
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    static String build(List<PlannerSummary.Summary> summaries, String domain, Instant now) {
        StringBuilder out = new StringBuilder();
        line(out, "BEGIN:VCALENDAR"); line(out, "VERSION:2.0"); line(out, "PRODID:-//TOADY//My Schedule Feed//KO");
        line(out, "CALSCALE:GREGORIAN"); line(out, "METHOD:PUBLISH");
        line(out, "X-WR-CALNAME:TOADY 나의 청약"); line(out, "X-WR-TIMEZONE:Asia/Seoul");
        line(out, "X-WR-CALDESC:TOADY에 저장한 관심 공고의 청약 일정");
        // 캘린더 앱마다 갱신 주기 힌트를 읽는 키가 달라 두 가지를 함께 둔다.
        line(out, "REFRESH-INTERVAL;VALUE=DURATION:PT6H"); line(out, "X-PUBLISHED-TTL:PT6H");
        String stamp = STAMP.format(now);
        for (var summary : summaries) {
            if (summary.unavailable()) continue;
            for (var event : summary.events()) {
                LocalDate date = event.date();
                if (date == null) continue;
                String url = domain + "/announcements/" + summary.id();
                line(out, "BEGIN:VEVENT");
                // 같은 공고·날짜는 라벨 정규화가 바뀌어도 같은 일정으로 갱신되게 UID에서 라벨을 뺀다.
                line(out, "UID:" + summary.id().replace('/', '-') + "-" + DAY.format(date) + "@toady.co.kr");
                line(out, "DTSTAMP:" + stamp);
                line(out, "DTSTART;VALUE=DATE:" + DAY.format(date));
                line(out, "DTEND;VALUE=DATE:" + DAY.format(date.plusDays(1)));
                line(out, "SUMMARY:" + text(summary.houseNm() + " · " + event.label()));
                if (summary.address() != null && !summary.address().isBlank()) line(out, "LOCATION:" + text(summary.address()));
                line(out, "URL:" + url);
                line(out, "DESCRIPTION:" + text("일정은 공식 모집공고를 함께 확인하세요.\n" + url));
                line(out, "TRANSP:TRANSPARENT");
                line(out, "BEGIN:VALARM"); line(out, "ACTION:DISPLAY"); line(out, "TRIGGER:-PT15H");
                line(out, "DESCRIPTION:" + text("내일 " + summary.houseNm() + " " + event.label())); line(out, "END:VALARM");
                line(out, "END:VEVENT");
            }
        }
        line(out, "END:VCALENDAR");
        return out.toString();
    }
    static String text(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\r", "").replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,");
    }
    /** 75바이트마다 줄을 접되 UTF-8 문자를 가르지 않는다. */
    private static void line(StringBuilder out, String value) {
        int width = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            int bytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (width + bytes > 75) { out.append("\r\n "); width = 1; }
            out.append(ch); width += bytes; i += Character.charCount(codePoint);
        }
        out.append("\r\n");
    }
}
