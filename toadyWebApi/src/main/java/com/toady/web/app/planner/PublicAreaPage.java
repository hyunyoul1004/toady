package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.app.announcement.ReceiptWindow;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import static com.toady.web.app.planner.PublicHtml.esc;

/**
 * 생활권 공개 페이지(/areas, /areas/{slug})와 생활권·무순위 공고를 접수 상태로 나누는 공통 기준(groups·classify).
 * 생활권 검색어(예: 운정 청약)로 들어오는 사용자에게 같은 기준의 공고 목록을 서버 HTML로 제공한다(봇은 웹 컨테이너 nginx를 거쳐 받는다).
 * 공고 검색은 AnnouncementSearchService, 가점은 ScoreBenchmarks를 쓴다. AreaCalendar, PublicCollectionPages, EligibilityFeed, 웹 푸시(push 패키지)도 여기 기준을 쓴다.
 */
// @RestController: HTML 문자열과 JSON을 응답하는 컨트롤러.
@RestController
public class PublicAreaPage {
    /** 생활권 페이지의 '최근 마감' 범위(접수 시작일 기준). 접수 중·예정 범위는 ReceiptWindow 하나를 쓴다. */
    static final int PAST_DAYS = 180;
    private final AnnouncementSearchService search;
    private final String domain;
    private final ScoreBenchmarks benchmarks;
    // @Value: 서비스 주소(toady.web.domain)를 설정에서 읽는다.
    public PublicAreaPage(AnnouncementSearchService search, @Value("${toady.web.domain}") String domain, ScoreBenchmarks benchmarks) {
        this.search = search; this.domain = domain.replaceAll("/+$", ""); this.benchmarks = benchmarks;
    }
    enum Status { OPEN, UPCOMING, CLOSED }
    /** 공고 한 건과 접수 시작·마감일, 오늘 기준 상태. */
    record Item(AnnouncementListItem source, LocalDate start, LocalDate end, Status status) {
        String id() { return source.type() + "/" + source.houseManageNo() + "/" + source.pblancNo(); }
    }
    record Groups(List<Item> open, List<Item> upcoming, List<Item> closed) {
        /**
         * 앱에서 처음 보여줄 달: 접수 중·예정 공고 중 가장 이른 접수 시작월, 없으면 이번 달.
         * 접수 중 공고가 시작일 줄(진한 줄)로 보이는 달을 열려고 지난달에 시작한 공고도 시작월로 연다(이번 달에는 연한 '접수 중' 줄로 보인다).
         */
        String month(LocalDate today) {
            return Stream.concat(open.stream(), upcoming.stream()).map(Item::start).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(today).toString().substring(0, 7);
        }
    }

    Groups groups(LivingAreas.Area area, LocalDate today) { return groups(area.label(), null, today, PAST_DAYS); }

    /** 생활권 공개 페이지와 같은 기준의 접수 중·예정 공고. 웹 푸시의 생활권 새 공고 판정에 쓴다. */
    public List<AnnouncementListItem> active(LivingAreas.Area area, LocalDate today) {
        return activeOnly(area.label(), null, today);
    }

