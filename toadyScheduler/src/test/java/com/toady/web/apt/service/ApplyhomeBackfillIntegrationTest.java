package com.toady.web.apt.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.ZoneId;

@SpringBootTest(useMainMethod = SpringBootTest.UseMainMethod.ALWAYS)
@EnabledIfSystemProperty(named = "applyhome.integration.enabled", matches = "true")
class ApplyhomeBackfillIntegrationTest {

	@Autowired
	private ApplyhomeSyncService syncService;

	@Test
	void loadsSelectedAnnouncementDateRangeIntoMariaDb() {
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		String configuredStartDate = System.getProperty("applyhome.test.start-date", "");
		LocalDate startDate = configuredStartDate.isBlank()
				? today.minusMonths(1)
				: LocalDate.parse(configuredStartDate);
		String configuredEndDate = System.getProperty("applyhome.test.end-date", "");
		LocalDate endDate = configuredEndDate.isBlank()
				? today.plusMonths(1)
				: LocalDate.parse(configuredEndDate);

		syncService.synchronize(startDate, endDate);
	}
}
