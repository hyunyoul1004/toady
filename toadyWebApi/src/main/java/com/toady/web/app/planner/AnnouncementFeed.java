package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 새 청약 공고 RSS(/rss.xml). 모집공고일이 오늘까지인 공고 중 최근 공고부터 담는다.
 * 검색엔진(네이버 서치어드바이저 RSS 제출 등)이 새 공고 페이지를 빨리 찾게 하는 용도라 공개 공고 데이터만 쓴다.
 * 웹 컨테이너 nginx가 /rss.xml을 /api/v1/public/rss.xml로 넘긴다. IndexNowNotifier도 같은 목록(recentUrls)을 쓴다.
 * 결과 XML은 30분 동안 메모리(Guava Cache)에 들고 있다.
 */
// @RestController: 반환한 문자열(RSS XML)을 그대로 응답 본문으로 쓴다.
@RestController
public class AnnouncementFeed {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementFeed.class);
    static final int SIZE = 50;
    private static final Map<String, String> TABLES = Map.of("apt", "TB_APT_LTTOT_PBLANC_DETAIL", "urban", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL",
            "remaining", "TB_REMNDR_LTTOT_PBLANC_DETAIL", "public-rental", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "optional", "TB_OPT_LTTOT_PBLANC_DETAIL");
    private static final Map<String, String> LABELS = Map.of("apt", "아파트", "urban", "도시형·오피스텔", "remaining", "무순위·잔여세대",
            "public-rental", "공공지원 민간임대", "optional", "임의공급");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter RFC_822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    record Entry(String type, String house, String notice, LocalDate announced) { }

    private final JdbcTemplate jdbc;
    private final PlannerSummary summaries;
    private final String domain;
    private final Clock clock;
    /** 운영자가 숨긴 공고는 RSS에 넣지 않는다(database.sql 26번). 단위 테스트에서 직접 만들면 없을 수 있다. */
    // @Autowired(required=false): 숨김 기능 빈이 있을 때만 넣는다.
    @Autowired(required = false)
    com.toady.web.app.operation.AnnouncementOverrides overrides;
    private final Cache<String, String> cache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(30)).maximumSize(1).build();
    /** 숨김 목록이 바뀌면 캐시를 비워 숨긴 공고가 바로 빠지게 한다. */
    // @EventListener: HiddenAnnouncements가 알리는 Changed 이벤트를 받는다.
    @org.springframework.context.event.EventListener(HiddenAnnouncements.Changed.class)
    void onHiddenChanged() { cache.invalidateAll(); }

    // @Autowired: 스프링이 쓸 생성자를 지정한다. @Value로 서비스 주소를 읽는다. 아래 생성자는 테스트용(시계 교체).
    @Autowired
    public AnnouncementFeed(JdbcTemplate jdbc, PlannerSummary summaries, @Value("${toady.web.domain}") String domain) {
        this(jdbc, summaries, domain, Clock.system(KST));
    }

    AnnouncementFeed(JdbcTemplate jdbc, PlannerSummary summaries, String domain, Clock clock) {
        this.jdbc = jdbc; this.summaries = summaries; this.domain = domain.replaceAll("/+$", ""); this.clock = clock;
    }

    // @GetMapping: RSS 주소. produces로 응답 형식(application/rss+xml)을 알린다.
    @GetMapping(value = "/api/v1/public/rss.xml", produces = "application/rss+xml;charset=UTF-8")
    public ResponseEntity<String> rss() {
        String xml;
        try { xml = cache.get("rss", this::build); }
        catch (Exception failure) { throw new IllegalStateException("RSS를 만들지 못했습니다", failure); }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(30))).body(xml);
    }

    /** 유형마다 최근 공고를 SIZE건씩 읽어 합친 뒤 모집공고일 최신순으로 SIZE건을 고른다. 날짜 형식(YYYY-MM-DD·YYYYMMDD)은 '-'를 빼 맞춘다. */
    List<Entry> recent() {
        String today = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        List<Entry> all = new ArrayList<>();
        for (var table : TABLES.entrySet()) {
            // 테이블명은 고정 목록에서만 온다.
            all.addAll(jdbc.query("SELECT HOUSE_MANAGE_NO, PBLANC_NO, REPLACE(RCRIT_PBLANC_DE,'-','') AS PBLANC_DAY FROM " + table.getValue()
                    + " WHERE RCRIT_PBLANC_DE IS NOT NULL AND REPLACE(RCRIT_PBLANC_DE,'-','') <= ? ORDER BY PBLANC_DAY DESC, HOUSE_MANAGE_NO DESC LIMIT " + SIZE,
                (r, i) -> new Entry(table.getKey(), r.getString(1), r.getString(2), day(r.getString(3))), today));
        }
        return all.stream().filter(e -> overrides == null || !overrides.hidden(e.type().toUpperCase(Locale.ROOT).replace('-', '_'), e.house(), e.notice()))
            .filter(e -> e.announced() != null && e.house() != null && e.notice() != null
                    && e.house().matches("[a-zA-Z0-9_-]{1,40}") && e.notice().matches("[a-zA-Z0-9_-]{1,40}"))
            .sorted(Comparator.comparing(Entry::announced).reversed().thenComparing(Entry::house, Comparator.reverseOrder()))
            .limit(SIZE).toList();
    }

    /** RSS에 담기는 최근 공고의 공개 주소(IndexNow 알림용). 요약을 만들지 못하거나 이름이 없는 공고는 뺀다. */
    List<String> recentUrls() {
        List<String> urls = new ArrayList<>();
        for (Entry entry : recent()) {
            try {
                PlannerSummary.Summary s = summaries.get(entry.type(), entry.house(), entry.notice());
                if (s.houseNm() != null && !s.houseNm().isBlank()) urls.add(domain + "/announcements/" + s.id());
            } catch (RuntimeException skipped) { /* RSS와 같은 기준으로 건너뛴다 */ }
        }
        return urls;
    }

    private String build() {
        List<Entry> entries = recent();
        String home = domain + "/";
        StringBuilder xml = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><rss version='2.0' xmlns:atom='http://www.w3.org/2005/Atom'><channel>")
            .append("<title>TOADY 새 청약 공고</title>")
            .append("<link>").append(esc(home)).append("</link>")
            .append("<atom:link href='").append(esc(domain + "/rss.xml")).append("' rel='self' type='application/rss+xml'/>")
            .append("<description>청약홈에 새로 올라온 아파트·무순위·임대·오피스텔 청약 공고의 분양가와 일정을 모았습니다.</description>")
            .append("<language>ko</language>");
        if (!entries.isEmpty()) xml.append("<lastBuildDate>").append(rfc822(entries.get(0).announced())).append("</lastBuildDate>");
        for (Entry entry : entries) {
            PlannerSummary.Summary s;
            try { s = summaries.get(entry.type(), entry.house(), entry.notice()); }
            catch (EntityNotFoundException | IllegalArgumentException missing) { continue; }
            catch (RuntimeException failure) { log.warn("RSS 항목을 건너뜁니다({}): {}", entry, failure.getMessage()); continue; }
            if (s.houseNm() == null || s.houseNm().isBlank()) continue;
            String url = domain + "/announcements/" + s.id();
            xml.append("<item><title>").append(esc(title(s.houseNm()))).append("</title>")
                .append("<link>").append(esc(url)).append("</link>")
                .append("<guid isPermaLink='true'>").append(esc(url)).append("</guid>")
                .append("<category>").append(esc(LABELS.get(entry.type()))).append("</category>")
                .append("<description>").append(esc(description(s))).append("</description>")
                .append("<pubDate>").append(rfc822(entry.announced())).append("</pubDate></item>");
        }
        return xml.append("</channel></rss>").toString();
    }

    /** 공고명에 이미 '공고'가 들어 있으면(예: …입주자모집공고) 덧붙이지 않는다. */
    static String title(String houseNm) { String name = houseNm.strip(); return name.contains("공고") ? name : name + " 청약 공고"; }

    /** 공개 공고 페이지 설명과 같은 순서(생활권 · 금액 · 일정 · 주소). */
    static String description(PlannerSummary.Summary s) {
        StringBuilder out = new StringBuilder();
        LivingAreas.match(s.address(), s.houseNm()).ifPresent(area -> out.append(area.label()).append(" 생활권 · "));
        String price = PublicHtml.priceRange(s.minPriceManwon(), s.maxPriceManwon());
        if (!price.isEmpty()) out.append("주택형별 최고 공급금액 ").append(price).append(" · ");
        out.append("접수 ").append(PublicHtml.receiptPeriod(s.startDate(), s.endDate(), "~", false, PublicHtml::shortDate))
            .append(" · 발표 ").append(PublicHtml.shortDate(s.winnerDate()));
        if (s.address() != null && !s.address().isBlank()) out.append(" · ").append(s.address().strip());
        return out.toString();
    }

    private static LocalDate day(String value) {
        if (value == null || !value.matches("\\d{8}")) return null;
        try { return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE); } catch (RuntimeException invalid) { return null; }
    }

    /** 모집공고일은 날짜만 있으므로 그날 오전 9시(KST)로 둔다. */
    private static String rfc822(LocalDate day) { return day.atTime(LocalTime.of(9, 0)).atZone(KST).format(RFC_822); }

    private static String esc(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8"); }
}
