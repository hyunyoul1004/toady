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
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
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
    /** 가입 인증은 링크(코드)와 가입 비밀번호가 모두 맞아야 한다. */
    CertifyDTO.CheckParam check() {
        jdbc.update("UPDATE TB_USER SET USER_PASSWORD=? WHERE USER_EMAIL='verify@example.test'", encoder.encode("Signup123!"));
        return new CertifyDTO.CheckParam("verify@example.test", "code", "Signup123!");
    }
    /** 코드는 DB에 해시로만 남으므로 실제로 보낸 메일 본문에서 꺼낸다(마지막 발송 기준). */
    String mailedCode() throws Exception {
        // 가입 인증(sendSignupEmail)과 비밀번호 찾기(sendEmail) 중 마지막으로 보낸 메일 본문.
        var sent = org.mockito.Mockito.mockingDetails(email).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("sendEmail") || call.getMethod().getName().equals("sendSignupEmail")).toList();
        assertFalse(sent.isEmpty(), "메일을 보내지 않았다");
        String body = (String) sent.get(sent.size() - 1).getArgument(2);
        var link = java.util.regex.Pattern.compile("certifyCode=([A-Z0-9-]{20,30})").matcher(body);
        if (link.find()) return link.group(1);
        var plain = java.util.regex.Pattern.compile("(?<![A-Z0-9])([A-HJ-NP-Z2-9]{24})(?![A-Z0-9])").matcher(body);
        assertTrue(plain.find(), body);
        return plain.group(1);
    }
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
    @Test void accountCodeWithWrongPasswordDoesNotAuthenticateOrConsumeCode() {
        code("ACCOUNT", LocalDateTime.now());
        var wrong = check(); wrong.setUserPassword("Attacker123!");
        assertEquals(com.toady.web.core.constants.ResultCode.PASSWORD_MISMATCH_ERROR.getCode(), service.certifyCheck(wrong).getBody().getResultCode());
        assertEquals("N", flag(), "비밀번호가 틀리면 코드를 소비하지 않는다");
        assertEquals("N", jdbc.queryForObject("SELECT EMAIL_CERTIFY_YN FROM TB_USER", String.class));
        assertEquals("SUCCESS", service.certifyCheck(check()).getBody().getResultCode());
    }
    @Test void unknownEmailLooksTheSameAsAWrongCode() {
        var empty = com.toady.web.core.constants.ResultCode.EMPTY_CERTIFY_REQUEST_ERROR.getCode();
        assertEquals(empty, service.certifyCheck(new CertifyDTO.CheckParam("nobody@example.test", "code", "Signup123!")).getBody().getResultCode());
        assertEquals(empty, service.certifyCheck(check()).getBody().getResultCode(), "가입자에게 코드가 틀린 경우와 같은 응답이다");
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
        // 폐기된 예전 링크는 '이미 인증된 계정'이 아니라 최신 메일을 쓰라고 안내한다(계정은 아직 미인증).
        var stale = service.certifyCheck(check()).getBody();
        assertEquals("ERR-204", stale.getResultCode());
        assertTrue(stale.getResultMessage().contains("가장 최근"), stale.getResultMessage());
        String fresh = mailedCode();
        assertTrue(jdbc.queryForObject("SELECT CERTIFY_CODE FROM TB_CERTIFY WHERE CERTIFY_YN='N'", String.class).startsWith("h1:"), "코드는 해시로만 저장한다");
        assertFalse(jdbc.queryForObject("SELECT CERTIFY_MESSAGE FROM TB_CERTIFY WHERE CERTIFY_YN='N'", String.class).contains(fresh), "메일 본문(코드)은 저장하지 않는다");
        var check = check(); check.setCertifyCode(fresh);
        assertEquals("SUCCESS", service.certifyCheck(check).getBody().getResultCode());
        assertEquals("Y", jdbc.queryForObject("SELECT EMAIL_CERTIFY_YN FROM TB_USER", String.class));
    }
    @Test void failedSignupResendPreservesOldLinkAndAllowsImmediateRetry() throws Exception {
        code("ACCOUNT", LocalDateTime.now().minusMinutes(5));
        org.mockito.Mockito.doThrow(new IllegalStateException("SMTP unavailable")).doNothing().when(email)
                .sendSignupEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        var request = new CertifyDTO.UserInfo("verify@example.test");
        assertThrows(com.toady.web.core.util.email.EmailDeliveryException.class, () -> service.resendAccountVerification(request));
        assertEquals("N", flag());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY", Integer.class));
        assertEquals("SUCCESS", service.resendAccountVerification(request).getBody().getResultCode());
    }
    @Test void recentSignupResendIsThrottledAndVerifiedUserReceivesNoEmail() {
        code("ACCOUNT", LocalDateTime.now());
        var request = new CertifyDTO.UserInfo("verify@example.test");
        // 재요청 간격·인증 완료·없는 계정 모두 같은 응답이라 계정 존재 여부가 드러나지 않는다(메일은 보내지 않는다).
        var throttled = service.resendAccountVerification(request).getBody();
        jdbc.update("UPDATE TB_USER SET EMAIL_CERTIFY_YN='Y'");
        var verified = service.resendAccountVerification(request).getBody();
        var unknown = service.resendAccountVerification(new CertifyDTO.UserInfo("nobody@example.test")).getBody();
        for (var body : List.of(throttled, verified, unknown)) {
            assertEquals("SUCCESS", body.getResultCode());
            assertEquals(unknown.getResultMessage(), body.getResultMessage());
        }
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
            assertEquals(2, results.stream().filter("SUCCESS"::equals).count(), "응답은 같고 메일은 한 번만 보낸다");
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY WHERE CERTIFY_YN='N'", Integer.class));
            org.mockito.Mockito.verify(email).sendSignupEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        }
    }
}
