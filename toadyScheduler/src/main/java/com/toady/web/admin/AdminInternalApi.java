package com.toady.web.admin;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 관리자 사이트(toadyAdmin)가 수집 실행·중지·자동 일정을 요청하는 내부 API.
 * 실행기와 타이머는 이 프로세스 안에 있어야 하므로 화면은 toadyAdmin이, 실행은 스케줄러가 맡는다.
 * 앞단 nginx는 /internal을 외부로 내보내지 않으며, 추가로 공유 토큰(SCHEDULER_INTERNAL_TOKEN, 32자 이상)을 요구한다.
 * 토큰이 없으면 모든 요청을 거절한다.
 */
@RestController
@RequestMapping("/internal/admin")
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminInternalApi {
    static final String HEADER = "X-Admin-Token";
    private final AdminJobService jobs;
    private final AdminJobRepository repository;
    private final AdminScheduleManager schedules;
    private final byte[] token;

    public AdminInternalApi(AdminJobService jobs, AdminJobRepository repository, AdminScheduleManager schedules,
                            @Value("${applyhome.admin.internal-token:}") String token) {
        this.jobs = jobs; this.repository = repository; this.schedules = schedules;
        this.token = token.length() >= 32 ? token.getBytes(StandardCharsets.UTF_8) : null;
    }

    public record JobRequest(JobKind kind, LocalDate startDate, LocalDate endDate, String requestedBy) { }
    public record RetryRequest(String requestedBy) { }
    public record ScheduleRequest(boolean enabled, String cron, int lookbackDays) { }

    /** 화면 표시용 현재 상태: 진행 중 작업, 최근 이력 50건, 자동 일정(다음 실행 시각 포함). */
    @GetMapping("/state")
    public ResponseEntity<?> state(HttpServletRequest request) {
        if (!authorized(request)) return denied();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("today", AdminJobService.today().toString());
        body.put("active", repository.active().orElse(null));
        body.put("jobs", repository.recent());
        body.put("schedules", schedules.list());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> start(@RequestBody JobRequest body, HttpServletRequest request) {
        if (!authorized(request)) return denied();
        return submit(body.kind(), body.startDate(), body.endDate(), body.requestedBy());
    }

    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String id, HttpServletRequest request) {
        if (!authorized(request)) return denied();
        return repository.cancel(id) ? ResponseEntity.ok(Map.of("message", "중지를 요청했습니다. 진행 중인 월의 수집이 끝나면 멈춥니다."))
                : ResponseEntity.badRequest().body(Map.of("message", "이미 종료되었거나 존재하지 않는 작업입니다."));
    }

    /** 기존 이력은 보존하고 같은 종류·기간의 새 작업을 만든다. */
    @PostMapping("/jobs/{id}/retry")
    public ResponseEntity<?> retry(@PathVariable String id, @RequestBody(required = false) RetryRequest body, HttpServletRequest request) {
        if (!authorized(request)) return denied();
        var job = repository.find(id).orElse(null);
        if (job == null) return ResponseEntity.badRequest().body(Map.of("message", "작업을 찾을 수 없습니다."));
        if (job.isActive()) return ResponseEntity.badRequest().body(Map.of("message", "진행 중인 작업은 다시 실행할 수 없습니다."));
        return submit(job.kind(), job.startDate(), job.endDate(), body == null ? null : body.requestedBy());
    }

    @PostMapping("/schedules/{kind}")
    public ResponseEntity<?> schedule(@PathVariable JobKind kind, @RequestBody ScheduleRequest body, HttpServletRequest request) {
        if (!authorized(request)) return denied();
        try {
            schedules.save(kind, body.enabled(), body.cron(), body.lookbackDays());
            return ResponseEntity.ok(Map.of("message", "자동 스케줄을 저장했습니다."));
        } catch (IllegalArgumentException failure) {
            return ResponseEntity.badRequest().body(Map.of("message", failure.getMessage()));
        }
    }

    /** 수동 작업을 접수하고 작업 ID를 돌려준다. 기간 오류·진행 중 작업은 사유와 함께 400으로 알린다. */
    private ResponseEntity<?> submit(JobKind kind, LocalDate start, LocalDate end, String requestedBy) {
        try {
            return ResponseEntity.ok(Map.of("id", jobs.submit(kind, start, end, requester(requestedBy), "MANUAL")));
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return ResponseEntity.badRequest().body(Map.of("message", failure.getMessage()));
        }
    }
    private static String requester(String value) {
        String name = value == null ? "" : value.trim();
        return name.isEmpty() ? "toadyAdmin" : name.length() > 100 ? name.substring(0, 100) : name;
    }
    /** 길이에 따라 비교 시간이 달라지지 않게 고정 시간 비교를 쓴다. */
    boolean authorized(HttpServletRequest request) {
        String given = request.getHeader(HEADER);
        return token != null && given != null && MessageDigest.isEqual(token, given.getBytes(StandardCharsets.UTF_8));
    }
    private static ResponseEntity<?> denied() { return ResponseEntity.status(403).body(Map.of("message", "허용되지 않은 요청입니다.")); }
}
