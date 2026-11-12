package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 아파트 공고의 "가장 인기 있는 주택형 1순위 경쟁률" 예상 투표. 접수 마감일까지만 받고,
 * 청약홈 경쟁률이 공개되면 실제 구간을 함께 돌려줘 발표·결과 시점에 다시 볼 이유를 만든다.
 * 방문자 식별자는 익명 측정과 같은 브라우저 무작위 값이며 회원 정보와 연결하지 않는다.
 */
@RestController
public class RatePoll {
    /** 구간 하한(포함). 마지막 구간은 상한이 없다. */
    static final double[] FLOORS = {0, 1, 5, 10, 30, 100};
    static final List<String> LABELS = List.of("미달 (1:1 미만)", "1 ~ 5 : 1", "5 ~ 10 : 1", "10 ~ 30 : 1", "30 ~ 100 : 1", "100 : 1 이상");
    private final JdbcTemplate jdbc; private final PlannerSummary summaries; private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public RatePoll(JdbcTemplate jdbc, PlannerSummary summaries) { this(jdbc, summaries, Clock.system(ZoneId.of("Asia/Seoul"))); }
    RatePoll(JdbcTemplate jdbc, PlannerSummary summaries, Clock clock) { this.jdbc = jdbc; this.summaries = summaries; this.clock = clock; }

    static final int VOTERS_PER_IP = 20;
    private final com.google.common.cache.Cache<String, java.util.Set<String>> voters = com.google.common.cache.CacheBuilder.newBuilder()
            .maximumSize(100_000).expireAfterWrite(java.time.Duration.ofDays(1)).build();
    boolean voterAllowed(String ipKey, String announcementId, String visitorId) {
        java.util.Set<String> seen;
        try { seen = voters.get(ipKey + "|" + announcementId, () -> java.util.concurrent.ConcurrentHashMap.newKeySet()); }
        catch (java.util.concurrent.ExecutionException impossible) { return true; }
        synchronized (seen) {
            if (seen.contains(visitorId)) return true;
            if (seen.size() >= VOTERS_PER_IP) return false;
            seen.add(visitorId);
            return true;
        }
    }

    public record Vote(@NotNull UUID visitorId, @NotNull @Min(0) @Max(5) Integer choice) { }

