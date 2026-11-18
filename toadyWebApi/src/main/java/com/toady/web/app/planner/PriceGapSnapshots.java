package com.toady.web.app.planner;

import com.toady.web.app.planner.research.AddressLocator;
import com.toady.web.app.planner.research.NeighborhoodResearchService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 접수 중·예정 공고의 '주변 시세 대비' 요약을 매일 미리 계산해 TB_PRICE_GAP(database.sql 18번)에 둔다.
 * 맞춤 청약·줍줍 목록 뱃지, 나의 청약 비교표, 공고 공개 페이지, 줍줍 알림이 이 값을 쓴다(화면 상세는 실시간 계산을 그대로 쓴다).
 * 정확한 지번 위치를 확인한 공고만 계산하고(운영자 확인 주소가 있으면 먼저 쓴다: ConfirmedAddresses), 신뢰도가 '참고'뿐이면 READY로 두지 않는다.
 * 카카오 REST 키나 실거래 키가 없으면 계산하지 않으며, 테이블이 없으면 조회는 빈 결과를 돌려준다.
 * 국토부 실거래는 하루 호출 한도가 있어(MolitDailyBudget) 사용자 요청과 몫을 나눠 쓰고, 한 번에 최대 60건·20분까지만 계산한다.
 * 위치 확인은 AddressLocator(카카오), 시세는 NeighborhoodResearchService(국토부), 비교 규칙은 PriceGapRules가 맡는다.
 */
// @RestController: 저장된 값을 조회하는 API(/api/v1/planner/price-gaps)도 함께 둔다. @Validated: 요청 검증을 켠다.
@RestController
@Validated
public class PriceGapSnapshots {
    private static final Logger log = LoggerFactory.getLogger(PriceGapSnapshots.class);
    static final int MAX_PER_RUN = 60, READY_DAYS = 3, RETRY_DAYS = 7, KEEP_DAYS = 180, MAX_MINUTES = 20;
    /** 공고별로 마지막에 시도한 운영자 확인 주소. 확인 주소가 새로 생기거나 바뀐 경우에만 재시도 기간을 건너뛴다(재시작하면 한 번 더 시도). */
    private final Map<String, String> triedConfirmed = new java.util.concurrent.ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final PriceGapStore store;
    private final EligibilityFeed feed;
    private final AddressLocator locator;
    private final NeighborhoodResearchService research;
    private final Clock clock;
    // @Autowired(required=false): 트랜잭션 도우미가 있으면 넣는다. 없으면(단위 테스트) 트랜잭션 없이 저장한다.
    // TransactionTemplate은 @Transactional 대신 코드로 트랜잭션 범위를 정할 때 쓴다(같은 클래스 안 호출에도 적용된다).
    @Autowired(required = false) private TransactionTemplate transactions;

    /** 운영자 확인 주소(관리자 '위치 확인'). 공고 상세와 같은 순서(확인 주소 → 공급 주소)로 위치를 정한다. */
    private ConfirmedAddresses confirmed = ConfirmedAddresses.NONE;
    /** 운영자가 숨긴 공고. 조회 API가 없는 것처럼 뺀다. 빈이 없으면(단위 테스트) 아무것도 숨기지 않는다. */
    private HiddenAnnouncements hidden;
    // @Autowired(required=false) 메서드 주입: 생성자를 늘리지 않고 빈이 있을 때만 넣는다.
    @Autowired(required = false)
    void useConfirmed(ConfirmedAddresses confirmed) { this.confirmed = confirmed == null ? ConfirmedAddresses.NONE : confirmed; }
    @Autowired(required = false)
    void useHidden(HiddenAnnouncements hidden) { this.hidden = hidden; }

