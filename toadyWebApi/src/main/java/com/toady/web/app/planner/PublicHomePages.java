package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.*;
import static com.toady.web.app.planner.PublicHtml.esc;

/**
 * 검색 봇용 홈(/)·가점 계산기(/score)·맞춤 청약(/match) 서버 HTML. 사람은 같은 주소에서 앱 화면을 본다.
 * 웹 컨테이너 nginx(toadyReactWeb/nginx.conf)가 User-Agent로 봇을 가려 봇만 /api/v1/public/... 로 보내고, 사람에게는 React 앱(SPA)을 준다.
 * 앱 화면은 자바스크립트로 그려지므로 JS를 실행하지 않는 봇(네이버·다음)에게는 홈과 같은 기본 HTML만 보였다.
 * 주소마다 고유한 제목·설명·대표 주소와 실제 공고 링크를 준다.
 */
// @RestController: 여기서는 JSON이 아니라 완성된 HTML 문자열을 응답한다(produces = text/html).
@RestController
public class PublicHomePages {
    static final int MATCH_ITEMS = 20, SCORE_ITEMS = 10;
    private final PublicCollectionPages collections; private final PublicAreaPage areas; private final PublicAnnouncementIndex index;
    private final ScoreBenchmarks benchmarks; private final String domain;
    public PublicHomePages(PublicCollectionPages collections, PublicAreaPage areas, PublicAnnouncementIndex index, ScoreBenchmarks benchmarks,
                           @Value("${toady.web.domain}") String domain) {
        this.collections = collections; this.areas = areas; this.index = index; this.benchmarks = benchmarks; this.domain = domain.replaceAll("/+$", "");
    }

