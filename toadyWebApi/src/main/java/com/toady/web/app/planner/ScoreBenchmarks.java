package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 가점 계산기에서 내 점수와 비교할 공개 당첨가점 기준. 공고별로 청약홈이 공개한 주택형·거주지역 전체의
 * 최저·최고 당첨가점만 모은다(평균의 평균이나 추정치는 만들지 않는다).
 * 지역은 주소의 시·도/시·군·구로, 생활권은 공개 페이지와 같은 판정으로 나눈다.
 */
@RestController
public class ScoreBenchmarks {
    static final int YEARS = 3, PER_AREA = 8, RECENT = 12;
    /** 이 건수보다 적으면 기간을 3년 → 5년 → 전체로 넓힌다. */
    static final int ENOUGH = 3;
    static final int MAX_ITEMS = 60;
    private final JdbcTemplate jdbc; private final Clock clock;
    /** 전체 기간 공고별 요약. 1시간마다 다시 읽고, 같은 시점의 동시 요청은 한 번만 조회한다. */
    private final Cache<String, List<Item>> index = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofHours(1)).build();

    @Autowired
    public ScoreBenchmarks(JdbcTemplate jdbc) { this(jdbc, Clock.system(ZoneId.of("Asia/Seoul"))); }
    ScoreBenchmarks(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public record Item(String id, String houseNm, String address, String receiptDate, String areaSlug, Double low, Double high,
                       String sido, String district) { }

    /** 기존 화면 호환: 생활권별 최근 3년 8건과 전국 최근 12건. */
    @GetMapping("/api/v1/public/score-benchmarks")
    public ResponseEntity<Map<String, Object>> get() {
        Instant now = clock.instant();
        LocalDate from = today(now).minusYears(YEARS);
        Map<String, Object> body = view(all().stream().filter(item -> !LocalDate.parse(item.receiptDate()).isBefore(from)).toList(), now);
        return ok(body);
    }

    /** 공개 당첨가점이 있는 시·도와 시·군·구 목록(전체 기간 건수). */
    @GetMapping("/api/v1/public/score-benchmarks/regions")
    public ResponseEntity<List<Map<String, Object>>> regions() { return ok(regions(all())); }

    /**
     * 지역(시·도·시·군·구) 또는 생활권으로 공고를 찾는다. 결과가 적으면 기간을 넓히고, 시·군·구에 아예 없으면 같은 시·도를 제안한다.
     * years: 3·5·0(전체). sort: recent(최신순)·low(공개 최저가점 낮은 순).
     */
    @GetMapping("/api/v1/public/score-benchmarks/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam(required = false) String sido, @RequestParam(required = false) String district,
            @RequestParam(required = false) String area, @RequestParam(defaultValue = "3") int years,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "recent") String sort) {
        return ok(search(all(), today(clock.instant()), sido, district, area, years, q, sort));
    }

    private static <T> ResponseEntity<T> ok(T body) { return PublicHtml.publicJson(body, Duration.ofMinutes(10)); }
    private static LocalDate today(Instant now) { return LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul")); }

    /** 테스트에서 데이터를 바꾼 뒤 다시 읽게 한다. */
    void invalidate() { index.invalidateAll(); }

    List<Item> all() {
        return PublicHtml.load(index, "all", this::load);
    }
    private List<Item> load() {
        List<String[]> rows = jdbc.query("SELECT d.HOUSE_MANAGE_NO,d.PBLANC_NO,d.HOUSE_NM,d.HSSPLY_ADRES,d.RCEPT_BGNDE,s.LWET_SCORE,s.TOP_SCORE "
                + "FROM TB_APT_LTTOT_PBLANC_SCORE s JOIN TB_APT_LTTOT_PBLANC_DETAIL d ON d.HOUSE_MANAGE_NO=s.HOUSE_MANAGE_NO AND d.PBLANC_NO=s.PBLANC_NO",
            (r, n) -> new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7)});
        return items(rows);
    }

    /** rows: 주택관리번호, 공고번호, 단지명, 주소, 접수시작일, 최저가점, 최고가점 (주택형·거주지역별 한 행) → 공고별 한 줄, 최신순 */
    static List<Item> items(List<String[]> rows) {
        Map<String, Item> byNotice = new LinkedHashMap<>();
        for (String[] row : rows) {
            BigDecimal low = ShareScores.number(row[5]), high = ShareScores.number(row[6]);
            if (low == null && high == null) continue;
            String id = "apt/" + row[0] + "/" + row[1];
            LocalDate receipt = PlannerSummary.date(row[4] == null ? "" : row[4]);
            if (receipt == null) continue;
            Item previous = byNotice.get(id);
            String slug = LivingAreas.match(row[3], row[2]).map(LivingAreas.Area::slug).orElse(null);
            String[] region = region(row[3]);
            byNotice.put(id, new Item(id, row[2], row[3], receipt.toString(), slug,
                min(previous == null ? null : previous.low(), low == null ? null : low.doubleValue()),
                max(previous == null ? null : previous.high(), high == null ? null : high.doubleValue()), region[0], region[1]));
        }
        Comparator<Item> newest = Comparator.comparing(Item::receiptDate).reversed().thenComparing(Item::id);
        return byNotice.values().stream().sorted(newest).toList();
    }

    /** 단위 테스트·기존 호출 호환용: 최근 3년 행만 받아 생활권·전국 최근 묶음을 만든다. */
    static Map<String, Object> summarize(List<String[]> rows, Instant now) { return view(items(rows), now); }

    private static Map<String, Object> view(List<Item> recentYears, Instant now) {
        List<Map<String, Object>> areas = new ArrayList<>();
        for (var area : LivingAreas.ALL) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("slug", area.slug()); entry.put("label", area.label());
            entry.put("items", recentYears.stream().filter(item -> area.slug().equals(item.areaSlug())).limit(PER_AREA).toList());
            areas.add(entry);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("checkedAt", now.toString());
        body.put("years", YEARS);
        body.put("areas", areas);
        body.put("recent", recentYears.stream().limit(RECENT).toList());
        return body;
    }

    static List<Map<String, Object>> regions(List<Item> all) {
        Map<String, Map<String, Long>> grouped = new TreeMap<>(Comparator.comparingInt(ScoreBenchmarks::sidoOrder));
        for (Item item : all) {
            if (item.sido() == null) continue;
            grouped.computeIfAbsent(item.sido(), key -> new TreeMap<>()).merge(item.district() == null ? "" : item.district(), 1L, Long::sum);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((sido, districts) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sido", sido);
            entry.put("count", districts.values().stream().mapToLong(Long::longValue).sum());
            entry.put("districts", districts.entrySet().stream().filter(e -> !e.getKey().isEmpty())
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue())).toList());
            out.add(entry);
        });
        return out;
    }

    static Map<String, Object> search(List<Item> all, LocalDate today, String sido, String district, String area, int years, String q, String sort) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        var areaMatch = area == null || area.isBlank() ? Optional.<LivingAreas.Area>empty() : LivingAreas.bySlug(area);
        String scope = areaMatch.map(a -> a.label() + " 생활권")
            .orElseGet(() -> blank(sido) ? "전국" : blank(district) ? sido : sido + " " + district);
        List<Item> inScope = all.stream().filter(item -> areaMatch.map(a -> a.slug().equals(item.areaSlug()))
                .orElseGet(() -> (blank(sido) || sido.equals(item.sido())) && (blank(district) || district.equals(item.district()))))
            .filter(item -> query.isEmpty() || (item.houseNm() + " " + item.address()).toLowerCase(Locale.ROOT).contains(query))
            .toList();
        int requested = years == 5 || years == 0 ? years : 3;
        // 요청 기간에 충분하지 않으면 5년, 전체 순으로 넓힌다. 사용자가 고른 기간보다 좁히지는 않는다.
        int applied = requested;
        for (int candidate : List.of(requested, 5, 0)) {
            if (narrower(candidate, requested)) continue;
            applied = candidate;
            if (within(inScope, today, candidate).size() >= ENOUGH) break;
        }
        List<Item> matched = new ArrayList<>(within(inScope, today, applied));
        if ("low".equals(sort)) matched.sort(Comparator.comparing((Item item) -> item.low() == null ? Double.MAX_VALUE : item.low()).thenComparing(Item::id));
        List<Double> lows = matched.stream().map(Item::low).filter(Objects::nonNull).sorted().toList();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", matched.size());
        stats.put("medianLow", lows.isEmpty() ? null : lows.size() % 2 == 1 ? lows.get(lows.size() / 2) : (lows.get(lows.size() / 2 - 1) + lows.get(lows.size() / 2)) / 2);
        stats.put("minLow", lows.isEmpty() ? null : lows.get(0));
        stats.put("maxLow", lows.isEmpty() ? null : lows.get(lows.size() - 1));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scope", scope);
        body.put("requestedYears", requested);
        body.put("years", applied);
        body.put("expanded", applied != requested);
        body.put("total", matched.size());
        body.put("stats", stats);
        body.put("items", matched.stream().limit(MAX_ITEMS).toList());
        // 시·군·구에 공개 자료가 전혀 없으면 같은 시·도 전체를 제안한다.
        if (matched.isEmpty() && areaMatch.isEmpty() && !blank(sido) && !blank(district) && query.isEmpty()) {
            long siblings = all.stream().filter(item -> sido.equals(item.sido())).count();
            if (siblings > 0) body.put("suggestion", Map.of("sido", sido, "count", siblings));
        }
        return body;
    }
    private static boolean narrower(int candidate, int requested) {
        if (requested == 0) return candidate != 0;
        return candidate != 0 && candidate < requested;
    }
    private static List<Item> within(List<Item> items, LocalDate today, int years) {
        if (years == 0) return items;
        LocalDate from = today.minusYears(years);
        return items.stream().filter(item -> !LocalDate.parse(item.receiptDate()).isBefore(from)).toList();
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    static final List<String> SIDO = List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");
    private static int sidoOrder(String sido) { int index = SIDO.indexOf(sido); return index < 0 ? SIDO.size() : index; }

    /** 주소 앞부분으로 [시·도 약칭, 시·군·구]를 정한다. 확인할 수 없으면 null. 세종은 시·군·구가 없다. */
    static String[] region(String address) {
        if (address == null) return new String[]{null, null};
        String[] tokens = address.replaceAll("\\([^)]*\\)", " ").trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return new String[]{null, null};
        String sido = sido(tokens[0]);
        if (sido == null) return new String[]{null, null};
        String district = null;
        if (!"세종".equals(sido) && tokens.length > 1 && tokens[1].matches(".+(시|군|구)")) district = tokens[1];
        return new String[]{sido, district};
    }
    static String sido(String token) {
        String t = token.trim();
        if (t.startsWith("충청북") || t.startsWith("충북")) return "충북";
        if (t.startsWith("충청남") || t.startsWith("충남")) return "충남";
        if (t.startsWith("전라북") || t.startsWith("전북")) return "전북";
        if (t.startsWith("전라남") || t.startsWith("전남")) return "전남";
        if (t.startsWith("경상북") || t.startsWith("경북")) return "경북";
        if (t.startsWith("경상남") || t.startsWith("경남")) return "경남";
        for (String name : List.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "제주"))
            if (t.startsWith(name)) return name;
        return null;
    }
    private static Double min(Double a, Double b) { return a == null ? b : b == null ? a : Math.min(a, b); }
    private static Double max(Double a, Double b) { return a == null ? b : b == null ? a : Math.max(a, b); }
}
