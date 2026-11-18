package com.toady.web.app.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.ContentStoreAccess;
import com.toady.web.app.planner.LivingAreas;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 관리자 2·3단계(database.sql 27번)가 서비스에 적용되는지: 공지, 검색어 통계, 생활권·질문 DB 반영, 설정, 사용량, 운영팀 글, 내부 관리 API. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:contentops;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.admin.internal-token=test-admin-internal-token-0123456789abcdef"
})
@AutoConfigureMockMvc
class ContentOperationsTest {
    static final String BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
    static final String TOKEN = "test-admin-internal-token-0123456789abcdef";
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.toady.web.core.util.email.EmailUtilService mail;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired AppSettings settings; @Autowired DailyCounter counter; @Autowired com.toady.web.app.planner.ContentStore store;

    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_SITE_NOTICE (NOTICE_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, MESSAGE VARCHAR(300) NOT NULL, LINK_URL VARCHAR(300), LINK_LABEL VARCHAR(30),"
                + " PLACEMENT VARCHAR(10) DEFAULT 'ALL' NOT NULL, LEVEL VARCHAR(10) DEFAULT 'INFO' NOT NULL, START_DT TIMESTAMP NOT NULL, END_DT TIMESTAMP NOT NULL, USE_YN VARCHAR(1) DEFAULT 'Y' NOT NULL,"
                + " ADMIN_NAME VARCHAR(100) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_SEARCH_STAT (STAT_DATE DATE NOT NULL, TERM VARCHAR(30) NOT NULL, EMPTY_YN VARCHAR(1) NOT NULL, HITS INT DEFAULT 0 NOT NULL, PRIMARY KEY (STAT_DATE, TERM, EMPTY_YN))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_APP_SETTING (SETTING_KEY VARCHAR(50) PRIMARY KEY, SETTING_VALUE VARCHAR(200) NOT NULL, ADMIN_NAME VARCHAR(100) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_DAILY_COUNTER (COUNTER_KEY VARCHAR(40) NOT NULL, COUNT_DATE DATE NOT NULL, COUNT_VALUE BIGINT DEFAULT 0 NOT NULL, PRIMARY KEY (COUNTER_KEY, COUNT_DATE))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_COMMENT_OFFICIAL (COMMENT_SEQ BIGINT PRIMARY KEY, PINNED_YN VARCHAR(1) DEFAULT 'N' NOT NULL, ADMIN_NAME VARCHAR(100) NOT NULL, REG_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_LIVING_AREA (SLUG VARCHAR(40) PRIMARY KEY, LABEL VARCHAR(30) NOT NULL, ALIASES VARCHAR(300) NOT NULL, TERMS VARCHAR(1000) NOT NULL,"
                + " SCOPE VARCHAR(200) DEFAULT '' NOT NULL, DESCRIPTION VARCHAR(100) NOT NULL, SORT_ORDER INT DEFAULT 0 NOT NULL, USE_YN VARCHAR(1) DEFAULT 'Y' NOT NULL, ADMIN_NAME VARCHAR(100) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_FAQ_ITEM (ITEM_ID VARCHAR(40) PRIMARY KEY, GROUP_ID VARCHAR(40) NOT NULL, GROUP_TITLE VARCHAR(50) NOT NULL, QUESTION VARCHAR(200) NOT NULL,"
                + " ANSWER CLOB NOT NULL, SORT_ORDER INT DEFAULT 0 NOT NULL, USE_YN VARCHAR(1) DEFAULT 'Y' NOT NULL, ADMIN_NAME VARCHAR(100) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        for (String table : new String[]{"TB_SITE_NOTICE", "TB_SEARCH_STAT", "TB_APP_SETTING", "TB_DAILY_COUNTER", "TB_COMMENT_OFFICIAL", "TB_LIVING_AREA", "TB_FAQ_ITEM", "TB_LTTOT_PBLANC_COMMENT", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_USER"})
            jdbc.update("DELETE FROM " + table);
        settings.refresh();
    }

    /** 생활권·질문 목록은 JVM 전체에서 공유되므로 다른 테스트에 영향이 없게 기본값으로 되돌린다. */
    @AfterEach void restore() { LivingAreas.replace(null); ContentStoreAccess.resetFaq(); }

    @Test void onlyActiveNoticesWithSafeLinksAreShown() throws Exception {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_SITE_NOTICE (MESSAGE, LINK_URL, PLACEMENT, LEVEL, START_DT, END_DT, ADMIN_NAME, UPDATE_DT) VALUES ('점검 안내', 'javascript:alert(1)', 'ALL', 'WARN', ?, ?, 'op', ?)", now.minusHours(1), now.plusHours(1), now);
        jdbc.update("INSERT INTO TB_SITE_NOTICE (MESSAGE, LINK_URL, PLACEMENT, LEVEL, START_DT, END_DT, ADMIN_NAME, UPDATE_DT) VALUES ('지난 공지', '/x', 'ALL', 'INFO', ?, ?, 'op', ?)", now.minusDays(2), now.minusDays(1), now);
        jdbc.update("INSERT INTO TB_SITE_NOTICE (MESSAGE, LINK_URL, PLACEMENT, LEVEL, START_DT, END_DT, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES ('멈춘 공지', '/x', 'ALL', 'INFO', ?, ?, 'N', 'op', ?)", now.minusHours(1), now.plusHours(1), now);
        jdbc.update("INSERT INTO TB_SITE_NOTICE (MESSAGE, LINK_URL, PLACEMENT, LEVEL, START_DT, END_DT, ADMIN_NAME, UPDATE_DT) VALUES ('새 기능', '/this-week', 'HOME', 'INFO', ?, ?, 'op', ?)", now.minusHours(1), now.plusHours(1), now);
        String body = mvc.perform(get("/api/v1/public/notices")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("점검 안내") && body.contains("새 기능") && !body.contains("지난 공지") && !body.contains("멈춘 공지"), body);
        assertFalse(body.contains("javascript"), "안전하지 않은 링크는 내보내지 않는다");
        assertTrue(body.contains("\"link\":\"/this-week\"") && body.contains("\"placement\":\"HOME\""), body);
    }

    @Test void searchTermsAreCountedWithoutVisitorsAndNumbersAreSkipped() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/analytics/search").header("User-Agent", BROWSER).contentType(MediaType.APPLICATION_JSON).content("{\"term\":\" 운정  신도시 \",\"results\":0}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/analytics/search").header("User-Agent", BROWSER).contentType(MediaType.APPLICATION_JSON).content("{\"term\":\"010-1234-5678\",\"results\":0}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/analytics/search").header("User-Agent", "Mozilla/5.0 (compatible; Googlebot/2.1)").contentType(MediaType.APPLICATION_JSON).content("{\"term\":\"봇검색\",\"results\":1}"));
        assertEquals(2, jdbc.queryForObject("SELECT HITS FROM TB_SEARCH_STAT WHERE TERM='운정 신도시' AND EMPTY_YN='Y'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_SEARCH_STAT", Integer.class), "숫자만 긴 값·봇 요청은 세지 않는다");
    }

    @Test void livingAreasAndFaqComeFromTheDatabaseAfterSeeding() throws Exception {
        ContentStoreAccess.seedAndRefresh(store);
        assertEquals(LivingAreas.BUILT_IN.size(), jdbc.queryForObject("SELECT COUNT(*) FROM TB_LIVING_AREA", Integer.class), "비어 있으면 기본 생활권으로 채운다");
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAQ_ITEM", Integer.class) > 5);
        jdbc.update("INSERT INTO TB_LIVING_AREA (SLUG, LABEL, ALIASES, TERMS, SCOPE, DESCRIPTION, SORT_ORDER, ADMIN_NAME, UPDATE_DT) VALUES ('godeok','고덕','고덕,고덕신도시','고덕,평택시 고덕동','평택','평택 고덕 생활권',1,'op',CURRENT_TIMESTAMP)");
        jdbc.update("UPDATE TB_LIVING_AREA SET USE_YN='N' WHERE SLUG='wirye'");
        jdbc.update("UPDATE TB_FAQ_ITEM SET QUESTION='바뀐 질문입니다?' WHERE ITEM_ID='rank1'");
        ContentStoreAccess.refresh(store);
        String areas = mvc.perform(get("/api/v1/living-areas")).andReturn().getResponse().getContentAsString();
        assertTrue(areas.contains("godeok") && !areas.contains("wirye"), areas);
        assertEquals("고덕", LivingAreas.match("경기도 평택시 고덕동 1").map(LivingAreas.Area::label).orElse(null));
        assertTrue(LivingAreas.match("서울 강동구 고덕동 1").isEmpty(), "짧은 이름은 시·군(평택)이 함께 있어야 한다");
        assertTrue(mvc.perform(get("/api/v1/public/faq-items")).andReturn().getResponse().getContentAsString().contains("바뀐 질문입니다?"));
        assertTrue(mvc.perform(get("/api/v1/public/faq")).andReturn().getResponse().getContentAsString().contains("바뀐 질문입니다?"), "검색용 페이지도 같은 내용");
    }

    @Test void settingsOverrideDefaultsWithinRangeAndCountersPersistPerDay() {
        assertEquals(50, settings.number(AppSettings.DM_DAILY_MESSAGES, 50, 1, 500));
        jdbc.update("INSERT INTO TB_APP_SETTING VALUES ('dm.dailyMessages','20','op',CURRENT_TIMESTAMP), ('dm.dailyNewThreads','999','op',CURRENT_TIMESTAMP), ('dm.requireStory','true','op',CURRENT_TIMESTAMP)");
        settings.refresh();
        assertEquals(20, settings.number(AppSettings.DM_DAILY_MESSAGES, 50, 1, 500));
        assertEquals(5, settings.number(AppSettings.DM_DAILY_NEW_THREADS, 5, 1, 50), "범위 밖이면 기본값");
        assertTrue(settings.flag(AppSettings.DM_REQUIRE_STORY, false));
        LocalDate day = LocalDate.of(2026, 11, 16);
        assertEquals(Boolean.TRUE, counter.tryIncrement("molit", day, 2));
        assertEquals(Boolean.TRUE, counter.tryIncrement("molit", day, 2));
        assertEquals(Boolean.FALSE, counter.tryIncrement("molit", day, 2), "한도에 닿으면 막는다");
        assertEquals(2L, counter.count("molit", day));
        assertEquals(Boolean.TRUE, counter.tryIncrement("molit", day.plusDays(1), 2), "날짜가 바뀌면 새로 센다");
        // 실거래 하루 한도: 관리자 설정 + DB 계수기(재시작해도 이어서 센다)
        jdbc.update("DELETE FROM TB_DAILY_COUNTER");
        var budget = new com.toady.web.app.planner.research.MolitDailyBudget(1000, java.time.Clock.systemUTC());
        jdbc.update("INSERT INTO TB_APP_SETTING VALUES ('molit.dailyLimit','100','op',CURRENT_TIMESTAMP)"); settings.refresh();
        budget.connect(settings, counter);
        int allowed = 0; for (int i = 0; i < 120; i++) if (budget.tryAcquire(false)) allowed++;
        assertEquals(80, allowed, "사용자 조회는 설정 한도의 80%");
        assertTrue(budget.tryAcquire(true), "미리 계산은 남은 20%를 쓸 수 있다");
    }

    @Test void officialStoriesAreMarkedAndPinnedOnesComeWithAFlag() throws Exception {
        UserEntity official = users.save(UserEntity.builder().userEmail("team@example.test").userNicName("TOADY운영팀").userPassword("x").emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES) VALUES ('OF1','N1','운영 단지','경기도 파주시 동패동 1')");
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (COMMENT_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES (501,'APT','OF1','N1','정정 안내',0,0,'N',?,CURRENT_TIMESTAMP)", official.getUserSeq());
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (COMMENT_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES (502,'APT','OF1','N1','일반 이야기',0,0,'N',?,CURRENT_TIMESTAMP)", official.getUserSeq());
        jdbc.update("INSERT INTO TB_COMMENT_OFFICIAL VALUES (501,'Y','op',CURRENT_TIMESTAMP)");
        mvc.perform(get("/api/v1/apt/announcements/OF1/N1/comments")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[?(@.commentSeq==501)].official").value(true)).andExpect(jsonPath("$.data.content[?(@.commentSeq==501)].pinned").value(true))
            .andExpect(jsonPath("$.data.content[?(@.commentSeq==502)].official").value(false));
        mvc.perform(get("/api/v1/apt/announcements/OF1/N1/comments/pinned")).andExpect(jsonPath("$.data[0].commentSeq").value(501));
    }

    @Test void internalAdminApiNeedsTheSharedTokenAndHandlesMembers() throws Exception {
        UserEntity member = users.save(UserEntity.builder().userEmail("help@example.test").userNicName("도움요청").userPassword("x").emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
        mvc.perform(post("/internal/admin/members/" + member.getUserSeq() + "/withdraw")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/admin/members/" + member.getUserSeq() + "/withdraw").header("X-Admin-Token", "wrong-token-0123456789abcdef0123456789")).andExpect(status().isNotFound());
        // 앞단 nginx를 거친 요청(X-Real-IP)은 토큰이 맞아도 막힌다.
        mvc.perform(post("/internal/admin/members/" + member.getUserSeq() + "/withdraw").header("X-Admin-Token", TOKEN).header("X-Real-IP", "203.0.113.5")).andExpect(status().isNotFound());
        mvc.perform(post("/internal/admin/members/" + member.getUserSeq() + "/resend-verification").header("X-Admin-Token", TOKEN)).andExpect(status().isConflict());
        mvc.perform(post("/internal/admin/push").header("X-Admin-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("title", "알림", "body", "내용입니다", "url", "/this-week")))).andExpect(status().isConflict());
        mvc.perform(post("/internal/admin/members/" + member.getUserSeq() + "/withdraw").header("X-Admin-Token", TOKEN)).andExpect(status().isOk());
        assertEquals("Y", jdbc.queryForObject("SELECT WITHDRAWAL_YN FROM TB_USER WHERE USER_SEQ=?", String.class, member.getUserSeq()));
        org.mockito.Mockito.verifyNoInteractions(mail);
    }

    /** 관리자 인증 메일 재발송: 하루 발송량이 다 차면 429, 메일 서버 실패는 503으로 알려 준다(500이 아니다). 실제 메일은 보내지 않는다(mock). */
    @Test void adminResendReportsMailQuotaAndDeliveryFailures() throws Exception {
        jdbc.update("DELETE FROM TB_CERTIFY");
        UserEntity member = users.save(UserEntity.builder().userEmail("wait@example.test").userNicName("인증대기").userPassword("x").emailCertifyYn("N").lockYn("N").withdrawalYn("N").build());
        String path = "/internal/admin/members/" + member.getUserSeq() + "/resend-verification";
        org.mockito.Mockito.doThrow(new com.toady.web.app.planner.MailQuotaException())
            .doThrow(new org.springframework.mail.MailSendException("SMTP unavailable"))
            .doNothing()
            .when(mail).sendSignupEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post(path).header("X-Admin-Token", TOKEN)).andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("메일 양")));
        mvc.perform(post(path).header("X-Admin-Token", TOKEN)).andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("보내지 못했습니다")));
        // 실패한 시도는 코드 기록이 롤백되어 4분을 기다리지 않고 바로 다시 보낼 수 있다.
        mvc.perform(post(path).header("X-Admin-Token", TOKEN)).andExpect(status().isOk());
        org.mockito.Mockito.verify(mail, org.mockito.Mockito.times(3)).sendSignupEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    /** 회원가입 중 하루 메일 발송량이 다 차면 500·502가 아니라 '잠시 후 다시' 안내(429)를 돌려주고, 가입은 되돌린다. */
    @Test void signupReturnsFriendly429WhenTheMailQuotaIsUsedUp() throws Exception {
        org.mockito.Mockito.doThrow(new com.toady.web.app.planner.MailQuotaException())
            .when(mail).sendSignupEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/webApi/user/regUser").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "userEmail", "quota@example.test", "userPassword", "Password123!", "userPasswordConfirm", "Password123!", "userNicName", "한도시험"))))
            .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.resultCode").value("ERR-429"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_EMAIL='quota@example.test'", Integer.class));
    }
}
