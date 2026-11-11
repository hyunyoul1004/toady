package com.toady.web.apt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneId;

@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@EnabledIfSystemProperty(named = "applyhome.competition.integration.enabled", matches = "true")
class ApplyhomeCompetitionBackfillIntegrationTest {

	@Autowired
	private ApplyhomeCompetitionSyncService syncService;

	@Test
	void loadsCompetitionResultsFromSelectedAnnouncementDateRange() {
		// 기본 범위는 경쟁률 API 데이터가 제공되는 초기 시점부터 오늘까지로 잡는다.
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		LocalDate startDate = LocalDate.parse(
				System.getProperty("applyhome.competition.test.start-date", "2017-01-01"));
		String configuredEndDate = System.getProperty("applyhome.competition.test.end-date", "");
		LocalDate endDate = configuredEndDate.isBlank() ? today : LocalDate.parse(configuredEndDate);

		syncService.synchronize(startDate, endDate);
	}
}
