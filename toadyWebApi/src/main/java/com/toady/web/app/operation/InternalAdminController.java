package com.toady.web.app.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.app.push.PushMessage;
import com.toady.web.app.push.PushSubscriptions;
import com.toady.web.app.push.WebPushSender;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.app.user.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 관리자 사이트가 부르는 내부 API(전체·시험 푸시 발송, 인증 메일 다시 보내기, 탈퇴 처리 대행).
 * /internal/**은 같은 서버의 컨테이너끼리만 부를 수 있고(InternalPathFilter), 여기에 더해 관리자와 같은 토큰(ADMIN_API_TOKEN, 32자 이상)을 요구한다.
 * 토큰이 설정되지 않으면 모든 요청을 404로 돌려 기능이 꺼진 것과 같다.
 */
@RestController
@RequestMapping("/internal/admin")
public class InternalAdminController {
    private static final Logger log = LoggerFactory.getLogger(InternalAdminController.class);
    /** 전체 발송은 하루 두 번까지(실수로 여러 번 보내 구독이 끊기지 않게). */
    static final int DAILY_BROADCASTS = 2;
    private final byte[] token;
    private final PushSubscriptions subscriptions;
    private final WebPushSender sender;
    private final ObjectMapper mapper;
    private final UserRepository users;
    private final CertifyService certify;
    private final AccountService accounts;
    private final DailyCounter counter;

    public InternalAdminController(@Value("${toady.admin.internal-token:}") String token, PushSubscriptions subscriptions, WebPushSender sender, ObjectMapper mapper,
                                   UserRepository users, CertifyService certify, AccountService accounts, DailyCounter counter) {
        this.token = token == null || token.trim().length() < 32 ? null : token.trim().getBytes(StandardCharsets.UTF_8);
        this.subscriptions = subscriptions; this.sender = sender; this.mapper = mapper; this.users = users; this.certify = certify; this.accounts = accounts; this.counter = counter;
    }

    private boolean denied(String header) {
        return token == null || header == null || !MessageDigest.isEqual(token, header.trim().getBytes(StandardCharsets.UTF_8));
    }

    public record Broadcast(@NotBlank @Size(max = 40) String title, @NotBlank @Size(max = 120) String body,
                            @NotBlank @Size(max = 200) @Pattern(regexp = "/[A-Za-z0-9/_?=&%.\\-]*") String url, Long testUserSeq) { }

    /** 푸시 발송. testUserSeq가 있으면 그 회원의 브라우저에만(시험 발송), 없으면 모든 구독에 보낸다. */
    @PostMapping("/push")
    public ResponseEntity<Map<String, Object>> push(@RequestHeader(value = "X-Admin-Token", required = false) String header, @Valid @RequestBody Broadcast request) {
        if (denied(header)) return ResponseEntity.notFound().build();
        if (!sender.enabled()) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "웹 푸시 키(VAPID)가 설정되지 않았습니다."));
        if (request.testUserSeq() == null) {
            Boolean allowed = counter.tryIncrement("push-broadcast", LocalDate.now(ZoneId.of("Asia/Seoul")), DAILY_BROADCASTS);
            if (Boolean.FALSE.equals(allowed)) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("message", "전체 발송은 하루 " + DAILY_BROADCASTS + "번까지입니다."));
        }
        List<PushSubscriptions.Row> rows = subscriptions.all(request.testUserSeq());
        String json = new PushMessage(request.title().strip(), request.body().strip(), request.url(), request.testUserSeq() == null ? "notice" : "notice-test", List.of()).json(mapper);
        AtomicInteger sent = new AtomicInteger(), removed = new AtomicInteger(), failed = new AtomicInteger();
        for (var row : rows) {
            switch (sender.send(row.target(), json, "notice")) {
                case ACCEPTED -> sent.incrementAndGet();
                case GONE -> { subscriptions.deleteHash(row.hash()); removed.incrementAndGet(); }
                default -> failed.incrementAndGet();
            }
        }
        log.info("관리자 푸시 발송: test={}, targets={}, sent={}, removed={}, failed={}", request.testUserSeq() != null, rows.size(), sent, removed, failed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("targets", rows.size()); result.put("sent", sent.get()); result.put("removed", removed.get()); result.put("failed", failed.get());
        return ResponseEntity.ok(result);
    }

    /** 가입 인증 메일 다시 보내기(인증 전 회원만). 메일 한도·재요청 간격 규칙은 회원이 직접 요청할 때와 같다. */
    @PostMapping("/members/{userSeq}/resend-verification")
    public ResponseEntity<Map<String, Object>> resend(@RequestHeader(value = "X-Admin-Token", required = false) String header, @PathVariable long userSeq) {
        if (denied(header)) return ResponseEntity.notFound().build();
        var user = users.findById(userSeq).orElse(null);
        if (user == null || !"N".equals(user.getWithdrawalYn())) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "회원을 찾을 수 없습니다."));
        if ("Y".equals(user.getEmailCertifyYn())) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "이미 인증을 마친 회원입니다."));
        var response = certify.resendAccountVerification(new CertifyDTO.UserInfo(user.getUserEmail()));
        Object body = response.getBody();
        String message = body instanceof com.toady.web.core.response.ResData<?> data ? data.getResultMessage() : "요청을 보냈습니다.";
        return ResponseEntity.ok(Map.of("message", message == null ? "요청을 보냈습니다." : message));
    }

    /** 회원 요청을 받은 운영자의 탈퇴 처리. */
    @PostMapping("/members/{userSeq}/withdraw")
    public ResponseEntity<Map<String, Object>> withdraw(@RequestHeader(value = "X-Admin-Token", required = false) String header, @PathVariable long userSeq) {
        if (denied(header)) return ResponseEntity.notFound().build();
        var outcome = accounts.withdrawByOperator(userSeq);
        return outcome.ok() ? ResponseEntity.ok(Map.of("message", "탈퇴 처리했습니다.")) : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", outcome.message()));
    }
}
