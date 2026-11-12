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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
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
import java.util.*;

/**
 * 접수 중·예정 공고의 '주변 시세 대비' 요약을 매일 미리 계산해 TB_PRICE_GAP(database.sql 18번)에 둔다.
 * 맞춤 청약·줍줍 목록 뱃지, 나의 청약 비교표, 공고 공개 페이지, 줍줍 알림이 이 값을 쓴다(화면 상세는 실시간 계산을 그대로 쓴다).
 * 정확한 지번 위치를 확인한 공고만 계산하고, 신뢰도가 '참고'뿐이면 READY로 두지 않는다.
 * 카카오 REST 키나 실거래 키가 없으면 계산하지 않으며, 테이블이 없으면 조회는 빈 결과를 돌려준다.
 */
@RestController
@Validated
public class PriceGapSnapshots {
    private static final Logger log = LoggerFactory.getLogger(PriceGapSnapshots.class);
    static final int MAX_PER_RUN = 60, READY_DAYS = 3, RETRY_DAYS = 7, KEEP_DAYS = 180;
    @org.springframework.context.annotation.Configuration @org.springframework.scheduling.annotation.EnableScheduling static class Scheduling { }
    private final JdbcTemplate jdbc;
    private final PriceGapStore store;
    private final EligibilityFeed feed;
    private final AddressLocator locator;
    private final NeighborhoodResearchService research;
    private final Clock clock;