    // 렌더링 결과는 PublicHtml.cached()가 잠시 메모리에 들고 있어 봇이 자주 와도 매번 DB를 읽지 않는다.
    @GetMapping(value = "/api/v1/public/home", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> home() { return PublicHtml.cached("home", this::renderHome); }
    // 이번 주 접수·발표, 다음 주 접수, 지역 칩을 HTML로 만들고 검색엔진용 구조화 데이터(JSON-LD: WebSite, ItemList)를 붙인다.
    private ResponseEntity<String> renderHome() {
        LocalDate today = index.today();
        PublicCollectionPages.Weekly week = collections.weeklyData(today);
        StringBuilder winners = new StringBuilder("<section class='card list'><h2>이번 주 당첨자 발표 <span class='muted'>").append(week.winners().size()).append("건</span></h2>");
        if (week.winners().isEmpty()) winners.append("<p class='empty'>이번 주 발표 예정인 아파트 공고가 없어요.</p>");
        for (var winner : week.winners()) winners.append("<a href='").append(esc(domain + "/announcements/" + winner.id())).append("'><strong><span class='when'>")
            .append(esc(PublicHtml.shortDate(winner.date(), today))).append("</span>").append(esc(winner.houseNm())).append("</strong><span class='muted'>").append(esc(winner.address())).append("</span></a>");
        winners.append("</section>");
        String body = "<section class='card'><span class='chip'>" + esc(week.week().label()) + "</span><h1>TOADY 두껍아 · 청약 일정과 분양 정보를 한 화면에서</h1>"
            + "<p class='muted'>청약홈 공고 일정, 분양가, 경쟁률·당첨가점, 위치와 동네 실거래가를 한곳에서 확인하는 청약 정보 서비스예요. 이번 주 " + week.status().summary()
            + ", 당첨자 발표 " + week.winners().size() + "건이 있어요.</p>"
            + "<div class='row'><a class='cta' data-app href='" + esc(domain + "/") + "'>청약 캘린더 열기</a><a class='cta secondary' data-app href='" + esc(domain + "/match") + "'>내가 넣을 수 있는 청약 찾기</a></div></section>"
            + areas.section(PublicCollectionPages.WEEK_SECTION, week.current(), "이번 주 접수하는 공고가 없어요.")
            + winners
            + areas.section("다음 주 접수 시작 (" + week.next().label() + ")", week.nextWeek(), "다음 주에 접수를 시작하는 공고가 아직 없어요.")
            + regionChips(today)
            + PublicHtml.explore(domain, "")
            + "<small>출처: 청약홈 공개 데이터. 일정·가격·신청 자격은 공식 모집공고를 함께 확인해 주세요.</small>";
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("@type", "SearchAction"); action.put("target", domain + "/?search={search_term_string}"); action.put("query-input", "required name=search_term_string");
        Map<String, Object> site = new LinkedHashMap<>();
        site.put("@context", "https://schema.org"); site.put("@type", "WebSite"); site.put("name", "TOADY 두껍아"); site.put("url", domain + "/");
        site.put("description", "전국 아파트, 오피스텔, 무순위(줍줍) 청약 일정, 분양가, 경쟁률, 당첨가점 및 청약 커뮤니티"); site.put("potentialAction", action);
        List<PublicHtml.Crumb> items = new ArrayList<>();
        for (var item : week.current()) items.add(new PublicHtml.Crumb(item.source().houseNm(), domain + "/announcements/" + item.id()));
        return PublicHtml.page(domain, "/", "TOADY 두껍아 - 청약 일정, 분양 정보 및 부동산 커뮤니티",
            "이번 주 청약 접수 중·예정 " + week.status().active() + "건·당첨자 발표 " + week.winners().size() + "건 · 전국 아파트, 오피스텔, 무순위(줍줍) 청약 일정, 분양가, 경쟁률, 당첨가점을 한 화면에서 확인하세요.",
            domain + "/", body, PublicHtml.jsonLd(site) + PublicHtml.itemListLd("이번 주 청약 접수 일정", items));
    }
    /** 접수 중·예정 공고가 있는 시·도(많은 순)와 그 안의 시·군·구 몇 곳. 지역 공개 페이지로 잇는다. */
    String regionChips(LocalDate today) {
        List<PublicAnnouncementIndex.Entry> all;
        // 지역별 청약 페이지와 같이 목록에 보이는 공고(Entry.listed)만 쓴다.
        try { all = index.recent().stream().filter(e -> e.listed(today)).toList(); } catch (RuntimeException unavailable) { return ""; }
        var bySido = PublicRegionPages.bySido(all);
        StringBuilder out = new StringBuilder("<section class='card'><h2>지역별 청약</h2><div class='chips'>");
        bySido.entrySet().stream().filter(e -> !e.getValue().isEmpty())
            .sorted(Comparator.comparingLong((Map.Entry<Regions.Sido, List<PublicAnnouncementIndex.Entry>> e) -> -e.getValue().stream().filter(x -> x.active(today)).count())
                .thenComparingInt(e -> Regions.ALL.indexOf(e.getKey())))
            .forEach(e -> out.append("<a class='chip' href='").append(esc(domain + e.getKey().path())).append("'>").append(esc(e.getKey().shortName())).append("</a>"));
        out.append("</div><div class='chips' style='margin-top:10px'>");
        bySido.forEach((sido, entries) -> PublicRegionPages.districts(sido, entries).stream()
            .filter(group -> group.entries().stream().anyMatch(x -> x.active(today))).limit(3)
            .forEach(group -> out.append("<a class='chip' href='").append(esc(domain + group.region().path())).append("'>").append(esc(group.region().shortLabel())).append("</a>")));
        return out.append("</div><a class='cta secondary' href='").append(esc(domain + "/regions")).append("'>지역별 청약 전체 보기</a></section>").toString();
    }

    @GetMapping(value = "/api/v1/public/score", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> score() { return PublicHtml.cached("score", this::renderScore); }
    private ResponseEntity<String> renderScore() {
        StringBuilder recent = new StringBuilder();
        try {
            List<ScoreBenchmarks.Item> items = benchmarks.all().stream().limit(SCORE_ITEMS).toList();
            if (!items.isEmpty()) {
                recent.append("<section class='card list'><h2>최근 공개 당첨가점</h2>");
                for (var item : items) recent.append("<a href='").append(esc(domain + "/announcements/" + item.id())).append("'><strong><span class='when'>")
                    .append(esc(PublicAnnouncementPage.scoreRange(item.low() == null ? "" : PublicAreaPage.score(item.low()), item.high() == null ? "" : PublicAreaPage.score(item.high()))))
                    .append("</span>").append(esc(item.houseNm())).append("</strong><span class='muted'>").append(esc(item.receiptDate().substring(0, 7).replace('-', '.') + " 접수 · " + item.address())).append("</span></a>");
                recent.append("</section>");
            }
        } catch (RuntimeException unavailable) { recent.setLength(0); }
        String body = PublicHtml.breadcrumb(List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("청약 가점 계산기", null)))
            + "<section class='card'><span class='chip'>84점 만점</span><h1>청약 가점 계산기</h1>"
            + "<p class='muted'>민영주택 가점제는 무주택 기간, 부양가족 수, 청약통장 가입 기간 세 항목을 더해 84점 만점으로 계산해요. 내 점수를 계산하고 최근 공개된 아파트 당첨가점과 비교해 보세요.</p>"
            + "<a class='cta' data-app href='" + esc(domain + "/score") + "'>내 청약 가점 계산하기</a></section>"
            + "<section class='card'><h2>가점 항목</h2><ul>"
            + "<li><strong>무주택 기간 (최대 32점)</strong><span class='muted'>1년 미만 2점, 1년마다 2점씩 더해 15년 이상 32점</span></li>"
            + "<li><strong>부양가족 수 (최대 35점)</strong><span class='muted'>0명 5점, 1명마다 5점씩 더해 6명 이상 35점</span></li>"
            + "<li><strong>청약통장 가입 기간 (최대 17점)</strong><span class='muted'>6개월 미만 1점, 1년 미만 2점, 이후 1년마다 1점씩 더해 15년 이상 17점</span></li></ul>"
            + "<p class='muted' style='margin-top:10px'>무주택 기간 산정 기준일, 부양가족 인정 범위는 공고·제도에 따라 달라질 수 있으니 모집공고를 함께 확인해 주세요.</p></section>"
            + recent + PublicHtml.explore(domain, "/score")
            + "<small>출처: 청약홈 공개 당첨가점. 가점 계산은 참고용이며 실제 당첨 여부와 다를 수 있어요.</small>";
        return PublicHtml.page(domain, "/score", "청약 가점 계산기 · 84점 만점 내 가점 계산과 당첨가점 비교 · TOADY",
            "무주택 기간(32점)·부양가족 수(35점)·청약통장 가입 기간(17점)으로 내 청약 가점을 계산하고, 최근 공개된 아파트 당첨가점과 비교해 보세요.",
            domain + "/score", body, PublicHtml.breadcrumbLd(List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("청약 가점 계산기", null)), domain + "/score"));
    }

