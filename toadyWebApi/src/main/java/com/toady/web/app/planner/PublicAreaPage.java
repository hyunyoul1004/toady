package com.toady.web.app.planner;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static com.toady.web.app.planner.PublicHtml.esc;

/** 생활권 검색어(예: 운정 청약)로 들어오는 사용자에게 같은 기준의 공고 목록을 서버 HTML로 제공한다. */
@RestController
public class PublicAreaPage {
    static final int PAST_DAYS = 180, FUTURE_DAYS = 180;
    private final AnnouncementSearchService search;
    private final String domain;
    private final ScoreBenchmarks benchmarks;
    public PublicAreaPage(AnnouncementSearchService search, @Value("${toady.web.domain}") String domain, ScoreBenchmarks benchmarks) {
        this.search = search; this.domain = domain.replaceAll("/+$", ""); this.benchmarks = benchmarks;
    }
    enum Status { OPEN, UPCOMING, CLOSED }
    record Item(AnnouncementListItem source, LocalDate start, LocalDate end, Status status) {
        String id() { return source.type() + "/" + source.houseManageNo() + "/" + source.pblancNo(); }
    }
    record Groups(List<Item> open, List<Item> upcoming, List<Item> closed) {
        /** 앱에서 처음 보여줄 달: 접수 중·예정 공고 중 가장 이른 접수월, 없으면 이번 달. */
        String month(LocalDate today) {
            return java.util.stream.Stream.concat(open.stream(), upcoming.stream()).map(Item::start).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).map(date -> date.isBefore(today.withDayOfMonth(1)) ? today : date).orElse(today).toString().substring(0, 7);
        }
    }

    Groups groups(LivingAreas.Area area, LocalDate today) { return groups(area.label(), null, today, PAST_DAYS); }

    /** 생활권 공개 페이지와 같은 기준의 접수 중·예정 공고. 웹 푸시의 생활권 새 공고 판정에 쓴다. */
    public List<AnnouncementListItem> active(LivingAreas.Area area, LocalDate today) {
        Groups groups = groups(area.label(), null, today, 45);
        return java.util.stream.Stream.concat(groups.open().stream(), groups.upcoming().stream()).map(Item::source).toList();
    }

    /** 접수 마감이 오늘부터 days일 안쪽(오늘 포함)인 APT 공고 ID. 결과 알림의 공개 시각 기록에 쓴다. */
    public List<String> aptEndedWithin(LocalDate today, int days) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setStartDate(today.minusDays(days + 60)); query.setEndDate(today); query.setSize(500);
        List<String> ids = new java.util.ArrayList<>();
        for (AnnouncementListItem item : search.searchAll(query, List.of("apt")).getContent()) {
            LocalDate start = PlannerSummary.date(item.rceptBgnde() == null ? "" : item.rceptBgnde());
            LocalDate end = PlannerSummary.date(item.rceptEndde() == null ? "" : item.rceptEndde());
            LocalDate last = end != null ? end : start;
            if (last != null && !last.isAfter(today) && !last.isBefore(today.minusDays(days))) ids.add(item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo());
        }
        return ids;
    }

    /** 주간 브리핑 집계: 기간 안에 접수를 시작하는 공고 수(아파트, 무순위·줍줍, 그 밖의 유형). */
    public record WeekCounts(int apt, int unranked, int others) { public int total() { return apt + unranked + others; } }
    public WeekCounts startingCounts(LocalDate from, LocalDate to) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setStartDate(from); query.setEndDate(to); query.setSize(500);
        int apt = 0, unranked = 0, others = 0;
        for (AnnouncementListItem item : search.searchAll(query, null).getContent()) {
            LocalDate start = PlannerSummary.date(item.rceptBgnde() == null ? "" : item.rceptBgnde());
            if (start == null || start.isBefore(from) || start.isAfter(to)) continue;
            if ("apt".equals(item.type())) apt++;
            else if (PublicCollectionPages.UNRANKED_TYPES.contains(item.type())) unranked++;
            else others++;
        }
        return new WeekCounts(apt, unranked, others);
    }

    /** 무순위·잔여세대·임의공급의 접수 중·예정 공고. 웹 푸시의 '줍줍' 새 공고 판정에 쓴다. */
    public List<AnnouncementListItem> activeUnranked(LocalDate today) {
        Groups groups = groups(null, PublicCollectionPages.UNRANKED_TYPES, today, 45);
        return java.util.stream.Stream.concat(groups.open().stream(), groups.upcoming().stream()).map(Item::source).toList();
    }

    /** 생활권·무순위 등 공개 목록이 같은 접수 상태 기준(접수 중/예정/마감)을 쓰도록 한곳에서 나눈다. */
    Groups groups(String keyword, List<String> types, LocalDate today, int pastDays) { return groups(keyword, types, today, pastDays, 12); }
    /** closedLimit: 최근 마감을 몇 건까지 남길지. 건수 통계를 내는 화면은 자르지 않고 받은 뒤 목록만 줄인다. */
    Groups groups(String keyword, List<String> types, LocalDate today, int pastDays, int closedLimit) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setKeyword(keyword);
        query.setStartDate(today.minusDays(pastDays));
        query.setEndDate(today.plusDays(FUTURE_DAYS));
        query.setSize(SEARCH_LIMIT);
        var page = search.searchAll(query, types);
        // 접수 시작일 최신순으로 자르므로, 넘치면 오래전에 시작한 접수 중 공고가 빠질 수 있다.
        if (page.getTotalElements() > SEARCH_LIMIT) log.warn("공개 목록 조회가 {}건을 넘어 일부가 빠졌습니다: keyword={}, types={}, total={}", SEARCH_LIMIT, keyword, types, page.getTotalElements());
        return classify(page.getContent(), today, closedLimit);
    }
    static final int SEARCH_LIMIT = 100;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicAreaPage.class);
    static Groups classify(List<AnnouncementListItem> sources, LocalDate today) { return classify(sources, today, 12); }
    /** closedLimit: 최근 마감 목록을 몇 건까지 남길지(생활권 페이지 12건). 집계에 쓰는 곳은 Integer.MAX_VALUE로 자르지 않는다. */
    static Groups classify(List<AnnouncementListItem> sources, LocalDate today, int closedLimit) {
        List<Item> open = new ArrayList<>(), upcoming = new ArrayList<>(), closed = new ArrayList<>();
        for (AnnouncementListItem source : sources) {
            LocalDate start = PlannerSummary.date(source.rceptBgnde() == null ? "" : source.rceptBgnde());
            LocalDate end = PlannerSummary.date(source.rceptEndde() == null ? "" : source.rceptEndde());
            if (start == null) continue;
            LocalDate last = end == null ? start : end;
            if (start.isAfter(today)) upcoming.add(new Item(source, start, end, Status.UPCOMING));
            else if (!last.isBefore(today)) open.add(new Item(source, start, end, Status.OPEN));
            else closed.add(new Item(source, start, end, Status.CLOSED));
        }
        Comparator<Item> byStart = Comparator.comparing(Item::start);
        open.sort(byStart); upcoming.sort(byStart); closed.sort(byStart.reversed());
        return new Groups(open, upcoming, closed.size() > closedLimit ? closed.subList(0, closedLimit) : closed);
    }

    @GetMapping(value = "/api/v1/public/areas", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> index() { return PublicHtml.cached("areas", this::renderIndex); }
    private ResponseEntity<String> renderIndex() {
        StringBuilder links = new StringBuilder();
        for (var area : LivingAreas.ALL) links.append("<a href='").append(esc(domain + "/areas/" + area.slug())).append("'><strong>")
            .append(esc(area.label())).append(" 청약</strong><span class='muted'>").append(esc(area.description())).append("</span></a>");
        String title = "생활권별 청약 일정 · TOADY";
        String description = "운정·동탄·다산·검단·위례·광교 등 생활권 이름으로 아파트 청약 일정과 분양가, 경쟁률을 찾아보세요.";
        return html("", title, description, domain + "/areas",
            "<nav aria-label='현재 위치'><a href='" + esc(domain) + "/'>TOADY</a><span>›</span><span>생활권</span></nav><section class='card'><h1>생활권별 청약 일정</h1><p class='muted'>"
                + esc(description) + "</p></section><section class='card list'>" + links + "</section>");
    }

    /** 앱 '생활권별 청약' 화면용: 생활권마다 접수 중·예정 건수와 캘린더에서 열 달. 10분 캐시. */
    public record AreaSummary(String slug, String label, String description, int open, int upcoming, String month) { }
    private final com.google.common.cache.Cache<String, List<AreaSummary>> areaSummaryCache = com.google.common.cache.CacheBuilder.newBuilder()
        .maximumSize(4).expireAfterWrite(java.time.Duration.ofMinutes(10)).build();
    @GetMapping("/api/v1/public/area-summaries")
    public ResponseEntity<List<AreaSummary>> areaSummaries() throws java.util.concurrent.ExecutionException {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<AreaSummary> body = areaSummaryCache.get(today.toString(), () -> LivingAreas.ALL.stream().map(area -> {
            Groups groups = groups(area, today);
            return new AreaSummary(area.slug(), area.label(), area.description(), groups.open().size(), groups.upcoming().size(), groups.month(today));
        }).toList());
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic()).body(body);
    }

    @GetMapping(value = "/api/v1/public/areas/{slug}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> page(@PathVariable String slug) {
        if (LivingAreas.bySlug(slug).isEmpty()) return renderPage(slug);
        return PublicHtml.cached("areas/" + slug, () -> renderPage(slug));
    }
    private ResponseEntity<String> renderPage(String slug) {
        var found = LivingAreas.bySlug(slug);
        if (found.isEmpty()) return ResponseEntity.status(404).header("Content-Type", "text/html;charset=UTF-8")
            .body("<!doctype html><html lang='ko'><meta charset='UTF-8'><title>생활권을 찾을 수 없음 · TOADY</title><h1>생활권을 찾을 수 없습니다.</h1><a href='/areas'>생활권 목록</a></html>");
        var area = found.get();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        Groups groups = groups(area, today);
        String app = domain + "/?area=" + area.slug() + "&month=" + groups.month(today);
        String title = area.label() + " 청약 일정·분양 정보 · TOADY";
        String description = area.description().split(" · ")[0] + " 아파트 청약 · 접수 중 " + groups.open().size() + "건, 접수 예정 " + groups.upcoming().size()
            + "건 · 분양가·경쟁률·당첨가점과 동네 실거래가를 한 화면에서 확인하세요.";
        StringBuilder others = new StringBuilder();
        for (var other : LivingAreas.ALL) if (!other.slug().equals(area.slug()))
            others.append("<a class='chip' href='").append(esc(domain + "/areas/" + other.slug())).append("'>").append(esc(other.label())).append("</a> ");
        String body = "<nav aria-label='현재 위치'><a href='" + esc(domain) + "/'>TOADY</a><span>›</span><a href='" + esc(domain) + "/areas'>생활권</a><span>›</span><span>" + esc(area.label()) + "</span></nav>"
            + "<section class='card'><span class='chip'>생활권</span><h1>" + esc(area.label()) + " 청약 일정</h1><p class='muted'>" + esc(area.description().split(" · ")[0])
            + "의 청약 공고를 주소·단지명 기준으로 모았어요. 분양가·경쟁률·당첨가점과 동네 실거래가는 캘린더에서 공고를 눌러 확인하세요.</p>"
            + "<a class='cta' data-app href='" + esc(app) + "'>" + esc(area.label()) + " 청약 캘린더 열기</a>"
            + AreaCalendar.subscribeLinks(domain, area) + "</section>"
            // 진행·예정이 모두 없으면 빈 카드를 두 번 보여주지 않고 한 번만 안내한다.
            + (groups.open().isEmpty() && groups.upcoming().isEmpty()
                ? "<section class='card'><h2>접수 중·예정 공고</h2><p class='empty'>지금 " + esc(area.label()) + "에서 접수 중이거나 예정된 공고가 없어요. 새 공고는 매일 반영됩니다.</p></section>"
                : (groups.open().isEmpty() ? "" : section("접수 중", groups.open(), "")) + (groups.upcoming().isEmpty() ? "" : section("접수 예정", groups.upcoming(), "")))
            + section("최근 마감", groups.closed(), "최근 6개월 안에 마감된 공고가 없습니다.")
            + scoreSection(area)
            + "<section class='card'><h2>다른 생활권</h2><div class='chips'>" + others + "</div></section>" + PublicHtml.explore(domain, "")
            + "<small>출처: 청약홈 공개 데이터. 생활권은 행정 경계가 아니며, 주소·단지명 기준으로 분류해 일부 공고가 빠지거나 포함될 수 있습니다.</small>";
        return html("", title, description, domain + "/areas/" + area.slug(), body);
    }

    static final int AREA_SCORES = 5;
    /** 생활권의 최근 공개 당첨가점(공고별 최저·최고)과 가점 계산기·자격 진단 연결. 자료가 없으면 비운다. */
    String scoreSection(LivingAreas.Area area) {
        List<ScoreBenchmarks.Item> items;
        try { items = benchmarks.all().stream().filter(item -> area.slug().equals(item.areaSlug())).limit(AREA_SCORES).toList(); }
        catch (RuntimeException unavailable) { return ""; }
        if (items.isEmpty()) return "";
        StringBuilder out = new StringBuilder("<section class='card list'><h2>").append(esc(area.label())).append(" 최근 공개 당첨가점</h2>");
        for (ScoreBenchmarks.Item item : items) {
            String range = (item.low() == null ? "" : "최저 " + score(item.low()) + "점") + (item.low() != null && item.high() != null ? " · " : "") + (item.high() == null ? "" : "최고 " + score(item.high()) + "점");
            out.append("<a href='").append(esc(domain + "/announcements/" + item.id() + "?tabType=rate")).append("'><strong><span class='when'>").append(esc(range))
                .append("</span>").append(esc(item.houseNm())).append("</strong><span class='muted'>").append(esc(item.receiptDate().substring(0, 7).replace('-', '.'))).append(" 접수</span></a>");
        }
        return out.append("<div class='row' style='margin-top:12px'><a class='cta' data-app href='").append(esc(domain + "/score")).append("'>내 가점 계산하고 비교하기</a>")
            .append("<a class='cta secondary' data-app href='").append(esc(domain + "/match")).append("'>내가 넣을 수 있는 청약 찾기</a></div></section>").toString();
    }
    static String score(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value); }

    String section(String heading, List<Item> items, String empty) {
        StringBuilder list = new StringBuilder("<section class='card list'><h2>").append(esc(heading)).append(" <span class='muted'>").append(items.size()).append("건</span></h2>");
        if (items.isEmpty()) return list.append("<p class='empty'>").append(esc(empty)).append("</p></section>").toString();
        for (Item item : items) {
            var source = item.source();
            String badge = switch (item.status()) { case OPEN -> "<span class='badge open'>접수 중</span>"; case UPCOMING -> "<span class='badge'>예정</span>"; default -> "<span class='badge'>마감</span>"; };
            String period = PublicHtml.date(item.start()) + (item.end() == null || item.end().equals(item.start()) ? "" : " ~ " + PublicHtml.date(item.end()));
            list.append("<a href='").append(esc(domain + "/announcements/" + item.id())).append("'><strong>").append(badge).append(esc(source.houseNm()))
                .append("</strong><span class='muted'>").append(esc(source.houseSecdNm() == null ? "" : source.houseSecdNm() + " · ")).append("접수 ").append(esc(period))
                .append("</span><br><span class='muted'>").append(esc(source.hssplyAdres())).append("</span></a>");
        }
        return list.append("</section>").toString();
    }

    private ResponseEntity<String> html(String current, String title, String description, String canonical, String body) {
        return PublicHtml.page(domain, current, title, description, canonical, body);
    }
}
