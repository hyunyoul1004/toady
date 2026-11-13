package com.toady.web.app.planner.research;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 위치 탭 지도용: 공고와 같은 시·군·구에서 최근 신고된 아파트 매매 실거래를 단지별로 묶는다.
 * 호가·시세 추정이 아니라 국토부에 신고된 계약 금액만 쓴다(해제 거래 제외). 필터는 화면이 이 결과 안에서 적용한다.
 */
@Service
public class ComplexPriceService {
    /**
     * 전용면적(㎡, 반올림)별 요약. 금액 단위는 만원.
     * monthAvg: 이 면적의 마지막 거래가 있었던 달(month, YYYY-MM)의 평균 거래가, monthCount: 그 달 거래 건수. 지도·목록은 이 값을 대표로 쓴다.
     */
    public record AreaPrice(int area, int count, long latest, String latestDate, int latestFloor, long median, long min, long max,
                            String month, long monthAvg, int monthCount) {
        public AreaPrice(int area, int count, long latest, String latestDate, int latestFloor, long median, long min, long max) {
            this(area, count, latest, latestDate, latestFloor, median, min, max, latestDate.substring(0, 7), latest, 1);
        }
    }
    public record RecentTrade(String date, BigDecimal area, int floor, long amount) { }
    /** lat·lng가 없으면 지도에는 못 놓고 목록에만 보인다(좌표 확인 중이거나 지번으로 찾지 못함). */
    public record Complex(String key, String name, String dong, String jibun, String buildYear, Double lat, Double lng,
                          int count, String latestDate, List<AreaPrice> areas, List<RecentTrade> recent) { }
    /** pending: 좌표를 확인하고 있는 단지 수(화면은 잠시 뒤 다시 불러온다). partial: 응답 시간 한도로 일부 기간만 받음. */
    public record Result(String status, String bcode, String fromMonth, String toMonth, Instant checkedAt, int months, boolean partial,
                         int pending, int total, List<Complex> complexes) { }

    static final Set<Integer> PERIODS = Set.of(3, 6, 12);
    static final int MAX_COMPLEXES = 900, RECENT_PER_COMPLEX = 10;
    /** 첫 응답까지 기다리는 시간(초)과 그중 좌표 확인을 기다리는 시간. 못 끝낸 일은 다음 요청이 이어 간다. */
    static final int FIRST_RESPONSE_SECONDS = 7, LOCATE_WAIT_SECONDS = 3;
    /** 최근 1년에 거래가 없던 단지도 보이도록 그 이전 2년(13~36개월 전)은 단지·면적별 마지막 거래월 요약만 남긴다. */
    static final int OLDER_FROM = 12, OLDER_TO = 36;

    /** 1년보다 오래된 달에서 모은 단지 요약(원본 거래는 버린다). 최신 달부터 거꾸로 받으므로 처음 본 달이 그 면적의 마지막 거래월이다. */
    static final class Older {
        final Map<String, List<NeighborhoodResearchService.Trade>> lastMonthTrades = new LinkedHashMap<>();
        final Map<String, java.time.YearMonth> lastMonth = new HashMap<>();
        int nextOffset = OLDER_FROM;
        boolean done() { return nextOffset >= OLDER_TO; }
    }
    private final Cache<String, Older> older = CacheBuilder.newBuilder().maximumSize(200).expireAfterWrite(6, TimeUnit.HOURS).build();
    private final NeighborhoodResearchService research;
    private final ComplexLocator locator;
    /** 좌표 확인과 기간 수집이 모두 끝난 결과만 잠시 보관한다. */
    private final Cache<String, Result> done = CacheBuilder.newBuilder().maximumSize(300).expireAfterWrite(30, TimeUnit.MINUTES).build();

    public ComplexPriceService(NeighborhoodResearchService research, ComplexLocator locator) {
        this.research = research; this.locator = locator;
    }

