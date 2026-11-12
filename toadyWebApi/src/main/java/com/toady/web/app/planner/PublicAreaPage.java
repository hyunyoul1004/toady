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
    public PublicAreaPage(AnnouncementSearchService search, @Value("${toady.web.domain}") String domain) {
        this.search = search; this.domain = domain.replaceAll("/+$", "");
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

    /** 생활권·무순위 등 공개 목록이 같은 접수 상태 기준(접수 중/예정/마감)을 쓰도록 한곳에서 나눈다. */
    Groups groups(String keyword, List<String> types, LocalDate today, int pastDays) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setKeyword(keyword);
        query.setStartDate(today.minusDays(pastDays));
        query.setEndDate(today.plusDays(FUTURE_DAYS));
        query.setSize(100);
        return classify(search.searchAll(query, types).getContent(), today);
    }
    static Groups classify(List<AnnouncementListItem> sources, LocalDate today) {
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
        return new Groups(open, upcoming, closed.size() > 12 ? closed.subList(0, 12) : closed);
    }

    @GetMapping(value = "/api/v1/public/areas", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> index() {
        StringBuilder links = new StringBuilder();
        for (var area : LivingAreas.ALL) links.append("<a href='").append(esc(domain + "/areas/" + area.slug())).append("'><strong>")
            .append(esc(area.label())).append(" 청약</strong><span class='muted'>").append(esc(area.description())).append("</span></a>");
        String title = "생활권별 청약 일정 · TOADY";
        String description = "운정·동탄·다산·검단·위례·광교 등 생활권 이름으로 아파트 청약 일정과 분양가, 경쟁률을 찾아보세요.";
        return html(title, description, domain + "/areas",
            "<nav><a href='" + esc(domain) + "/'>TOADY</a><span>›</span><span>생활권</span></nav><section class='card'><h1>생활권별 청약 일정</h1><p class='muted'>"
                + esc(description) + "</p></section><section class='card list'>" + links + "</section>");
    }

    @GetMapping(value = "/api/v1/public/areas/{slug}", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> page(@PathVariable String slug) {
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
        String body = "<nav><a href='" + esc(domain) + "/'>TOADY</a><span>›</span><a href='" + esc(domain) + "/areas'>생활권</a><span>›</span><span>" + esc(area.label()) + "</span></nav>"
            + "<section class='card'><span class='chip'>생활권</span><h1>" + esc(area.label()) + " 청약 일정</h1><p class='muted'>" + esc(area.description().split(" · ")[0])
            + "의 청약 공고를 주소·단지명 기준으로 모았어요. 분양가·경쟁률·당첨가점과 동네 실거래가는 캘린더에서 공고를 눌러 확인하세요.</p>"
            + "<a class='cta' data-app href='" + esc(app) + "'>" + esc(area.label()) + " 청약 캘린더 열기</a>"
            + AreaCalendar.subscribeLinks(domain, area) + "</section>"
            // 진행·예정이 모두 없으면 빈 카드를 두 번 보여주지 않고 한 번만 안내한다.
            + (groups.open().isEmpty() && groups.upcoming().isEmpty()
                ? "<section class='card'><h2>접수 중·예정 공고</h2><p class='empty'>지금 " + esc(area.label()) + "에서 접수 중이거나 예정된 공고가 없어요. 새 공고는 매일 반영됩니다.</p></section>"
                : (groups.open().isEmpty() ? "" : section("접수 중", groups.open(), "")) + (groups.upcoming().isEmpty() ? "" : section("접수 예정", groups.upcoming(), "")))
            + section("최근 마감", groups.closed(), "최근 6개월 안에 마감된 공고가 없습니다.")
            + "<section class='card'><h2>다른 생활권</h2><div class='chips'>" + others + "</div></section>" + PublicHtml.explore(domain, "")
            + "<small>출처: 청약홈 공개 데이터. 생활권은 행정 경계가 아니며, 주소·단지명 기준으로 분류해 일부 공고가 빠지거나 포함될 수 있습니다.</small>";
        return html(title, description, domain + "/areas/" + area.slug(), body);
    }

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

    private ResponseEntity<String> html(String title, String description, String canonical, String body) {
        return PublicHtml.page(title, description, canonical, body);
    }
}