    @Autowired
    public PriceGapSnapshots(JdbcTemplate jdbc, PriceGapStore store, EligibilityFeed feed, AddressLocator locator, NeighborhoodResearchService research) {
        this(jdbc, store, feed, locator, research, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    PriceGapSnapshots(JdbcTemplate jdbc, PriceGapStore store, EligibilityFeed feed, AddressLocator locator, NeighborhoodResearchService research, Clock clock) {
        this.jdbc = jdbc; this.store = store; this.feed = feed; this.locator = locator; this.research = research; this.clock = clock;
    }

    public record Request(@NotNull @Size(max = 50) List<@NotNull @Pattern(regexp = "(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }

    @PostMapping("/api/v1/planner/price-gaps")
    public List<PriceGapStore.Gap> lookup(@Valid @RequestBody Request request) { return List.copyOf(store.forIds(request.ids()).values()); }

    /** 저녁 묶음 알림 전에 미리 계산한다(기본 17:20). PRICE_GAP_CRON=- 이면 끈다. */
    @Scheduled(cron = "${toady.research.gap-cron:0 20 17 * * *}", zone = "Asia/Seoul")
    public void scheduled() {
        if (!locator.enabled() || !research.tradesEnabled()) return;
        try {
            var report = run();
            log.info("주변 시세 비교 미리 계산: ready={}, noLocation={}, noTrades={}, unavailable={}", report.get("READY"), report.get("NO_LOCATION"), report.get("NO_TRADES"), report.get("UNAVAILABLE"));
        } catch (DataAccessException missing) {
            log.warn("TB_PRICE_GAP을 사용할 수 없어 주변 시세 비교를 미리 계산하지 않습니다. database.sql 18번을 적용하세요.");
        }
    }

    /** 공고별로 계산해 저장하고 상태별 건수를 돌려준다. 한 번에 최대 60건, 최근에 계산한 공고는 건너뛴다. */
    public Map<String, Integer> run() {
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<String, Integer> report = new LinkedHashMap<>(Map.of("READY", 0, "NO_LOCATION", 0, "NO_TRADES", 0, "UNAVAILABLE", 0));
        @SuppressWarnings("unchecked")
        List<EligibilityFeed.Item> items = (List<EligibilityFeed.Item>) feed.load(today).get("items");
        Map<String, Object[]> previous = new HashMap<>();
        jdbc.query("SELECT ANNOUNCEMENT_ID,STATUS,COMPUTED_DT FROM TB_PRICE_GAP", (org.springframework.jdbc.core.RowCallbackHandler) r ->
            previous.put(r.getString(1), new Object[]{r.getString(2), r.getTimestamp(3).toLocalDateTime()}));
        // 접수 중인 공고를 먼저 계산한다.
        List<EligibilityFeed.Item> ordered = items.stream().sorted(Comparator.comparing((EligibilityFeed.Item item) -> "OPEN".equals(item.status()) ? 0 : 1)).toList();
        int processed = 0;
        for (EligibilityFeed.Item item : ordered) {
            if (processed >= MAX_PER_RUN) break;
            Map<Integer, Long> supplies = supplies(item);
            if (supplies.isEmpty()) continue;
            Object[] before = previous.get(item.id());
            if (before != null && fresh((String) before[0], (LocalDateTime) before[1], now)) continue;
            processed++;
            String status = compute(item, supplies, now);
            report.merge(status, 1, Integer::sum);
        }
        jdbc.update("DELETE FROM TB_PRICE_GAP WHERE COMPUTED_DT < ?", Timestamp.valueOf(now.minusDays(KEEP_DAYS)));
        return report;
    }

    static boolean fresh(String status, LocalDateTime computed, LocalDateTime now) {
        int days = "READY".equals(status) ? READY_DAYS : "UNAVAILABLE".equals(status) ? 1 : RETRY_DAYS;
        return computed.isAfter(now.minusDays(days));
    }
    /** 전용면적(반올림) → 그 면적 주택형의 최고 공급금액. */
    static Map<Integer, Long> supplies(EligibilityFeed.Item item) {
        Map<Integer, Long> out = new TreeMap<>();
        for (var unit : item.units()) {
            if (unit.area() == null || unit.amountManwon() == null || unit.amountManwon() <= 0) continue;
            out.merge((int) Math.round(unit.area().doubleValue()), unit.amountManwon(), Math::max);
        }
        return out;
    }

    private String compute(EligibilityFeed.Item item, Map<Integer, Long> supplies, LocalDateTime now) {
        try {
            var location = locator.locate(item.address());
            if (location.isEmpty()) { save(item.id(), "NO_LOCATION", null, null, null, now); return "NO_LOCATION"; }
            List<BigDecimal> areas = supplies.keySet().stream().limit(8).map(BigDecimal::valueOf).toList();
            var gap = research.priceGap(location.get().bcode(), location.get().dong(), areas);
            if ("UNAVAILABLE".equals(gap.status()) || "NOT_CONFIGURED".equals(gap.status())) { save(item.id(), "UNAVAILABLE", location.get(), null, null, now); return "UNAVAILABLE"; }
            var row = PriceGapRules.representative(PriceGapRules.rows(supplies, gap.areas()));
            if (row.isEmpty()) { save(item.id(), "NO_TRADES", location.get(), null, null, now); return "NO_TRADES"; }
            save(item.id(), "READY", location.get(), row.get(), gap.months(), now);
            return "READY";
        } catch (DataAccessException storage) {
            throw storage;
        } catch (Exception failure) {
            log.warn("주변 시세 비교 계산 실패: id={}, error={}", item.id(), failure.getClass().getSimpleName());
            save(item.id(), "UNAVAILABLE", null, null, null, now);
            return "UNAVAILABLE";
        }
    }

    private void save(String id, String status, AddressLocator.Location location, PriceGapRules.Row row, Integer months, LocalDateTime now) {
        jdbc.update("DELETE FROM TB_PRICE_GAP WHERE ANNOUNCEMENT_ID=?", id);
        jdbc.update("INSERT INTO TB_PRICE_GAP (ANNOUNCEMENT_ID,STATUS,BCODE,DONG,AREA,SUPPLY_MANWON,BASIS,SCOPE,BASIS_COUNT,CONFIDENCE,Q1_MANWON,Q3_MANWON,MONTHS,COMPUTED_DT) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            id, status, location == null ? null : location.bcode(), location == null ? null : location.dong(),
            row == null ? null : row.area(), row == null ? null : row.supply(), row == null ? null : row.basis(), row == null ? null : row.scope(),
            row == null ? null : row.basisCount(), row == null ? null : row.confidence(), row == null ? null : row.q1(), row == null ? null : row.q3(),
            months, Timestamp.valueOf(now));
    }
}
