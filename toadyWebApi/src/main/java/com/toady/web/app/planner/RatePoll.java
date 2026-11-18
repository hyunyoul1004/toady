package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.core.security.RequestLimitFilter;

/**
 * 아파트 공고의 "가장 인기 있는 주택형 1순위 경쟁률" 예상 투표. 접수 마감일까지만 받고,
 * 청약홈 경쟁률이 공개되면 투표를 닫고, 1순위 모든 지역(해당·기타)이 반영된 뒤 실제 구간을 돌려줘 발표·결과 시점에 다시 볼 이유를 만든다.
 * 운영자가 숨긴 공고는 없는 공고처럼 404로 답한다.
 * 방문자 식별자는 익명 측정과 같은 브라우저 무작위 값이며 회원 정보와 연결하지 않는다.
 */
// @RestController: JSON 응답 컨트롤러. 테이블 TB_RATE_POLL에 방문자별 선택 한 행을 저장한다.
@RestController
public class RatePoll {
    /** 구간 하한(포함). 마지막 구간은 상한이 없다. */
    static final double[] FLOORS = {0, 1, 5, 10, 30, 100};
    static final List<String> LABELS = List.of("미달 (1:1 미만)", "1 ~ 5 : 1", "5 ~ 10 : 1", "10 ~ 30 : 1", "30 ~ 100 : 1", "100 : 1 이상");
    private final JdbcTemplate jdbc; private final PlannerSummary summaries; private final Clock clock;

    // @Autowired: 생성자가 둘이라 스프링이 쓸 생성자를 지정한다. 아래 생성자는 테스트에서 시계를 바꿔 넣을 때 쓴다.
    @Autowired
    public RatePoll(JdbcTemplate jdbc, PlannerSummary summaries) { this(jdbc, summaries, Clock.system(ZoneId.of("Asia/Seoul"))); }
    RatePoll(JdbcTemplate jdbc, PlannerSummary summaries, Clock clock) { this.jdbc = jdbc; this.summaries = summaries; this.clock = clock; }

    /** 운영자가 숨긴 공고. 없는 공고처럼 404로 답한다. 빈이 없으면(단위 테스트) 아무것도 숨기지 않는다. */
    private HiddenAnnouncements hidden;
    // @Autowired(required=false) 메서드 주입: 생성자를 늘리지 않고 숨김 확인 빈이 있을 때만 넣는다.
    @Autowired(required = false)
    void useHidden(HiddenAnnouncements hidden) { this.hidden = hidden; }

