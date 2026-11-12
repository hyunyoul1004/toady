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
@SpringBootTest(properties = {
        "applyhome.admin.enabled=true", "applyhome.admin.username=operator",
        "applyhome.admin.password=local-test-password",
        "applyhome.scheduler.cron=-", "applyhome.competition.scheduler.cron=-",
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
    @MockitoBean ApplyhomeSyncService announcements;
    @MockitoBean ApplyhomeCompetitionSyncService competition;

    @BeforeEach void clearHistoryAfterWorkersFinish() {
        await().atMost(Duration.ofSeconds(5)).until(() -> repository.active().isEmpty());
        jdbc.update("DELETE FROM TB_SCHEDULER_ADMIN_JOB");
    }

    @Test void requiresAuthenticationAndAcceptsConfiguredCredentials() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/admin/login"));
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
                .andExpect(redirectedUrlPattern("**/admin/login"));
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