    static int bucket(double rate) {
        for (int i = FLOORS.length - 1; i >= 0; i--) if (rate >= FLOORS[i]) return i;
        return 0;
    }
    /** 1순위 경쟁률 중 숫자로 공개된 최고값. 미달 표기(△ 등)·빈 값은 건너뛴다. */
    static Double topRate(List<String> rates) {
        return rates.stream().map(value -> {
            try { String text = value == null ? "" : value.replace(",", "").trim(); return text.matches("[0-9]+(?:\\.[0-9]+)?") ? new BigDecimal(text).doubleValue() : null; }
            catch (NumberFormatException invalid) { return null; }
        }).filter(Objects::nonNull).max(Double::compare).orElse(null);
    }
    private static boolean valid(String house, String notice) { return house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}"); }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    private Optional<PlannerSummary.Summary> summary(String house, String notice) {
        try { return Optional.of(summaries.get("apt", house, notice)); } catch (EntityNotFoundException | IllegalArgumentException missing) { return Optional.empty(); }
    }

    @GetMapping("/api/v1/polls/rate/apt/{house}/{notice}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String house, @PathVariable String notice, @RequestParam(required = false) UUID visitorId) {
        if (!valid(house, notice)) return ResponseEntity.notFound().build();
        var found = summary(house, notice);
        return found.map(s -> ResponseEntity.ok(view(s, house, notice, visitorId))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/api/v1/polls/rate/apt/{house}/{notice}")
    public ResponseEntity<Map<String, Object>> vote(@PathVariable String house, @PathVariable String notice, @Valid @RequestBody Vote vote,
                                                    jakarta.servlet.http.HttpServletRequest request) {
        if (!valid(house, notice)) return ResponseEntity.notFound().build();
        var found = summary(house, notice);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> current = view(found.get(), house, notice, vote.visitorId());
        if (!Boolean.TRUE.equals(current.get("open"))) return ResponseEntity.status(409).body(Map.of("message", "투표가 마감되었어요."));
        String id = "apt/" + house + "/" + notice;
        // 방문자 ID는 브라우저가 만들므로, 같은 네트워크에서 공고 하나에 새 ID로 표를 늘리는 것을 하루 몇 개로 제한한다.
        // 이미 표가 있는 방문자의 선택 변경은 언제나 허용한다(재시작으로 메모리가 비어도 DB 기준). 공용 IP(회사·통신사)를 고려해 여유를 둔다.
        boolean existing = current.get("myChoice") != null;
        if (!existing && !voterAllowed(com.toady.web.core.security.RequestLimitFilter.limitKey(com.toady.web.core.security.RequestLimitFilter.clientIp(request)), id, vote.visitorId().toString()))
            return ResponseEntity.status(429).body(Map.of("message", "같은 네트워크에서 이 공고에 투표한 사람이 많아 잠시 후 다시 시도해 주세요."));
        var now = java.sql.Timestamp.valueOf(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
        // 같은 방문자는 마감 전까지 선택을 바꿀 수 있다(행 하나). MariaDB·H2 공통 문법으로 갱신 후 없으면 추가한다.
        if (jdbc.update("UPDATE TB_RATE_POLL SET CHOICE=?,REG_DT=? WHERE ANNOUNCEMENT_ID=? AND VISITOR_ID=?", vote.choice(), now, id, vote.visitorId().toString()) == 0) {
            try { jdbc.update("INSERT INTO TB_RATE_POLL (ANNOUNCEMENT_ID,VISITOR_ID,CHOICE,REG_DT) VALUES (?,?,?,?)", id, vote.visitorId().toString(), vote.choice(), now); }
            catch (org.springframework.dao.DuplicateKeyException raced) { jdbc.update("UPDATE TB_RATE_POLL SET CHOICE=?,REG_DT=? WHERE ANNOUNCEMENT_ID=? AND VISITOR_ID=?", vote.choice(), now, id, vote.visitorId().toString()); }
        }
        return ResponseEntity.ok(view(found.get(), house, notice, vote.visitorId()));
    }

    Map<String, Object> view(PlannerSummary.Summary s, String house, String notice, UUID visitorId) {
        String id = "apt/" + house + "/" + notice;
        Double actual = topRate(jdbc.queryForList("SELECT CMPET_RATE FROM TB_APT_LTTOT_PBLANC_CMPET WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? AND SUBSCRPT_RANK_CODE='1'", String.class, house, notice));
        LocalDate last = s.endDate() != null ? s.endDate() : s.startDate();
        boolean open = actual == null && last != null && !today().isAfter(last);
        long[] counts = new long[LABELS.size()];
        jdbc.query("SELECT CHOICE,COUNT(*) FROM TB_RATE_POLL WHERE ANNOUNCEMENT_ID=? GROUP BY CHOICE", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
            int choice = r.getInt(1); if (choice >= 0 && choice < counts.length) counts[choice] = r.getLong(2);
        }, id);
        Integer mine = visitorId == null ? null : jdbc.query("SELECT CHOICE FROM TB_RATE_POLL WHERE ANNOUNCEMENT_ID=? AND VISITOR_ID=?", (r, n) -> r.getInt(1), id, visitorId.toString()).stream().findFirst().orElse(null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("open", open);
        body.put("closesOn", last == null ? null : last.toString());
        body.put("labels", LABELS);
        body.put("counts", Arrays.stream(counts).boxed().toList());
        body.put("total", Arrays.stream(counts).sum());
        body.put("myChoice", mine);
        body.put("actualRate", actual);
        body.put("actualChoice", actual == null ? null : bucket(actual));
        return body;
    }
}
