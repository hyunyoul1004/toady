package com.toady.web.apt.scheduler;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 일 단위 갱신 문서에 맞춘 수집. 시각은 API 공개 보장 시간이 아닌 운영 정책이다. */
@Component
// 관리자 기능을 켜면 DB 기반 타이머가 수집을 맡으므로 기존 정적 스케줄은 등록하지 않는다.
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "false", matchIfMissing = true)
public class ApplyhomeSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ApplyhomeSyncService syncService;
    private final ApplyhomeCompetitionSyncService competitionSyncService;
    private final int lookbackDays;
    private final Clock clock;
    /** 관리자 기능을 끈 정적 모드에서 잠금에 걸린 경쟁률 수집을 한 번 더 돌리는 데 쓴다(없으면 재시도하지 않는다). */
    @Autowired(required = false) private org.springframework.scheduling.TaskScheduler retryScheduler;
    static final java.time.Duration BUSY_RETRY = java.time.Duration.ofMinutes(10);

    @Autowired
    public ApplyhomeSyncScheduler(ApplyhomeSyncService syncService,
            ApplyhomeCompetitionSyncService competitionSyncService,
            @Value("${applyhome.scheduler.lookback-days:90}") int lookbackDays) {
        this(syncService, competitionSyncService, lookbackDays, Clock.system(SEOUL));
    }

    ApplyhomeSyncScheduler(ApplyhomeSyncService syncService,
            ApplyhomeCompetitionSyncService competitionSyncService, int lookbackDays, Clock clock) {
        if (lookbackDays < 1) throw new IllegalArgumentException("lookback-days must be positive");
        this.syncService = syncService;
        this.competitionSyncService = competitionSyncService;
        this.lookbackDays = lookbackDays;
        this.clock = clock.withZone(SEOUL);
    }

    @Scheduled(cron = "${applyhome.scheduler.cron:0 15 3 * * *}", zone = "Asia/Seoul")
    public void synchronizeDaily() {
        LocalDate today = LocalDate.now(clock);
        log.info("Starting announcement sync from {} through {} at {}", today.minusDays(lookbackDays), today, Instant.now(clock));
        syncService.synchronize(today.minusDays(lookbackDays), today);
        log.info("Completed announcement sync at {}", Instant.now(clock));
    }

    @Scheduled(cron = "${applyhome.competition.scheduler.cron:0 15 4,20 * * *}", zone = "Asia/Seoul")
    public void synchronizeCompetition() { runCompetition(true); }

    void runCompetition(boolean retryWhenBusy) {
        LocalDate today = LocalDate.now(clock);
        log.info("Starting competition sync from {} through {} at {}", today.minusDays(lookbackDays), today, Instant.now(clock));
        try {
            competitionSyncService.synchronize(today.minusDays(lookbackDays), today);
        } catch (com.toady.web.apt.service.ApplyhomeSyncBusyException busy) {
            // 공고 수집이 길어져 잠금이 남아 있으면 이번 회차를 버리지 않고 한 번만 뒤에 다시 시도한다.
            if (retryWhenBusy && retryScheduler != null) {
                log.warn("Competition sync is busy; retrying once in {} minutes", BUSY_RETRY.toMinutes());
                retryScheduler.schedule(() -> runCompetition(false), Instant.now().plus(BUSY_RETRY));
            } else log.warn("Competition sync skipped because another sync holds the lock");
            return;
        }
        log.info("Completed competition sync at {}", Instant.now(clock));
    }
}
