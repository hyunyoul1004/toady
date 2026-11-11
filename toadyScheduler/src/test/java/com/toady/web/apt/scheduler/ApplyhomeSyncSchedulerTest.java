package com.toady.web.apt.scheduler;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplyhomeSyncSchedulerTest {
    @Test void usesKoreanDateAndRevisitsOlderAnnouncementsForBothJobs() {
        var sync = mock(ApplyhomeSyncService.class);
        var competition = mock(ApplyhomeCompetitionSyncService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T16:00:00Z"), ZoneOffset.UTC);
        var scheduler = new ApplyhomeSyncScheduler(sync, competition, 90, clock);
        scheduler.synchronizeDaily();
        scheduler.synchronizeCompetition();
        LocalDate end = LocalDate.of(2026, 11, 12);
        verify(sync).synchronize(end.minusDays(90), end);
        verify(competition).synchronize(end.minusDays(90), end);
        assertThrows(IllegalArgumentException.class, () -> new ApplyhomeSyncScheduler(sync, competition, 0, clock));
    }

    @Test void defaultsRunDailyAt0315AndCompetitionAt0415And2015IncludingWeekends() throws Exception {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var properties = yaml.getObject();
        String daily = defaultValue(properties.getProperty("applyhome.scheduler.cron"));
        String competition = defaultValue(properties.getProperty("applyhome.competition.scheduler.cron"));
        assertEquals("0 15 3 * * *", daily);
        assertEquals("0 15 4,20 * * *", competition);
        assertEquals("Asia/Seoul", ApplyhomeSyncScheduler.class.getDeclaredMethod("synchronizeDaily")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class).zone());
        assertEquals("Asia/Seoul", ApplyhomeSyncScheduler.class.getDeclaredMethod("synchronizeCompetition")
                .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class).zone());
        var saturday = LocalDateTime.of(2026, 11, 14, 0, 0);
        assertEquals(saturday.withHour(3).withMinute(15), CronExpression.parse(daily).next(saturday));
        var cron = CronExpression.parse(competition);
        assertEquals(saturday.withHour(4).withMinute(15), cron.next(saturday));
        assertEquals(saturday.withHour(20).withMinute(15), cron.next(saturday.withHour(4).withMinute(15)));
        assertEquals(saturday.plusDays(1).withHour(4).withMinute(15), cron.next(saturday.withHour(20).withMinute(15)));
    }

    private String defaultValue(String expression) {
        return expression.substring(expression.indexOf(':') + 1, expression.length() - 1);
    }
}