    /**
     * 결과(1순위 경쟁률·당첨가점)가 나올 수 있는 APT 공고 ID: 접수를 시작했고(시작일 ≤ 오늘) 접수 마감이 days일 안쪽(마감일 ≥ 오늘-days).
     * 마감일까지 기다리면 1순위가 마감 전날 끝나는 공고(특별공급 → 1순위 → 마감)의 결과 알림이 하루 이틀 늦어진다.
     * 결과가 실제로 있는지는 AnnouncementResults로 따로 확인한다. 결과 알림의 공개 시각 기록에 쓴다.
     */
    public List<String> aptResultWindow(LocalDate today, int days) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setStartDate(today.minusDays(days + 60)); query.setEndDate(today); query.setSize(500);
        List<String> ids = new ArrayList<>();
        for (AnnouncementListItem item : search.searchAll(query, List.of("apt")).getContent()) {
            LocalDate start = PlannerSummary.date(item.rceptBgnde() == null ? "" : item.rceptBgnde());
            LocalDate end = PlannerSummary.date(item.rceptEndde() == null ? "" : item.rceptEndde());
            if (resultWindow(start, end, today, days)) ids.add(item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo());
        }
        return ids;
    }
    /**
     * 결과 알림 대상 기간의 공통 기준(공고 목록 쪽 aptResultWindow와 구독자별 PushDigest.resultLines가 함께 쓴다).
     * 접수 시작일 ≤ 오늘이고 접수 마감일 ≥ 오늘-days. 한쪽 날짜가 없으면 다른 쪽 날짜로 대신한다. 둘 다 없으면 대상이 아니다.
     */
    public static boolean resultWindow(LocalDate start, LocalDate end, LocalDate today, int days) {
        LocalDate first = start != null ? start : end, last = end != null ? end : start;
        return first != null && !first.isAfter(today) && !last.isBefore(today.minusDays(days));
    }

    /** 주간 브리핑 집계: 다음 주 접수를 시작하는 공고 수(아파트, 무순위·줍줍, 그 밖의 유형). 계산은 PublicCollectionPages.nextWeekCounts. */
    public record WeekCounts(int apt, int unranked, int others) { public int total() { return apt + unranked + others; } }

    /** 무순위·잔여세대·임의공급의 접수 중·예정 공고. 웹 푸시의 '줍줍' 새 공고 판정에 쓴다. */
    public List<AnnouncementListItem> activeUnranked(LocalDate today) {
        return activeOnly(null, PublicCollectionPages.UNRANKED_TYPES, today);
    }

    /** 생활권·무순위 등 공개 목록이 같은 접수 상태 기준(접수 중/예정/마감)을 쓰도록 한곳에서 나눈다. */
    Groups groups(String keyword, List<String> types, LocalDate today, int pastDays) { return groups(keyword, types, today, pastDays, 12); }
    /**
     * closedLimit: 최근 마감을 몇 건까지 남길지. 건수 통계를 내는 화면은 자르지 않고 받은 뒤 목록만 줄인다.
     * 접수 중·예정은 ReceiptWindow 범위(시작 400일 이내이면서 마감 ≥ 오늘, 또는 180일 안에 시작)로 따로 읽고,
     * 최근 마감은 접수 시작일이 pastDays 안인 공고에서 고른다. 그래서 pastDays가 짧은 곳(푸시 45일 등)도 오래 열린 공고를 빠뜨리지 않는다.
     */
    Groups groups(String keyword, List<String> types, LocalDate today, int pastDays, int closedLimit) {
        var active = activeSearch(keyword, types, today);
        var recent = search(keyword, types, today.minusDays(pastDays), today);
        if (active.getTotalElements() > SEARCH_LIMIT || recent.getTotalElements() > SEARCH_LIMIT)
            log.warn("공개 목록 조회가 {}건을 넘어 일부가 빠졌습니다: keyword={}, types={}, active={}, recent={}", SEARCH_LIMIT, keyword, types,
                active.getTotalElements(), recent.getTotalElements());
        List<AnnouncementListItem> merged = new ArrayList<>(active.getContent());
        java.util.Set<String> seen = new java.util.HashSet<>();
        merged.forEach(item -> seen.add(item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo()));
        for (AnnouncementListItem item : recent.getContent())
            if (seen.add(item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo())) merged.add(item);
        return classify(merged, today, closedLimit);
    }
    /**
     * 접수 중·예정 공고만(최근 마감은 읽지 않는다). 웹 푸시가 실행마다 생활권 전체를 돌며 부르므로 조회를 한 번(유형별)으로 줄인다.
     * 판정은 groups와 같은 classify(ReceiptWindow)다.
     */
    List<AnnouncementListItem> activeOnly(String keyword, List<String> types, LocalDate today) {
        var active = activeSearch(keyword, types, today);
        if (active.getTotalElements() > SEARCH_LIMIT)
            log.warn("접수 중·예정 조회가 {}건을 넘어 일부가 빠졌습니다: keyword={}, types={}, active={}", SEARCH_LIMIT, keyword, types, active.getTotalElements());
        Groups groups = classify(active.getContent(), today, 0);
        return Stream.concat(groups.open().stream(), groups.upcoming().stream()).map(Item::source).toList();
    }
    /** 접수 중·예정 후보: 접수 기간이 오늘~오늘+UPCOMING_DAYS와 겹치는 공고(시작일은 최대 400일 전까지, AnnouncementSearchService overlap). */
    private org.springframework.data.domain.Page<AnnouncementListItem> activeSearch(String keyword, List<String> types, LocalDate today) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setKeyword(keyword);
        query.setStartDate(today);
        query.setEndDate(ReceiptWindow.searchEnd(today));
        query.setOverlap(true);
        query.setSize(SEARCH_LIMIT);
        return search.searchAll(query, types);
    }
    /** 접수 시작일이 from~to인 공고를 통합 검색 한 페이지(최대 SEARCH_LIMIT건)로 읽는다. */
    private org.springframework.data.domain.Page<AnnouncementListItem> search(String keyword, List<String> types, LocalDate from, LocalDate to) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setKeyword(keyword);
        query.setStartDate(from);
        query.setEndDate(to);
        query.setSize(SEARCH_LIMIT);
        return search.searchAll(query, types);
    }
    /**
     * 한 번에 읽는 공고 상한. 통합 검색이 허용하는 최대((page+1)×size ≤ 1000)라 최근 마감 건수(운영 100건 이상)와
     * 웹 푸시의 무순위 접수 중·예정 판정이 잘리지 않는다. 실제 조회는 조건에 맞는 행만큼만 읽는다.
     */
    static final int SEARCH_LIMIT = PublicCollectionPages.MAX_ITEMS;
    private static final Logger log = LoggerFactory.getLogger(PublicAreaPage.class);
    static Groups classify(List<AnnouncementListItem> sources, LocalDate today) { return classify(sources, today, 12); }
    /** closedLimit: 최근 마감 목록을 몇 건까지 남길지(생활권 페이지 12건). 집계에 쓰는 곳은 Integer.MAX_VALUE로 자르지 않는다. */
    static Groups classify(List<AnnouncementListItem> sources, LocalDate today, int closedLimit) {
        List<Item> open = new ArrayList<>(), upcoming = new ArrayList<>(), closed = new ArrayList<>();
        for (AnnouncementListItem source : sources) {
            LocalDate start = PlannerSummary.date(source.rceptBgnde() == null ? "" : source.rceptBgnde());
            LocalDate end = PlannerSummary.date(source.rceptEndde() == null ? "" : source.rceptEndde());
            if (start == null) continue;
            // 접수 중·예정 판정은 ReceiptWindow 하나로 한다. 180일보다 먼 예정 공고는 어느 목록에도 넣지 않는다.
            if (start.isAfter(today)) { if (ReceiptWindow.upcoming(start, today)) upcoming.add(new Item(source, start, end, Status.UPCOMING)); }
            else if (ReceiptWindow.open(start, end, today)) open.add(new Item(source, start, end, Status.OPEN));
            else closed.add(new Item(source, start, end, Status.CLOSED));
        }
        Comparator<Item> byStart = Comparator.comparing(Item::start);
        open.sort(byStart); upcoming.sort(byStart); closed.sort(byStart.reversed());
        return new Groups(open, upcoming, closed.size() > closedLimit ? closed.subList(0, closedLimit) : closed);
    }

    // 생활권 목록 HTML. 5분 페이지 캐시.
    @GetMapping(value = "/api/v1/public/areas", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> index() { return PublicHtml.cached("areas", this::renderIndex); }
    private ResponseEntity<String> renderIndex() {
        StringBuilder links = new StringBuilder();
        for (var area : LivingAreas.all()) links.append("<a href='").append(esc(domain + "/areas/" + area.slug())).append("'><strong>")
            .append(esc(area.label())).append(" 청약</strong><span class='muted'>").append(esc(area.description())).append("</span></a>");
        String title = "생활권별 청약 일정 · TOADY";
        String description = "운정·동탄·다산·검단·위례·광교 등 생활권 이름으로 아파트 청약 일정과 분양가, 경쟁률을 찾아보세요.";
        // 행정구역(시·도) 지역 페이지로도 이어지게 해 생활권 밖 공고까지 링크로 닿게 한다.
        StringBuilder regions = new StringBuilder("<section class='card'><h2>시·도별 청약</h2><div class='chips'>");
        for (Regions.Sido sido : Regions.ALL) regions.append("<a class='chip' href='").append(esc(domain + sido.path())).append("'>").append(esc(sido.shortName())).append("</a>");
        regions.append("</div><a class='cta secondary' href='").append(esc(domain + "/regions")).append("'>시·군·구별 청약 전체 보기</a></section>");
        List<PublicHtml.Crumb> crumbs = List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("생활권", null));
        List<PublicHtml.Crumb> items = LivingAreas.all().stream().map(area -> new PublicHtml.Crumb(area.label() + " 청약", domain + "/areas/" + area.slug())).toList();
        return PublicHtml.page(domain, "", title, description, domain + "/areas",
            PublicHtml.breadcrumb(crumbs) + "<section class='card'><h1>생활권별 청약 일정</h1><p class='muted'>"
                + esc(description) + "</p></section><section class='card list'>" + links + "</section>" + regions + PublicHtml.explore(domain, "/areas"),
            PublicHtml.breadcrumbLd(crumbs, domain + "/areas") + PublicHtml.itemListLd("생활권별 청약", items));
    }

    /** 앱 '생활권별 청약' 화면용: 생활권마다 접수 중·예정 건수와 캘린더에서 열 달. 10분 캐시. */
    public record AreaSummary(String slug, String label, String description, int open, int upcoming, String month) { }
    private final Cache<String, List<AreaSummary>> areaSummaryCache = CacheBuilder.newBuilder()
        .maximumSize(4).expireAfterWrite(Duration.ofMinutes(10)).build();
    // 앱 화면용 JSON. 생활권마다 검색을 돌리므로 10분 메모리 캐시 + 공용 캐시 헤더를 쓴다.
    @GetMapping("/api/v1/public/area-summaries")
    public ResponseEntity<List<AreaSummary>> areaSummaries() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<AreaSummary> body = PublicHtml.load(areaSummaryCache, today.toString(), () -> LivingAreas.all().stream().map(area -> {
            Groups groups = groups(area, today);
            return new AreaSummary(area.slug(), area.label(), area.description(), groups.open().size(), groups.upcoming().size(), groups.month(today));
        }).toList());
        return PublicHtml.publicJson(body, Duration.ofMinutes(10));
    }
    /** 숨김 목록이 바뀌면 생활권 요약 캐시를 비운다. */
    // @EventListener: HiddenAnnouncements가 알리는 Changed 이벤트를 받는다.
    @org.springframework.context.event.EventListener(HiddenAnnouncements.Changed.class)
    void onHiddenChanged() { areaSummaryCache.invalidateAll(); }

    // 생활권 한 곳의 HTML. @PathVariable로 slug를 받는다. 없는 slug는 캐시에 넣지 않고 바로 404를 준다.
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
        boolean quiet = groups.open().isEmpty() && groups.upcoming().isEmpty();
        // 접수 중·예정이 없을 때 '접수 중 0건'으로 알리지 않고 최근 마감·공개 결과를 앞에 둔다.
        String description = area.description().split(" · ")[0] + " 아파트 청약 · " + (quiet ? quietSummary(area, groups) : "접수 중 " + groups.open().size() + "건, 접수 예정 " + groups.upcoming().size() + "건")
            + " · 분양가·경쟁률·당첨가점과 동네 실거래가를 한 화면에서 확인하세요.";
        StringBuilder others = new StringBuilder();
        for (var other : LivingAreas.all()) if (!other.slug().equals(area.slug()))
            others.append("<a class='chip' href='").append(esc(domain + "/areas/" + other.slug())).append("'>").append(esc(other.label())).append("</a> ");
        List<PublicHtml.Crumb> crumbs = List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("생활권", domain + "/areas"), new PublicHtml.Crumb(area.label(), null));
        String body = PublicHtml.breadcrumb(crumbs)
            + "<section class='card'><span class='chip'>생활권</span><h1>" + esc(area.label()) + " 청약 일정</h1><p class='muted'>" + esc(area.description().split(" · ")[0])
            + "의 청약 공고를 주소·단지명 기준으로 모았어요. 분양가·경쟁률·당첨가점과 동네 실거래가는 캘린더에서 공고를 눌러 확인하세요.</p>"
            + "<a class='cta' data-app href='" + esc(app) + "'>" + esc(area.label()) + " 청약 캘린더 열기</a>"
            + AreaCalendar.subscribeLinks(domain, area) + "</section>"
            // 진행·예정이 모두 없으면 빈 카드를 두 번 보여주지 않고 한 번만 안내한다.
            + (groups.open().isEmpty() && groups.upcoming().isEmpty()
                ? "<section class='card'><h2>접수 중·예정 공고</h2><p class='empty'>지금 " + esc(area.label()) + "에서 접수 중이거나 예정된 공고가 없어요. "
                    + (groups.closed().isEmpty() ? "" : "최근 6개월 동안 " + groups.closed().size() + (groups.closed().size() >= 12 ? "건 이상이" : "건이") + " 마감됐고, 공개된 경쟁률·당첨가점은 아래 공고에서 확인할 수 있어요. ") + "새 공고는 매일 반영됩니다.</p></section>"
                : (groups.open().isEmpty() ? "" : section("접수 중", groups.open(), "")) + (groups.upcoming().isEmpty() ? "" : section("접수 예정", groups.upcoming(), "")))
            + section("최근 마감", groups.closed(), "최근 6개월 안에 마감된 공고가 없습니다.")
            + scoreSection(area)
            + "<section class='card'><h2>다른 생활권</h2><div class='chips'>" + others + "</div></section>" + PublicHtml.explore(domain, "")
            + "<small>출처: 청약홈 공개 데이터. 생활권은 행정 경계가 아니며, 주소·단지명 기준으로 분류해 일부 공고가 빠지거나 포함될 수 있습니다.</small>";
        List<PublicHtml.Crumb> items = new ArrayList<>();
        Stream.of(groups.open(), groups.upcoming(), groups.closed()).flatMap(List::stream)
            .forEach(item -> items.add(new PublicHtml.Crumb(item.source().houseNm(), domain + "/announcements/" + item.id())));
        return PublicHtml.page(domain, "", title, description, domain + "/areas/" + area.slug(), body,
            PublicHtml.breadcrumbLd(crumbs, domain + "/areas/" + area.slug()) + PublicHtml.itemListLd(area.label() + " 청약 공고", items));
    }
    /** 접수 중·예정이 없는 생활권의 검색 설명: 최근 마감 건수와 가장 최근 공고. */
    static String quietSummary(LivingAreas.Area area, Groups groups) {
        if (groups.closed().isEmpty()) return "최근 6개월 공고 없음 · 새 공고는 매일 반영";
        Item last = groups.closed().get(0);
        // 최근 마감 목록은 12건까지만 받으므로 꽉 차면 '이상'으로 쓴다.
        return "최근 6개월 마감 " + groups.closed().size() + (groups.closed().size() >= 12 ? "건 이상" : "건") + " · 최근 " + last.source().houseNm() + " " + PublicHtml.shortDate(last.start()) + " 접수 · 경쟁률·당첨가점 결과 확인";
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
            out.append("<a href='").append(esc(domain + "/announcements/" + item.id())).append("'><strong><span class='when'>").append(esc(range))
                .append("</span>").append(esc(item.houseNm())).append("</strong><span class='muted'>").append(esc(item.receiptDate().substring(0, 7).replace('-', '.'))).append(" 접수</span></a>");
        }
        return out.append("<div class='row' style='margin-top:12px'><a class='cta' data-app href='").append(esc(domain + "/score")).append("'>내 가점 계산하고 비교하기</a>")
            .append("<a class='cta secondary' data-app href='").append(esc(domain + "/match")).append("'>내가 넣을 수 있는 청약 찾기</a></div></section>").toString();
    }
    static String score(double value) { return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value); }

    /** 공고 목록 카드 하나(배지·단지명·유형·접수 기간·주소). */
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

}
