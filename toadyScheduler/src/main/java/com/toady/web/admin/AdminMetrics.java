package com.toady.web.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * API가 쌓는 익명 조회·행동 로그를 주 단위(월~일, 한국시간)로 집계한다. 개인 식별 정보는 다루지 않는다.
 * 재방문은 최근 9주 안에서 이전 날에도 방문한 방문자로 계산하므로, 그 이전 방문만 있는 사람은 신규로 보인다.
 */
@Service
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminMetrics {
    static final int WEEKS = 8;
    private final JdbcTemplate jdbc;
    public AdminMetrics(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Week(LocalDate start, LocalDate end, long visitors, long sessions, long pageViews, long returning,
                       long activeExplorers, long detailViews, long favorites, long shares) {
        public long getReturningRate() { return visitors == 0 ? 0 : Math.round(returning * 100.0 / visitors); }
        public String getLabel() { return "%d.%02d ~ %d.%02d".formatted(start.getMonthValue(), start.getDayOfMonth(), end.getMonthValue(), end.getDayOfMonth()); }
    }
    public record Source(String label, String host, long sessions, long percent) { }
    public record Report(List<Week> weeks, List<Source> sources, long sourceSessions, String sourceNotice, String error) {
        public Week getCurrent() { return weeks.isEmpty() ? null : weeks.get(0); }
        public Week getPrevious() { return weeks.size() < 2 ? null : weeks.get(1); }
        public long getSearchPercent() {
            return sources.stream().filter(source -> source.label().startsWith("검색")).mapToLong(Source::sessions).sum() * 100 / Math.max(1, sourceSessions);
        }
    }

    static LocalDate weekStart(LocalDate day) { return day.with(DayOfWeek.MONDAY); }

    public Report report(LocalDate today) {
        LocalDate first = weekStart(today).minusWeeks(WEEKS - 1), windowStart = first.minusWeeks(1);
        try {
            Map<String, NavigableSet<LocalDate>> visitorDays = new HashMap<>();
            jdbc.query("SELECT DISTINCT VISITOR_ID,VISIT_DATE FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> visitorDays.computeIfAbsent(r.getString(1), key -> new TreeSet<>()).add(r.getDate(2).toLocalDate()),
                windowStart, today);
            Map<LocalDate, long[]> traffic = new HashMap<>();
            jdbc.query("SELECT VISIT_DATE,COUNT(*),COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ? GROUP BY VISIT_DATE",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> traffic.put(r.getDate(1).toLocalDate(), new long[]{r.getLong(2), r.getLong(3)}), first, today);
            Map<String, Map<String, Set<String>>> actions = new HashMap<>(); // 주 시작일 → 행동 → 방문자
            Map<String, Map<String, Set<String>>> detailSubjects = new HashMap<>(); // 주 시작일 → 방문자 → 본 공고
            Map<String, Map<String, Long>> actionCounts = new HashMap<>();
            jdbc.query("SELECT VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE FROM TB_USAGE_EVENT WHERE VISIT_DATE BETWEEN ? AND ? AND ACTION_NAME IN ('detail_view','favorite_add','share')",
                (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    String week = weekStart(r.getDate(4).toLocalDate()).toString(), visitor = r.getString(1), action = r.getString(2);
                    actions.computeIfAbsent(week, key -> new HashMap<>()).computeIfAbsent(action, key -> new HashSet<>()).add(visitor);
                    actionCounts.computeIfAbsent(week, key -> new HashMap<>()).merge(action, 1L, Long::sum);
                    if (action.equals("detail_view")) detailSubjects.computeIfAbsent(week, key -> new HashMap<>()).computeIfAbsent(visitor, key -> new HashSet<>()).add(r.getString(3));
                }, first, today);
            List<Week> weeks = new ArrayList<>();
            for (int i = 0; i < WEEKS; i++) {
                LocalDate start = weekStart(today).minusWeeks(i), end = start.plusDays(6);
                long views = 0, sessions = 0, visitors = 0, returning = 0;
                for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) { long[] row = traffic.get(day); if (row != null) { views += row[0]; sessions += row[1]; } }
                for (NavigableSet<LocalDate> days : visitorDays.values()) {
                    LocalDate last = days.floor(end);
                    if (last == null || last.isBefore(start)) continue;
                    visitors++;
                    // 이번 주 마지막 방문 이전에 다른 날 방문이 있으면 다시 온 방문자다.
                    if (days.lower(last) != null) returning++;
                }
                String key = start.toString();
                Map<String, Set<String>> weekActions = actions.getOrDefault(key, Map.of());
                Set<String> active = new HashSet<>(weekActions.getOrDefault("favorite_add", Set.of()));
                detailSubjects.getOrDefault(key, Map.of()).forEach((visitor, subjects) -> { if (subjects.size() >= 2) active.add(visitor); });
                Map<String, Long> counts = actionCounts.getOrDefault(key, Map.of());
                weeks.add(new Week(start, end, visitors, sessions, views, returning, active.size(),
                    counts.getOrDefault("detail_view", 0L), counts.getOrDefault("favorite_add", 0L), counts.getOrDefault("share", 0L)));
            }
            List<Source> sources = new ArrayList<>(); String notice = null; long total = 0;
            LocalDate sourceStart = today.minusDays(27);
            try {
                total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?", Long.class, sourceStart, today)).orElse(0L);
                Map<String, Long> byLabel = new LinkedHashMap<>(); Map<String, String> hosts = new HashMap<>();
                for (var row : jdbc.queryForList("SELECT REFERRER_HOST,COUNT(DISTINCT SESSION_ID) AS SESSIONS FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ? AND REFERRER_HOST IS NOT NULL GROUP BY REFERRER_HOST", sourceStart, today)) {
                    String host = String.valueOf(row.get("REFERRER_HOST")); String label = sourceLabel(host);
                    byLabel.merge(label, ((Number) row.get("SESSIONS")).longValue(), Long::sum);
                    hosts.merge(label, host, (a, b) -> a.length() <= b.length() ? a : b);
                }
                long known = byLabel.values().stream().mapToLong(Long::longValue).sum();
                byLabel.put("직접 방문·알 수 없음", Math.max(0, total - known));
                final long denominator = Math.max(1, total);
                byLabel.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(entry -> sources.add(new Source(entry.getKey(), hosts.getOrDefault(entry.getKey(), ""), entry.getValue(), Math.round(entry.getValue() * 100.0 / denominator))));
            } catch (BadSqlGrammarException missingColumn) {
                notice = "유입 경로 컬럼(REFERRER_HOST)이 아직 없습니다. database.sql 10번을 적용하면 이후 방문부터 집계됩니다.";
            }
            return new Report(weeks, sources, total, notice, null);
        } catch (DataAccessException failure) {
            return new Report(List.of(), List.of(), 0, null, "지표 테이블을 읽지 못했습니다. TB_PAGE_VIEW·TB_USAGE_EVENT 존재 여부와 스케줄러 DB 계정의 SELECT 권한을 확인하세요.");
        }
    }

    /**
     * 검색·메신저처럼 의미 있는 묶음으로 보이게 도메인을 분류한다. 모르는 도메인은 그대로 둔다.
     * 부분 문자열이 아니라 도메인 자체 또는 하위 도메인으로만 판별한다(dropbox.com이 x.com으로 잡히지 않게).
     */
    static String sourceLabel(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (GOOGLE.matcher(h).matches()) return "검색 · 구글";
        if (isHost(h, "naver.com")) return isHost(h, "cafe.naver.com") ? "네이버 카페" : isHost(h, "blog.naver.com") ? "네이버 블로그" : "검색 · 네이버";
        if (isHost(h, "daum.net")) return isHost(h, "cafe.daum.net") ? "다음 카페" : "검색 · 다음";
        if (isHost(h, "bing.com")) return "검색 · 빙";
        if (isHost(h, "kakao.com") || isHost(h, "kakaocorp.com")) return "카카오톡·카카오";
        if (SNS.stream().anyMatch(domain -> isHost(h, domain))) return "SNS";
        return h;
    }
    // google.com, google.co.kr, google.com.au 같은 구글 국가 도메인만 인정한다(google.example.com 제외).
    private static final java.util.regex.Pattern GOOGLE =
            java.util.regex.Pattern.compile("(?:.+\\.)?google\\.(?:com|co\\.[a-z]{2}|com\\.[a-z]{2}|[a-z]{2})");
    private static final List<String> SNS = List.of("instagram.com", "threads.net", "threads.com", "facebook.com",
            "t.co", "x.com", "twitter.com");
    static boolean isHost(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }
}
