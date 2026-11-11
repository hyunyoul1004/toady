package com.toady.web.apt.scheduler;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** 일 단위 갱신 문서에 맞춘 수집. 시각은 API 공개 보장 시간이 아닌 운영 정책이다. */
@Component
public class ApplyhomeSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncScheduler.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ApplyhomeSyncService syncService;
    private final ApplyhomeCompetitionSyncService competitionSyncService;
    private final int lookbackDays;
    private final Clock clock;

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
    public void synchronizeCompetition() {
        LocalDate today = LocalDate.now(clock);
        log.info("Starting competition sync from {} through {} at {}", today.minusDays(lookbackDays), today, Instant.now(clock));
        competitionSyncService.synchronize(today.minusDays(lookbackDays), today);
        log.info("Completed competition sync at {}", Instant.now(clock));
    }
}
