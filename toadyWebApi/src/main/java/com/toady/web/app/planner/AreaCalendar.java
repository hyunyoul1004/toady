package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.toady.web.app.announcement.ReceiptWindow;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * 로그인 없이 생활권 단위로 구독하는 공개 캘린더. 같은 생활권 검색 기준의 접수 중·예정 공고만 담는다.
 * 개인 정보가 없어 주소를 공유해도 되며, 공고가 한꺼번에 들어오므로 알림은 붙이지 않는다.
 */
// @RestController: 이 클래스의 응답을 그대로 본문으로 내보낸다(여기서는 JSON이 아니라 .ics 텍스트).
@RestController
public class AreaCalendar {
    static final int MAX_ANNOUNCEMENTS = 40;
    private static final Duration CACHE = Duration.ofMinutes(30);
    private final PublicAreaPage areas; private final PlannerSummary summaries; private final String domain; private final Clock clock;
    /** 생활권별 피드를 30분 동안 메모리(Guava Cache)에 들고 있는다. 만료 순간 요청이 몰려도 같은 생활권은 한 번만 만든다. */
    private final Cache<String, String> cache = CacheBuilder.newBuilder()
            .maximumSize(50).expireAfterWrite(CACHE).build();
    /** 숨김 목록이 바뀌면 캐시를 비워 숨긴 공고가 바로 빠지게 한다. */
    // @EventListener: HiddenAnnouncements가 알리는 Changed 이벤트를 받는다.
    @org.springframework.context.event.EventListener(HiddenAnnouncements.Changed.class)
    void onHiddenChanged() { cache.invalidateAll(); }

    // @Autowired: 생성자가 둘이라 스프링이 쓸 생성자를 이것으로 지정한다. 아래 생성자는 테스트에서 시계를 바꿔 넣을 때 쓴다.
    @Autowired
    // @Value("${toady.web.domain}"): 서비스 주소(예: https://www.toady.co.kr)를 설정에서 읽는다.
    public AreaCalendar(PublicAreaPage areas, PlannerSummary summaries, @Value("${toady.web.domain}") String domain) {
        this(areas, summaries, domain, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    AreaCalendar(PublicAreaPage areas, PlannerSummary summaries, String domain, Clock clock) {
        this.areas = areas; this.summaries = summaries; this.domain = domain.replaceAll("/+$", ""); this.clock = clock;
    }

    // 사람에게 보여 주는 주소는 /calendar/areas/{slug}.ics이고, 웹 컨테이너 nginx(toadyReactWeb/nginx.conf)가 아래 /api/v1/public/... 로 넘긴다.
    static String feedUrl(String domain, LivingAreas.Area area) { return domain.replaceAll("/+$", "") + "/calendar/areas/" + area.slug() + ".ics"; }
    static String webcal(String url) { return url.replaceFirst("^https?://", "webcal://"); }
    static String googleUrl(String url) { return "https://calendar.google.com/calendar/r?cid=" + URLEncoder.encode(webcal(url), StandardCharsets.UTF_8); }

    /** 생활권 공개 페이지에 넣는 구독 버튼. 아이폰·맥은 webcal, 안드로이드·PC는 구글 캘린더로 연다. */
    static String subscribeLinks(String domain, LivingAreas.Area area) {
        String url = feedUrl(domain, area);
        return "<div class='row' style='margin-top:8px'><a class='cta secondary' href='" + PublicHtml.esc(webcal(url)) + "'>아이폰·맥 캘린더에 구독</a>"
            + "<a class='cta secondary' href='" + PublicHtml.esc(googleUrl(url)) + "' rel='noopener'>구글 캘린더에 구독</a></div>"
            + "<p class='muted' style='margin:8px 0 0'>로그인 없이 " + PublicHtml.esc(area.label()) + " 새 공고 일정이 내 캘린더에 자동으로 들어와요. 캘린더 앱에 따라 반영까지 몇 시간 걸릴 수 있어요.</p>";
    }

    // 캘린더 앱이 주기적으로 가져가는 구독 주소. @PathVariable: 주소의 {slug}를 받는다.
    // Cache-Control public 30분으로 nginx·브라우저도 캐시할 수 있게 한다.
    @GetMapping("/api/v1/public/areas/{slug}/calendar.ics")
    public ResponseEntity<String> feed(@PathVariable String slug) {
        var found = LivingAreas.bySlug(slug);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        String body = PublicHtml.load(cache, slug, () -> build(found.get(), clock.instant()));
        return ResponseEntity.ok().header("Content-Type", "text/calendar;charset=UTF-8")
            .header("Content-Disposition", "inline; filename=\"toady-" + slug + ".ics\"")
            .cacheControl(CacheControl.maxAge(CACHE).cachePublic()).body(body);
    }

    /** 생활권 공고 중 접수 중·예정과 최근 2주 안 마감 공고를 최대 40개까지 담아 알림 없는 피드를 만든다. */
    String build(LivingAreas.Area area, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul"));
        // 최근 2주 안에 마감된 공고도 발표·계약 일정이 남아 있어 함께 담는다.
        var groups = areas.groups(area.label(), null, today, 45);
        List<PlannerSummary.Summary> list = new ArrayList<>();
        Stream.of(groups.open(), groups.upcoming(), groups.closed()).flatMap(List::stream)
            .filter(item -> recent(item, today))
            .limit(MAX_ANNOUNCEMENTS).forEach(item -> {
                try { list.add(summaries.get(item.source().type(), item.source().houseManageNo(), item.source().pblancNo())); }
                catch (EntityNotFoundException | IllegalArgumentException ignored) { }
            });
        return CalendarIcs.build(list, domain, now, "TOADY " + area.label() + " 청약", area.label() + " 생활권 청약 일정 (TOADY, 청약홈 공개 데이터)", false);
    }

    /**
     * 캘린더에 담는지: 접수 중·예정이거나 마감 후 2주 이내. 마감일은 ReceiptWindow.lastDay(시작 후 400일 상한)로 본다.
     * 상시 접수(9999-12-31 등) 공고가 원래 마감일로는 끝없이 '최근 마감'으로 남아 자리를 차지하지 않게, 400일이 지나면 다른 화면처럼 끝난 공고로 친다.
     */
    static boolean recent(PublicAreaPage.Item item, LocalDate today) {
        if (item.status() != PublicAreaPage.Status.CLOSED) return true;
        LocalDate last = item.start() == null ? item.end() : ReceiptWindow.lastDay(item.start(), item.end());
        return last != null && !last.isBefore(today.minusDays(14));
    }
}
