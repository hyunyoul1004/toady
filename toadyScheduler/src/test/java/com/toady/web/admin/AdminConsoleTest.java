package com.toady.web.admin;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 관리자 DDL을 H2에서 적용하고 내부 API·접근 제어·실행 이력을 검증한다. 실제 수집 서비스와 외부 API는 호출하지 않는다. */
@SpringBootTest(properties = {
        "applyhome.admin.enabled=true",
        "applyhome.admin.internal-token=" + AdminConsoleTest.TOKEN,
        "applyhome.scheduler.cron=-", "applyhome.competition.scheduler.cron=-",
        "applyhome.admin.busy-retry-seconds=1", "applyhome.admin.busy-wait-seconds=1", "data.retention.cron=-",
        "spring.datasource.url=jdbc:h2:mem:admin;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=file:../database.sql"
})
@AutoConfigureMockMvc
class AdminConsoleTest {
    static final String TOKEN = "test-internal-token-0123456789abcdef";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminJobRepository repository;
    @Autowired AdminJobService jobs;
    @Autowired AdminScheduleManager schedules;
    @MockitoBean ApplyhomeSyncService announcements;
    @MockitoBean ApplyhomeCompetitionSyncService competition;

    @BeforeEach void clearHistoryAfterWorkersFinish() {
        await().atMost(Duration.ofSeconds(5)).until(() -> repository.active().isEmpty());
        jdbc.update("DELETE FROM TB_SCHEDULER_ADMIN_JOB");
    }

    @Test void runsPastScoreBackfillByMonthThroughTheInternalApi() throws Exception {
        when(competition.synchronizeScores(any(), any())).thenReturn(3L);
        mvc.perform(post("/internal/admin/jobs").header(AdminInternalApi.HEADER, TOKEN).contentType("application/json")
                        .content("{\"kind\":\"SCORES\",\"startDate\":\"2020-01-30\",\"endDate\":\"2020-02-02\",\"requestedBy\":\"operator\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").exists());
        await().atMost(Duration.ofSeconds(5)).until(() -> repository.recent().size() == 1
                && !repository.recent().getFirst().isActive());
        AdminJob job = repository.recent().getFirst();
        assertEquals("SUCCEEDED", job.status());
        assertEquals(2, job.completedChunks());
        assertTrue(job.message().contains("6건"));
        verify(competition).synchronizeScores(LocalDate.of(2020,1,30), LocalDate.of(2020,1,31));
        verify(competition).synchronizeScores(LocalDate.of(2020,2,1), LocalDate.of(2020,2,2));
        verifyNoInteractions(announcements);
        // 관리자 사이트가 읽는 상태 응답에 완료 이력이 담긴다.
        mvc.perform(get("/internal/admin/state").header(AdminInternalApi.HEADER, TOKEN)).andExpect(status().isOk())
                .andExpect(jsonPath("$.jobs[0].status").value("SUCCEEDED"))
                .andExpect(content().string(containsString("6건")));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = JobKind.class, names = {"ALL", "ANNOUNCEMENTS", "COMPETITION"})
    void dispatchesEveryCollectionType(JobKind kind) {
        LocalDate start = LocalDate.of(2020, 1, 1), end = LocalDate.of(2020, 1, 2);
        String id = jobs.submit(kind, start, end, "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(5)).until(() -> !repository.find(id).orElseThrow().isActive());
        assertEquals("SUCCEEDED", repository.find(id).orElseThrow().status());
        verify(announcements, times(kind == JobKind.ALL || kind == JobKind.ANNOUNCEMENTS ? 1 : 0)).synchronize(start, end);
        verify(competition, times(kind == JobKind.ALL || kind == JobKind.COMPETITION ? 1 : 0)).synchronize(start, end);
        verify(competition, never()).synchronizeScores(any(), any());
    }

    @Test void manualRunsAreLimitedToAboutOneYear() {
        var refused = assertThrows(IllegalArgumentException.class, () -> jobs.submit(JobKind.ALL, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1), "operator", "MANUAL"));
        assertTrue(refused.getMessage().contains("나눠 실행"), refused.getMessage());
        assertTrue(repository.active().isEmpty(), "거절된 실행은 이력을 남기지 않는다");
    }

