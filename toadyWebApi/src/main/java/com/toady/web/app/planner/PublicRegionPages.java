package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import static com.toady.web.app.planner.PublicHtml.esc;

/**
 * 지역별 청약 공개 페이지: /regions(전체), /regions/{시·도}, /regions/{시·도}/{시·군·구}.
 * 사이트맵과 같은 공고 목록(최근 1년 + 앞으로)을 공급 주소의 시·도/시·군·구로 묶어, 검색 봇이 모든 공고 페이지에 링크로 닿게 한다.
 * 봇에게만 서버 HTML을 주고, 사람에게는 같은 주소에서 앱이 그 지역으로 거른 청약 캘린더를 연다.
 * (웹 컨테이너 nginx가 User-Agent로 봇을 가려 /api/v1/public/regions... 로 넘긴다.) 공고 목록은 PublicAnnouncementIndex에서 받는다.
 */
// @RestController: 완성된 HTML 문자열을 응답한다(produces = text/html).
@RestController
public class PublicRegionPages {
    static final int PAGE_SIZE = 20;
    private final PublicAnnouncementIndex index; private final String domain;
    public PublicRegionPages(PublicAnnouncementIndex index, @Value("${toady.web.domain}") String domain) {
        this.index = index; this.domain = domain.replaceAll("/+$", "");
    }

    /** 지역 한 곳의 공고 묶음. */
    record Group(Regions.Region region, List<PublicAnnouncementIndex.Entry> entries) { }

    /** 시·도 → 그 시·도의 공고. 시·도 순서는 앱 지역 목록과 같다. */
    static Map<Regions.Sido, List<PublicAnnouncementIndex.Entry>> bySido(List<PublicAnnouncementIndex.Entry> all) {
        Map<Regions.Sido, List<PublicAnnouncementIndex.Entry>> out = new LinkedHashMap<>();
        for (Regions.Sido sido : Regions.ALL) out.put(sido, new ArrayList<>());
        for (var entry : all) if (entry.region() != null) out.get(entry.region().sido()).add(entry);
        return out;
    }
    /** 시·도 안의 시·군·구별 공고(공고가 많은 순, 같으면 이름순). */
    static List<Group> districts(Regions.Sido sido, List<PublicAnnouncementIndex.Entry> entries) {
        Map<String, List<PublicAnnouncementIndex.Entry>> grouped = entries.stream().filter(e -> e.region().district() != null)
            .collect(Collectors.groupingBy(e -> e.region().district(), TreeMap::new, Collectors.toList()));
        return grouped.entrySet().stream().map(e -> new Group(new Regions.Region(sido, e.getKey()), e.getValue()))
            .sorted(Comparator.comparingInt((Group g) -> -g.entries().size()).thenComparing(g -> g.region().district())).toList();
    }