    public Result complexes(String bcode, String region, int months) {
        int period = PERIODS.contains(months) ? months : 6;
        String cacheKey = bcode.substring(0, 5) + "/" + period + "/" + (region == null ? "" : region);
        Result cached = done.getIfPresent(cacheKey);
        if (cached != null) return withBcode(cached, bcode);
        // 처음 보는 지역도 몇 초 안에 받은 달까지 먼저 보여 주고(partial), 나머지는 화면이 다시 부를 때 이어 받는다.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(FIRST_RESPONSE_SECONDS);
        NeighborhoodResearchService.DistrictTrades district = research.districtTrades(bcode, period, deadline);
        if (!"READY".equals(district.status()))
            return new Result(district.status(), bcode, district.fromMonth(), district.toMonth(), district.checkedAt(), district.months(), district.partial(), 0, 0, List.of());

        String lawd5 = bcode.substring(0, 5);
        Map<String, List<NeighborhoodResearchService.Trade>> groups = district.trades().stream()
            .collect(Collectors.groupingBy(trade -> complexKey(lawd5, trade), LinkedHashMap::new, Collectors.toList()));
        // 최근 1년을 다 받았으면 남은 시간에 그 이전 달을 이어 받고, 최근 1년에 없던 단지만 더한다.
        boolean olderDone = true;
        if (period == 12 && !district.partial()) {
            Older state = olderState(lawd5, deadline);
            olderDone = state.done();
            synchronized (state) {
                // 최근 1년에 거래가 있는 단지는 그 값을 쓰고, 없던 단지만 더한다.
                Map<String, List<NeighborhoodResearchService.Trade>> extra = new LinkedHashMap<>();
                state.lastMonthTrades.forEach((key, trades) -> {
                    String complex = key.substring(0, key.lastIndexOf('#'));
                    if (!groups.containsKey(complex)) extra.computeIfAbsent(complex, k -> new ArrayList<>()).addAll(trades);
                });
                groups.putAll(extra);
            }
        }
        List<Map.Entry<String, List<NeighborhoodResearchService.Trade>>> busiest = groups.entrySet().stream()
            .sorted(Comparator.comparingInt((Map.Entry<String, List<NeighborhoodResearchService.Trade>> e) -> e.getValue().size()).reversed())
            .limit(MAX_COMPLEXES).toList();

        // 좌표 확인은 공고와 같은 법정동 단지부터 맡긴다(화면 첫 확대 범위에 먼저 보이도록).
        List<ComplexLocator.Target> targets = nearFirst(busiest, bcode.substring(5)).stream().filter(e -> !e.getValue().get(0).jibun().isBlank()).map(e -> {
            NeighborhoodResearchService.Trade any = e.getValue().get(0);
            String query = ((region == null ? "" : region + " ") + any.dong() + " " + any.jibun()).trim();
            return new ComplexLocator.Target(e.getKey(), lawd5, any.dong(), any.jibun(), query);
        }).toList();
        Set<String> keys = targets.stream().map(ComplexLocator.Target::key).collect(Collectors.toSet());
        Map<String, Optional<ComplexLocator.Point>> points = locator.known(keys);
        List<ComplexLocator.Target> unknown = targets.stream().filter(t -> !points.containsKey(t.key())).toList();
        if (!unknown.isEmpty()) {
            locator.locate(unknown, Math.min(deadline, System.nanoTime() + TimeUnit.SECONDS.toNanos(LOCATE_WAIT_SECONDS)));
            points.putAll(locator.known(unknown.stream().map(ComplexLocator.Target::key).toList()));
        }
        int pending = (int) keys.stream().filter(k -> !points.containsKey(k) && locator.pending(k)).count();

        List<Complex> complexes = busiest.stream().map(e -> toComplex(e.getKey(), e.getValue(), points.get(e.getKey()))).toList();
        boolean partial = district.partial() || !olderDone;
        String from = period == 12 && olderDone ? java.time.YearMonth.parse(district.toMonth()).minusMonths(OLDER_TO - 1).toString() : district.fromMonth();
        Result result = new Result("READY", bcode, from, district.toMonth(), district.checkedAt(), district.months(), partial,
            pending, district.trades().size(), complexes);
        if (pending == 0 && !partial) done.put(cacheKey, result);
        return result;
    }

    /** 13~36개월 전 달을 시간 안에서 이어 받아 단지·면적별 마지막 거래월의 거래만 남긴다. 같은 시·군·구는 한 번에 한 요청만 진행한다. */
    private Older olderState(String lawd5, long deadline) {
        Older state;
        try { state = older.get(lawd5, Older::new); } catch (java.util.concurrent.ExecutionException impossible) { state = new Older(); }
        synchronized (state) {
            java.time.YearMonth end = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
            while (!state.done() && System.nanoTime() < deadline) {
                java.time.YearMonth month = end.minusMonths(state.nextOffset);
                Optional<List<NeighborhoodResearchService.Trade>> trades = research.monthTrades(lawd5, month, deadline);
                if (trades.isEmpty() && System.nanoTime() >= deadline) break;   // 시간 초과: 다음 요청에서 이 달부터 다시
                for (NeighborhoodResearchService.Trade trade : trades.orElse(List.of())) {
                    String key = complexKey(lawd5, trade) + "#" + trade.area().setScale(0, RoundingMode.HALF_UP).intValue();
                    java.time.YearMonth seen = state.lastMonth.get(key);
                    if (seen == null) { state.lastMonth.put(key, month); state.lastMonthTrades.computeIfAbsent(key, k -> new ArrayList<>()).add(trade); }
                    else if (seen.equals(month)) state.lastMonthTrades.get(key).add(trade);
                }
                state.nextOffset++;
            }
        }
        return state;
    }

