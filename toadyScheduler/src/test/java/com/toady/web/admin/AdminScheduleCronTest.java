package com.toady.web.admin;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 일정 하나에 cron 여러 개를 '|'로 잇는 기능(매시 + 특정 시간대 10분마다)을 검사한다. */
class AdminScheduleCronTest {
    static final String DEFAULT = AdminScheduleManager.SCHEDULED_RESULTS_CRON;

    @Test
    void defaultScheduledResultsCronIsValidAndRunsHourlyPlusPeakWindows() {
        AdminScheduleManager.validate(DEFAULT, AdminScheduleManager.SCHEDULED_RESULTS_LOOKBACK);
        LocalDateTime t = LocalDateTime.of(2026, 11, 17, 2, 30);
        assertThat(AdminScheduleManager.next(DEFAULT, t)).isEqualTo(LocalDateTime.of(2026, 11, 17, 3, 0));
        // 18~21시는 10분마다, 22시 정각까지 이어진다.
        assertThat(AdminScheduleManager.next(DEFAULT, LocalDateTime.of(2026, 11, 17, 18, 0))).isEqualTo(LocalDateTime.of(2026, 11, 17, 18, 10));
        assertThat(AdminScheduleManager.next(DEFAULT, LocalDateTime.of(2026, 11, 17, 21, 50))).isEqualTo(LocalDateTime.of(2026, 11, 17, 22, 0));
        assertThat(AdminScheduleManager.next(DEFAULT, LocalDateTime.of(2026, 11, 17, 22, 0))).isEqualTo(LocalDateTime.of(2026, 11, 17, 23, 0));
        // 가점 시간대(7~11시)도 10분마다.
        assertThat(AdminScheduleManager.next(DEFAULT, LocalDateTime.of(2026, 11, 17, 7, 20))).isEqualTo(LocalDateTime.of(2026, 11, 17, 7, 30));
        assertThat(AdminScheduleManager.next(DEFAULT, LocalDateTime.of(2026, 11, 17, 11, 50))).isEqualTo(LocalDateTime.of(2026, 11, 17, 12, 0));
    }

    @Test
    void singleCronStillWorksAndBadPartsAreRejected() {
        AdminScheduleManager.validate("0 15 4,20 * * *", 90);
        assertThatThrownBy(() -> AdminScheduleManager.validate("0 0 * * * * | not-a-cron", 7))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("'|'");
        // 합친 일정도 10분 미만 간격은 막는다.
        assertThatThrownBy(() -> AdminScheduleManager.validate("0 0 * * * * | 0 5 * * * *", 7))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10분");
        // 하루 한 번 겹치는 간격도 7일 안에서 찾는다(저장 시각과 무관).
        assertThatThrownBy(() -> AdminScheduleManager.validate("0 0 * * * * | 0 5 3 * * *", 7))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("10분");
        // 끝에 남은 빈 항목도 잘못된 입력이다.
        assertThatThrownBy(() -> AdminScheduleManager.validate("0 0 * * * * |", 7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unknownJobKindsFromANewerVersionAreSkipped() {
        assertThat(JobKind.parse("SCHEDULED_RESULTS")).isEqualTo(JobKind.SCHEDULED_RESULTS);
        assertThat(JobKind.parse("SOMETHING_NEWER")).isNull();
        assertThat(JobKind.parse(null)).isNull();
    }

    @Test
    void triggerNeverRepeatsTheSameSlotWhenItWakesUpEarly() {
        var trigger = AdminScheduleManager.trigger(DEFAULT);
        Instant slot = LocalDateTime.of(2099, 1, 1, 18, 0).atZone(AdminJobService.ZONE).toInstant();
        Instant next = trigger.nextExecution(context(slot));
        assertThat(next).isEqualTo(LocalDateTime.of(2099, 1, 1, 18, 10).atZone(AdminJobService.ZONE).toInstant());
    }

    static TriggerContext context(Instant lastScheduled) {
        return new TriggerContext() {
            @Override public Clock getClock() { return Clock.systemUTC(); }
            @Override public Instant lastScheduledExecution() { return lastScheduled; }
            @Override public Instant lastActualExecution() { return lastScheduled; }
            @Override public Instant lastCompletion() { return lastScheduled; }
        };
    }
}
