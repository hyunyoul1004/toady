package com.toady.web.app.planner;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 가점 계산기에서 내 점수와 비교할 공개 당첨가점 기준. 공고별로 청약홈이 공개한 주택형·거주지역 전체의
 * 최저·최고 당첨가점만 모은다(평균의 평균이나 추정치는 만들지 않는다). 생활권은 공개 페이지와 같은 판정을 쓴다.
 */
@RestController
public class ScoreBenchmarks {
    static final int YEARS = 3, PER_AREA = 8, RECENT = 12;
    private static final Duration CACHE = Duration.ofHours(1);
    private final JdbcTemplate jdbc; private final Clock clock;
    private volatile Map<String, Object> cached; private volatile Instant cachedAt = Instant.EPOCH;

    @org.springframework.beans.factory.annotation.Autowired
    public ScoreBenchmarks(JdbcTemplate jdbc) { this(jdbc, Clock.system(ZoneId.of("Asia/Seoul"))); }
    ScoreBenchmarks(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public record Item(String id, String houseNm, String address, String receiptDate, String areaSlug, Double low, Double high) { }
    private record Row(String house, String notice, String name, String address, String receipt, String low, String high) { }

    @GetMapping("/api/v1/public/score-benchmarks")
    public ResponseEntity<Map<String, Object>> get() {
        Instant now = clock.instant();
        if (cached == null || cachedAt.plus(CACHE).isBefore(now)) { cached = build(now); cachedAt = now; }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(30)).cachePublic()).body(cached);
    }

    Map<String, Object> build(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul"));
        String from = today.minusYears(YEARS).format(DateTimeFormatter.BASIC_ISO_DATE);
        // 공고 테이블의 날짜는 yyyy-MM-dd와 yyyyMMdd가 섞여 있어 하이픈을 빼고 비교한다.
        List<Row> rows = jdbc.query("SELECT d.HOUSE_MANAGE_NO,d.PBLANC_NO,d.HOUSE_NM,d.HSSPLY_ADRES,d.RCEPT_BGNDE,s.LWET_SCORE,s.TOP_SCORE "
                + "FROM TB_APT_LTTOT_PBLANC_SCORE s JOIN TB_APT_LTTOT_PBLANC_DETAIL d ON d.HOUSE_MANAGE_NO=s.HOUSE_MANAGE_NO AND d.PBLANC_NO=s.PBLANC_NO "
                + "WHERE REPLACE(d.RCEPT_BGNDE,'-','') >= ?",
            (r, n) -> new Row(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7)), from);
        return summarize(rows.stream().map(row -> new String[]{row.house(), row.notice(), row.name(), row.address(), row.receipt(), row.low(), row.high()}).toList(), now);
    }

    /** rows: 주택관리번호, 공고번호, 단지명, 주소, 접수시작일, 최저가점, 최고가점 (주택형·거주지역별 한 행) */
    static Map<String, Object> summarize(List<String[]> rows, Instant now) {
        Map<String, Item> byNotice = new LinkedHashMap<>();
        for (String[] row : rows) {
            BigDecimal low = ShareScores.number(row[5]), high = ShareScores.number(row[6]);
            if (low == null && high == null) continue;
            String id = "apt/" + row[0] + "/" + row[1];
            LocalDate receipt = PlannerSummary.date(row[4] == null ? "" : row[4]);
            if (receipt == null) continue;
            Item previous = byNotice.get(id);
            String slug = LivingAreas.match(row[3], row[2]).map(LivingAreas.Area::slug).orElse(null);
            byNotice.put(id, new Item(id, row[2], row[3], receipt.toString(), slug,
                min(previous == null ? null : previous.low(), low == null ? null : low.doubleValue()), max(previous == null ? null : previous.high(), high == null ? null : high.doubleValue())));
        }
        Comparator<Item> newest = Comparator.comparing(Item::receiptDate).reversed().thenComparing(Item::id);
        List<Item> all = byNotice.values().stream().sorted(newest).toList();
        List<Map<String, Object>> areas = new ArrayList<>();
        for (var area : LivingAreas.ALL) {
            List<Item> items = all.stream().filter(item -> area.slug().equals(item.areaSlug())).limit(PER_AREA).toList();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slug", area.slug()); entry.put("label", area.label()); entry.put("items", items);
            areas.add(entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("checkedAt", now.toString());
        body.put("years", YEARS);
        body.put("areas", areas);
        body.put("recent", all.stream().limit(RECENT).toList());
        return body;
    }
    private static Double min(Double a, Double b) { return a == null ? b : b == null ? a : Math.min(a, b); }
    private static Double max(Double a, Double b) { return a == null ? b : b == null ? a : Math.max(a, b); }
}
