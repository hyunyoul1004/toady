package com.toady.web.admin;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncBusyException;
import com.toady.web.apt.service.ApplyhomeSyncService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.*;
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

    public AdminJobService(AdminJobRepository repository, ApplyhomeSyncService announcements,
            ApplyhomeCompetitionSyncService competition) {
        this.repository = repository;
        this.announcements = announcements;
        this.competition = competition;
    }
    // 관리자에서 허용하는 조회 하한이며, 이 날짜 이후의 모든 원본 데이터 제공을 보장하는 값은 아니다.
    static LocalDate earliestDate() { return LocalDate.of(2017, 1, 1); }
    // 서버 OS의 시간대와 무관하게 화면·이력·자동 스케줄의 날짜 기준을 한국시간으로 맞춘다.
    static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Seoul")); }
    static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("Asia/Seoul")); }

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
        try {
            AdminJob job = repository.find(id).orElseThrow();
            for (DateRange chunk : chunks(job.startDate(), job.endDate())) {
                boolean done = false;
                while (!done) {
                    AdminJob current = repository.find(id).orElseThrow();
                    // 오래된 작업이 이미 중단 상태로 정리되었다면 이후 구간을 더 실행하지 않는다.
                    if (!current.isActive()) return;
                    // 진행 중인 수집 트랜잭션을 강제로 끊지 않는다. 중지는 다음 구간 시작 전에 반영한다.
                    if (closing || Thread.currentThread().isInterrupted() || current.cancelRequested()) {
                        repository.finish(id, closing ? "INTERRUPTED" : "CANCELLED",
                                "작업을 중지했습니다. 완료된 기간의 데이터는 유지됩니다.", now());
                        return;
                    }
                    if (!repository.update(id, "RUNNING", completed,
                            chunk.start() + " ~ " + chunk.end() + " 수집 중", now())) return;
                    try {
                        switch (job.kind()) {
                            case ALL -> {
                                // 전체 수집은 공고·주택형을 먼저 반영한 뒤 같은 기간의 결과 데이터를 가져온다.
                                announcements.synchronize(chunk.start(), chunk.end());
                                competition.synchronize(chunk.start(), chunk.end());
                            }
                            case ANNOUNCEMENTS -> announcements.synchronize(chunk.start(), chunk.end());
                            case COMPETITION -> competition.synchronize(chunk.start(), chunk.end());
                            case SCORES -> scoreRows += competition.synchronizeScores(chunk.start(), chunk.end());
                        }
                        done = true;
                    } catch (ApplyhomeSyncBusyException exception) {
                        // 관리자 테이블을 사용하지 않는 기존 자동 수집이나 다른 인스턴스가 잠금을 가진 경우다.
                        // 실패로 확정하지 않고 잠시 기다린 뒤 같은 구간을 재시도한다.
                        repository.update(id, "WAITING", completed, "자동 또는 다른 인스턴스의 수집이 끝나기를 기다립니다.", now());
                        TimeUnit.SECONDS.sleep(15);
                    }
                }
                // 모든 하위 수집이 끝난 구간만 진행률에 포함한다. 개별 API 행 수와는 다른 지표다.
                completed++;
                if (!repository.update(id, "RUNNING", completed, "해당 기간 수집 완료", now())) return;
            }
            // 가점 건수는 API 응답 처리 건수다. 기존 행 갱신도 포함하므로 신규 INSERT 건수는 아니다.
            String message = job.kind() == JobKind.SCORES
                    ? "가점 " + scoreRows + "건 반영 완료. 0건인 경우 모집공고일 범위와 원본 공개 여부를 확인하세요."
                    : "선택한 전체 기간을 수집했습니다. 같은 키의 기존 데이터는 갱신됩니다.";
            repository.finish(id, "SUCCEEDED", message, now());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            repository.finish(id, "INTERRUPTED", "실행이 중단되었습니다. 완료된 기간 이후부터 다시 실행하세요.", now());
        } catch (Exception exception) {
            // 상세 예외는 작업 ID와 함께 서버 로그에 남기고, 화면에는 원본 응답·인증정보를 노출하지 않는다.
            log.error("Manual scheduler job failed: id={}", id, exception);
            repository.finish(id, "FAILED", "수집에 실패했습니다. 완료된 기간은 유지됩니다. 서버 로그에서 작업 ID로 원인을 확인하세요.", now());
        } finally {
            localJob.compareAndSet(id, null);
        }
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
     * 시작 시 이전 프로세스가 남긴 활성 작업을 중단 상태로 정리한다. 스케줄러는 한 인스턴스만 실행하고,
     * compose가 새 컨테이너를 띄우기 전에 이전 컨테이너를 멈추므로 남은 활성 행은 끝난 프로세스의 것이다.
     * 정리하지 않으면 생존 신호가 만료되는 10분 동안 새 수동·자동 실행이 막힌다.
     */
    public int interruptLeftovers() {
        int cleaned = repository.interruptLeftovers(localJob.get(), now());
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
