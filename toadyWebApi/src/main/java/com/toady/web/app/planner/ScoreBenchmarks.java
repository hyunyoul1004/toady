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
 * 테이블: TB_APT_LTTOT_PBLANC_SCORE(당첨가점)와 TB_APT_LTTOT_PBLANC_DETAIL(공고 상세). 가점 계산기 화면과 PublicHomePages(/score)가 쓴다.
 * 전체 목록은 1시간 메모리 캐시, 응답은 10분 공용 캐시(Cache-Control public).
 */
// @RestController: JSON 응답 컨트롤러.
@RestController
public class ScoreBenchmarks {
    static final int YEARS = 3, PER_AREA = 8, RECENT = 12;
    /** 이 건수보다 적으면 기간을 3년 → 5년 → 전체로 넓힌다. */
    static final int ENOUGH = 3;
    static final int MAX_ITEMS = 60;
    private final JdbcTemplate jdbc; private final Clock clock;
    /** 전체 기간 공고별 요약. 1시간마다 다시 읽고, 같은 시점의 동시 요청은 한 번만 조회한다. */
    private final Cache<String, List<Item>> index = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofHours(1)).build();
    /** 운영자가 숨긴 공고는 기준에서 뺀다. 캐시(1시간)와 상관없이 바로 반영되도록 읽을 때마다 거른다. 테스트에서 직접 만들면 없을 수 있다. */
    // @Autowired(required=false): 빈이 있을 때만 넣는다.
    @Autowired(required = false)
    HiddenAnnouncements hidden;

    // @Autowired: 스프링이 쓸 생성자. 아래 생성자는 테스트에서 시계를 바꿔 넣을 때 쓴다.
    @Autowired
    public ScoreBenchmarks(JdbcTemplate jdbc) { this(jdbc, Clock.system(ZoneId.of("Asia/Seoul"))); }
    ScoreBenchmarks(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    /** 공고 하나의 공개 가점 요약. low/high: 공개된 주택형·지역 중 최저·최고 가점. sido·district는 Regions로 정한다. */
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

    /**
     * 공개 당첨가점이 있는 시·도와 시·군·구 목록. 건수는 검색 결과의 '공개 N건'과 같은 기준이 되도록 years(3·5·0=전체) 기간만 센다.
     * 목록 자체는 전체 기간 기준이라 최근 자료가 없는 지역도 0건으로 남는다(고르면 검색이 기간을 넓혀 준다).
     * 응답 캐시(Cache-Control)는 주소에 years가 들어가 기간별로 따로 잡힌다.
     */
    @GetMapping("/api/v1/public/score-benchmarks/regions")
    public ResponseEntity<List<Map<String, Object>>> regions(@RequestParam(defaultValue = "0") int years) {
        return ok(regions(all(), today(clock.instant()), years));
    }

    /**
     * 지역(시·도·시·군·구) 또는 생활권으로 공고를 찾는다. 결과가 적으면 기간을 넓히고, 시·군·구에 아예 없으면 같은 시·도를 제안한다.
     * years: 3·5·0(전체). sort: recent(최신순)·low(공개 최저가점 낮은 순).
     */
    // @RequestParam(required=false): 조건을 안 주면 전국. defaultValue는 값이 없을 때 쓸 기본값.
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

    /** 공개 가점이 있는 공고 전체(최신순). 숨긴 공고는 뺀다. */
    List<Item> all() {
        List<Item> loaded = PublicHtml.load(index, "all", this::load);
        return hidden == null ? loaded : hidden.visible(loaded, Item::id);
    }
    private List<Item> load() {
        List<String[]> rows = jdbc.query("SELECT d.HOUSE_MANAGE_NO,d.PBLANC_NO,d.HOUSE_NM,d.HSSPLY_ADRES,d.RCEPT_BGNDE,s.LWET_SCORE,s.TOP_SCORE,d.SUBSCRPT_AREA_CODE_NM "
                + "FROM TB_APT_LTTOT_PBLANC_SCORE s JOIN TB_APT_LTTOT_PBLANC_DETAIL d ON d.HOUSE_MANAGE_NO=s.HOUSE_MANAGE_NO AND d.PBLANC_NO=s.PBLANC_NO",
            (r, n) -> new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getString(8)});
        return items(rows);
    }

    /**
     * rows: 주택관리번호, 공고번호, 단지명, 주소, 접수시작일, 최저가점, 최고가점[, 공급지역명] (주택형·거주지역별 한 행) → 공고별 한 줄, 최신순.
     * 공급지역명이 있으면 지역별 공개 페이지와 같이 '광주시 오포읍'처럼 시·도 없이 시작하는 주소도 시·도로 묶는다.
     */
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
            String[] region = region(row[3], row.length > 7 ? row[7] : null);
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
        for (var area : LivingAreas.all()) {
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

    /** 전체 기간 건수(기존 호출 호환). */
    static List<Map<String, Object>> regions(List<Item> all) { return regions(all, LocalDate.now(ZoneId.of("Asia/Seoul")), 0); }

    /**
     * 시·도·시·군·구 목록은 all 전체에서, 건수(count)는 years 기간(search의 requestedYears와 같은 해석) 안의 공고만 센다.
     * allCount는 전체 기간 건수다. 기간 안에 0건인 지역을 드롭다운에서 '0 (전체 기간 N)'처럼 보여 줘 결과(기간을 넓혀 찾음)와 헷갈리지 않게 한다.
     */
    static List<Map<String, Object>> regions(List<Item> all, LocalDate today, int years) {
        int requested = years == 5 || years == 0 ? years : 3;
        LocalDate from = requested == 0 ? null : today.minusYears(requested);
        // 시·도 → 시·군·구('' = 시·군·구 미확인) → [기간 안 건수, 전체 기간 건수]
        Map<String, Map<String, long[]>> grouped = new TreeMap<>(Comparator.comparingInt(ScoreBenchmarks::sidoOrder));
        for (Item item : all) {
            if (item.sido() == null) continue;
            long[] counts = grouped.computeIfAbsent(item.sido(), key -> new TreeMap<>()).computeIfAbsent(item.district() == null ? "" : item.district(), key -> new long[2]);
            if (from == null || !LocalDate.parse(item.receiptDate()).isBefore(from)) counts[0]++;
            counts[1]++;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((sido, districts) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("sido", sido);
            entry.put("count", districts.values().stream().mapToLong(c -> c[0]).sum());
            entry.put("allCount", districts.values().stream().mapToLong(c -> c[1]).sum());
            entry.put("districts", districts.entrySet().stream().filter(e -> !e.getKey().isEmpty())
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()[0], "allCount", e.getValue()[1])).toList());
            out.add(entry);
        });
        return out;
    }

    /** 지역·생활권·검색어로 거르고, 기간을 필요하면 넓힌 뒤 최저 가점 통계(중앙값·최소·최대)와 목록을 만든다. */
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
        // 주소로 시·도(전국 검색일 때)나 시·군·구(시·도 검색일 때)를 정하지 못한 공고 수. 이 공고는 아래 단계(시·도·시·군·구)를 골라 찾으면 나오지 않아
        // 전국 건수가 시·도 합보다, 시·도 건수가 시·군·구 합보다 많을 수 있다. 화면이 '지역 미확인 N건 포함'으로 알린다.
        if (areaMatch.isEmpty()) {
            long unknown = blank(sido) ? matched.stream().filter(item -> item.sido() == null).count()
                : blank(district) ? matched.stream().filter(item -> item.district() == null).count() : 0;
            body.put("unknownRegion", unknown);
        }
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

    /** 주소 앞부분으로 [시·도 약칭, 시·군·구]를 정한다(지역별 공개 페이지와 같은 Regions 규칙). 확인할 수 없으면 null. 세종은 시·군·구가 없다. */
    static String[] region(String address) { return region(address, null); }
    /** 주소와 청약홈 공급지역명(SUBSCRPT_AREA_CODE_NM)으로 정한다. PublicAnnouncementIndex(지역별 공개 페이지)와 같은 Regions.of(주소, 공급지역명) 규칙이다. */
    static String[] region(String address, String regionName) {
        return Regions.of(address, regionName).map(found -> new String[]{found.sido().shortName(), found.district()}).orElse(new String[]{null, null});
    }
    private static Double min(Double a, Double b) { return a == null ? b : b == null ? a : Math.min(a, b); }
    private static Double max(Double a, Double b) { return a == null ? b : b == null ? a : Math.max(a, b); }
}
