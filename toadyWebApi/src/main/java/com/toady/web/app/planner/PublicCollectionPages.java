package com.toady.web.app.planner;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import static com.toady.web.app.planner.PublicHtml.esc;

/**
 * 검색어 단위 공개 모음 페이지: 무순위·줍줍(/zupzup), 이번 주 청약 일정(/weekly).
 * 공고 데이터에서 매번 자동으로 만들며, 단지별로 손으로 쓰는 문구는 두지 않는다.
 */
@RestController
public class PublicCollectionPages {
    static final List<String> UNRANKED_TYPES = List.of("remaining", "optional");
    private final PublicAreaPage areas; private final AnnouncementSearchService search; private final JdbcTemplate jdbc; private final String domain; private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public PublicCollectionPages(PublicAreaPage areas, AnnouncementSearchService search, JdbcTemplate jdbc, @Value("${toady.web.domain}") String domain) {
        this(areas, search, jdbc, domain, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    PublicCollectionPages(PublicAreaPage areas, AnnouncementSearchService search, JdbcTemplate jdbc, String domain, Clock clock) {
        this.areas = areas; this.search = search; this.jdbc = jdbc; this.domain = domain.replaceAll("/+$", ""); this.clock = clock;
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    private String nav(String label) { return "<nav aria-label='현재 위치'><a href='" + esc(domain) + "/'>TOADY</a><span>›</span><span>" + esc(label) + "</span></nav>"; }

    @GetMapping(value = "/api/v1/public/zupzup", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> zupzup() { return PublicHtml.cached("zupzup", this::renderZupzup); }
    private ResponseEntity<String> renderZupzup() {
        LocalDate today = today();
        var groups = areas.groups(null, UNRANKED_TYPES, today, 60, Integer.MAX_VALUE);
        String title = "무순위·줍줍 청약 일정" + (groups.open().isEmpty() ? "" : " · 접수 중 " + groups.open().size() + "건") + " · TOADY";
        String description = "APT 무순위·잔여세대와 임의공급(줍줍) 청약 일정 · 접수 중 " + groups.open().size() + "건, 접수 예정 " + groups.upcoming().size()
            + "건 · 공급금액·위치·동네 실거래가를 한 화면에서 확인하세요.";
        String app = domain + "/match/zupzup";
        String body = nav("무순위·줍줍")
            + "<section class='card'><span class='chip'>매일 업데이트</span><h1>무순위·줍줍 청약 일정</h1>"
            + "<p class='muted'>청약홈에 올라온 APT 무순위·잔여세대와 임의공급 공고를 모았어요. 계약 포기·부적격으로 다시 나온 물량이라 접수 기간이 짧으니 일정을 먼저 확인하세요.</p>"
            + stats(groups) + "<a class='cta' data-app href='" + esc(app) + "'>줍줍 모아보기 · 새 공고 알림 받기</a></section>"
            + (groups.open().isEmpty() && groups.upcoming().isEmpty()
                ? "<section class='card'><h2>접수 중·예정 공고</h2><p class='empty'>지금 접수 중이거나 예정된 무순위·임의공급 공고가 없어요. 새 공고는 매일 반영됩니다.</p></section>"
                : (groups.open().isEmpty() ? "" : areas.section("접수 중", groups.open(), "")) + (groups.upcoming().isEmpty() ? "" : areas.section("접수 예정", groups.upcoming(), "")))
            + areas.section("최근 마감", groups.closed().subList(0, Math.min(12, groups.closed().size())), "최근 2개월 안에 마감된 공고가 없습니다.")
            + "<section class='card'><h2>신청 전에 확인하세요</h2><ul>"
            + "<li><strong>신청 자격</strong><span class='muted'>2025년 6월부터 무순위(사후) 청약은 원칙적으로 무주택 세대구성원만 신청할 수 있어요. 거주지역 요건은 공고마다 달라요.</span></li>"
            + "<li><strong>재당첨 제한</strong><span class='muted'>당첨되면 다른 청약의 재당첨 제한·당첨자 관리 대상이 될 수 있어요.</span></li>"
            + "<li><strong>계약 일정</strong><span class='muted'>발표 후 계약까지 기간이 짧아 자금 계획을 미리 세워 두세요.</span></li></ul>"
            + "<p class='muted' style='margin-top:10px'>정확한 자격과 제출 서류는 각 공고의 모집공고 원문을 확인해 주세요.</p></section>"
            + PublicHtml.explore(domain, "/zupzup")
            + "<small>출처: 청약홈 공개 데이터(APT 무순위·잔여세대, 임의공급). 공고 유형은 청약홈 분류를 따릅니다.</small>";
        return PublicHtml.page(domain, "/zupzup", title, description, domain + "/zupzup", body);
    }

    record Week(LocalDate monday, LocalDate sunday) {
        static Week of(LocalDate day) { LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); return new Week(monday, monday.plusDays(6)); }
        Week next() { return new Week(monday.plusWeeks(1), sunday.plusWeeks(1)); }
        boolean overlaps(LocalDate start, LocalDate end) { LocalDate last = end == null ? start : end; return !start.isAfter(sunday) && !last.isBefore(monday); }
        boolean contains(LocalDate day) { return day != null && !day.isBefore(monday) && !day.isAfter(sunday); }
        String label() { return PublicHtml.shortDate(monday) + " ~ " + PublicHtml.shortDate(sunday); }
    }
    record Winner(String id, String houseNm, String address, LocalDate date) { }

    /** 이번 주(월~일) 접수 중인 공고, 다음 주 접수 시작 공고, 이번 주 APT 당첨자 발표. */
    @GetMapping(value = "/api/v1/public/weekly", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> weekly() { return PublicHtml.cached("weekly", this::renderWeekly); }
    private ResponseEntity<String> renderWeekly() {
        LocalDate today = today();
        Weekly data = weeklyData(today);
        Week week = data.week(), next = data.next();
        List<PublicAreaPage.Item> current = data.current();
        List<Winner> winners = data.winners();
        List<PublicAreaPage.Item> nextStarts = data.nextWeek();
        String title = "이번 주 청약 일정 (" + week.label() + ") · 접수 " + current.size() + "건 · TOADY";
        String description = week.label() + " 청약 접수 " + current.size() + "건, 당첨자 발표 " + winners.size() + "건, 다음 주 접수 시작 " + nextStarts.size()
            + "건 · 아파트·오피스텔·무순위·공공임대 일정을 한 번에 확인하세요.";
        StringBuilder winnerList = new StringBuilder("<section class='card list'><h2>이번 주 당첨자 발표 <span class='muted'>").append(winners.size()).append("건</span></h2>");
        if (winners.isEmpty()) winnerList.append("<p class='empty'>이번 주 발표 예정인 아파트 공고가 없어요.</p>");
        for (Winner winner : winners) winnerList.append("<a href='").append(esc(domain + "/announcements/" + winner.id() + "?tabType=rate")).append("'><strong><span class='when'>")
            .append(esc(PublicHtml.shortDate(winner.date()) + " " + dayName(winner.date()))).append("</span>").append(esc(winner.houseNm())).append("</strong><span class='muted'>")
            .append(esc(winner.address())).append("</span></a>");
        winnerList.append("</section>");
        String body = nav("이번 주 청약")
            + "<section class='card'><span class='chip'>" + esc(week.label()) + "</span><h1>이번 주 청약 일정</h1>"
            + "<p class='muted'>이번 주 접수하는 공고와 당첨자 발표, 다음 주에 시작하는 공고를 모았어요. 매일 새 공고가 반영됩니다.</p>"
            + "<div class='stats'><div><strong>" + current.size() + "</strong><span>이번 주 접수</span></div><div><strong>" + winners.size()
            + "</strong><span>당첨자 발표</span></div><div><strong>" + nextStarts.size() + "</strong><span>다음 주 시작</span></div></div>"
            + "<a class='cta' data-app href='" + esc(domain + "/?focus=all&month=" + today.toString().substring(0, 7)) + "'>청약 캘린더에서 보기</a></section>"
            + areas.section("이번 주 접수", current, "이번 주 접수하는 공고가 없어요.")
            + winnerList
            + areas.section("다음 주 접수 시작 (" + next.label() + ")", nextStarts, "다음 주에 접수를 시작하는 공고가 아직 없어요.")
            + PublicHtml.explore(domain, "/weekly")
            + "<small>출처: 청약홈 공개 데이터. 당첨자 발표는 아파트(APT) 공고 기준이며, 일정은 공식 모집공고를 함께 확인해 주세요.</small>";
        return PublicHtml.page(domain, "/weekly", title, description, domain + "/weekly", body);
    }

    /** 앱 '이번 주 청약' 화면용 목록 한 줄. status: OPEN·UPCOMING·CLOSED. */
    public record WeeklyItem(String id, String type, String houseNm, String houseKind, String address, String start, String end, String status, String region) {
        static WeeklyItem of(PublicAreaPage.Item item) {
            var source = item.source();
            return new WeeklyItem(item.id(), source.type(), source.houseNm(), source.houseSecdNm(), source.hssplyAdres(),
                item.start() == null ? null : item.start().toString(), item.end() == null ? null : item.end().toString(), item.status().name(), source.regionName());
        }
    }
    public record WeeklyWinner(String id, String houseNm, String address, String date) { }
    public record WeeklySummary(String monday, String sunday, String nextMonday, String nextSunday, String today,
                                List<WeeklyItem> current, List<WeeklyWinner> winners, List<WeeklyItem> nextWeek) { }
    private final com.google.common.cache.Cache<String, WeeklySummary> weeklyCache = com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(4).expireAfterWrite(java.time.Duration.ofMinutes(10)).build();

    /** 앱 안 '이번 주 청약' 화면 데이터. 서버 HTML(/weekly)과 같은 기준이며 10분 캐시한다. */
    @GetMapping("/api/v1/public/weekly-summary")
    public ResponseEntity<WeeklySummary> weeklySummary() {
        LocalDate today = today();
        WeeklySummary body = PublicHtml.load(weeklyCache, today.toString(), () -> {
            Weekly data = weeklyData(today);
            return new WeeklySummary(data.week().monday().toString(), data.week().sunday().toString(), data.next().monday().toString(), data.next().sunday().toString(), today.toString(),
                data.current().stream().map(WeeklyItem::of).toList(),
                data.winners().stream().map(w -> new WeeklyWinner(w.id(), w.houseNm(), w.address(), w.date() == null ? null : w.date().toString())).toList(),
                data.nextWeek().stream().map(WeeklyItem::of).toList());
        });
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic()).body(body);
    }

    private final com.google.common.cache.Cache<String, List<WeeklyItem>> activeCache = com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(4).expireAfterWrite(java.time.Duration.ofMinutes(10)).build();
    /**
     * 앱 '지역별 청약' 화면용: 전국 접수 중·예정 공고(전 유형). 화면이 주소로 시·도/시·군·구를 묶는다(캘린더 검색과 같은 지역 목록).
     * 접수 기간이 긴 공고도 넣도록 시작일을 넉넉히 거슬러 올라간다. 10분 캐시.
     */
    @GetMapping("/api/v1/public/active-announcements")
    public ResponseEntity<List<WeeklyItem>> activeAnnouncements() {
        LocalDate today = today();
        List<WeeklyItem> body = PublicHtml.load(activeCache, today.toString(), () -> {
            var groups = PublicAreaPage.classify(startingBetween(today.minusDays(LOOKBACK_DAYS), today.plusDays(60)), today, 0);
            List<WeeklyItem> items = new java.util.ArrayList<>();
            groups.open().forEach(item -> items.add(WeeklyItem.of(item)));
            groups.upcoming().forEach(item -> items.add(WeeklyItem.of(item)));
            return items;
        });
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic()).body(body);
    }

    record Weekly(Week week, Week next, List<PublicAreaPage.Item> current, List<Winner> winners, List<PublicAreaPage.Item> nextWeek) { }
    /** 이번 주 접수(접수 기간이 이번 주와 겹침)·이번 주 APT 발표·다음 주 접수 시작. HTML과 앱 화면이 함께 쓴다. */
    Weekly weeklyData(LocalDate today) {
        Week week = Week.of(today), next = week.next();
        // 접수 기간이 긴 공고(공공임대 등)도 이번 주에 열려 있으면 넣도록 시작일을 넉넉히 거슬러 올라가 모든 페이지를 읽는다.
        List<AnnouncementListItem> found = startingBetween(week.monday().minusDays(LOOKBACK_DAYS), next.sunday());
        var thisWeek = PublicAreaPage.classify(found.stream().filter(item -> {
            LocalDate start = PlannerSummary.date(nz(item.rceptBgnde())), end = PlannerSummary.date(nz(item.rceptEndde()));
            return start != null && week.overlaps(start, end);
        }).toList(), today, Integer.MAX_VALUE);
        var nextWeek = PublicAreaPage.classify(found.stream().filter(item -> next.contains(PlannerSummary.date(nz(item.rceptBgnde())))).toList(), today, Integer.MAX_VALUE);
        List<PublicAreaPage.Item> current = new java.util.ArrayList<>(thisWeek.open()); current.addAll(thisWeek.upcoming()); current.addAll(thisWeek.closed());
        current.sort(java.util.Comparator.comparing(PublicAreaPage.Item::start));
        return new Weekly(week, next, current, winners(week), nextWeek.upcoming());
    }

    static final int LOOKBACK_DAYS = 120, MAX_ITEMS = 1000;
    /**
     * 접수 시작일이 범위 안인 공고를 한 번에 읽는다(내부 호출이라 통합 검색 상한 1000건까지 한 페이지로).
     * 페이지를 나눠 부르면 유형별로 앞부분을 매번 다시 읽어 조회량이 커진다.
     */
    List<AnnouncementListItem> startingBetween(LocalDate from, LocalDate to) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setStartDate(from); query.setEndDate(to); query.setSize(MAX_ITEMS); query.setPage(0);
        var result = search.searchAll(query, null);
        if (result.getTotalElements() > MAX_ITEMS)
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("이번 주 청약 집계 대상이 {}건을 넘어 일부가 빠질 수 있습니다: {}", MAX_ITEMS, result.getTotalElements());
        return result.getContent();
    }

    List<Winner> winners(Week week) { return winners(jdbc, week); }
    /** 이번 주 APT 당첨자 발표. 서버 /weekly 페이지와 앱 weekly-summary(오늘의 요약 숫자·이번 주 청약 목록)가 같은 결과를 쓴다. */
    static List<Winner> winners(JdbcTemplate jdbc, Week week) {
        String from = week.monday().format(DateTimeFormatter.BASIC_ISO_DATE), to = week.sunday().format(DateTimeFormatter.BASIC_ISO_DATE);
        return jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,PRZWNER_PRESNATN_DE FROM TB_APT_LTTOT_PBLANC_DETAIL "
                + "WHERE REPLACE(PRZWNER_PRESNATN_DE,'-','') BETWEEN ? AND ? ORDER BY PRZWNER_PRESNATN_DE,HOUSE_NM LIMIT 200",
            (r, n) -> new Winner("apt/" + r.getString(1) + "/" + r.getString(2), r.getString(3), r.getString(4), PlannerSummary.date(nz(r.getString(5)))), from, to)
            .stream().filter(w -> w.id().matches("apt/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}")).toList();
    }
    private static String stats(PublicAreaPage.Groups groups) {
        return "<div class='stats'><div><strong>" + groups.open().size() + "</strong><span>접수 중</span></div><div><strong>" + groups.upcoming().size()
            + "</strong><span>접수 예정</span></div><div><strong>" + groups.closed().size() + "</strong><span>최근 마감</span></div></div>";
    }
    private static String dayName(LocalDate date) { return date == null ? "" : "(" + "월화수목금토일".charAt(date.getDayOfWeek().getValue() - 1) + ")"; }
    private static String nz(String value) { return value == null ? "" : value; }
}
