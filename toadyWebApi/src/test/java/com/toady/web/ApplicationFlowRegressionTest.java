package com.toady.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.user.dto.UserDTO;
import com.toady.web.app.user.service.UserService;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 개별 메서드가 아니라 가입→인증→로그인→댓글→비밀번호 변경의 실제 API 연결을 검증한다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:flowaudit;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.security.mail-response-min-ms=0"
})
@AutoConfigureMockMvc
class ApplicationFlowRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserService users;
    @Autowired CertifyService certify;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @MockitoBean EmailUtilService email;
    static final String EMAIL = "audit@example.technology";
    static final String PASSWORD = "Password123!";

    @BeforeEach void clear() {
        for (String table : new String[]{"TB_CERTIFY", "TB_LTTOT_PBLANC_COMMENT", "TB_USER", "TB_APT_LTTOT_PBLANC_DETAIL"}) jdbc.update("DELETE FROM " + table);
        org.mockito.Mockito.reset(email);
    }
    String body(Object value) throws Exception { return json.writeValueAsString(value); }
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

    void announcement() {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,RCEPT_BGNDE) VALUES ('FLOW','FLOW','통합검증','2026-11-01')");
    }
    void register() throws Exception {
        mvc.perform(post("/webApi/user/regUser").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", EMAIL, "userPassword", PASSWORD, "userPasswordConfirm", PASSWORD, "userNicName", "검증회원"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
    }
    String login(String password) throws Exception {
        String response = mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("userEmail", EMAIL, "userPassword", password))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("accessToken").asText();
    }
    @Test void registrationTemplateFailureRollsBackTheMember() throws Exception {
        Object target = org.springframework.test.util.AopTestUtils.getUltimateTargetObject(certify);
        Object original = org.springframework.test.util.ReflectionTestUtils.getField(target, "DOMAIN");
        try {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "DOMAIN", "https://example.test/{unexpected}");
            var request = UserDTO.RegUser.builder().userEmail(EMAIL).userNicName("검증회원")
                    .userPassword(PASSWORD).userPasswordConfirm(PASSWORD).build();
            assertThrows(IllegalStateException.class, () -> users.regUser(request));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CERTIFY", Integer.class));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(target, "DOMAIN", original);
        }
    }
    @Test void fullAccountAndCommentLifecycle() throws Exception {
        register();
        String code = mailedCode();
        assertTrue(jdbc.queryForObject("SELECT CERTIFY_CODE FROM TB_CERTIFY WHERE CERTIFY_TYPE='ACCOUNT'", String.class).startsWith("h1:"), "코드는 해시로만 저장한다");
        mvc.perform(post("/webApi/certify/certify-check").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL, "certifyCode", code, "userPassword", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        String token = login(PASSWORD);
        announcement();
        String endpoint = "/api/v1/apt/announcements/FLOW/FLOW/comments";
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "무인증"))))
                .andExpect(status().isForbidden());
        String created = mvc.perform(post(endpoint).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "처음 댓글"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).path("data").path("commentSeq").asLong();
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN,LOCK_YN,WITHDRAWAL_YN) VALUES ('other@example.test',?,'다른회원','Y','N','N')", passwords.encode(PASSWORD));
        String otherLogin = mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", "other@example.test", "userPassword", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String otherToken = json.readTree(otherLogin).path("data").path("accessToken").asText();
        mvc.perform(patch(endpoint + "/" + id).header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("content", "다른 회원의 수정")))).andExpect(status().isForbidden());
        mvc.perform(delete(endpoint + "/" + id).header("Authorization", "Bearer " + otherToken)).andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/announcements").param("keyword", "통합검증"))
                .andExpect(jsonPath("$.data.content[0].commentCount").value(1));
        mvc.perform(patch(endpoint + "/" + id).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("content", "수정 댓글"))))
                .andExpect(status().isOk());
        mvc.perform(get(endpoint)).andExpect(jsonPath("$.data.content[0].content").value("수정 댓글"));
        // 답글은 한 단계만: 답글에 다시 답글을 달 수 없다.
        String reply = mvc.perform(post(endpoint).header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("content", "답글", "parentCommentSeq", id)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long replyId = json.readTree(reply).path("data").path("commentSeq").asLong();
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("content", "답글의 답글", "parentCommentSeq", replyId)))).andExpect(status().isBadRequest());
        // 부모 댓글을 지우면 답글도 함께 지워져 맥락 없는 답글이 남지 않는다(댓글 수 0).
        mvc.perform(delete(endpoint + "/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        assertEquals("Y", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=?", String.class, replyId));
        mvc.perform(get("/api/v1/announcements").param("keyword", "통합검증"))
                .andExpect(jsonPath("$.data.content[0].commentCount").value(0));
        mvc.perform(post("/webApi/certify/find-password-request-certify").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL)))).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        String reset = mailedCode();
        mvc.perform(post("/webApi/certify/certify-confrim-and-password-change").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL, "certifyCode", reset, "userPassword", "Newpass123!", "userPasswordConfirm", "Newpass123!"))))
                .andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("content", "이전 토큰")))).andExpect(status().isUnauthorized());
        assertFalse(login("Newpass123!").isBlank());
    }
    @Test void unverifiedAccountCannotBePreemptedByAnotherPassword() throws Exception {
        // 공격자가 남의 이메일로 먼저 가입해 둔다.
        mvc.perform(post("/webApi/user/regUser").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", EMAIL, "userPassword", "Attacker123!", "userPasswordConfirm", "Attacker123!", "userNicName", "선점자"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        // 이메일 주인이 다시 가입하면 미인증 계정을 자기 정보로 덮어쓴다(4분 안이라 새 메일 없이 기존 링크를 그대로 쓴다).
        register();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER", Integer.class));
        assertEquals("검증회원", jdbc.queryForObject("SELECT USER_NIC_NAME FROM TB_USER", String.class));
        String code = mailedCode();
        mvc.perform(post("/webApi/certify/certify-check").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL, "certifyCode", code, "userPassword", "Attacker123!"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("ERR-205"));
        mvc.perform(post("/webApi/certify/certify-check").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL, "certifyCode", code))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/webApi/certify/certify-check").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL, "certifyCode", code, "userPassword", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        login(PASSWORD);
        // 인증을 마친 계정은 더 이상 덮어쓸 수 없다.
        mvc.perform(post("/webApi/user/regUser").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", EMAIL, "userPassword", "Attacker123!", "userPasswordConfirm", "Attacker123!", "userNicName", "선점자"))))
                .andExpect(jsonPath("$.resultCode").value(com.toady.web.core.constants.ResultCode.ALREADY_EMAIL_ERROR.getCode()));
    }
    @Test void encodedPathsCannotBypassTheMailLimit() throws Exception {
        for (int i = 0; i < 5; i++)
            mvc.perform(post(java.net.URI.create("/webApi/certify/find-password-request-certify")).header("X-Real-IP", "203.0.113.77").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("userEmail", "enc" + i + "@example.test"))));
        // 글자 하나를 인코딩해도 Spring은 같은 컨트롤러로 매칭하므로 제한도 같아야 한다.
        mvc.perform(post(java.net.URI.create("/webApi/user/reg%55ser")).header("X-Real-IP", "203.0.113.77").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", "encoded@example.test", "userPassword", PASSWORD, "userPasswordConfirm", PASSWORD, "userNicName", "인코딩"))))
                .andExpect(status().isTooManyRequests());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_EMAIL='encoded@example.test'", Integer.class));
    }
    @Test void mailSendingRequestsAreLimitedPerClientIp() throws Exception {
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/webApi/certify/find-password-request-certify").header("X-Real-IP", "203.0.113.9").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("userEmail", "nobody" + i + "@example.test")))).andExpect(status().isOk());
        mvc.perform(post("/webApi/user/regUser").header("X-Real-IP", "203.0.113.9").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", "flood@example.test", "userPassword", PASSWORD, "userPasswordConfirm", PASSWORD, "userNicName", "대량가입"))))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.resultCode").value("ERR-429"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_EMAIL='flood@example.test'", Integer.class));
        // 다른 IP는 영향을 받지 않는다.
        mvc.perform(post("/webApi/certify/find-password-request-certify").header("X-Real-IP", "203.0.113.10").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", "nobody@example.test")))).andExpect(status().isOk());
    }
    @Test void nonAsciiEmailsCannotReachAnotherMailbox() throws Exception {
        // 켈빈 기호(U+212A)는 소문자로 바꾸면 'k'가 된다. 가입은 거절하고, 비밀번호 찾기는 계정을 찾지 않는다.
        mvc.perform(post("/webApi/user/regUser").header("X-Real-IP", "203.0.113.91").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", "\u212Aim@example.test", "userPassword", PASSWORD, "userPasswordConfirm", PASSWORD, "userNicName", "유니코드"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("ERR-401"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER", Integer.class));
        mvc.perform(post("/webApi/user/regUser").header("X-Real-IP", "203.0.113.91").contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "userEmail", EMAIL, "userPassword", PASSWORD, "userPasswordConfirm", PASSWORD, "userNicName", "검증회원"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        org.mockito.Mockito.clearInvocations(email);
        mvc.perform(post("/webApi/certify/find-password-request-certify").header("X-Real-IP", "203.0.113.91").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("userEmail", EMAIL.replace("t", "\u0442")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.resultCode").value("SUCCESS"));
        org.mockito.Mockito.verify(email, org.mockito.Mockito.never()).sendEmail(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
    @Test void invalidInputsReturnBadRequestInsteadOfServerErrors() throws Exception {
        for (String payload : new String[]{"{}", "{\"userEmail\":null,\"certifyCode\":null}", "{\"userEmail\":\"bad\",\"certifyCode\":\"x\"}"}) {
            mvc.perform(post("/webApi/certify/certify-check").contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.resultCode").value("ERR-401"));
        }
        // 화면에서 쓰지 않던 게시판 API는 제거했다.
        mvc.perform(get("/webApi/boards/board-list")).andExpect(status().isNotFound());
    }
    @Test void cursorPagingSurvivesNewCommentsAndDeletesWithIdenticalTimestamps() throws Exception {
        announcement();
        for (int n = 0; n < 4; n++) jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('APT','FLOW','FLOW',?,0,0,'N',1,'2026-11-12 09:00:00')", "row" + n);
        String endpoint = "/api/v1/apt/announcements/FLOW/FLOW/comments";
        var first = json.readTree(mvc.perform(get(endpoint).param("size", "2")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").path("content");
        long cursor = first.get(1).path("commentSeq").asLong();
        jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y' WHERE CONTENT='row1'");
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('APT','FLOW','FLOW','new',0,0,'N',1,CURRENT_TIMESTAMP)");
        mvc.perform(get(endpoint).param("size", "2").param("beforeCommentSeq", Long.toString(cursor)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content").value("row0"));
        mvc.perform(get(endpoint).param("page", "1").param("beforeCommentSeq", Long.toString(cursor))).andExpect(status().isBadRequest());
        mvc.perform(get(endpoint).param("page", "bad")).andExpect(status().isBadRequest());
    }
}
