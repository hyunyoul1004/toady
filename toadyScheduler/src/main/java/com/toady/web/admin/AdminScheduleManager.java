package com.toady.web.admin;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.Trigger;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

/**
 * DB에 저장된 자동 스케줄을 런타임 타이머에 반영한다.
 * 관리자 기능 활성화 시 기존 정적 @Scheduled 수집기를 대신하므로 같은 작업이 두 경로로 등록되지 않는다.
 * 타이머는 실행 요청만 접수하고 실제 수집은 AdminJobService의 작업 스레드가 담당한다.
 */
@Service
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminScheduleManager {
    private static final Logger log = LoggerFactory.getLogger(AdminScheduleManager.class);
    private static final ZoneId ZONE = AdminJobService.ZONE;
    private final JdbcTemplate jdbc;
    private final AdminJobService jobs;
    private final ThreadPoolTaskScheduler timer = new ThreadPoolTaskScheduler();
    private final Map<JobKind, ScheduledFuture<?>> futures = new EnumMap<>(JobKind.class);
    /** 다른 작업과 겹쳐 미뤄 둔 재시도(설정이 바뀌면 함께 취소). 타이머 스레드와 refresh가 함께 쓰므로 동시 맵. */
    private final Map<JobKind, ScheduledFuture<?>> retries = new java.util.concurrent.ConcurrentHashMap<>();
    /** 종류별 cron 타이머 세대. refresh로 타이머를 바꿀 때마다 올라간다(동기화된 refresh·registerRetry에서만 쓴다). */
    private final Map<JobKind, Integer> generations = new EnumMap<>(JobKind.class);
    // 설정 내용이 같으면 기존 타이머를 유지한다. 조회 때마다 다음 실행 시각이 재설정되는 것을 방지한다.
    private final Map<JobKind, String> versions = new EnumMap<>(JobKind.class);
    private final String defaultDaily;
    private final String defaultCompetition;
    private final int defaultLookback;
    private volatile boolean ready;

    // 다른 작업과 겹쳐 건너뛴 자동 실행을 한 번 더 시도하기까지의 대기 시간.
    private final Duration busyRetryDelay;
    // 시작 후 이전 프로세스 작업을 다시 정리하는 시점. 정리 기준(생존 신호 주기의 2.5배)보다 조금 뒤로 둔다.
    static final Duration LEFTOVER_RECHECK_DELAY = AdminJobService.LEFTOVER_STALE_AFTER.plusSeconds(10);

    public AdminScheduleManager(JdbcTemplate jdbc, AdminJobService jobs,
            @Value("${applyhome.scheduler.cron:0 15 3 * * *}") String daily,
            @Value("${applyhome.competition.scheduler.cron:0 15 4,20 * * *}") String competition,
            @Value("${applyhome.scheduler.lookback-days:90}") int lookback,
            @Value("${applyhome.admin.busy-retry-seconds:300}") long busyRetrySeconds) {
        this.jdbc = jdbc; this.jobs = jobs;
        this.busyRetryDelay = Duration.ofSeconds(Math.max(1, busyRetrySeconds));
        this.defaultDaily = daily; this.defaultCompetition = competition; this.defaultLookback = lookback;
        timer.setPoolSize(1); timer.setThreadNamePrefix("admin-schedule-"); timer.initialize();
    }

    /** 테이블 준비를 확인하고, 아직 저장되지 않은 종류의 기본 스케줄만 최초 등록한다. */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void initialize() {
        // 관리자 기능을 명시적으로 켰는데 테이블이 없으면, 자동 수집이 조용히 빠지지 않도록 시작을 실패시킨다.
        jdbc.queryForObject("SELECT COUNT(*) FROM TB_SCHEDULER_ADMIN_JOB", Long.class);
        jobs.interruptLeftovers();
        // 빠른 재시작이면 이전 프로세스의 마지막 생존 신호가 아직 기준 시간 안이다. 기준이 지난 뒤 한 번 더 정리한다.
        timer.schedule(this::interruptLeftoversAfterRestart, Instant.now().plus(LEFTOVER_RECHECK_DELAY));
        seed(JobKind.ANNOUNCEMENTS, true, defaultDaily);
        seed(JobKind.COMPETITION, true, defaultCompetition);
        seed(JobKind.SCORES, false, "0 15 6 * * *");
        seed(JobKind.ALL, false, "0 15 1 * * *");
        seed(JobKind.SCHEDULED_RESULTS, true, SCHEDULED_RESULTS_CRON, SCHEDULED_RESULTS_LOOKBACK);
        ready = true;
        refresh();
    }
    private void interruptLeftoversAfterRestart() {
        try {
            jobs.interruptLeftovers();
        } catch (RuntimeException exception) {
            log.warn("Leftover scheduler job cleanup after restart failed: {}", exception.getMessage());
        }
    }
    /**
     * 일정 맞춤 수집 기본 일정: 매시 정각 + 경쟁률이 올라오는 18~21시와 가점이 올라오는 7~11시에는 10분마다.
     * 대상 공고가 없으면 외부 호출 없이 끝난다. 재시도 일수(7일) 동안 결과가 비어 있는 공고를 계속 다시 확인한다.
     */
    static final String SCHEDULED_RESULTS_CRON = "0 0 * * * * | 0 */10 18-21 * * * | 0 */10 7-11 * * *";
    static final int SCHEDULED_RESULTS_LOOKBACK = 7;

    /** 환경변수는 최초 등록의 기본값으로만 사용하며, 관리자가 저장한 기존 설정은 덮어쓰지 않는다. */
    private void seed(JobKind kind, boolean enabled, String cron) { seed(kind, enabled, cron, defaultLookback); }
    private void seed(JobKind kind, boolean enabled, String cron, int lookback) {
        // 재시작 시 기존 행을 유지하고, MariaDB와 H2에서 동일하게 실행할 수 있는 INSERT 구문을 사용한다.
        try {
            jdbc.update("""
                    INSERT INTO TB_SCHEDULER_ADMIN_SCHEDULE (JOB_KIND, ENABLED, CRON_EXPRESSION, LOOKBACK_DAYS, UPDATED_AT)
                    SELECT ?, ?, ?, ?, ? WHERE NOT EXISTS
                    (SELECT 1 FROM TB_SCHEDULER_ADMIN_SCHEDULE WHERE JOB_KIND = ?)
                    """, kind.name(), enabled && !cron.equals("-"), cron.equals("-") ? "0 15 3 * * *" : cron,
                    lookback, AdminJobService.now(), kind.name());
        } catch (DuplicateKeyException ignored) { /* 다른 인스턴스가 같은 종류를 먼저 등록했다면 기존 값을 사용한다. */ }
    }
    /**
     * 다음 실행 시각은 저장하지 않고 현재 한국시간과 cron으로 계산한다. 비활성 스케줄은 null이다.
     * 이 버전이 모르는 종류나 읽지 못하는 cron 행은 건너뛴다(한 행 때문에 모든 자동 수집이 멈추지 않게).
     */
    public List<AdminSchedule> list() {
        List<AdminSchedule> schedules = jdbc.query("SELECT * FROM TB_SCHEDULER_ADMIN_SCHEDULE ORDER BY JOB_KIND", (rs, n) -> {
            JobKind kind = JobKind.parse(rs.getString("JOB_KIND"));
            String cron = rs.getString("CRON_EXPRESSION");
            boolean enabled = rs.getBoolean("ENABLED");
            try {
                return kind == null ? null : new AdminSchedule(kind, enabled, cron,
                        rs.getInt("LOOKBACK_DAYS"), rs.getTimestamp("UPDATED_AT").toLocalDateTime(),
                        enabled ? next(cron, LocalDateTime.now(ZONE)) : null);
            } catch (IllegalArgumentException invalidCron) {
                log.warn("Skipping schedule {} with an unreadable cron", kind);
                return null;
            }
        });
        schedules.removeIf(java.util.Objects::isNull);
        return schedules;
    }
    /** DB 저장 후 이 인스턴스의 타이머를 즉시 갱신한다. 다른 인스턴스는 주기 조회로 반영한다. */
    public synchronized void save(JobKind kind, boolean enabled, String cron, int lookback) {
        validate(cron, lookback);
        int changed = jdbc.update("""
                UPDATE TB_SCHEDULER_ADMIN_SCHEDULE SET ENABLED = ?, CRON_EXPRESSION = ?,
                 LOOKBACK_DAYS = ?, UPDATED_AT = ? WHERE JOB_KIND = ?
                """, enabled, cron.trim(), lookback, AdminJobService.now(), kind.name());
        if (changed != 1) throw new IllegalArgumentException("스케줄 설정을 찾을 수 없습니다.");
        refresh();
    }
    /**
     * 일정 하나에 cron 여러 개를 '|'로 이어 쓸 수 있다(예: 매시 정각 + 특정 시간대 10분마다). 같은 시각은 한 번만 실행한다.
     */
    static List<CronExpression> parse(String cron) {
        List<CronExpression> expressions = new ArrayList<>();
        for (String part : cron.split("\\|", -1)) {
            String trimmed = part.trim();
            if (!CronExpression.isValidExpression(trimmed)) throw new IllegalArgumentException("올바른 Spring cron 표현식 6개 필드를 입력하세요. 여러 개는 '|'로 구분합니다.");
            expressions.add(CronExpression.parse(trimmed));
        }
        return expressions;
    }
    /** 여러 cron 중 가장 이른 다음 실행 시각(모두 끝났으면 null). */
    static LocalDateTime next(List<CronExpression> expressions, LocalDateTime after) {
        LocalDateTime earliest = null;
        for (CronExpression expression : expressions) {
            LocalDateTime next = expression.next(after);
            if (next != null && (earliest == null || next.isBefore(earliest))) earliest = next;
        }
        return earliest;
    }
    static LocalDateTime next(String cron, LocalDateTime after) { return next(parse(cron), after); }
    /**
     * 여러 cron을 합친 타이머. 직전 예정 시각 이후로 다음 시각을 구해, 실행이 예정보다 조금 일찍 깨어나도 같은 시각을 두 번 돌지 않는다.
     */
    static Trigger trigger(String cron) {
        List<CronExpression> expressions = parse(cron);
        return context -> {
            Instant base = Instant.now();
            Instant last = context.lastScheduledExecution();
            if (last != null && last.isAfter(base)) base = last;
            LocalDateTime next = next(expressions, LocalDateTime.ofInstant(base, ZONE));
            return next == null ? null : next.atZone(ZONE).toInstant();
        };
    }
    /** cron 문법·조회 범위를 검증하고, 앞으로 7일(최대 2,000회) 실행 사이에 10분 미만의 간격이 있는지 확인한다. */
    static void validate(String cron, int lookback) {
        if (cron == null || cron.isBlank() || cron.length() > 100) {
            throw new IllegalArgumentException("올바른 Spring cron 표현식 6개 필드를 입력하세요. 여러 개는 '|'로 구분합니다(100자 이내).");
        }
        var expressions = parse(cron);
        // 자동 실행도 수동 실행과 같은 최대 기간을 둔다(외부 API 일일 한도 보호, '다시 실행'도 같은 제한 안에서 동작).
        if (lookback < 1 || lookback > AdminJobService.MANUAL_MAX_DAYS) throw new IllegalArgumentException("조회 일수는 1~" + AdminJobService.MANUAL_MAX_DAYS + "일 사이여야 합니다.");
        LocalDateTime previous = LocalDateTime.now(ZONE).withSecond(0).withNano(0);
        LocalDateTime horizon = previous.plusDays(7);
        for (int i = 0; i < 2000 && previous.isBefore(horizon); i++) {
            LocalDateTime next = next(expressions, previous);
            if (next == null) throw new IllegalArgumentException("실행 가능한 날짜가 없는 스케줄입니다.");
            if (i > 0 && Duration.between(previous, next).compareTo(Duration.ofMinutes(10)) < 0) {
                throw new IllegalArgumentException("자동 수집 간격은 10분 이상으로 설정하세요.");
            }
            previous = next;
        }
    }
    /** 설정 변경 반영과 작업 생존 확인을 생존 신호 주기(30초)마다 수행한다. 초기 테이블 준비 전에는 실행하지 않는다. */
    @Scheduled(fixedDelay = AdminJobService.HEARTBEAT_INTERVAL_MILLIS)
    public synchronized void refresh() {
        if (!ready) return;
        jobs.maintain();
        for (AdminSchedule schedule : list()) {
            String signature = schedule.enabled() + ":" + schedule.cronExpression() + ":" + schedule.lookbackDays();
            if (signature.equals(versions.get(schedule.kind()))) continue;
            var old = futures.remove(schedule.kind());
            // 타이머 교체는 이후 자동 실행만 바꾼다. 이미 시작한 수집의 중지는 별도 중지 요청으로 처리한다.
            if (old != null) old.cancel(false);
            // 겹쳐서 미뤄 둔 재시도도 이전 설정으로 실행되지 않게 함께 취소한다(자동 실행을 끈 뒤에 한 번 더 도는 일 방지).
            var pendingRetry = retries.remove(schedule.kind());
            if (pendingRetry != null) pendingRetry.cancel(false);
            // 타이머를 바꿀 때마다 세대 번호를 올린다. 이전 타이머가 이미 실행 중이었다면 그 실행은 재시도를 등록하지 않는다.
            int generation = generations.merge(schedule.kind(), 1, Integer::sum);
            if (schedule.enabled()) {
                futures.put(schedule.kind(), timer.schedule(() -> trigger(schedule, false, generation),
                        trigger(schedule.cronExpression())));
            }
            versions.put(schedule.kind(), signature);
        }
    }
    /**
     * 재시도 등록은 설정 반영(refresh)과 같은 잠금 안에서 한다. 그 사이 자동 실행이 꺼지거나 설정이 바뀌었으면 등록하지 않는다
     * (refresh가 미뤄 둔 재시도를 지운 직후에 새 재시도가 등록돼 이전 설정으로 한 번 더 도는 일을 막는다).
     */
    private synchronized void registerRetry(AdminSchedule schedule, Integer generation) {
        // 이 실행을 시작한 타이머가 그 사이 바뀌었으면(자동 실행 끔·설정 변경) 이전 설정의 재시도이므로 등록하지 않는다.
        if (generation != null && !generation.equals(generations.get(schedule.kind()))) return;
        var holder = new java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>>();
        ScheduledFuture<?> future = timer.schedule(() -> { retries.remove(schedule.kind(), holder.get()); trigger(schedule, true); }, Instant.now().plus(busyRetryDelay));
        holder.set(future);
        var previous = retries.put(schedule.kind(), future);
        if (previous != null) previous.cancel(false);
    }
    /**
     * 실행 중인 다른 작업과 겹치면 요청을 쌓지 않고, 정해진 시간 뒤에 한 번만 다시 시도한다.
     * 재시도도 겹치면 건너뛴 사실만 로그에 남긴다(예: 긴 전체 수집과 같은 시각에 걸린 경쟁률 수집).
     */
    void trigger(AdminSchedule schedule, boolean retry) { trigger(schedule, retry, null); }
    /** generation: 이 실행을 시작한 cron 타이머의 세대(직접 호출이면 null). */
    void trigger(AdminSchedule schedule, boolean retry, Integer generation) {
        // 일정 맞춤 수집은 자주 돌므로 다른 종류의 미뤄 둔 재시도가 있으면 양보한다(예: 20:15 경쟁률 수집이
        // 20:10 실행과 겹쳐 20:20에 재시도할 때 20:20 실행이 먼저 슬롯을 잡아 그날 저녁 수집이 빠지지 않게).
        if (schedule.kind() == JobKind.SCHEDULED_RESULTS && retries.keySet().stream().anyMatch(kind -> kind != JobKind.SCHEDULED_RESULTS)) {
            log.info("Scheduled results run skipped: another scheduled job is waiting to retry");
            return;
        }
        try {
            LocalDate today = AdminJobService.today();
            jobs.submit(schedule.kind(), scheduleStart(today, schedule.lookbackDays()), today, "자동 스케줄", "SCHEDULE");
        } catch (IllegalStateException busy) {
            if (retry) {
                log.warn("Scheduled retry skipped for {}: {}", schedule.kind(), busy.getMessage());
                return;
            }
            // 일정 맞춤 수집은 다음 실행이 곧 오므로 재시도를 쌓지 않는다.
            if (schedule.kind() == JobKind.SCHEDULED_RESULTS) {
                log.info("Scheduled results run skipped: {}", busy.getMessage());
                return;
            }
            log.info("Scheduled submission for {} is busy; retrying once in {}", schedule.kind(), busyRetryDelay);
            try {
                registerRetry(schedule, generation);
            } catch (RuntimeException rejected) {
                log.warn("Scheduled retry could not be registered for {}: {}", schedule.kind(), rejected.getMessage());
            }
        } catch (Exception exception) {
            log.warn("Scheduled submission skipped for {}: {}", schedule.kind(), exception.getMessage());
        }
    }
    // 긴 재조회 기간을 설정해도 관리자에서 허용하는 최초 날짜보다 앞선 범위를 요청하지 않는다.
    static LocalDate scheduleStart(LocalDate today, int days) {
        LocalDate start = today.minusDays(days);
        return start.isBefore(AdminJobService.earliestDate()) ? AdminJobService.earliestDate() : start;
    }
    @PreDestroy public synchronized void close() {
        futures.values().forEach(future -> future.cancel(false)); timer.shutdown();
    }
}