    static final int VOTERS_PER_IP = 20;
    // IP+공고별로 하루 동안 본 방문자 ID 묶음(메모리). 한 IP에서 새 ID로 표를 부풀리는 것을 막는 용도다.
    private final Cache<String, Set<String>> voters = CacheBuilder.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofDays(1)).build();
    /** 이미 본 방문자면 허용, 새 방문자면 한 IP당 공고별 VOTERS_PER_IP명까지만 허용한다. */
    boolean voterAllowed(String ipKey, String announcementId, String visitorId) {
        Set<String> seen;
        try { seen = voters.get(ipKey + "|" + announcementId, () -> ConcurrentHashMap.newKeySet()); }
        catch (ExecutionException impossible) { return true; }
        synchronized (seen) {
            if (seen.contains(visitorId)) return true;
            if (seen.size() >= VOTERS_PER_IP) return false;
            seen.add(visitorId);
            return true;
        }
    }

    /** 투표 본문. visitorId: 브라우저가 만든 무작위 UUID, choice: 구간 번호(0~5). */
    public record Vote(@NotNull UUID visitorId, @NotNull @Min(0) @Max(5) Integer choice) { }

    /** 경쟁률이 들어가는 구간 번호(FLOORS 기준). */
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
    /** 청약홈 미달 표기: (△3)·△3·▲3·(3). 화면 rateFormat.js parseRate와 같은 규칙. */
    static boolean shortage(String value) {
        String text = value == null ? "" : value.replace(",", "").trim();
        return text.matches("^\\(?\\s*[△▲]\\s*[0-9]+\\s*\\)?$") || text.matches("^\\(\\s*[0-9]+\\s*\\)$");
    }
    private static boolean valid(String house, String notice) { return house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}"); }

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    private Optional<PlannerSummary.Summary> summary(String house, String notice) {
        if (hidden != null && hidden.hidden("apt", house, notice)) return Optional.empty();
        try { return Optional.of(summaries.get("apt", house, notice)); } catch (EntityNotFoundException | IllegalArgumentException missing) { return Optional.empty(); }
    }

    // @GetMapping: 투표 현황 조회. @PathVariable로 공고 키를, @RequestParam(required=false)로 내 방문자 ID(선택)를 받는다.
    @GetMapping("/api/v1/polls/rate/apt/{house}/{notice}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String house, @PathVariable String notice, @RequestParam(required = false) UUID visitorId) {
        if (!valid(house, notice)) return ResponseEntity.notFound().build();
        var found = summary(house, notice);
        return found.map(s -> ResponseEntity.ok(view(s, house, notice, visitorId))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // @PostMapping: 투표하기(또는 선택 바꾸기). 마감 뒤면 409, 같은 네트워크에서 너무 많으면 429.
    // HttpServletRequest를 파라미터로 받으면 스프링이 현재 요청 객체를 넣어 준다(접속 IP 확인용).
    @PostMapping("/api/v1/polls/rate/apt/{house}/{notice}")
    public ResponseEntity<Map<String, Object>> vote(@PathVariable String house, @PathVariable String notice, @Valid @RequestBody Vote vote,
                                                    HttpServletRequest request) {
        if (!valid(house, notice)) return ResponseEntity.notFound().build();
        var found = summary(house, notice);
        if (found.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> current = view(found.get(), house, notice, vote.visitorId());
        if (!Boolean.TRUE.equals(current.get("open"))) return ResponseEntity.status(409).body(Map.of("message", "투표가 마감되었어요."));
        String id = "apt/" + house + "/" + notice;
        // 방문자 ID는 브라우저가 만들므로, 같은 네트워크에서 공고 하나에 새 ID로 표를 늘리는 것을 하루 몇 개로 제한한다.
        // 이미 표가 있는 방문자의 선택 변경은 언제나 허용한다(재시작으로 메모리가 비어도 DB 기준). 공용 IP(회사·통신사)를 고려해 여유를 둔다.
        boolean existing = current.get("myChoice") != null;
        if (!existing && !voterAllowed(RequestLimitFilter.limitKey(RequestLimitFilter.clientIp(request)), id, vote.visitorId().toString()))
            return ResponseEntity.status(429).body(Map.of("message", "같은 네트워크에서 이 공고에 투표한 사람이 많아 잠시 후 다시 시도해 주세요."));
        var now = Timestamp.valueOf(LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))));
        // 같은 방문자는 마감 전까지 선택을 바꿀 수 있다(행 하나). MariaDB·H2 공통 문법으로 갱신 후 없으면 추가한다.
        if (jdbc.update("UPDATE TB_RATE_POLL SET CHOICE=?,REG_DT=? WHERE ANNOUNCEMENT_ID=? AND VISITOR_ID=?", vote.choice(), now, id, vote.visitorId().toString()) == 0) {
            try { jdbc.update("INSERT INTO TB_RATE_POLL (ANNOUNCEMENT_ID,VISITOR_ID,CHOICE,REG_DT) VALUES (?,?,?,?)", id, vote.visitorId().toString(), vote.choice(), now); }
            catch (DuplicateKeyException raced) { jdbc.update("UPDATE TB_RATE_POLL SET CHOICE=?,REG_DT=? WHERE ANNOUNCEMENT_ID=? AND VISITOR_ID=?", vote.choice(), now, id, vote.visitorId().toString()); }
        }
        return ResponseEntity.ok(view(found.get(), house, notice, vote.visitorId()));
    }

    private static final java.util.regex.Pattern RANK_ONE = java.util.regex.Pattern.compile("1순위 \\(([^)]*)\\)");
    /**
     * 1순위 경쟁률이 모든 접수 지역(해당·경기·기타)까지 반영됐는지. 해당지역 결과만 먼저 공개된 날 정답을 확정하면
     * 다음 날 기타지역 접수로 최고 경쟁률이 바뀔 수 있어서다. 아래 중 하나면 확정한다.
     * ① 마지막 1순위 접수일(없으면 접수 마감일)의 다음 날 이후 ② 어느 주택형이든 1순위 접수 지역 수만큼 거주 구분 행이 모두 들어옴.
     * 1순위 지역별 접수일이 없는 공고(옛 자료)는 들어온 결과를 그대로 확정한다.
     */
    static boolean rankOneSettled(PlannerSummary.Summary s, Collection<Set<String>> regionsByModel, LocalDate today) {
        LocalDate lastRankOne = null;
        Set<String> areas = new LinkedHashSet<>();
        for (var event : s.events()) {
            var matcher = RANK_ONE.matcher(event.label() == null ? "" : event.label());
            if (!matcher.find() || event.date() == null) continue;
            for (String area : matcher.group(1).replace("지역", "").split("·")) if (!area.isBlank()) areas.add(area.trim());
            if (lastRankOne == null || event.date().isAfter(lastRankOne)) lastRankOne = event.date();
        }
        if (areas.isEmpty()) return true;
        LocalDate last = lastRankOne != null ? lastRankOne : s.endDate();
        if (last != null && today.isAfter(last)) return true;
        return regionsByModel.stream().anyMatch(regions -> regions.size() >= areas.size());
    }

    /** 화면에 줄 투표 상태: 열림 여부, 마감일, 구간별 표 수, 내 선택, 실제 경쟁률과 그 구간. */
    Map<String, Object> view(PlannerSummary.Summary s, String house, String notice, UUID visitorId) {
        String id = "apt/" + house + "/" + notice;
        List<String> firstRank = new ArrayList<>();
        Map<String, Set<String>> regionsByModel = new HashMap<>();
        jdbc.query("SELECT MODEL_NO,RESIDE_SECD,CMPET_RATE FROM TB_APT_LTTOT_PBLANC_CMPET WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? AND SUBSCRPT_RANK_CODE='1'",
            (RowCallbackHandler) r -> {
                firstRank.add(r.getString(3));
                regionsByModel.computeIfAbsent(String.valueOf(r.getString(1)), key -> new HashSet<>()).add(String.valueOf(r.getString(2)));
            }, house, notice);
        Double top = topRate(firstRank);
        // 모든 주택형이 미달(△)로만 공개되면 숫자 경쟁률이 없어도 결과가 나온 것이다. 이때 정답은 '미달'(0번 선택지).
        boolean allShort = top == null && firstRank.stream().anyMatch(RatePoll::shortage);
        boolean published = top != null || allShort;
        LocalDate last = s.endDate() != null ? s.endDate() : s.startDate();
        // 결과가 하나라도 나오면(해당지역만이라도) 투표는 닫는다. 정답은 1순위 모든 지역이 반영된 뒤에만 확정한다.
        boolean open = !published && last != null && !today().isAfter(last);
        boolean settled = published && rankOneSettled(s, regionsByModel.values(), today());
        Double actual = settled ? top : null;
        long[] counts = new long[LABELS.size()];
        jdbc.query("SELECT CHOICE,COUNT(*) FROM TB_RATE_POLL WHERE ANNOUNCEMENT_ID=? GROUP BY CHOICE", (RowCallbackHandler) r -> {
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
        body.put("actualChoice", !settled ? null : actual != null ? Integer.valueOf(bucket(actual)) : Integer.valueOf(0));
        // 해당지역 결과만 나와 정답을 미룬 상태(화면은 '집계 중'으로 보여 줄 수 있다).
        body.put("settling", published && !settled);
        return body;
    }
}