    @Test void recordsFailureAndAllowsALaterJob() {
        doThrow(new IllegalStateException("upstream unavailable")).when(announcements).synchronize(any(), any());
        String id = jobs.submit(JobKind.ANNOUNCEMENTS, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2), "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(5)).until(() -> !repository.find(id).orElseThrow().isActive());
        assertEquals("FAILED", repository.find(id).orElseThrow().status());
        assertTrue(repository.active().isEmpty());
    }

    @Test void refusesParallelJobsAndCancelsAfterCurrentChunk() throws Exception {
        // 첫 구간을 의도적으로 멈춰 중복 접수 차단과 구간 종료 후 중지 처리를 검증한다.
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(competition.synchronizeScores(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 1L;
        });
        String id = jobs.submit(JobKind.SCORES, LocalDate.of(2020,1,1), LocalDate.of(2020,2,1), "operator", "MANUAL");
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> jobs.submit(JobKind.ALL,
                    LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), "operator", "MANUAL"));
            assertTrue(repository.cancel(id));
        } finally { release.countDown(); }
        await().atMost(Duration.ofSeconds(5)).until(() -> !repository.find(id).orElseThrow().isActive());
        assertEquals("CANCELLED", repository.find(id).orElseThrow().status());
        verify(competition, times(1)).synchronizeScores(any(), any());
    }

    @Test void persistsScheduleSettingsAndRejectsExcessivelyFrequentCron() throws Exception {
        mvc.perform(post("/internal/admin/schedules/SCORES").header(AdminInternalApi.HEADER, TOKEN).contentType("application/json")
                        .content("{\"enabled\":false,\"cron\":\"0 30 6 * * *\",\"lookbackDays\":365}"))
                .andExpect(status().isOk());
        var score = schedules.list().stream().filter(s -> s.kind() == JobKind.SCORES).findFirst().orElseThrow();
        assertFalse(score.enabled());
        assertEquals("0 30 6 * * *", score.cronExpression());
        assertEquals(365, score.lookbackDays());
        mvc.perform(post("/internal/admin/schedules/SCORES").header(AdminInternalApi.HEADER, TOKEN).contentType("application/json")
                        .content("{\"enabled\":true,\"cron\":\"* * * * * *\",\"lookbackDays\":365}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
        assertFalse(schedules.list().stream().filter(s -> s.kind() == JobKind.SCORES).findFirst().orElseThrow().enabled());
    }

    @Test void recoversStaleHistoryAndPreventsDuplicateScheduleTriggers() {
        var old = AdminJobService.now().minusMinutes(11);
        repository.insert("old", JobKind.SCORES, "SCHEDULE", LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), 1, "auto", old);
        repository.recoverStale(AdminJobService.now().minusMinutes(10), AdminJobService.now());
        assertEquals("INTERRUPTED", repository.find("old").orElseThrow().status());
        // 활성 슬롯이 반환된 뒤에도 같은 분의 자동 실행 키는 재사용할 수 없어야 한다.
        assertThrows(org.springframework.dao.DuplicateKeyException.class, () -> repository.insert("repeat",
                JobKind.SCORES, "SCHEDULE", LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), 1, "auto", old));
    }
    @Test void marksLeftoverActiveJobsFromAPreviousProcessAsInterruptedOnStartup() {
        // 생존 신호가 방금 갱신된 작업은 아직 살아 있는 다른 프로세스의 것일 수 있으므로 건드리지 않는다.
        repository.insert("alive", JobKind.ANNOUNCEMENTS, "MANUAL", LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), 1, "auto",
                AdminJobService.now().minus(AdminJobService.LEFTOVER_STALE_AFTER).plusSeconds(20));
        assertEquals(0, jobs.interruptLeftovers());
        assertTrue(repository.find("alive").orElseThrow().isActive());
        repository.finish("alive", "CANCELLED", "test", AdminJobService.now());
        assertTrue(AdminScheduleManager.LEFTOVER_RECHECK_DELAY.compareTo(AdminJobService.LEFTOVER_STALE_AFTER) > 0,
                "시작 직후 재정리는 정리 기준 시간이 지난 뒤에 실행되어야 한다");
        // 생존 신호가 주기의 2.5배 넘게 끊겼다면(10분 미만이어도) 이전 프로세스의 작업으로 보고 정리한다.
        repository.insert("leftover", JobKind.ANNOUNCEMENTS, "MANUAL", LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), 1, "auto",
                AdminJobService.now().minus(AdminJobService.LEFTOVER_STALE_AFTER).minusSeconds(5));
        assertEquals(1, jobs.interruptLeftovers());
        assertEquals("INTERRUPTED", repository.find("leftover").orElseThrow().status());
        assertTrue(repository.active().isEmpty());
        String id = jobs.submit(JobKind.ANNOUNCEMENTS, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2), "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(5)).until(() -> !repository.find(id).orElseThrow().isActive());
    }

    @Test void retriesOnlyTheBusyCompetitionStepOfAnAllJob() {
        LocalDate start = LocalDate.of(2020, 1, 1), end = LocalDate.of(2020, 1, 2);
        doThrow(new com.toady.web.apt.service.ApplyhomeSyncBusyException()).doNothing()
                .when(competition).synchronize(start, end);
        String id = jobs.submit(JobKind.ALL, start, end, "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(10)).until(() -> !repository.find(id).orElseThrow().isActive());
        assertEquals("SUCCEEDED", repository.find(id).orElseThrow().status());
        // 공고 단계는 이미 끝났으므로 경쟁률 잠금 대기 후 재시도에서 다시 실행하지 않는다.
        verify(announcements, times(1)).synchronize(start, end);
        verify(competition, times(2)).synchronize(start, end);
    }

    @Test void keepsAPartialAnnouncementFailureAcrossABusyCompetitionRetry() {
        LocalDate start = LocalDate.of(2019, 3, 1), end = LocalDate.of(2019, 3, 2);
        doThrow(new com.toady.web.apt.service.ApplyhomeSyncService.PartialFailureException("주택형 수집 중 1건 실패(나머지는 저장됨): A/1"))
                .when(announcements).synchronize(start, end);
        doThrow(new com.toady.web.apt.service.ApplyhomeSyncBusyException()).doNothing().when(competition).synchronize(start, end);
        String id = jobs.submit(JobKind.ALL, start, end, "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(10)).until(() -> !repository.find(id).orElseThrow().isActive());
        var job = repository.find(id).orElseThrow();
        // 경쟁률을 잠금 대기 후 다시 돌려도 앞 단계의 일부 실패를 성공으로 덮지 않는다.
        assertEquals("FAILED", job.status());
        assertTrue(job.message().contains("일부 공고만 실패") && job.message().contains("A/1"), job.message());
        verify(competition, times(2)).synchronize(start, end);
    }

    @Test void aPartialFailureInAnOldMonthDoesNotSkipLaterMonths() {
        LocalDate start = LocalDate.of(2018, 1, 20), end = LocalDate.of(2018, 2, 3);
        doThrow(new com.toady.web.apt.service.ApplyhomePartialFailureException("경쟁률 수집 중 1건 실패(나머지는 저장됨): B/2"))
                .when(competition).synchronize(LocalDate.of(2018, 1, 20), LocalDate.of(2018, 1, 31));
        String id = jobs.submit(JobKind.COMPETITION, start, end, "operator", "MANUAL");
        await().atMost(Duration.ofSeconds(10)).until(() -> !repository.find(id).orElseThrow().isActive());
        // 1월 구간이 일부 실패해도 2월 구간을 수집하고, 끝에서 실패를 알린다.
        verify(competition).synchronize(LocalDate.of(2018, 2, 1), LocalDate.of(2018, 2, 3));
        assertEquals("FAILED", repository.find(id).orElseThrow().status());
        assertTrue(repository.find(id).orElseThrow().message().contains("2018-01-20~2018-01-31"));
    }

    @Test void internalApiRequiresTheSharedTokenAndControlsJobs() throws Exception {
        String token = TOKEN;
        // 토큰이 없거나 틀리면 거절한다(세션 로그인과 무관, CSRF 대상 아님).
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/admin/state"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/admin/state").header("X-Admin-Token", "wrong"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/internal/admin/state").header("X-Admin-Token", token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.schedules").isArray());
        // 잘못된 기간은 사유와 함께 400
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/admin/jobs").header("X-Admin-Token", token)
                        .contentType("application/json").content("{\"kind\":\"SCORES\",\"startDate\":\"2016-01-01\",\"endDate\":\"2016-01-02\",\"requestedBy\":\"운영자\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").exists());
        // 정상 요청은 작업을 접수하고 요청자를 이력에 남긴다.
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/admin/jobs").header("X-Admin-Token", token)
                        .contentType("application/json").content("{\"kind\":\"SCORES\",\"startDate\":\"2018-05-01\",\"endDate\":\"2018-05-02\",\"requestedBy\":\"운영자\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
        await().atMost(Duration.ofSeconds(10)).until(() -> !repository.find(id).orElseThrow().isActive());
        assertEquals("운영자", repository.find(id).orElseThrow().requestedBy());
        // 자동 일정 저장도 같은 검증(재조회 일수 상한)을 거친다.
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/internal/admin/schedules/SCORES").header("X-Admin-Token", token)
                        .contentType("application/json").content("{\"enabled\":false,\"cron\":\"0 15 3 * * *\",\"lookbackDays\":3000}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test void retriesABusyScheduledRunOnce() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(competition.synchronizeScores(any(), any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return 1L;
        });
        String manual = jobs.submit(JobKind.SCORES, LocalDate.of(2020,1,1), LocalDate.of(2020,1,2), "operator", "MANUAL");
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            schedules.trigger(new AdminSchedule(JobKind.COMPETITION, true, "0 15 4 * * *", 1, AdminJobService.now(), null), false);
            assertTrue(repository.recent().stream().noneMatch(job -> "SCHEDULE".equals(job.source())), "바쁜 동안에는 접수하지 않는다");
        } finally { release.countDown(); }
        await().atMost(Duration.ofSeconds(5)).until(() -> !repository.find(manual).orElseThrow().isActive());
        // 설정한 대기(1초) 뒤 한 번 더 시도해 자동 실행이 접수되어야 한다.
        await().atMost(Duration.ofSeconds(10)).until(() -> repository.recent().stream()
                .anyMatch(job -> "SCHEDULE".equals(job.source()) && job.kind() == JobKind.COMPETITION && !job.isActive()));
    }

    @Test void deletesExpiredLogsInBatchesAndKeepsAreaBaselines() {
        jdbc.update("DELETE FROM TB_PAGE_VIEW"); jdbc.update("DELETE FROM TB_PUSH_AREA_SEEN");
        jdbc.update("DELETE FROM TB_USAGE_EVENT"); jdbc.update("DELETE FROM TB_REMINDER_DELIVERY");
        java.time.LocalDateTime now = java.time.LocalDateTime.of(2026, 11, 12, 4, 40);
        LocalDate today = now.toLocalDate();
        for (int i = 0; i < 5; i++) pageView(i, i, i, today.minusDays(91 + i), null);
        pageView(9, 9, 9, today.minusDays(90), null);
        action(1, 1, "detail_view", "apt/A/1", today.minusDays(181));
        action(2, 1, "detail_view", "apt/A/1", today.minusDays(179));
        jdbc.update("INSERT INTO TB_REMINDER_DELIVERY (USER_SEQ,SEND_DATE,STATUS,UPDATED_AT) VALUES (1,?,'SENT',?),(1,?,'SENT',?)",
                today.minusDays(91), now, today.minusDays(1), now);
        jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES ('a','*',?),('a','apt/B/1',?),('a','apt/C/1',?),('a','apt/D/1',?)",
                com.toady.web.retention.DataRetentionJob.PUSH_AREA_BASELINE, com.toady.web.retention.DataRetentionJob.PUSH_AREA_BASELINE,
                now.minusDays(400), now.minusDays(10));
        // 1년 넘게 열려 있지만 최근에도 보인 장기 공고는 마지막 확인 기준이라 남는다(database.sql 15번 LAST_SEEN_DT).
        jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT,LAST_SEEN_DT) VALUES ('a','apt/E/1',?,?)", now.minusDays(500), now.minusDays(5));
        var job = new com.toady.web.retention.DataRetentionJob(jdbc);
        var result = job.run(now);
        assertEquals(java.util.Map.of("TB_PAGE_VIEW", 5L, "TB_USAGE_EVENT", 1L, "TB_REMINDER_DELIVERY", 1L, "TB_PUSH_AREA_SEEN", 1L), result);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class));
        // 생활권 기록 시작 표시('*')와 마지막 확인 시각이 아직 없는 기준 시각 행(B)은 남기고, 1년 넘게 보이지 않은 공고(C)만 지운다.
        assertEquals(java.util.List.of("*", "apt/B/1", "apt/D/1", "apt/E/1"), jdbc.queryForList(
                "SELECT ANNOUNCEMENT_ID FROM TB_PUSH_AREA_SEEN ORDER BY ANNOUNCEMENT_ID", String.class));
    }

    @Test void clampsAutomaticHistoryToTheSupportedDateRange() {
        assertEquals(LocalDate.of(2017, 1, 1),
                AdminScheduleManager.scheduleStart(LocalDate.of(2026, 11, 12), 3660));
        assertEquals(LocalDate.of(2026, 11, 11),
                AdminScheduleManager.scheduleStart(LocalDate.of(2026, 11, 12), 1));
    }


    @Test void readinessIsPublicAfterAdminInitialization() throws Exception {
        mvc.perform(get("/internal/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test void oldAdminScreensAreGoneAndOtherPathsAreDenied() throws Exception {
        // 화면은 관리자 사이트(toadyAdmin)로 옮겼다. 스케줄러는 로그인·화면 없이 내부 API만 연다.
        for (String path : java.util.List.of("/", "/admin", "/admin/", "/admin/login", "/admin/metrics", "/admin/style.css")) {
            mvc.perform(get(path)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/login").param("username", "operator").param("password", "local-test-password"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/jobs").param("kind", "SCORES").param("startDate", "2020-01-01").param("endDate", "2020-01-02"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(competition);
        // 토큰을 맞게 보내도 내부 API 밖의 경로는 열리지 않으며, 내부 API는 세션 쿠키를 만들지 않는다.
        mvc.perform(get("/admin").header(AdminInternalApi.HEADER, TOKEN)).andExpect(status().isForbidden());
        var state = mvc.perform(get("/internal/admin/state").header(AdminInternalApi.HEADER, TOKEN))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie")).andReturn();
        assertNull(state.getRequest().getSession(false));
    }

    private void pageView(int event, int visitor, int session, LocalDate date, String referrer) {
        jdbc.update("INSERT INTO TB_PAGE_VIEW (EVENT_ID,VISITOR_ID,SESSION_ID,PAGE_PATH,VISIT_DATE,REG_DT,REFERRER_HOST) VALUES (?,?,?,?,?,?,?)",
                "%036d".formatted(event), "%036d".formatted(1000 + visitor), "%036d".formatted(2000 + session), "/", date, date.atStartOfDay(), referrer);
    }
    private void action(int event, int visitor, String action, String subject, LocalDate date) {
        jdbc.update("INSERT INTO TB_USAGE_EVENT (EVENT_ID,VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",
                "%036d".formatted(500 + event), "%036d".formatted(1000 + visitor), action, subject, date, date.atStartOfDay());
    }
}
