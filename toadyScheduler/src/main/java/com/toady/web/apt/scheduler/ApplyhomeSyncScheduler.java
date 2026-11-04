package com.toady.web.apt.scheduler;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 한국시간 기준으로 오늘 전후 한 달의 모집공고 데이터를 동기화한다.
 */
@Component
public class ApplyhomeSyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(ApplyhomeSyncScheduler.class);
	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private final ApplyhomeSyncService syncService;
	private final ApplyhomeCompetitionSyncService competitionSyncService;

	public ApplyhomeSyncScheduler(
			ApplyhomeSyncService syncService,
			ApplyhomeCompetitionSyncService competitionSyncService) {
		this.syncService = syncService;
		this.competitionSyncService = competitionSyncService;
	}

	/**
	 * 모집공고일을 기준으로 오늘부터 한 달 전부터 한 달 후까지 조회한다.
	 * cron은 applyhome.scheduler.cron 설정으로 바꿀 수 있다.
	 */
	@Scheduled(cron = "${applyhome.scheduler.cron:0 15 3 * * *}", zone = "Asia/Seoul")
	public void synchronizeDaily() {
		LocalDate today = LocalDate.now(SEOUL);
		LocalDate startDate = today.minusMonths(1);
		LocalDate endDate = today.plusMonths(1);
		log.info("Starting scheduled Applyhome synchronization from {} through {} at {}",
				startDate, endDate, Instant.now());
		syncService.synchronize(startDate, endDate);
		log.info("Completed scheduled Applyhome synchronization from {} through {} at {}",
				startDate, endDate, Instant.now());
	}

	@Scheduled(cron = "${applyhome.competition.scheduler.polling-cron:0 */10 17-19 * * *}", zone = "Asia/Seoul")
	public void synchronizeCompetitionEveryTenMinutes() {
		synchronizeCompetition();
	}

	@Scheduled(cron = "${applyhome.competition.scheduler.final-cron:0 0 20 * * *}", zone = "Asia/Seoul")
	public void synchronizeCompetitionAtEightPm() {
		synchronizeCompetition();
	}

	private void synchronizeCompetition() {
		log.info("Starting scheduled Applyhome competition synchronization at {}", Instant.now());
		competitionSyncService.synchronizeCurrentAnnouncements();
		log.info("Completed scheduled Applyhome competition synchronization at {}", Instant.now());
	}
}