    // 전체 지역 목록 페이지. 결과는 PublicHtml.cached()로 잠시 메모리에 둔다.
    @GetMapping(value = "/api/v1/public/regions", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> index() { return PublicHtml.cached("regions", this::renderIndex); }
    private ResponseEntity<String> renderIndex() {
        LocalDate today = index.today();
        // 목록에 보이는 공고(Entry.listed)만 센다. 180일보다 먼 예정 공고는 건수·칩에서도 뺀다.
        var all = index.recent().stream().filter(e -> e.listed(today)).toList();
        var sidos = bySido(all);
        int active = (int) all.stream().filter(e -> e.active(today)).count();
        StringBuilder body = new StringBuilder(PublicHtml.breadcrumb(List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("지역별 청약", null))))
            .append("<section class='card'><span class='chip'>접수 중·예정 ").append(active).append("건 · 매일 업데이트</span><h1>지역별 청약 일정</h1><p class='muted'>지금 접수 중이거나 곧 접수하는 공고(앞으로 180일)가 있는 지역과 최근 1년 동안 나온 청약 공고를 시·도와 시·군·구별로 모았어요. ")
            .append("지역 옆 숫자는 접수 중·예정 건수예요.</p></section>");
        List<PublicHtml.Crumb> listed = new ArrayList<>();
        for (var sido : sidos.entrySet()) {
            if (sido.getValue().isEmpty()) continue;
            listed.add(new PublicHtml.Crumb(sido.getKey().name() + " 청약", domain + sido.getKey().path()));
            // 앱 지역별 청약 카드와 같은 숫자(접수 중·예정)를 앞에 두고, 최근 1년 건수는 이름을 붙여 따로 적는다.
            body.append("<section class='card'><h2><a href='").append(esc(domain + sido.getKey().path())).append("'>").append(esc(sido.getKey().name())).append(" 청약</a> <span class='muted'>")
                .append(esc(activeCounts(sido.getValue(), today))).append(" · 최근 1년 ").append(sido.getValue().size()).append("건</span></h2>")
                .append(districtChips(sido.getKey(), sido.getValue(), today)).append("</section>");
        }
        if (listed.isEmpty()) body.append("<section class='card'><p class='empty'>최근 1년 안에 나온 공고가 없어요.</p></section>");
        body.append(livingAreas()).append(PublicHtml.explore(domain, "/regions"))
            .append("<small>출처: 청약홈 공개 데이터. 지역은 공급 위치 주소의 시·도/시·군·구 기준이에요.</small>");
        String title = "지역별 청약 일정 · 시·도·시·군·구별 아파트 분양 공고 · TOADY";
        String description = "서울·경기·인천 등 시·도와 시·군·구별로 최근 1년 청약 공고와 접수 중·예정 공고(" + active + "건)를 모았어요. 분양가·경쟁률·당첨가점을 지역별로 확인하세요.";
        String head = PublicHtml.breadcrumbLd(List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("지역별 청약", null)), domain + "/regions")
            + PublicHtml.itemListLd("지역별 청약", listed);
        return PublicHtml.page(domain, "", title, description, domain + "/regions", body.toString(), head);
    }

    // @PathVariable: 주소의 {sido}(예: 경기 또는 경기도). @RequestParam(required=false): ?page=2 같은 쪽 번호(없으면 1쪽).
    @GetMapping(value = "/api/v1/public/regions/{sido}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> sido(@PathVariable String sido, @RequestParam(required = false) String page) {
        var found = Regions.bySegment(sido);
        int number = page(page);
        if (found.isEmpty() || number < 1) return notFound();
        return PublicHtml.cached("regions/" + found.get().shortName() + "?" + number, () -> render(new Regions.Region(found.get(), null), number));
    }
    @GetMapping(value = "/api/v1/public/regions/{sido}/{district}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> district(@PathVariable String sido, @PathVariable String district, @RequestParam(required = false) String page) {
        var found = Regions.bySegment(sido);
        int number = page(page);
        // 그 시·도의 실제 시·군·구 이름만 받는다(검단신도시·평택고덕국제화지구 같은 지구 이름은 404).
        var name = found.flatMap(value -> Regions.district(value, district));
        if (name.isEmpty() || number < 1) return notFound();
        return PublicHtml.cached("regions/" + found.get().shortName() + "/" + name.get() + "?" + number, () -> render(new Regions.Region(found.get(), name.get()), number));
    }
    /** 쪽 번호: 없으면 1, 숫자가 아니거나 범위를 벗어나면 0(404). */
    static int page(String value) {
        if (value == null || value.isEmpty()) return 1;
        return value.matches("[1-9][0-9]{0,2}") ? Integer.parseInt(value) : 0;
    }

    // 지역 한 곳의 페이지: 접수 중·예정은 1쪽에만, 지난 공고는 20건씩 쪽을 나눈다. 쪽마다 canonical·prev/next 링크를 단다.
    private ResponseEntity<String> render(Regions.Region region, int page) {
        LocalDate today = index.today();
        List<PublicAnnouncementIndex.Entry> entries = index.recent().stream().filter(e -> e.listed(today) && e.region() != null && e.region().sido().equals(region.sido())
            && (region.district() == null || region.district().equals(e.region().district()))).toList();
        // 시·도는 공고가 없어도 페이지를 둔다(지역 목록 고정). 다만 내용이 빈 페이지가 색인되지 않게 noindex를 붙이고 사이트맵에서도 뺀다.
        // 공고가 없는 시·군·구는 알 수 없는 주소로 본다.
        if (entries.isEmpty() && region.district() != null) return notFound();
        List<PublicAnnouncementIndex.Entry> ordered = PublicAnnouncementIndex.ordered(entries, today);
        List<PublicAnnouncementIndex.Entry> active = ordered.stream().filter(e -> e.active(today)).toList();
        // 180일보다 먼 접수 예정 공고는 접수 중·예정 건수(ReceiptWindow)에도, '지난 공고'에도 넣지 않는다.
        List<PublicAnnouncementIndex.Entry> past = ordered.stream().filter(e -> e.status(today) == PublicAnnouncementIndex.Status.CLOSED).toList();
        int pages = Math.max(1, (past.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page > pages) return notFound();
        List<PublicAnnouncementIndex.Entry> pastPage = past.subList((page - 1) * PAGE_SIZE, Math.min(past.size(), page * PAGE_SIZE));
        long open = active.stream().filter(e -> e.status(today) == PublicAnnouncementIndex.Status.OPEN).count();
        String label = region.label(), name = region.district() == null ? region.sido().name() : region.district();
        String canonical = domain + region.path() + (page > 1 ? "?page=" + page : "");

        List<PublicHtml.Crumb> crumbs = new ArrayList<>(List.of(new PublicHtml.Crumb("TOADY", domain + "/"), new PublicHtml.Crumb("지역별 청약", domain + "/regions")));
        if (region.district() != null) crumbs.add(new PublicHtml.Crumb(region.sido().name(), domain + region.sido().path()));
        crumbs.add(new PublicHtml.Crumb(name, null));

        StringBuilder body = new StringBuilder(PublicHtml.breadcrumb(crumbs))
            .append("<section class='card'><span class='chip'>").append(esc(region.district() == null ? "시·도" : region.sido().shortName() + " · 시·군·구")).append("</span><h1>")
            .append(esc(label)).append(" 청약 일정").append(page > 1 ? " · " + page + "쪽" : "").append("</h1><p class='muted'>")
            .append(esc(label)).append("에서 최근 1년 동안 나온 청약 공고와 접수 중·예정 공고를 공급 주소 기준으로 모았어요. 분양가·경쟁률·당첨가점은 공고를 눌러 확인하세요.</p>")
            .append("<div class='stats'><div><strong>").append(open).append("</strong><span>접수 중</span></div><div><strong>").append(active.size() - open)
            .append("</strong><span>접수 예정</span></div><div><strong>").append(entries.size()).append("</strong><span>최근 1년 공고</span></div></div>")
            .append("<a class='cta' data-app href='").append(esc(domain + "/?search=" + Regions.segment(label))).append("'>").append(esc(label)).append(" 청약 캘린더 열기</a></section>");
        if (region.district() == null) {
            if (!districts(region.sido(), entries).isEmpty())
                body.append("<section class='card'><h2>시·군·구별 청약</h2><p class='muted'>숫자는 접수 중·예정 건수예요.</p>").append(districtChips(region.sido(), entries, today)).append("</section>");
        }
        if (page == 1) {
            body.append(list("접수 중·예정", active, "지금 " + label + "에서 접수 중이거나 예정된 공고가 없어요. 새 공고는 매일 반영됩니다.", today));
        }
        body.append(list(page == 1 ? "지난 공고" : "지난 공고 " + page + "쪽", pastPage, "최근 1년 안에 마감된 공고가 없어요.", today));
        if (pages > 1) {
            body.append("<nav class='pager card' aria-label='쪽 이동'>")
                .append(page > 1 ? "<a rel='prev' href='" + esc(domain + region.path() + (page == 2 ? "" : "?page=" + (page - 1))) + "'>← 최근 공고</a>" : "<span></span>")
                .append("<span class='muted'>").append(page).append(" / ").append(pages).append("</span>")
                .append(page < pages ? "<a rel='next' href='" + esc(domain + region.path() + "?page=" + (page + 1)) + "'>이전 공고 →</a>" : "<span></span>")
                .append("</nav>");
        }
        if (region.district() != null) body.append("<section class='card'><h2>").append(esc(region.sido().name())).append("의 다른 지역</h2><a class='cta secondary' href='")
            .append(esc(domain + region.sido().path())).append("'>").append(esc(region.sido().name())).append(" 청약 전체 보기</a></section>");
        body.append(livingAreas()).append(PublicHtml.explore(domain, ""))
            .append("<small>출처: 청약홈 공개 데이터. 지역은 공급 위치 주소의 시·도/시·군·구 기준이며 주소가 불분명한 공고는 빠질 수 있어요.</small>");

        String title = label + " 청약 일정·분양 공고" + (active.isEmpty() ? "" : " · 접수 중·예정 " + active.size() + "건") + (page > 1 ? " · " + page + "쪽" : "") + " · TOADY";
        String description = label + " 아파트·오피스텔·무순위 청약 공고 · " + (active.isEmpty()
            ? "지금 접수 중인 공고는 없고 최근 1년 공고 " + entries.size() + "건" + (past.isEmpty() ? "" : " · 최근 " + past.get(0).houseNm() + " " + PublicHtml.shortDate(past.get(0).sortDate(), today) + " 접수")
            : "접수 중 " + open + "건, 접수 예정 " + (active.size() - open) + "건 · 최근 1년 공고 " + entries.size() + "건")
            + " · 분양가·경쟁률·당첨가점을 확인하세요.";
        List<PublicHtml.Crumb> items = new ArrayList<>();
        for (var entry : page == 1 ? concat(active, pastPage) : pastPage) items.add(new PublicHtml.Crumb(entry.houseNm(), domain + entry.path()));
        String head = (entries.isEmpty() ? "<meta name='robots' content='noindex'>" : "") + PublicHtml.breadcrumbLd(crumbs, canonical) + PublicHtml.itemListLd(label + " 청약 공고", items)
            + (page > 1 ? "<link rel='prev' href='" + esc(domain + region.path() + (page == 2 ? "" : "?page=" + (page - 1))) + "'>" : "")
            + (page < pages ? "<link rel='next' href='" + esc(domain + region.path() + "?page=" + (page + 1)) + "'>" : "");
        return PublicHtml.page(domain, "", title, description, canonical, body.toString(), head);
    }
    /** '접수 중 3 · 예정 6'(앱 지역별 청약 카드와 같은 숫자·문구). */
    static String activeCounts(List<PublicAnnouncementIndex.Entry> entries, LocalDate today) {
        long open = entries.stream().filter(e -> e.status(today) == PublicAnnouncementIndex.Status.OPEN).count();
        long active = entries.stream().filter(e -> e.active(today)).count();
        return "접수 중 " + open + " · 예정 " + (active - open);
    }
    /**
     * 시·군·구 칩. 숫자는 앱 칩과 같은 접수 중·예정 건수이고, 지난 공고만 있는 곳은 숫자 없이 이름만 둔다(검색 봇이 지난 공고 페이지에도 닿게).
     * 순서는 공고가 많은 순(districts)이다.
     */
    private String districtChips(Regions.Sido sido, List<PublicAnnouncementIndex.Entry> entries, LocalDate today) {
        StringBuilder out = new StringBuilder("<div class='chips'>");
        for (Group district : districts(sido, entries)) {
            long active = district.entries().stream().filter(e -> e.active(today)).count();
            out.append("<a class='chip' href='").append(esc(domain + district.region().path())).append("'>").append(esc(district.region().district()))
                .append(active > 0 ? " " + active : "").append("</a>");
        }
        return out.append("</div>").toString();
    }
    private static <T> List<T> concat(List<T> a, List<T> b) { List<T> out = new ArrayList<>(a); out.addAll(b); return out; }

    private String list(String heading, List<PublicAnnouncementIndex.Entry> entries, String empty, LocalDate today) {
        StringBuilder out = new StringBuilder("<section class='card list'><h2>").append(esc(heading)).append(" <span class='muted'>").append(entries.size()).append("건</span></h2>");
        if (entries.isEmpty()) return out.append("<p class='empty'>").append(esc(empty)).append("</p></section>").toString();
        for (var entry : entries) out.append(item(domain, entry, today));
        return out.append("</section>").toString();
    }
    /** 공고 한 줄: 상태 배지·단지명·유형·접수 기간·주소. 공고 페이지의 '같은 지역 다른 공고'도 같은 모양을 쓴다. */
    static String item(String domain, PublicAnnouncementIndex.Entry entry, LocalDate today) {
        String badge = switch (entry.status(today)) { case OPEN -> "<span class='badge open'>접수 중</span>"; case UPCOMING -> "<span class='badge'>예정</span>"; default -> "<span class='badge'>마감</span>"; };
        String period = entry.start() == null ? "모집공고 " + PublicHtml.shortDate(entry.announced(), today)
            : "접수 " + PublicHtml.shortDate(entry.start(), today) + (entry.end() == null || entry.end().equals(entry.start()) ? "" : " ~ " + PublicHtml.shortDate(entry.end(), today));
        String kind = entry.houseKind().isBlank() ? entry.typeLabel() : entry.typeLabel() + " · " + entry.houseKind();
        return "<a href='" + esc(domain + entry.path()) + "'><strong>" + badge + esc(entry.houseNm()) + "</strong><span class='muted'>" + esc(kind + " · " + period)
            + "</span><br><span class='muted'>" + esc(entry.address()) + "</span></a>";
    }
    private String livingAreas() {
        StringBuilder out = new StringBuilder("<section class='card'><h2>신도시 생활권</h2><div class='chips'>");
        for (var area : LivingAreas.all()) out.append("<a class='chip' href='").append(esc(domain + "/areas/" + area.slug())).append("'>").append(esc(area.label())).append("</a>");
        return out.append("</div></section>").toString();
    }
    private static ResponseEntity<String> notFound() {
        return ResponseEntity.status(404).header("Content-Type", "text/html;charset=UTF-8")
            .body("<!doctype html><html lang='ko'><meta charset='UTF-8'><meta name='robots' content='noindex'><title>지역을 찾을 수 없음 · TOADY</title><h1>지역을 찾을 수 없습니다.</h1><a href='/regions'>지역별 청약</a></html>");
    }
}
