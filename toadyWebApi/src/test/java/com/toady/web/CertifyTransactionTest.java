package com.toady.web;

import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:certify;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
class CertifyTransactionTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @Autowired CertifyService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AnnouncementSearchService search;
    @BeforeEach void seed() {
        org.mockito.Mockito.reset(email);
        jdbc.update("DELETE FROM TB_CERTIFY"); jdbc.update("DELETE FROM TB_USER");
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN) VALUES ('verify@example.test','original','verify','N','N','N')");
    }
    @AfterEach void cleanupConstraints() {
        jdbc.execute("ALTER TABLE TB_USER DROP CONSTRAINT IF EXISTS FAIL_USER");
        jdbc.execute("ALTER TABLE TB_CERTIFY DROP CONSTRAINT IF EXISTS FAIL_CERTIFY");
    }
    void code(String type, LocalDateTime created) {
        Long id = jdbc.queryForObject("SELECT USER_SEQ FROM TB_USER", Long.class);
        jdbc.update("INSERT INTO TB_CERTIFY (CERTIFY_TYPE,CERTIFY_SEND_TYPE,CERTIFY_CODE,CERTIFY_MESSAGE,CERTIFY_YN,CREATE_USER_SEQ,CREATE_DT) VALUES (?,'EMAIL','code','test','N',?,?)", type, id, created);
    }
    CertifyDTO.CheckParam check() { return new CertifyDTO.CheckParam("verify@example.test", "code"); }
    CertifyDTO.PasswordChange password() { return new CertifyDTO.PasswordChange("verify@example.test", "Newpass123!", "Newpass123!", "code"); }
    String flag() { return jdbc.queryForObject("SELECT CERTIFY_YN FROM TB_CERTIFY", String.class); }
    @Test void accountFailureRollsBackConsumedCode() {
        code("ACCOUNT", LocalDateTime.now());
        jdbc.execute("ALTER TABLE TB_USER ADD CONSTRAINT FAIL_USER CHECK (EMAIL_CERTIFY_YN = 'N')");
        assertThrows(RuntimeException.class, () -> service.certifyCheck(check()));
        assertEquals("N", flag());
    }
    @Test void codeFailureRollsBackNewPassword() {
        code("FIND_PW", LocalDateTime.now());
        jdbc.execute("ALTER TABLE TB_CERTIFY ADD CONSTRAINT FAIL_CERTIFY CHECK (CERTIFY_YN = 'N')");
        assertThrows(RuntimeException.class, () -> service.certifyConfrimAndPasswordChange(password()));
        assertEquals("original", jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER", String.class));
        assertEquals("N", flag());
    }
    @Test void expiredAccountCodeCannotAuthenticateUser() {
        code("ACCOUNT", LocalDateTime.now().minusHours(25)); service.certifyCheck(check());
        assertEquals("N", flag());
        assertEquals("N", jdbc.queryForObject("SELECT EMAIL_CERTIFY_YN FROM TB_USER", String.class));
    }
    @Test void validAccountCodeAuthenticatesUser() {
        code("ACCOUNT", LocalDateTime.now()); service.certifyCheck(check());
        assertEquals("Y", flag());
        assertEquals("Y", jdbc.queryForObject("SELECT EMAIL_CERTIFY_YN FROM TB_USER", String.class));
    }
    @Test void concurrentPasswordRequestsConsumeCodeOnlyOnce() throws Exception {
        code("FIND_PW", LocalDateTime.now());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<Object>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> { start.await(); return service.certifyConfrimAndPasswordChange(password()).getBody(); }));
            start.countDown();
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String a = mapper.writeValueAsString(tasks.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS));
            String b = mapper.writeValueAsString(tasks.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            var codes = List.of(mapper.readTree(a).get("resultCode").asText(), mapper.readTree(b).get("resultCode").asText());
            assertEquals(1, codes.stream().filter(com.toady.web.core.constants.ResultCode.SUCCESS.getCode()::equals).count());
            assertEquals(1, codes.stream().filter(com.toady.web.core.constants.ResultCode.ALREADY_CERTIFY_ERROR.getCode()::equals).count());
            assertEquals("Y", flag());
        }
    }
    @Test void expiredPasswordCodeCannotChangePassword() {
        code("FIND_PW", LocalDateTime.now().minusMinutes(11));
        service.certifyConfrimAndPasswordChange(password());
        assertEquals("original", jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER", String.class));
        assertEquals("N", flag());
    }

    @Test void passwordChangeInvalidatesOtherOutstandingCodes() {
        code("FIND_PW", LocalDateTime.now());
        code("FIND_PW", LocalDateTime.now().minusMinutes(4));
        jdbc.update("UPDATE TB_CERTIFY SET CERTIFY_CODE = 'older' WHERE CREATE_DT < ?", LocalDateTime.now().minusMinutes(3));
        service.certifyConfrimAndPasswordChange(password());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY WHERE CERTIFY_YN = 'N'", Integer.class));
        var old = password(); old.setCertifyCode("older"); old.setUserPassword("Otherpass123!"); old.setUserPasswordConfirm("Otherpass123!");
        assertEquals(com.toady.web.core.constants.ResultCode.ALREADY_CERTIFY_ERROR.getCode(),
                service.certifyConfrimAndPasswordChange(old).getBody().getResultCode());
    }
    @Test void failedEmailDoesNotThrottleRetry() throws Exception {
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable"))
                .doNothing().when(email).sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        var request = new CertifyDTO.UserInfo("verify@example.test");
        assertThrows(com.toady.web.core.util.email.EmailDeliveryException.class, () -> service.findPasswordRequestCertify(request));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY", Integer.class));
        assertEquals(com.toady.web.core.constants.ResultCode.SUCCESS.getCode(), service.findPasswordRequestCertify(request).getBody().getResultCode());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY", Integer.class));
    }
    @Test void oversizedUnifiedPageIsRejectedBeforeQuerying() {
        var params = new AnnouncementSearch(); params.setPage(100); params.setSize(100);
        assertThrows(IllegalArgumentException.class, () -> search.searchAll(params, List.of()));
    }

    @Test void expiredSignupCanResendAndAuthenticateWithNewLink() throws Exception {
        code("ACCOUNT", LocalDateTime.now().minusHours(25));
        assertEquals("SUCCESS", service.resendAccountVerification(new CertifyDTO.UserInfo("verify@example.test")).getBody().getResultCode());
        assertEquals("Y", jdbc.queryForObject("SELECT CERTIFY_YN FROM TB_CERTIFY WHERE CERTIFY_CODE='code'", String.class));
        String fresh = jdbc.queryForObject("SELECT CERTIFY_CODE FROM TB_CERTIFY WHERE CERTIFY_YN='N'", String.class);
        String body = jdbc.queryForObject("SELECT CERTIFY_MESSAGE FROM TB_CERTIFY WHERE CERTIFY_YN='N'", String.class);
        assertTrue(body.length() > 200); // 엔티티의 TEXT 정의로 실제 이메일 본문을 저장한다.
        var check = check(); check.setCertifyCode(fresh);
        assertEquals("SUCCESS", service.certifyCheck(check).getBody().getResultCode());
        assertEquals("Y", jdbc.queryForObject("SELECT EMAIL_CERTIFY_YN FROM TB_USER", String.class));
    }
    @Test void failedSignupResendPreservesOldLinkAndAllowsImmediateRetry() throws Exception {
        code("ACCOUNT", LocalDateTime.now().minusMinutes(5));
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(email)
                .sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        var request = new CertifyDTO.UserInfo("verify@example.test");
        assertThrows(com.toady.web.core.util.email.EmailDeliveryException.class, () -> service.resendAccountVerification(request));
        assertEquals("N", flag());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY", Integer.class));
        assertEquals("SUCCESS", service.resendAccountVerification(request).getBody().getResultCode());
    }
    @Test void recentSignupResendIsThrottledAndVerifiedUserReceivesNoEmail() {
        code("ACCOUNT", LocalDateTime.now());
        var request = new CertifyDTO.UserInfo("verify@example.test");
        assertEquals(com.toady.web.core.constants.ResultCode.RE_CERTIFY_REQUEST_ERROR.getCode(), service.resendAccountVerification(request).getBody().getResultCode());
        jdbc.update("UPDATE TB_USER SET EMAIL_CERTIFY_YN='Y'");
        assertEquals(com.toady.web.core.constants.ResultCode.ALREADY_CERTIFY_ERROR.getCode(), service.resendAccountVerification(request).getBody().getResultCode());
        org.mockito.Mockito.verifyNoInteractions(email);
    }
    @Test void smtpOperationsHaveFiniteTimeouts(@Autowired org.springframework.mail.javamail.JavaMailSender sender) {
        var properties = ((org.springframework.mail.javamail.JavaMailSenderImpl) sender).getJavaMailProperties();
        for (String key : List.of("mail.smtp.connectiontimeout", "mail.smtp.timeout", "mail.smtp.writetimeout")) {
            assertEquals("5000", properties.getProperty(key));
        }
    }

    @Test void concurrentSignupResendsSendOnlyOneNewEmail() throws Exception {
        code("ACCOUNT", LocalDateTime.now().minusHours(25));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 2; i++) tasks.add(pool.submit(() -> {
                start.await();
                return service.resendAccountVerification(new CertifyDTO.UserInfo("verify@example.test")).getBody().getResultCode();
            }));
            start.countDown();
            var results = List.of(tasks.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS), tasks.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("SUCCESS"::equals).count());
            assertEquals(1, results.stream().filter(com.toady.web.core.constants.ResultCode.RE_CERTIFY_REQUEST_ERROR.getCode()::equals).count());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY WHERE CERTIFY_YN='N'", Integer.class));
            org.mockito.Mockito.verify(email).sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
    }
}
