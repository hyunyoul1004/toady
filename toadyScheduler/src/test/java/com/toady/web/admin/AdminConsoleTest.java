package com.toady.web.admin;

import com.toady.web.apt.service.ApplyhomeCompetitionSyncService;
import com.toady.web.apt.service.ApplyhomeSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 관리자 DDL을 H2에서 적용하고 인증·화면·실행 이력을 검증한다. 실제 수집 서비스와 외부 API는 호출하지 않는다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "applyhome.admin.enabled=true", "applyhome.admin.username=operator",
        "applyhome.admin.password=local-test-password",
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
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdminJobRepository repository;
    @Autowired AdminJobService jobs;
    @Autowired AdminScheduleManager schedules;
    @Autowired AdminMetrics metrics;
    @org.springframework.boot.test.web.server.LocalServerPort int port;
    @MockitoBean ApplyhomeSyncService announcements;
    @MockitoBean ApplyhomeCompetitionSyncService competition;

    @BeforeEach void clearHistoryAfterWorkersFinish() {
        await().atMost(Duration.ofSeconds(5)).until(() -> repository.active().isEmpty());
        jdbc.update("DELETE FROM TB_SCHEDULER_ADMIN_JOB");
    }

    @Test void requiresAuthenticationAndAcceptsConfiguredCredentials() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        mvc.perform(get("/admin/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("수집 관리에 로그인")));
        mvc.perform(formLogin("/admin/login").user("operator").password("local-test-password"))
                .andExpect(authenticated().withUsername("operator"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void rendersAllJobTypesAndProtectsMutationsAgainstCsrf() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk())
                .andExpect(content().string(containsString("공고 · 주택형")))
                .andExpect(content().string(containsString("APT 당첨 가점")))
                .andExpect(content().string(containsString("자동 스케줄")))
                .andExpect(content().string(containsString("_csrf")));
        mvc.perform(post("/admin/jobs").param("kind", "SCORES")
                        .param("startDate", "2020-01-01").param("endDate", "2020-02-01"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(competition);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void runsPastScoreBackfillByMonthAndRendersCompletedHistory() throws Exception {
        when(competition.synchronizeScores(any(), any())).thenReturn(3L);
        mvc.perform(post("/admin/jobs").with(csrf()).param("kind", "SCORES")
                        .param("startDate", "2020-01-30").param("endDate", "2020-02-02"))
                .andExpect(redirectedUrl("/admin/"));
        await().atMost(Duration.ofSeconds(5)).until(() -> repository.recent().size() == 1
                && !repository.recent().getFirst().isActive());
        AdminJob job = repository.recent().getFirst();
        assertEquals("SUCCEEDED", job.status());
        assertEquals(2, job.completedChunks());
        assertTrue(job.message().contains("6건"));
        verify(competition).synchronizeScores(LocalDate.of(2020,1,30), LocalDate.of(2020,1,31));
        verify(competition).synchronizeScores(LocalDate.of(2020,2,1), LocalDate.of(2020,2,2));
        verifyNoInteractions(announcements);
        mvc.perform(get("/admin")).andExpect(status().isOk())
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

    @Test @WithMockUser(roles = "ADMIN")
    void persistsScheduleSettingsAndRejectsExcessivelyFrequentCron() throws Exception {
        mvc.perform(post("/admin/schedules/SCORES").with(csrf())
                        .param("cron", "0 30 6 * * *").param("lookbackDays", "365"))
                .andExpect(redirectedUrl("/admin/"));
        var score = schedules.list().stream().filter(s -> s.kind() == JobKind.SCORES).findFirst().orElseThrow();
        assertFalse(score.enabled());
        assertEquals("0 30 6 * * *", score.cronExpression());
        assertEquals(365, score.lookbackDays());
        mvc.perform(post("/admin/schedules/SCORES").with(csrf()).param("enabled", "true")
                        .param("cron", "* * * * * *").param("lookbackDays", "365"))
                .andExpect(flash().attributeExists("error"));
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
    @Test void locksLoginPerClientIpAfterRepeatedFailures() throws Exception {
        org.springframework.test.web.servlet.request.RequestPostProcessor attacker = request -> {
            request.setRemoteAddr("203.0.113.7"); return request;
        };
        for (int i = 1; i <= AdminLoginThrottle.MAX_FAILURES; i++) {
            mvc.perform(post("/admin/login").with(csrf()).with(attacker)
                            .param("username", "operator").param("password", "wrong-password-" + i))
                    .andExpect(redirectedUrl(i < AdminLoginThrottle.MAX_FAILURES ? "/admin/login?error" : "/admin/login?locked"));
        }
        // 잠긴 동안에는 올바른 비밀번호도 확인하지 않고 돌려보낸다.
        mvc.perform(post("/admin/login").with(csrf()).with(attacker)
                        .param("username", "operator").param("password", "local-test-password"))
                .andExpect(redirectedUrl("/admin/login?locked"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
        // 글자를 인코딩한 로그인 경로로도 잠금을 피할 수 없다.
        mvc.perform(post(java.net.URI.create("/admin/%6Cogin")).with(csrf()).with(attacker)
                        .param("username", "operator").param("password", "local-test-password"))
                .andExpect(org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated());
        mvc.perform(get("/admin/login").param("locked", "")).andExpect(content().string(containsString("15분")));
        // 다른 IP의 정상 로그인은 영향을 받지 않는다.
        mvc.perform(formLogin("/admin/login").user("operator").password("local-test-password"))
                .andExpect(authenticated().withUsername("operator"));
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

    @Test void redirectsStayRelativeBehindTheHttpsProxy() throws Exception {
        var cookies = new java.net.CookieManager();
        var client = java.net.http.HttpClient.newBuilder().cookieHandler(cookies)
                .followRedirects(java.net.http.HttpClient.Redirect.NEVER).build();
        String base = "http://127.0.0.1:" + port;
        var entry = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/admin/")).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        assertEquals(302, entry.statusCode());
        assertEquals("/admin/login", entry.headers().firstValue("Location").orElseThrow());
        String page = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/admin/login")).build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()).body();
        var token = java.util.regex.Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page);
        assertTrue(token.find(), "로그인 폼에 CSRF 토큰이 있어야 한다");
        String form = "username=operator&password=local-test-password&_csrf="
                + java.net.URLEncoder.encode(token.group(1), java.nio.charset.StandardCharsets.UTF_8);
        var login = client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(base + "/admin/login"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form)).build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());
        assertEquals(302, login.statusCode());
        // 절대 주소(http://…)면 HTTPS 프록시 뒤에서 브라우저가 http로 이동한다.
        assertEquals("/admin/", login.headers().firstValue("Location").orElseThrow());
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

    @Test void loginRedirectAndBothDashboardPathsWorkWithTheSameSession() throws Exception {
        mvc.perform(get("/admin/")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/login"));
        var login = mvc.perform(formLogin("/admin/login").user("operator").password("local-test-password"))
                .andExpect(authenticated().withUsername("operator"))
                .andExpect(redirectedUrl("/admin/"))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        for (String path : java.util.List.of("/admin", "/admin/")) {
            mvc.perform(get(path).session(session)).andExpect(status().isOk())
                    .andExpect(view().name("admin/index"))
                    .andExpect(content().string(containsString("href=\"/admin/\"")));
        }
    }

    private void pageView(int event, int visitor, int session, LocalDate date, String referrer) {
        jdbc.update("INSERT INTO TB_PAGE_VIEW (EVENT_ID,VISITOR_ID,SESSION_ID,PAGE_PATH,VISIT_DATE,REG_DT,REFERRER_HOST) VALUES (?,?,?,?,?,?,?)",
                "%036d".formatted(event), "%036d".formatted(1000 + visitor), "%036d".formatted(2000 + session), "/", date, date.atStartOfDay(), referrer);
    }
    private void action(int event, int visitor, String action, String subject, LocalDate date) {
        jdbc.update("INSERT INTO TB_USAGE_EVENT (EVENT_ID,VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",
                "%036d".formatted(500 + event), "%036d".formatted(1000 + visitor), action, subject, date, date.atStartOfDay());
    }

    @Test @WithMockUser(roles = "ADMIN")
    void metricsPageSummarizesReturningVisitorsExplorersAndSources() throws Exception {
        jdbc.update("DELETE FROM TB_PAGE_VIEW"); jdbc.update("DELETE FROM TB_USAGE_EVENT");
        LocalDate monday = AdminJobService.today().with(java.time.DayOfWeek.MONDAY);
        // 방문자 1: 지난주 구글로 처음 방문 후 이번 주 재방문. 방문자 2: 이번 주 첫 방문에서 공고 2개를 본 활성 탐색자.
        pageView(1, 1, 1, monday.minusDays(7), "www.google.com");
        pageView(2, 1, 2, monday, null);
        pageView(3, 2, 3, monday, "m.search.naver.com");
        action(1, 2, "detail_view", "apt/A/1", monday);
        action(2, 2, "detail_view", "apt/B/1", monday);
        action(3, 1, "detail_view", "apt/A/1", monday);
        action(4, 1, "share", "apt/A/1", monday);
        AdminMetrics.Report report = metrics.report(AdminJobService.today());
        AdminMetrics.Week week = report.getCurrent();
        assertEquals(2, week.visitors());
        assertEquals(1, week.returning());
        assertEquals(50, week.getReturningRate());
        assertEquals(1, week.activeExplorers(), "공고를 하나만 본 방문자는 활성 탐색자가 아니다");
        assertEquals(1, week.shares());
        assertEquals(3, report.sourceSessions());
        assertEquals(66, report.getSearchPercent());
        mvc.perform(get("/admin/metrics")).andExpect(status().isOk())
                .andExpect(content().string(containsString("주간 추이")))
                .andExpect(content().string(containsString("검색 · 구글")))
                .andExpect(content().string(containsString("검색 · 네이버")))
                .andExpect(content().string(containsString("직접 방문·알 수 없음")))
                .andExpect(content().string(containsString("50%")));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(content().string(containsString("/admin/metrics")));
    }
}
