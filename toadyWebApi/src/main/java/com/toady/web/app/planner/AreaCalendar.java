package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
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

/**
 * 로그인 없이 생활권 단위로 구독하는 공개 캘린더. 같은 생활권 검색 기준의 접수 중·예정 공고만 담는다.
 * 개인 정보가 없어 주소를 공유해도 되며, 공고가 한꺼번에 들어오므로 알림은 붙이지 않는다.
 */
@RestController
public class AreaCalendar {
    static final int MAX_ANNOUNCEMENTS = 40;
    private static final Duration CACHE = Duration.ofMinutes(30);
    private final PublicAreaPage areas; private final PlannerSummary summaries; private final String domain; private final Clock clock;
    /** 생활권별 피드. 만료 순간 요청이 몰려도 같은 생활권은 한 번만 만든다. */
    private final com.google.common.cache.Cache<String, String> cache = com.google.common.cache.CacheBuilder.newBuilder()
            .maximumSize(50).expireAfterWrite(CACHE).build();

    @org.springframework.beans.factory.annotation.Autowired
    public AreaCalendar(PublicAreaPage areas, PlannerSummary summaries, @Value("${toady.web.domain}") String domain) {
        this(areas, summaries, domain, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    AreaCalendar(PublicAreaPage areas, PlannerSummary summaries, String domain, Clock clock) {
        this.areas = areas; this.summaries = summaries; this.domain = domain.replaceAll("/+$", ""); this.clock = clock;
    }

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

    @GetMapping("/api/v1/public/areas/{slug}/calendar.ics")
    public ResponseEntity<String> feed(@PathVariable String slug) {
        var found = LivingAreas.bySlug(slug);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        String body;
        try { body = cache.get(slug, () -> build(found.get(), clock.instant())); }
        catch (java.util.concurrent.ExecutionException | com.google.common.util.concurrent.UncheckedExecutionException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failed.getCause());
        }
        return ResponseEntity.ok().header("Content-Type", "text/calendar;charset=UTF-8")
            .header("Content-Disposition", "inline; filename=\"toady-" + slug + ".ics\"")
            .cacheControl(CacheControl.maxAge(CACHE).cachePublic()).body(body);
    }

    String build(LivingAreas.Area area, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul"));
        // 최근 2주 안에 마감된 공고도 발표·계약 일정이 남아 있어 함께 담는다.
        var groups = areas.groups(area.label(), null, today, 45);
        List<PlannerSummary.Summary> list = new ArrayList<>();
        Stream.of(groups.open(), groups.upcoming(), groups.closed()).flatMap(List::stream)
            .filter(item -> item.status() != PublicAreaPage.Status.CLOSED || (item.end() != null && !item.end().isBefore(today.minusDays(14))))
            .limit(MAX_ANNOUNCEMENTS).forEach(item -> {
                try { list.add(summaries.get(item.source().type(), item.source().houseManageNo(), item.source().pblancNo())); }
                catch (EntityNotFoundException | IllegalArgumentException ignored) { }
            });
        return CalendarIcs.build(list, domain, now, "TOADY " + area.label() + " 청약", area.label() + " 생활권 청약 일정 (TOADY, 청약홈 공개 데이터)", false);
    }
}