    // @Autowired: 스프링이 쓸 생성자를 지정한다. 아래 생성자는 테스트에서 시계를 바꿔 넣을 때 쓴다.
    @Autowired
    public PriceGapSnapshots(JdbcTemplate jdbc, PriceGapStore store, EligibilityFeed feed, AddressLocator locator, NeighborhoodResearchService research) {
        this(jdbc, store, feed, locator, research, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    PriceGapSnapshots(JdbcTemplate jdbc, PriceGapStore store, EligibilityFeed feed, AddressLocator locator, NeighborhoodResearchService research, Clock clock) {
        this.jdbc = jdbc; this.store = store; this.feed = feed; this.locator = locator; this.research = research; this.clock = clock;
    }

    public record Request(@NotNull @Size(max = 50) List<@NotNull @Pattern(regexp = "(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }

    // @PostMapping + @Valid @RequestBody: 공고 키 목록(최대 50개)을 받아 미리 계산된 비교 요약을 돌려준다.
    @PostMapping("/api/v1/planner/price-gaps")
    // 숨긴 공고는 없는 것처럼 빈 결과로 둔다(목록·알림과 같은 기준).
    public List<PriceGapStore.Gap> lookup(@Valid @RequestBody Request request) {
        List<String> ids = hidden == null ? request.ids() : request.ids().stream().filter(id -> !hidden.hiddenId(id)).toList();
        return ids.isEmpty() ? List.of() : List.copyOf(store.forIds(ids).values());
    }

    /** 저녁 묶음 알림 전에 미리 계산한다(기본 17:20). PRICE_GAP_CRON=- 이면 끈다. */
    // @Scheduled(cron): 설정 toady.research.gap-cron 값, 없으면 매일 17:20(한국시간)에 실행한다.
    @Scheduled(cron = "${toady.research.gap-cron:0 20 17 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (!locator.enabled() || !research.tradesEnabled()) return;
        try {
            // 미리 계산은 사용자 조회 몫(하루 한도의 80%)과 별도로 남겨 둔 몫까지 쓸 수 있다.
            var report = research.inBackground(this::run);
            log.info("주변 시세 비교 미리 계산: ready={}, noLocation={}, noTrades={}, unavailable={}, kept={}", report.get("READY"), report.get("NO_LOCATION"), report.get("NO_TRADES"), report.get("UNAVAILABLE"), report.get("KEPT"));
        } catch (BadSqlGrammarException missing) {
            log.warn("TB_PRICE_GAP을 사용할 수 없어 주변 시세 비교를 미리 계산하지 않습니다. database.sql 18번을 적용하세요.");
        } catch (DataAccessException failure) {
            log.warn("주변 시세 비교 저장 실패: error={}", failure.getMostSpecificCause().getMessage());
        }
    }

    /** 공고별로 계산해 저장하고 상태별 건수를 돌려준다. 한 번에 최대 60건, 최근에 계산한 공고는 건너뛴다. */
    public Map<String, Integer> run() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Integer> report = new LinkedHashMap<>(Map.of("READY", 0, "NO_LOCATION", 0, "NO_TRADES", 0, "UNAVAILABLE", 0, "KEPT", 0, "LAND_LEASE", 0));
        @SuppressWarnings("unchecked")
        List<EligibilityFeed.Item> items = (List<EligibilityFeed.Item>) feed.load(today).get("items");
        Map<String, Object[]> previous = new HashMap<>();
        jdbc.query("SELECT ANNOUNCEMENT_ID,STATUS,COMPUTED_DT,AREA,SUPPLY_MANWON FROM TB_PRICE_GAP", (RowCallbackHandler) r ->
            previous.put(r.getString(1), new Object[]{r.getString(2), r.getTimestamp(3).toLocalDateTime(), r.getObject(4) == null ? null : r.getInt(4), r.getObject(5) == null ? null : r.getLong(5)}));
        // 접수 중인 공고를 먼저 계산한다.
        List<EligibilityFeed.Item> ordered = items.stream().sorted(Comparator.comparing((EligibilityFeed.Item item) -> "OPEN".equals(item.status()) ? 0 : 1)).toList();
        int processed = 0;
        long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(MAX_MINUTES);
        for (EligibilityFeed.Item item : ordered) {
            // 제공처가 느려도 저녁 알림 작업을 오래 막지 않도록 한 번 실행 시간을 제한한다(남은 공고는 다음 날).
            if (processed >= MAX_PER_RUN || System.nanoTime() > deadline) break;
            Map<Integer, Long> supplies = supplies(item);
            if (supplies.isEmpty()) continue;
            Object[] before = previous.get(item.id());
            // 토지임대부는 비교하지 않는다. 운영 화면에서 이유가 보이도록 상태만 남긴다(외부 호출 없음).
            if (PriceGapRules.landLease(item.houseNm())) {
                if (before == null || !"LAND_LEASE".equals(before[0])) { save(item.id(), "LAND_LEASE", null, null, null, now); report.merge("LAND_LEASE", 1, Integer::sum); }
                continue;
            }
            // 정정 공고로 분양가가 바뀌었으면 기간과 상관없이 다시 계산한다.
            boolean changed = before != null && "READY".equals(before[0]) && !Objects.equals(supplies.get((Integer) before[2]), before[3]);
            // 위치를 못 찾았던 공고에 운영자 확인 주소가 생겼으면 재시도 기간(7일)을 기다리지 않고 다시 계산한다.
            String operator = confirmed.of(item.id());
            // 같은 확인 주소로 이미 한 번 시도해 못 찾았으면 다시 강제하지 않고 원래 재시도 기간을 따른다(매일 카카오 호출 반복 방지).
            if (before != null && "NO_LOCATION".equals(before[0]) && operator != null && !operator.equals(triedConfirmed.get(item.id()))) changed = true;
            if (operator != null) triedConfirmed.put(item.id(), operator);
            if (before != null && !changed && fresh((String) before[0], (LocalDateTime) before[1], now)) continue;
            processed++;
            // 아직 보여 주는 READY 값(분양가 그대로·7일 이내)은 이번 재계산이 일시 실패해도 지우지 않고 다음 날 다시 시도한다.
            boolean keep = before != null && "READY".equals(before[0]) && !changed
                && !((LocalDateTime) before[1]).isBefore(now.minusDays(PriceGapStore.SHOW_DAYS));
            String status = compute(item, operator, supplies, now, keep);
            report.merge(status, 1, Integer::sum);
        }
        jdbc.update("DELETE FROM TB_PRICE_GAP WHERE COMPUTED_DT < ?", Timestamp.valueOf(now.minusDays(KEEP_DAYS)));
        return report;
    }

    /** 날짜 기준으로 판단한다(매일 같은 시각에 돌므로 시각 비교면 하루씩 밀린다). UNAVAILABLE은 다음 날 다시 계산한다. */
    static boolean fresh(String status, LocalDateTime computed, LocalDateTime now) {
        int days = "READY".equals(status) ? READY_DAYS : "UNAVAILABLE".equals(status) ? 1 : RETRY_DAYS;
        return ChronoUnit.DAYS.between(computed.toLocalDate(), now.toLocalDate()) < days;
    }
    /** 전용면적(반올림) → 그 면적 주택형의 최고 공급금액. */
    static Map<Integer, Long> supplies(EligibilityFeed.Item item) {
        Map<Integer, Long> out = new TreeMap<>();
        for (var unit : item.units()) {
            // 화면(usePriceGap)과 같이 전용 10㎡ 미만 표기는 비교 면적에서 뺀다.
            if (unit.area() == null || unit.area().doubleValue() < 10 || unit.amountManwon() == null || unit.amountManwon() <= 0) continue;
            out.merge((int) Math.round(unit.area().doubleValue()), unit.amountManwon(), Math::max);
        }
        return out;
    }

    /** 실거래를 조회할 면적 수 상한(면적마다 통계를 만들므로 응답·계산 크기를 묶어 둔다). */
    static final int MAX_AREAS = 8;
    /** 면적대 기준: 60㎡ 이하는 59㎡, 60~85㎡는 84㎡, 85㎡ 초과는 114㎡에 가까운 면적을 대표로 둔다. */
    private static final int[][] BANDS = {{0, 60, 59}, {61, 85, 84}, {86, Integer.MAX_VALUE, 114}};
    /**
     * 비교할 면적을 고른다. 상한보다 적으면 전부. 많으면 면적대(60 이하·60~85·85 초과)마다 대표 하나를 먼저 넣고
     * 나머지는 84㎡에 가까운 순으로 채운다. 작은 면적순으로 자르면 소형 주택형이 많은 공고에서 84㎡가 빠지기 때문이다.
     * 결과는 면적 오름차순.
     */
    static List<Integer> comparisonAreas(Collection<Integer> areas, int limit) {
        List<Integer> all = areas.stream().distinct().sorted().toList();
        if (all.size() <= limit) return all;
        LinkedHashSet<Integer> picked = new LinkedHashSet<>();
        for (int[] band : BANDS)
            all.stream().filter(area -> area >= band[0] && area <= band[1])
                .min(Comparator.<Integer>comparingInt(area -> Math.abs(area - band[2])).thenComparingInt(area -> area)).ifPresent(picked::add);
        all.stream().sorted(Comparator.<Integer>comparingInt(area -> Math.abs(area - 84)).thenComparingInt(area -> area))
            .forEach(area -> { if (picked.size() < limit) picked.add(area); });
        return picked.stream().limit(limit).sorted().toList();
    }

    // 공고 하나 계산: 위치 확인 → 면적별 실거래 → 대표 면적 고르기 → 저장. 결과 상태 문자열을 돌려준다.
    // DB 오류는 위로 던져 전체 작업을 멈추고, 외부 API 오류는 이 공고만 UNAVAILABLE로 둔다.
    private String compute(EligibilityFeed.Item item, String operatorAddress, Map<Integer, Long> supplies, LocalDateTime now, boolean keepPrevious) {
        try {
            var location = locator.locate(item.address(), operatorAddress);
            if (location.isEmpty()) { save(item.id(), "NO_LOCATION", null, null, null, now); return "NO_LOCATION"; }
            List<BigDecimal> areas = comparisonAreas(supplies.keySet(), MAX_AREAS).stream().map(BigDecimal::valueOf).toList();
            var gap = research.priceGap(location.get().bcode(), location.get().dong(), areas);
            // 응답 시간 한도로 일부 달만 받았으면 한 번 더 부른다. 받은 달은 캐시에 있어 나머지 달만 새 한도 안에서 받는다.
            if (gap.partial()) gap = research.priceGap(location.get().bcode(), location.get().dong(), areas);
            // 그래도 일부 달뿐이면 저장하지 않고 다음 날 다시 계산한다(며칠씩 고정하지 않게).
            if ("UNAVAILABLE".equals(gap.status()) || "NOT_CONFIGURED".equals(gap.status()) || gap.partial()) return unavailable(item.id(), location.get(), now, keepPrevious);
            var row = PriceGapRules.representative(PriceGapRules.rows(supplies, gap.areas()));
            if (row.isEmpty()) { save(item.id(), "NO_TRADES", location.get(), null, null, now); return "NO_TRADES"; }
            save(item.id(), "READY", location.get(), row.get(), gap.months(), now);
            return "READY";
        } catch (DataAccessException storage) {
            throw storage;
        } catch (Exception failure) {
            log.warn("주변 시세 비교 계산 실패: id={}, error={}", item.id(), failure.getClass().getSimpleName());
            return unavailable(item.id(), null, now, keepPrevious);
        }
    }
    private String unavailable(String id, AddressLocator.Location location, LocalDateTime now, boolean keepPrevious) {
        if (keepPrevious) return "KEPT";
        save(id, "UNAVAILABLE", location, null, null, now);
        return "UNAVAILABLE";
    }

    /** 지우고 다시 넣는 두 문장을 한 트랜잭션으로 묶어, 넣기가 실패해도 기존 값이 사라지지 않게 한다. */
    private void save(String id, String status, AddressLocator.Location location, PriceGapRules.Row row, Integer months, LocalDateTime now) {
        if (transactions == null) { saveNow(id, status, location, row, months, now); return; }
        transactions.executeWithoutResult(tx -> saveNow(id, status, location, row, months, now));
    }
    private void saveNow(String id, String status, AddressLocator.Location location, PriceGapRules.Row row, Integer months, LocalDateTime now) {
        jdbc.update("DELETE FROM TB_PRICE_GAP WHERE ANNOUNCEMENT_ID=?", id);
        jdbc.update("INSERT INTO TB_PRICE_GAP (ANNOUNCEMENT_ID,STATUS,BCODE,DONG,AREA,SUPPLY_MANWON,BASIS,SCOPE,BASIS_COUNT,CONFIDENCE,Q1_MANWON,Q3_MANWON,MONTHS,COMPUTED_DT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, status, location == null ? null : location.bcode(), location == null ? null : location.dong(),
            row == null ? null : row.area(), row == null ? null : row.supply(), row == null ? null : row.basis(), row == null ? null : row.scope(),
            row == null ? null : row.basisCount(), row == null ? null : row.confidence(), row == null ? null : row.q1(), row == null ? null : row.q3(),
            months, Timestamp.valueOf(now));
    }
}
