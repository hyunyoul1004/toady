package com.toady.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.app.planner.*;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:planneraudit;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.planner.mail-limit=4", "toady.planner.auth-reserve=1"
})
@AutoConfigureMockMvc
class PlannerIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired MailBudget budget; @Autowired PlannerService planner;
    @MockitoBean EmailUtilService mail;
    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_FAVORITE (USER_SEQ BIGINT,ANNOUNCEMENT_TYPE VARCHAR(20),HOUSE_MANAGE_NO VARCHAR(40),PBLANC_NO VARCHAR(40),NOTE VARCHAR(2000),CHECKLIST INT,EMAIL_ENABLED BOOLEAN,REG_DT TIMESTAMP,PRIMARY KEY(USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MAIL_BUDGET (ID INT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MAIL_ATTEMPT (ID CHAR(36) PRIMARY KEY,MAIL_KIND VARCHAR(10),REG_DT TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_REMINDER_DELIVERY (USER_SEQ BIGINT,SEND_DATE DATE,STATUS VARCHAR(12),UPDATED_AT TIMESTAMP,PRIMARY KEY(USER_SEQ,SEND_DATE))");
        for(String table:List.of("TB_FAVORITE","TB_MAIL_ATTEMPT","TB_MAIL_BUDGET","TB_REMINDER_DELIVERY","TB_CERTIFY","TB_USER","TB_APT_LTTOT_PBLANC_SCORE","TB_APT_LTTOT_PBLANC_DETAIL")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO TB_MAIL_BUDGET VALUES (1)");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P1','N1','<운정 테스트>','경기도 파주시 동패동','2026-11-12','2026-11-13','2026-11-20')");
        org.mockito.Mockito.reset(mail);
    }
    UserEntity user(String email) {
        return users.save(UserEntity.builder().userEmail(email).userNicName(email.substring(0,email.indexOf('@'))).userPassword(passwords.encode("Password123!"))
                .emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
    }
    String token(String email) throws Exception {
        String body=mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userEmail",email,"userPassword","Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).path("data").path("accessToken").asText();
    }
    @Test void favoritesArePrivateValidatedAndUseAuthoritativeSummary() throws Exception {
        user("owner@example.test");user("other@example.test");String owner=token("owner@example.test"),other=token("other@example.test");
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"비공개 조사\",\"checklist\":5,\"emailEnabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/favorites").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].summary.houseNm").value("<운정 테스트>"));
        mvc.perform(get("/api/v1/me/favorites").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/v1/me/favorites/apt/P1/N1").header("Authorization",other)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
        long ownerId=users.findByUserEmail("owner@example.test").getUserSeq();
        planner.save(ownerId,"apt","P1","N1","비공개 조사",5,true); // 동일 값 재저장도 새 INSERT가 아니다.
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\",\"checklist\":32}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/favorites/disable-emails").header("Authorization",owner)).andExpect(status().isOk());
        assertFalse(jdbc.queryForObject("SELECT EMAIL_ENABLED FROM TB_FAVORITE",Boolean.class));
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test void publicRequestsAndAuthenticationErrorsNeverChallengeTheBrowserForBasicCredentials() throws Exception {
        mvc.perform(get("/api/v1/planner/settings").accept(MediaType.ALL))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/v1/announcements").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/v1/me/favorites").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resultCode").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/v1/planner/settings").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.resultCode").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/v1/planner/settings").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void livingAreaSearchFindsAddressWithoutDistrictWideFalseMatchAndHtmlIsEscaped() throws Exception {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE) VALUES ('P2','N2','파주 다른 공고','경기도 파주시 문산읍','2026-11-12')");
        mvc.perform(get("/api/v1/announcements/suggestions").param("keyword","운정역")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/announcements").param("keyword","운정")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        String html=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/public/sitemaps/apt/0.xml")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/sitemap.xml")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/planner/summaries").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"apt/P1/N1\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].houseNm").value("<운정 테스트>"));
        mvc.perform(post("/api/v1/planner/summaries").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[null]}"))
            .andExpect(status().isBadRequest());
        assertTrue(html.contains("&lt;운정 테스트&gt;"));assertFalse(html.contains("<운정 테스트>"));assertTrue(html.contains("rel='canonical'"));
    }
    @Test void allPublicAccountEndpointsRejectInvalidInputWithoutBrowserChallenge() throws Exception {
        for(String path:List.of("/webApi/user/regUser","/webApi/user/login","/webApi/certify/certify-check","/webApi/certify/resend-account-verification","/webApi/certify/find-password-request-certify","/webApi/certify/certify-confrim-and-password-change")) {
            mvc.perform(post(path).accept(MediaType.TEXT_HTML,MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError()).andExpect(header().doesNotExist("WWW-Authenticate"));
        }
    }
    @Test void shareMetadataAndPngDependOnAnnouncementAndSelectedTab() throws Exception {
        String detail=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rate=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains(org.springframework.web.util.HtmlUtils.htmlEscape("청약 일정·분양 정보")));assertTrue(rate.contains("청약 경쟁률"));
        assertTrue(rate.contains("?tabType=rate"));assertTrue(rate.contains("twitter:card"));assertTrue(rate.contains("summary_large_image"));
        assertTrue(rate.contains("announcement-images/apt/P1/N1.png?tabType=rate&amp;v=2"));
        assertFalse(rate.contains("<운정 테스트>"));
        byte[] image=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
        assertEquals(1200,decoded.getWidth());assertEquals(630,decoded.getHeight());
        mvc.perform(get("/api/v1/public/announcement-images/apt/missing/missing.png")).andExpect(status().isNotFound());
        String unknown=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","<script>oops</script>")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(unknown.contains("oops"));
    }
    @Test void sharedCompetitionMetadataAndImageIncludeOnlyPublishedScoreRange() throws Exception {
        byte[] before=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andReturn().getResponse().getContentAsByteArray();
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_SCORE (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,RESIDE_SECD,LWET_SCORE,TOP_SCORE,AVRG_SCORE) VALUES ('P1','N1','01','01','56','74','65')");
        String html=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(org.springframework.web.util.HtmlUtils.htmlEscape("청약 경쟁률·당첨가점")));
        assertTrue(html.contains("최저 56점 / 최고 74점"));
        assertTrue(html.contains(org.springframework.web.util.HtmlUtils.htmlEscape("공개 주택형·지역 전체 기준")));
        byte[] after=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertFalse(java.util.Arrays.equals(before,after));
        String detail=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains("최저 56점"));
    }
    @Test void rollingQuotaIsAtomicAndReservesAuthenticationCapacity() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<6;i++) tasks.add(()->budget.reserve(true));
            int accepted=0;for(Future<Boolean> result:pool.invokeAll(tasks)) if(result.get()) accepted++;
            assertEquals(3,accepted);assertTrue(budget.reserve(false));assertFalse(budget.reserve(false));
            jdbc.update("UPDATE TB_MAIL_ATTEMPT SET REG_DT=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(25)));
            assertTrue(budget.reserve(false));
        } finally {pool.shutdownNow();}
    }
    @Test void missingAnnouncementCannotBeSaved() throws Exception {
        var account=user("missing@example.test");
        assertThrows(jakarta.persistence.EntityNotFoundException.class,()->planner.save(account.getUserSeq(),"apt","missing","missing","",0,false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
    }
    @Test void reminderBundlesEventsAndDoesNotSendTwiceOrAfterOptOut() throws Exception {
        var account=user("reminder@example.test");
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET RCEPT_BGNDE=?,RCEPT_ENDDE=?",today.toString(),today.plusDays(1).toString());
        planner.save(account.getUserSeq(),"apt","P1","N1","개인 메모는 이메일에 넣지 않음",3,true);
        var reminder=new PlannerReminder(jdbc,planner,users,mail,"https://www.toady.co.kr");
        reminder.send();reminder.send();
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(1)).sendReminder(org.mockito.ArgumentMatchers.eq("reminder@example.test"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.argThat(body -> body.contains("접수 시작") && body.contains("접수 마감") && !body.contains("개인 메모")));
        assertEquals("SENT",jdbc.queryForObject("SELECT STATUS FROM TB_REMINDER_DELIVERY",String.class));
        jdbc.update("DELETE FROM TB_REMINDER_DELIVERY");planner.disableEmails(account.getUserSeq());reminder.send();
        org.mockito.Mockito.verifyNoMoreInteractions(mail);
    }
    @Test void reminderQuotaDefersBeforeSendAndAmbiguousSmtpFailureDoesNotRetry() throws Exception {
        var account=user("quota@example.test");
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET RCEPT_BGNDE=?",today.toString());
        planner.save(account.getUserSeq(),"apt","P1","N1","",0,true);
        var reminder=new PlannerReminder(jdbc,planner,users,mail,"https://www.toady.co.kr");
        org.mockito.Mockito.doThrow(new MailQuotaException()).when(mail).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        reminder.send();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM TB_REMINDER_DELIVERY",Integer.class));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("unknown")).when(mail).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        reminder.send();reminder.send();
        assertEquals("UNKNOWN",jdbc.queryForObject("SELECT STATUS FROM TB_REMINDER_DELIVERY",String.class));
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(2)).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }

}
