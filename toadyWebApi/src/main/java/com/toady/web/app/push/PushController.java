package com.toady.web.app.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.PlannerSummary;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 웹 푸시 구독·해지·테스트 발송. 로그인 없이도 구독할 수 있고, 로그인 상태면 회원의 관심 공고로 범위를 잡는다.
 * 엔드포인트는 경로·쿼리에 싣지 않고 본문으로만 받는다(접근 로그에 남지 않도록).
 */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {
    static final String ID = "(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}";
    private static final Duration TEST_INTERVAL = Duration.ofSeconds(30);
    private final PushSubscriptions store; private final WebPushSender sender; private final PlannerSummary summaries; private final ObjectMapper mapper;
    private final Clock clock = Clock.system(PushSubscriptions.KST);
    private final Map<String, Instant> lastTest = new ConcurrentHashMap<>();

    public PushController(PushSubscriptions store, WebPushSender sender, PlannerSummary summaries, ObjectMapper mapper) {
        this.store = store; this.sender = sender; this.summaries = summaries; this.mapper = mapper;
    }

    public record Keys(@NotNull @Pattern(regexp = "[A-Za-z0-9_-]{80,100}={0,2}") String p256dh, @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{16,40}={0,2}") String auth) { }
    public record Subscribe(@NotBlank @Size(max = 1000) String endpoint, @NotNull @Valid Keys keys, UUID visitorId, Boolean favorites,
            @Size(max = 6) List<@NotNull @Pattern(regexp = "[a-z0-9-]{1,40}") String> areas,
            @Size(max = 50) List<@NotNull @Pattern(regexp = ID) String> favoriteIds) { }
    public record Endpoint(@NotBlank @Size(max = 1000) String endpoint) { }
    public record Test(@NotBlank @Size(max = 1000) String endpoint, @Pattern(regexp = ID) String announcementId) { }

    private boolean available() { return sender.enabled() && store.ready(); }
    private static ResponseEntity<Map<String, Object>> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }

    @GetMapping("/config")
    public Map<String, Object> config() {
        boolean on = available();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("enabled", on);
        body.put("publicKey", on ? sender.publicKey() : null);
        return body;
    }

    @PutMapping("/subscription")
    public ResponseEntity<Map<String, Object>> subscribe(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody Subscribe request) {
        if (!available()) return error(503, "알림 기능을 준비 중이에요.");
        if (!WebPushSender.allowedEndpoint(request.endpoint())) return error(400, "지원하지 않는 브라우저 알림 주소예요.");
        try { WebPushCrypto.publicKey(WebPushCrypto.b64(request.keys().p256dh())); if (WebPushCrypto.b64(request.keys().auth()).length != 16) throw new IllegalArgumentException(); }
        catch (Exception invalid) { return error(400, "브라우저 알림 키를 확인하지 못했어요."); }
        List<String> areas = Optional.ofNullable(request.areas()).orElse(List.of()).stream().distinct().filter(PushTopics::valid).toList();
        List<String> favoriteIds = user != null ? List.of() : Optional.ofNullable(request.favoriteIds()).orElse(List.of()).stream().distinct().toList();
        var scope = new PushSubscriptions.Scope(user == null ? null : user.getUserSeq(), request.visitorId(), !Boolean.FALSE.equals(request.favorites()), favoriteIds, areas);
        store.upsert(request.endpoint(), strip(request.keys().p256dh()), strip(request.keys().auth()), scope, clock);
        return ResponseEntity.ok(view(store.find(request.endpoint())));
    }

    /** 이 브라우저의 구독 범위. 엔드포인트를 본문으로 보내기 위해 POST를 쓴다. */
    @PostMapping("/subscription/lookup")
    public ResponseEntity<Map<String, Object>> lookup(@Valid @RequestBody Endpoint request) {
        if (!available()) return error(503, "알림 기능을 준비 중이에요.");
        return ResponseEntity.ok(view(store.find(request.endpoint())));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@Valid @RequestBody Endpoint request) {
        if (!store.ready()) return error(503, "알림 기능을 준비 중이에요.");
        store.delete(request.endpoint());
        return ResponseEntity.ok(view(Optional.empty()));
    }

    /** 구독한 본인 브라우저에만 보내는 확인 알림. 30초에 한 번으로 제한한다. */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(@Valid @RequestBody Test request) {
        if (!available()) return error(503, "알림 기능을 준비 중이에요.");
        var row = store.find(request.endpoint());
        if (row.isEmpty()) return error(404, "이 브라우저의 알림 구독을 찾지 못했어요. 알림을 다시 켜 주세요.");
        Instant now = clock.instant();
        Instant previous = lastTest.get(row.get().hash());
        if (previous != null && previous.plus(TEST_INTERVAL).isAfter(now)) return error(429, "잠시 후 다시 시도해 주세요.");
        lastTest.put(row.get().hash(), now);
        if (lastTest.size() > 10_000) lastTest.entrySet().removeIf(entry -> entry.getValue().plus(TEST_INTERVAL).isBefore(now));
        String houseNm = null, id = request.announcementId();
        if (id != null) {
            String[] parts = id.split("/");
            try { houseNm = summaries.get(parts[0], parts[1], parts[2]).houseNm(); } catch (EntityNotFoundException | IllegalArgumentException missing) { id = null; }
        }
        var result = sender.send(row.get().target(), PushMessage.test(id, houseNm).json(mapper), "test");
        return switch (result) {
            case ACCEPTED -> { store.delivered(row.get().hash(), PushSubscriptions.now(clock), false); yield ResponseEntity.ok(Map.of("sent", true)); }
            case GONE -> { store.deleteHash(row.get().hash()); yield error(410, "브라우저 알림 구독이 만료됐어요. 알림을 다시 켜 주세요."); }
            case RETRY -> error(503, "알림 서버가 응답하지 않아요. 잠시 후 다시 시도해 주세요.");
            case REJECTED -> error(502, "알림을 보내지 못했어요.");
        };
    }

    private static String strip(String value) { return value.replace("=", ""); }

    static Map<String, Object> view(Optional<PushSubscriptions.Row> row) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subscribed", row.isPresent());
        body.put("favorites", row.map(PushSubscriptions.Row::favorites).orElse(false));
        body.put("areas", row.map(PushSubscriptions.Row::areas).orElse(List.of()));
        body.put("member", row.map(r -> r.userSeq() != null).orElse(false));
        return body;
    }
}