    /** 같은 법정동(법정동 코드 뒤 5자리) 단지를 앞으로, 나머지는 원래 순서(거래 많은 순)를 유지한다. */
    static List<Map.Entry<String, List<NeighborhoodResearchService.Trade>>> nearFirst(List<Map.Entry<String, List<NeighborhoodResearchService.Trade>>> entries, String legalDong) {
        List<Map.Entry<String, List<NeighborhoodResearchService.Trade>>> same = new ArrayList<>(), others = new ArrayList<>();
        for (var entry : entries) (legalDong.equals(entry.getValue().get(0).dongCode()) ? same : others).add(entry);
        same.addAll(others);
        return same;
    }

    private static Result withBcode(Result result, String bcode) {
        return new Result(result.status(), bcode, result.fromMonth(), result.toMonth(), result.checkedAt(), result.months(), result.partial(),
            result.pending(), result.total(), result.complexes());
    }

    /** 같은 법정동·지번이면 한 단지로 본다(단지명이 바뀐 경우 포함). 지번이 없으면 이름으로 묶는다. */
    static String complexKey(String lawd5, NeighborhoodResearchService.Trade trade) {
        return lawd5 + "|" + trade.dong() + "|" + (trade.jibun().isBlank() ? "#" + trade.apartment() : trade.jibun());
    }

    static Complex toComplex(String key, List<NeighborhoodResearchService.Trade> trades, Optional<ComplexLocator.Point> point) {
        List<NeighborhoodResearchService.Trade> latestFirst = trades.stream()
            .sorted(Comparator.comparing(NeighborhoodResearchService.Trade::date).reversed().thenComparing(Comparator.comparingInt(NeighborhoodResearchService.Trade::floor).reversed())).toList();
        NeighborhoodResearchService.Trade latest = latestFirst.get(0);
        // 단지명은 가장 최근 신고 이름을 쓴다(개명 반영).
        String name = latest.apartment();
        String buildYear = trades.stream().map(NeighborhoodResearchService.Trade::buildYear).filter(y -> y.matches("\\d{4}")).findFirst().orElse("");
        Map<Integer, List<NeighborhoodResearchService.Trade>> byArea = latestFirst.stream()
            .collect(Collectors.groupingBy(t -> t.area().setScale(0, RoundingMode.HALF_UP).intValue(), TreeMap::new, Collectors.toList()));
        List<AreaPrice> areas = byArea.entrySet().stream().map(e -> {
            List<NeighborhoodResearchService.Trade> list = e.getValue();
            List<Long> amounts = list.stream().map(NeighborhoodResearchService.Trade::amountManwon).sorted().toList();
            NeighborhoodResearchService.Trade first = list.get(0);
            // 마지막 거래가 있었던 달의 거래만 평균한다(한 건만 튀는 값에 덜 흔들리게, 그러면서도 최근 수준을 보이게).
            java.time.YearMonth month = java.time.YearMonth.from(first.date());
            List<Long> sameMonth = list.stream().filter(t -> java.time.YearMonth.from(t.date()).equals(month)).map(NeighborhoodResearchService.Trade::amountManwon).toList();
            long average = Math.round(sameMonth.stream().mapToLong(Long::longValue).average().orElse(first.amountManwon()));
            return new AreaPrice(e.getKey(), list.size(), first.amountManwon(), first.date().toString(), first.floor(),
                NeighborhoodResearchService.median(amounts), amounts.get(0), amounts.get(amounts.size() - 1), month.toString(), average, sameMonth.size());
        }).toList();
        List<RecentTrade> recent = latestFirst.stream().limit(RECENT_PER_COMPLEX)
            .map(t -> new RecentTrade(t.date().toString(), t.area(), t.floor(), t.amountManwon())).toList();
        ComplexLocator.Point at = point == null ? null : point.orElse(null);
        return new Complex(key, name, latest.dong(), latest.jibun(), buildYear, at == null ? null : at.lat(), at == null ? null : at.lng(),
            trades.size(), latest.date().toString(), areas, recent);
    }
}
