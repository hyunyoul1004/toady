package com.toady.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.certify.dto.CertifyDTO;
import com.toady.web.app.certify.service.CertifyService;
import com.toady.web.app.user.service.NicknameRules;
import com.toady.web.app.user.service.UserService;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.security.service.SimpleUserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 계정 악용 막기: 비ASCII 이메일 변형 거부(로그인·인증 확인·재발송), 차단한 회원의 답글 숨김(목록·배지·답글 수),
 * 가입 인증 메일의 이메일별 하루 상한, 사칭 닉네임 거부. 메일은 목(mock)이라 실제로 보내지 않는다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:abuseguard;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class AccountAbuseGuardTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.app.community.DmNotifier notifier;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserService users;
    @Autowired SimpleUserDetailsService details;
    @Autowired CertifyService certify;
    @Autowired com.toady.web.app.community.Community community;
    final ObjectMapper json = new ObjectMapper();
    /** IP별 요청 제한이 테스트끼리 섞이지 않게 요청마다 다른 주소를 쓴다. */
    static int ipIndex = 0;
    static boolean schemaReady = false;
    long me, other;

    @BeforeEach void seed() throws Exception {
        if (!schemaReady) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_PROFILE (USER_SEQ BIGINT PRIMARY KEY, PROFILE_ICON VARCHAR(30) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
            runSection(25);
            schemaReady = true;
        }
        community.recheck();
        org.mockito.Mockito.reset(email);
        for (String table : new String[]{"TB_USER_BLOCK", "TB_USER_PROFILE", "TB_LTTOT_PBLANC_COMMENT", "TB_CERTIFY", "TB_USER"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN) VALUES ('me@example.test',?,'가람','Y','N','N')", encoder.encode("Original1!"));
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN) VALUES ('other@example.test','x','나래','Y','N','N')");
        me = seq("me@example.test"); other = seq("other@example.test");
    }

    /** database.sql의 해당 구역(주석 줄 제외)을 운영과 같은 정의로 실행한다. */
    void runSection(int number) throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("..", "database.sql"));
        int start = sql.indexOf("-- " + number + ". ");
        int end = sql.indexOf("-- " + (number + 1) + ". ", start);
        StringBuilder body = new StringBuilder();
        for (String line : sql.substring(start, end < 0 ? sql.length() : end).split("\n")) if (!line.trim().startsWith("--")) body.append(line).append('\n');
        for (String statement : body.toString().split(";")) if (!statement.isBlank()) jdbc.execute(statement);
    }

    long seq(String email) { return jdbc.queryForObject("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL=?", Long.class, email); }
    String login(String email) { return users.generatorToken((CustomUserDetails) details.loadUserByUsername(email)); }
    JsonNode call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        String ip = "203.0.113." + (++ipIndex % 250 + 1);
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString().isEmpty() ? json.createObjectNode().put("status", response.getStatus()) : json.readTree(response.getContentAsString());
    }
    JsonNode register(String email, String nickname) throws Exception {
        return call(post("/webApi/user/regUser"), null, Map.of("userEmail", email, "userPassword", "Signup12!", "userPasswordConfirm", "Signup12!", "userNicName", nickname));
    }

    // ── 1. 비ASCII 이메일 변형 ──────────────────────────────────────────

    @Test void nonAsciiEmailVariantsAreRejectedWithTheGenericFailure() throws Exception {
        // 비밀번호가 맞아도 악센트 변형 주소로는 로그인하지 않는다(실패 제한 키를 우회하지 못하게)
        JsonNode accented = call(post("/webApi/user/login"), null, Map.of("userEmail", "mé@example.test", "userPassword", "Original1!"));
        JsonNode wrong = call(post("/webApi/user/login"), null, Map.of("userEmail", "me@example.test", "userPassword", "Wrong123!"));
        assertEquals("ERR-104", accented.get("resultCode").asText(), accented.toString());
        assertEquals(wrong.get("resultMessage").asText(), accented.get("resultMessage").asText(), "틀린 비밀번호와 같은 안내");
        assertEquals("ERR-104", wrong.get("resultCode").asText());

        // 인증번호 확인·비밀번호 재설정·재발송도 계정을 찾지 않고 일반 응답으로 돌린다
        var check = certify.certifyCheck(new CertifyDTO.CheckParam("mé@example.test", "code", "Original1!")).getBody();
        assertEquals("ERR-201", check.getResultCode());
        var change = certify.certifyConfrimAndPasswordChange(new CertifyDTO.PasswordChange("mé@example.test", "Newpass123!", "Newpass123!", "code")).getBody();
        assertEquals("ERR-201", change.getResultCode());
        var resend = certify.resendAccountVerification(new CertifyDTO.UserInfo("mé@example.test")).getBody();
        assertEquals(CertifyService.resendAccepted().getBody().getResultMessage(), resend.getResultMessage());
        org.mockito.Mockito.verifyNoInteractions(email);
        assertTrue(encoder.matches("Original1!", jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER WHERE USER_SEQ=?", String.class, me)), "비밀번호는 그대로");
    }

    // ── 2. 차단한 회원의 답글 ──────────────────────────────────────────

    @Test void repliesFromBlockedMembersAreHiddenFromListBadgeAndCounts() throws Exception {
        String insert = "INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,PARENT_COMMENT_SEQ,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('APT','H1','P1',?,?,0,0,'N',?,CURRENT_TIMESTAMP)";
        jdbc.update(insert, null, "내 이야기", me);
        long mine = jdbc.queryForObject("SELECT MAX(COMMENT_SEQ) FROM TB_LTTOT_PBLANC_COMMENT", Long.class);
        jdbc.update(insert, mine, "답글이에요", other);
        String token = login("me@example.test");

        JsonNode before = call(get("/api/v1/me/account/replies"), token, null).get("data");
        assertEquals(1, before.get("items").size());
        assertEquals(1, before.get("unread").asInt());
        assertEquals(1, call(get("/api/v1/me/account/comments"), token, null).at("/data/items/0/replyCount").asInt());

        jdbc.update("INSERT INTO TB_USER_BLOCK (USER_SEQ, BLOCKED_USER_SEQ, REG_DT) VALUES (?,?,?)", me, other, Timestamp.valueOf(LocalDateTime.now()));
        JsonNode after = call(get("/api/v1/me/account/replies"), token, null).get("data");
        assertEquals(0, after.get("items").size(), "차단한 회원의 답글은 받은 답글 목록에 없다");
        assertEquals(0, after.get("unread").asInt(), "배지 수에서도 빠진다");
        JsonNode since = call(get("/api/v1/me/account/replies").param("after", "0"), token, null).get("data");
        assertEquals(0, since.get("unread").asInt(), "마지막으로 본 번호 기준 새 답글 수에서도 빠진다");
        assertEquals(0, call(get("/api/v1/me/account/comments"), token, null).at("/data/items/0/replyCount").asInt(), "내 이야기별 답글 수에서도 빠진다");

        // 차단당한 쪽(다른 회원)이 받은 답글은 그대로다: 기존 차단 정책처럼 차단한 사람 쪽에서만 숨긴다
        jdbc.update(insert, null, "남의 이야기", other);
        long theirs = jdbc.queryForObject("SELECT MAX(COMMENT_SEQ) FROM TB_LTTOT_PBLANC_COMMENT", Long.class);
        jdbc.update(insert, theirs, "내 답글", me);
        assertEquals(1, call(get("/api/v1/me/account/replies"), login("other@example.test"), null).at("/data/items").size());
    }

    // ── 3. 가입 인증 메일 하루 상한 ──────────────────────────────────────

    @Test void signupVerificationMailIsCappedPerEmailPerDay() throws Exception {
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN, CREATE_DT) VALUES ('wait@example.test','x','대기','N','N','N',?)",
                Timestamp.valueOf(LocalDateTime.now()));
        var request = new CertifyDTO.UserInfo("Wait@Example.test");
        String accepted = CertifyService.resendAccepted().getBody().getResultMessage();
        assertTrue(accepted.contains("하루 " + CertifyService.DAILY_ACCOUNT_MAILS + "통"), "공통 안내에 하루 상한이 들어 있다");
        for (int i = 0; i < CertifyService.DAILY_ACCOUNT_MAILS + 2; i++) {
            // 4분 간격 제한을 넘기도록 앞서 보낸 코드를 과거로 옮긴다
            jdbc.update("UPDATE TB_CERTIFY SET CREATE_DT=?", Timestamp.valueOf(LocalDateTime.now().minusMinutes(5)));
            assertEquals(accepted, certify.resendAccountVerification(request).getBody().getResultMessage(), "상한을 넘어도 같은 응답(가입 여부를 숨긴다)");
        }
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(CertifyService.DAILY_ACCOUNT_MAILS)).sendSignupEmail(anyString(), anyString(), anyString());
        assertTrue(certify.accountMailCapped("wait@example.test"));

        // 같은 이메일로 다시 가입해도 메일을 더 보내지 않고, 계정 정보도 덮어쓰지 않는다
        String hash = jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER WHERE USER_EMAIL='wait@example.test'", String.class);
        JsonNode again = register("wait@example.test", "다시가입");
        assertEquals(CertifyService.ACCOUNT_MAIL_CAPPED, again.get("resultMessage").asText());
        assertEquals(hash, jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER WHERE USER_EMAIL='wait@example.test'", String.class));
        org.mockito.Mockito.verify(email, org.mockito.Mockito.times(CertifyService.DAILY_ACCOUNT_MAILS)).sendSignupEmail(anyString(), anyString(), anyString());
        assertFalse(certify.accountMailCapped("someone-else@example.test"), "다른 이메일에는 영향이 없다");
    }

    // ── 4. 사칭 닉네임 ──────────────────────────────────────────────────

    @Test void impersonatingNicknamesAreRejectedOnSignupAndChange() throws Exception {
        for (String nickname : new String[]{"TOADY지기", "toady", "운영자", "공식관리자", "토디", "Admin01", "aDmIn", "운영팀장"}) {
            JsonNode signup = register("new" + Math.abs(nickname.hashCode()) + "@example.test", nickname);
            assertEquals(NicknameRules.RESERVED_MESSAGE, signup.get("resultMessage").asText(), nickname);
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_EMAIL LIKE 'new%'", Integer.class));
        String token = login("me@example.test");
        JsonNode change = call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "토디공식"));
        assertEquals(NicknameRules.RESERVED_MESSAGE, change.get("resultMessage").asText());
        assertEquals("가람", jdbc.queryForObject("SELECT USER_NIC_NAME FROM TB_USER WHERE USER_SEQ=?", String.class, me));
        // 평범한 닉네임은 그대로 된다
        assertEquals("SUCCESS", call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "두껍이")).get("resultCode").asText());
        assertTrue(NicknameRules.reserved("To ady"), "공백을 끼워도 걸린다");
        assertTrue(NicknameRules.reserved("관 리 자"));
        assertFalse(NicknameRules.reserved("두껍이"));
        // 일반 영단어 속 admin은 막지 않고, 앞·뒤에 붙은 admin만 막는다
        assertFalse(NicknameRules.reserved("badminton12"));
        assertFalse(NicknameRules.reserved("Badminton러버"));
        assertTrue(NicknameRules.reserved("admin123"));
        assertTrue(NicknameRules.reserved("toadyadmin"));
    }

    @Test
    void signupMailCapFollowsTheEffectiveLimitWhenLeftAtZero() {
        // 환경변수 가입 상한 0은 '적용 중인 한도의 절반'이다. 서비스 설정이 없으면 환경변수 한도(400)의 절반이다.
        var budget = new com.toady.web.app.planner.MailBudget(jdbc, 400, 100, 0);
        assertEquals(200, com.toady.web.app.planner.MailBudgetAccess.signupLimit(budget, 400));
        // 서비스 설정으로 한도만 100으로 줄이면 절반(50)이 다시 계산된다(관리자 메일 현황 값과 같다).
        assertEquals(50, com.toady.web.app.planner.MailBudgetAccess.signupLimit(budget, 100));
    }
}
