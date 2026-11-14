package com.toady.web.admin;

import com.toady.web.apt.api.ApplyhomeApiClient.ProviderRejectedException;
import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncBusyException;
import com.toady.web.apt.service.ApplyhomePartialFailureException;
import com.toady.web.apt.service.ApplyhomeSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 수동 요청과 자동 스케줄을 같은 백그라운드 실행 경로로 처리한다.
 * 실행 이력은 DB에 보관하고, 실제 데이터 수집과 저장은 기존 수집 서비스에 위임한다.
 * 작업 전체를 하나의 트랜잭션으로 묶지 않으므로 실패·중지 전에 완료된 저장 결과는 유지된다.
 */
@Service
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminJobService {
    private static final Logger log = LoggerFactory.getLogger(AdminJobService.class);
    private final AdminJobRepository repository;
    private final ApplyhomeSyncService announcements;
    private final ApplyhomeCompetitionSyncService competition;
    // 같은 JVM의 중복 접수를 먼저 차단한다. 다른 인스턴스와의 경합은 DB의 ACTIVE_SLOT이 차단한다.
    private final AtomicReference<String> localJob = new AtomicReference<>();
    // 대기 공간 하나는 이전 Runnable이 반환되는 짧은 전환 구간을 위한 것이다.
    // 사용자 요청을 무제한으로 적재하지 않으며, 실제 수집은 전용 스레드 하나에서 실행한다.
    private final ExecutorService worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(1), runnable -> {
                Thread thread = new Thread(runnable, "scheduler-admin-worker");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean closing;
    // 다른 수집이 잠금을 가진 동안 같은 구간을 다시 시도하기까지의 대기 시간.
    private final Duration busyWait;

    /** 실행 중 작업의 생존 신호 주기. AdminScheduleManager.refresh가 이 주기로 maintain을 호출한다. */
    static final long HEARTBEAT_INTERVAL_MILLIS = 30_000;
    /**
     * 시작 시 정리할 이전 프로세스 작업의 기준. 생존 신호 주기의 2.5배 동안 신호가 없으면 끝난 프로세스로 본다.
     * 빠르게 재시작해 아직 이 시간이 지나지 않은 작업은 시작 직후 한 번 더 정리한다(AdminScheduleManager).
     */
    static final Duration LEFTOVER_STALE_AFTER = Duration.ofMillis(HEARTBEAT_INTERVAL_MILLIS * 5 / 2);

    public AdminJobService(AdminJobRepository repository, ApplyhomeSyncService announcements,
            ApplyhomeCompetitionSyncService competition,
            @Value("${applyhome.admin.busy-wait-seconds:15}") long busyWaitSeconds) {
        this.repository = repository;
        this.announcements = announcements;
        this.competition = competition;
        this.busyWait = Duration.ofSeconds(Math.max(1, busyWaitSeconds));
    }
    /** 수동 수집 한 번의 최대 기간(일). 외부 API 일일 한도를 보호한다. */
    static final int MANUAL_MAX_DAYS = 366;
    // 관리자에서 허용하는 조회 하한이며, 이 날짜 이후의 모든 원본 데이터 제공을 보장하는 값은 아니다.
    static LocalDate earliestDate() { return LocalDate.of(2017, 1, 1); }
    // 서버 OS의 시간대와 무관하게 화면·이력·자동 스케줄의 날짜 기준을 한국시간으로 맞춘다.
    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static LocalDate today() { return LocalDate.now(ZONE); }
    static LocalDateTime now() { return LocalDateTime.now(ZONE); }

    /**
     * 실행 이력을 먼저 저장한 뒤 작업 ID를 반환한다. 반환 성공은 수집 완료가 아니라 접수 완료를 뜻한다.
     * 기간은 모집공고일 기준이며, source는 MANUAL 또는 SCHEDULE로 이력에 기록한다.
     * 비동기 실행기가 이력을 즉시 읽으므로 이력 INSERT를 감싼 장기 트랜잭션을 만들지 않는다.
     */
    public String submit(JobKind kind, LocalDate start, LocalDate end, String user, String source) {
        if (kind == null || start == null || end == null || start.isAfter(end)
                || start.isBefore(earliestDate()) || end.isAfter(today())) {
            throw new IllegalArgumentException("2017년 1월 1일부터 오늘까지의 올바른 모집공고일 범위를 선택하세요.");
        }
        // 수동 실행은 공고마다 외부 API를 여러 번 부른다. 너무 긴 기간은 공공데이터 일일 한도를 다 써 정규 수집까지 막으므로 나눠 실행하게 한다.
        if ("MANUAL".equals(source) && ChronoUnit.DAYS.between(start, end) >= MANUAL_MAX_DAYS) {
            throw new IllegalArgumentException("한 번에 " + MANUAL_MAX_DAYS + "일(약 1년)까지만 수집할 수 있어요. 기간을 나눠 실행해 주세요.");
        }
        String id = UUID.randomUUID().toString();
        if (!localJob.compareAndSet(null, id)) throw new IllegalStateException("이미 수집 작업이 진행 중입니다.");
        try {
            repository.insert(id, kind, source, start, end, chunks(start, end).size(), user, now());
        } catch (DuplicateKeyException exception) {
            localJob.compareAndSet(id, null);
            throw new IllegalStateException("다른 수집 작업이 진행 중입니다. 진행 중인 작업을 확인하세요.");
        } catch (RuntimeException exception) {
            localJob.compareAndSet(id, null);
            throw exception;
        }
        try {
            worker.execute(() -> run(id));
        } catch (RejectedExecutionException exception) {
            repository.finish(id, "FAILED", "실행기를 사용할 수 없습니다. 잠시 후 다시 실행하세요.", now());
            localJob.compareAndSet(id, null);
            throw new IllegalStateException("실행기를 사용할 수 없습니다. 잠시 후 다시 실행하세요.");
        }
        return id;
    }

    /** 월별 구간을 순차 처리하고, 구간 경계에서 중지 요청과 실행 소유 상태를 다시 확인한다. */
    private void run(String id) {
        int completed = 0;
        long scoreRows = 0;
        // 일부 공고만 실패한 구간: 나머지는 저장됐으므로 다음 구간을 계속 수집하고 끝에서 함께 알린다.
        // (오래된 달의 공고 하나가 계속 실패해도 이번 달 구간이 막히지 않게)
        List<String> partialFailures = new ArrayList<>();
        // 지금 구간에서 생긴 일부 실패. 같은 구간의 다음 단계(경쟁률)가 다른 오류로 끝나도 실패 문구에 함께 남긴다.
        String chunkPartial = null;
        try {
            AdminJob job = repository.find(id).orElseThrow();
            for (DateRange chunk : chunks(job.startDate(), job.endDate())) {
                boolean done = false;
                // 전체 수집에서 경쟁률 단계만 잠금 대기로 재시도할 때 이미 끝난 공고 단계를 다시 실행하지 않는다.
                boolean announcementsDone = false;
                // 잠금 대기로 경쟁률 단계만 다시 돌 때도 앞 단계의 일부 실패를 잃지 않도록 루프 밖에 둔다.
                ApplyhomePartialFailureException partial = null;
                while (!done) {
                    AdminJob current = repository.find(id).orElseThrow();
                    // 오래된 작업이 이미 중단 상태로 정리되었다면 이후 구간을 더 실행하지 않는다.
                    if (!current.isActive()) return;
                    // 진행 중인 수집 트랜잭션을 강제로 끊지 않는다. 중지는 다음 구간 시작 전에 반영한다.
                    if (closing || Thread.currentThread().isInterrupted() || current.cancelRequested()) {
                        repository.finish(id, closing ? "INTERRUPTED" : "CANCELLED",
                                withNotes("작업을 중지했습니다. 완료된 기간의 데이터는 유지됩니다.", partialFailures, chunkPartial), now());
                        return;
                    }
                    if (!repository.update(id, "RUNNING", completed,
                            chunk.start() + " ~ " + chunk.end() + " 수집 중", now())) return;
                    try {
                        switch (job.kind()) {
                            case ALL -> {
                                // 전체 수집은 공고·주택형을 먼저 반영한 뒤 같은 기간의 결과 데이터를 가져온다.
                                if (!announcementsDone) {
                                    try {
                                        announcements.synchronize(chunk.start(), chunk.end());
                                    } catch (ApplyhomePartialFailureException failure) {
                                        // 일부 주택형만 실패했으면 나머지는 저장됐으므로 결과 데이터 수집까지 마친 뒤 실패를 알린다.
                                        partial = failure;
                                        chunkPartial = chunk.start() + "~" + chunk.end() + ": " + failure.getMessage();
                                    }
                                    announcementsDone = true;
                                }
                                competition.synchronize(chunk.start(), chunk.end());
                            }
                            case ANNOUNCEMENTS -> announcements.synchronize(chunk.start(), chunk.end());
                            case COMPETITION -> competition.synchronize(chunk.start(), chunk.end());
                            case SCORES -> scoreRows += competition.synchronizeScores(chunk.start(), chunk.end());
                        }
                        done = true;
                    } catch (ApplyhomePartialFailureException failure) {
                        // 가점 수집은 실패 전까지 반영한 건수도 더한다(일부 실패해도 저장된 행이 0건처럼 보이지 않게).
                        if (job.kind() == JobKind.SCORES) scoreRows += failure.savedRows();
                        // 공고 단계와 경쟁률 단계가 모두 일부 실패하면 둘 다 남긴다.
                        partial = partial == null ? failure : new ApplyhomePartialFailureException(partial.getMessage() + " / " + failure.getMessage());
                        done = true;
                    } catch (ApplyhomeSyncBusyException exception) {
                        // 관리자 테이블을 사용하지 않는 기존 자동 수집이나 다른 인스턴스가 잠금을 가진 경우다.
                        // 실패로 확정하지 않고 잠시 기다린 뒤 같은 구간을 재시도한다.
                        repository.update(id, "WAITING", completed, "자동 또는 다른 인스턴스의 수집이 끝나기를 기다립니다.", now());
                        TimeUnit.MILLISECONDS.sleep(busyWait.toMillis());
                    }
                }
                if (partial != null) {
                    log.warn("Manual scheduler job partially failed: id={}, range={}~{}, {}", id, chunk.start(), chunk.end(), partial.getMessage());
                    partialFailures.add(chunk.start() + "~" + chunk.end() + ": " + partial.getMessage());
                }
                chunkPartial = null;
                // 모든 하위 수집이 끝난 구간만 진행률에 포함한다. 개별 API 행 수와는 다른 지표다.
                completed++;
                if (!repository.update(id, "RUNNING", completed, "해당 기간 수집 완료", now())) return;
            }
            // 가점 건수는 API 응답 처리 건수다. 기존 행 갱신도 포함하므로 신규 INSERT 건수는 아니다.
            String message = job.kind() == JobKind.SCORES
                    ? "가점 " + scoreRows + "건 반영 완료. 0건인 경우 모집공고일 범위와 원본 공개 여부를 확인하세요."
                    : "선택한 전체 기간을 수집했습니다. 같은 키의 기존 데이터는 갱신됩니다.";
            if (!partialFailures.isEmpty()) {
                // 일부 실패는 전체 실패와 구분해 어느 기간·공고가 빠졌는지 남긴다(키와 건수만, 응답 원문 없음).
                String detail = (job.kind() == JobKind.SCORES ? "가점 " + scoreRows + "건 반영. " : "") + String.join(" / ", partialFailures);
                repository.finish(id, "FAILED", "일부 공고만 실패했습니다(나머지는 저장됨). " + (detail.length() > 400 ? detail.substring(0, 400) + "…" : detail), now());
                return;
            }
            repository.finish(id, "SUCCEEDED", message, now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            repository.finish(id, "INTERRUPTED", "실행이 중단되었습니다. 완료된 기간 이후부터 다시 실행하세요.", now());
        } catch (Exception exception) {
            // 배포·종료로 실행기를 멈추면 HTTP 호출 중의 인터럽트가 일반 예외로 바뀌어 온다. 실제 장애와 구분해 중단으로 기록한다.
            if (closing || Thread.currentThread().isInterrupted()) {
                log.warn("Manual scheduler job interrupted by shutdown: id={}", id);
                repository.finish(id, "INTERRUPTED", withNotes("실행이 중단되었습니다. 완료된 기간 이후부터 다시 실행하세요.", partialFailures, chunkPartial), now());
                return;
            }
            // 상세 예외는 작업 ID와 함께 서버 로그에 남기고, 화면에는 원본 응답·인증정보를 노출하지 않는다.
            log.error("Manual scheduler job failed: id={}", id, exception);
            Throwable cause = exception;
            while (cause != null && !(cause instanceof ProviderRejectedException)) cause = cause.getCause();
            if (cause instanceof ProviderRejectedException rejected) {
                // 인증키 오류·일일 한도 초과는 원인을 그대로 알려 운영자가 다시 실행할 시점을 판단하게 한다.
                repository.finish(id, "FAILED", withNotes("청약홈 API가 요청을 거절해 중단했습니다(HTTP " + rejected.status()
                        + "). 인증키와 일일 호출 한도를 확인하세요. 완료된 기간은 유지됩니다.", partialFailures, chunkPartial), now());
                return;
            }
            repository.finish(id, "FAILED", withNotes("수집에 실패했습니다. 완료된 기간은 유지됩니다. 서버 로그에서 작업 ID로 원인을 확인하세요.", partialFailures, chunkPartial), now());
        } finally {
            localJob.compareAndSet(id, null);
        }
    }

    /**
     * 끝난 작업 문구에 앞 구간·이번 구간의 일부 실패를 덧붙인다(중지·거절로 끝나도 빠진 공고가 있었다는 사실을 남긴다). 900자 제한.
     * 운영자가 '완료된 기간은 유지'만 보고 그 기간을 온전하다고 여겨 다시 실행하지 않는 일을 막는다.
     */
    static String withNotes(String message, List<String> partialFailures, String chunkPartial) {
        List<String> notes = new ArrayList<>(partialFailures);
        if (chunkPartial != null) notes.add(chunkPartial);
        String full = notes.isEmpty() ? message : message + " 일부 실패: " + String.join(" / ", notes);
        return full.length() > 900 ? full.substring(0, 900) + "…" : full;
    }

    /**
     * 로컬 실행기의 생존 신호를 갱신하고, 신호가 끊긴 작업을 중단 상태로 정리한다.
     * heartbeat는 프로세스의 생존 신호이며 수집 진행 속도나 외부 API의 정상 응답을 보장하지 않는다.
     */
    public void maintain() {
        String id = localJob.get();
        if (id != null) repository.heartbeat(id, now());
        repository.recoverStale(now().minusMinutes(10), now());
    }
    /** 시작·종료일을 모두 포함하며, 월말을 경계로 서로 겹치지 않는 조회 구간을 만든다. */
    static List<DateRange> chunks(LocalDate start, LocalDate end) {
        List<DateRange> ranges = new ArrayList<>();
        for (LocalDate cursor = start; !cursor.isAfter(end); ) {
            LocalDate last = cursor.withDayOfMonth(cursor.lengthOfMonth());
            if (last.isAfter(end)) last = end;
            ranges.add(new DateRange(cursor, last));
            cursor = last.plusDays(1);
        }
        return ranges;
    }
    record DateRange(LocalDate start, LocalDate end) { }
    /**
     * 시작 시 이전 프로세스가 남긴 활성 작업을 중단 상태로 정리한다. 생존 신호가 LEFTOVER_STALE_AFTER보다
     * 오래된 작업만 대상으로 해, 아직 살아 있는 다른 프로세스(예: 교체 중 잠시 겹친 이전 컨테이너)의 작업은 건드리지 않는다.
     * 정리하지 않으면 생존 신호가 만료되는 10분 동안 새 수동·자동 실행이 막힌다.
     */
    public int interruptLeftovers() {
        LocalDateTime now = now();
        int cleaned = repository.interruptLeftovers(localJob.get(), now.minus(LEFTOVER_STALE_AFTER), now);
        if (cleaned > 0) log.warn("Marked {} leftover scheduler job(s) from a previous process as INTERRUPTED", cleaned);
        return cleaned;
    }
    // 앱 종료 시 실행기를 닫는다. 중단 작업은 자동 재개하지 않고 관리자가 이력을 확인해 다시 실행한다.
    // 작업 스레드가 중단 상태를 기록할 수 있도록 DB 연결이 닫히기 전에 잠시 기다린다(compose stop_grace_period 30초 안).
    @PreDestroy public void close() {
        closing = true;
        worker.shutdownNow();
        try {
            if (!worker.awaitTermination(20, TimeUnit.SECONDS)) log.warn("Scheduler admin worker did not stop within 20 seconds");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