    @GetMapping(value = "/api/v1/public/match", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> match() { return PublicHtml.cached("match", this::renderMatch); }
    private ResponseEntity<String> renderMatch() {
        LocalDate today = index.today();
        List<PublicAnnouncementIndex.Entry> active;
        try {
            active = PublicAnnouncementIndex.ordered(index.recent().stream().filter(e -> e.active(today))
                .filter(e -> e.type().equals("apt") || PublicCollectionPages.UNRANKED_TYPES.contains(e.type())).toList(), today);
        } catch (RuntimeException unavailable) { active = List.of(); }
        StringBuilder list = new StringBuilder("<section class='card list'><h2>지금 접수 중·예정인 아파트·무순위 공고 <span class='muted'>").append(active.size()).append("건</span></h2>");
        if (active.isEmpty()) list.append("<p class='empty'>지금 접수 중이거나 예정된 공고가 없어요. 새 공고는 매일 반영됩니다.</p>");
        for (var entry : active.stream().limit(MATCH_ITEMS).toList()) list.append(PublicRegionPages.item(domain, entry, today));
        list.append("</section>");
        List<PublicHtml.Crumb> crumbs = List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("내게 맞는 청약", null));
        String body = PublicHtml.breadcrumb(crumbs)
            + "<section class='card'><span class='chip'>자격 진단</span><h1>내게 맞는 청약 찾기</h1>"
            + "<p class='muted'>나이·혼인·무주택 여부, 청약통장 가입 기간, 거주 지역을 고르면 지금 접수 중·예정인 아파트와 무순위·줍줍 공고 중 신청할 수 있는 공고와 특별공급 유형을 골라 드려요.</p>"
            + "<a class='cta' data-app href='" + esc(domain + "/match") + "'>내 조건으로 청약 찾기</a></section>"
            + list
            + "<section class='card'><h2>함께 보기</h2><div class='chips'><a class='chip' href='" + esc(domain + "/zupzup") + "'>무순위·줍줍 청약</a><a class='chip' href='"
            + esc(domain + "/regions") + "'>지역별 청약</a><a class='chip' href='" + esc(domain + "/help/faq") + "'>1순위·특별공급 자격 질문</a></div></section>"
            + PublicHtml.explore(domain, "/match")
            + "<small>출처: 청약홈 공개 데이터. 자격 진단은 참고용이며 최종 자격은 모집공고와 청약홈에서 확인해 주세요.</small>";
        return PublicHtml.page(domain, "/match", "내게 맞는 청약 찾기 · 지금 넣을 수 있는 아파트·무순위 공고 · TOADY",
            "나이·무주택·청약통장·거주 지역 조건으로 지금 접수 중·예정인 아파트·무순위 공고(" + active.size() + "건) 중 신청할 수 있는 공고와 특별공급 유형을 찾아보세요.",
            domain + "/match", body, PublicHtml.breadcrumbLd(crumbs, domain + "/match"));
    }
}
