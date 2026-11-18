package com.toady.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.operation.Sanctions;
import com.toady.web.app.user.service.UserService;
import com.toady.web.app.user.service.WithdrawnSanctions;
import com.toady.web.core.security.TokenVersions;
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
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 계정 안전: 탈퇴 후 재가입해도 제재·차단이 이어지는지, 서버 로그아웃이 기존 토큰을 끊는지,
 * 쪽지함의 안 읽은 수가 메뉴 배지와 같은 기준(차단한 회원 제외)인지. 메일은 목(mock)이라 실제로 보내지 않는다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:accountsafety;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class AccountSafetyTest {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.app.community.DmNotifier notifier;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserService users;
    @Autowired SimpleUserDetailsService details;
    @Autowired WithdrawnSanctions withdrawn;
    @Autowired TokenVersions tokenVersions;
    @Autowired com.toady.web.core.security.jwt.JwtProvider jwtProvider;
    @Autowired Sanctions sanctions;
    @Autowired com.toady.web.app.community.Community community;
    final ObjectMapper json = new ObjectMapper();
    static int testIndex = 0;
    static boolean schemaReady = false;
    String ip;
    long a, b;

    @BeforeEach void seed() throws Exception {
        ip = "192.0.2." + (++testIndex);
        if (!schemaReady) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_PROFILE (USER_SEQ BIGINT PRIMARY KEY, PROFILE_ICON VARCHAR(30) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
            for (int section : new int[]{25, 26, 32}) runSection(section);
            // 33번은 TB_USER가 JPA 테이블이라 주석으로 둔 구문을 테스트에서 직접 실행한다.
            jdbc.execute("ALTER TABLE TB_USER ADD COLUMN IF NOT EXISTS TOKEN_VERSION INT NOT NULL DEFAULT 0");
            schemaReady = true;
        }
        tokenVersions.recheck();
        community.recheck();
        for (String table : new String[]{"TB_WITHDRAWN_SANCTION", "TB_USER_SANCTION", "TB_USER_BLOCK", "TB_DM_MESSAGE", "TB_DM_THREAD", "TB_DM_SETTING", "TB_USER_PROFILE", "TB_LTTOT_PBLANC_COMMENT", "TB_CERTIFY", "TB_USER"}) {
            jdbc.update("DELETE FROM " + table);
        }
        Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusDays(10));
        for (String[] row : new String[][]{{"a@example.test", "가람"}, {"b@example.test", "나래"}}) {
            jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN, CREATE_DT) VALUES (?,?,?,'Y','N','N',?)",
                    row[0], encoder.encode("Original1!"), row[1], old);
        }
        a = seq("a@example.test"); b = seq("b@example.test");
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
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString().isEmpty() ? json.createObjectNode().put("status", response.getStatus()) : json.readTree(response.getContentAsString());
    }
    int status(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }
    JsonNode register(String email, String nickname) throws Exception {
        return call(post("/webApi/user/regUser"), null, Map.of("userEmail", email, "userPassword", "Rejoin12!", "userPasswordConfirm", "Rejoin12!", "userNicName", nickname));
    }

    @Test void reSignupAfterWithdrawalKeepsActiveSanctionsAndBlocks() throws Exception {
        LocalDateTime now = LocalDateTime.now(KST).truncatedTo(ChronoUnit.SECONDS);
        Timestamp banEnd = Timestamp.valueOf(now.plusDays(7)), dmEnd = Timestamp.valueOf(now.plusDays(3));
        jdbc.update("INSERT INTO TB_USER_SANCTION (USER_SEQ, KIND, REASON, START_DT, END_DT, ADMIN_NAME, REG_DT) VALUES (?, 'STORY_BAN', 'SPAM', ?, ?, 'admin', ?)",
                a, Timestamp.valueOf(now), banEnd, Timestamp.valueOf(now));
        // 이미 끝난 제재는 남기지 않는다
        jdbc.update("INSERT INTO TB_USER_SANCTION (USER_SEQ, KIND, REASON, START_DT, END_DT, ADMIN_NAME, REG_DT) VALUES (?, 'STORY_BAN', 'ABUSE', ?, ?, 'admin', ?)",
                a, Timestamp.valueOf(now.minusDays(9)), Timestamp.valueOf(now.minusDays(2)), Timestamp.valueOf(now));
        jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, SUSPENDED_UNTIL, UPDATE_DT) VALUES (?, 'ALL', ?, ?)", a, dmEnd, Timestamp.valueOf(now));
        jdbc.update("INSERT INTO TB_USER_BLOCK (USER_SEQ, BLOCKED_USER_SEQ, REG_DT) VALUES (?,?,?)", b, a, Timestamp.valueOf(now));

        assertEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), login("a@example.test"), Map.of("password", "Original1!", "phrase", "탈퇴합니다")).get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE USER_SEQ=?", Integer.class, a), "탈퇴 계정의 제재 행은 지운다");
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM TB_WITHDRAWN_SANCTION", Integer.class), "진행 중인 이야기 정지·쪽지 정지·차단 하나씩");
        String hashes = String.join(",", jdbc.queryForList("SELECT EMAIL_HASH FROM TB_WITHDRAWN_SANCTION", String.class));
        assertFalse(hashes.contains("example"), "이메일 원문은 남기지 않는다");
        assertTrue(jdbc.queryForObject("SELECT MAX(EXPIRE_DT) FROM TB_WITHDRAWN_SANCTION", Timestamp.class).toLocalDateTime().isBefore(now.plusDays(366)), "최대 1년까지만 보관");

        // 대소문자를 바꿔 다시 가입해도 같은 사람으로 본다
        assertEquals("SUCCESS", register("A@Example.test", "가람둘").get("resultCode").asText());
        long rejoined = seq("a@example.test");
        assertNotEquals(a, rejoined);
        assertEquals(banEnd, jdbc.queryForObject("SELECT END_DT FROM TB_USER_SANCTION WHERE USER_SEQ=? AND KIND='STORY_BAN'", Timestamp.class, rejoined));
        assertNotNull(sanctions.storyBanMessage(rejoined), "새 계정도 이야기를 쓸 수 없다");
        assertEquals(dmEnd, jdbc.queryForObject("SELECT SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=?", Timestamp.class, rejoined));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE USER_SEQ=? AND BLOCKED_USER_SEQ=?", Integer.class, b, rejoined), "차단도 새 계정에 이어진다");

        // 다른 이메일로 가입한 사람에게는 걸리지 않는다
        assertEquals("SUCCESS", register("fresh@example.test", "새회원").get("resultCode").asText());
        long fresh = seq("fresh@example.test");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE USER_SEQ=?", Integer.class, fresh));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE BLOCKED_USER_SEQ=?", Integer.class, fresh));
    }

    @Test void withdrawalWithoutSanctionsLeavesNoRecordAndExpiredRecordsArePurged() throws Exception {
        assertEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), login("a@example.test"), Map.of("password", "Original1!", "phrase", "탈퇴합니다")).get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_WITHDRAWN_SANCTION", Integer.class), "제재·차단이 없으면 아무것도 남기지 않는다");

        // 보관 기한이 지난 기록은 다시 걸지 않고, 정리 때 지워진다
        LocalDateTime now = LocalDateTime.now(KST);
        jdbc.update("INSERT INTO TB_WITHDRAWN_SANCTION (EMAIL_HASH, KIND, BLOCKER_SEQ, REASON, END_DT, EXPIRE_DT, REG_DT) VALUES (?, 'STORY_BAN', 0, 'SPAM', NULL, ?, ?)",
                hashOf("old@example.test"), Timestamp.valueOf(now.minusMinutes(1)), Timestamp.valueOf(now.minusDays(366)));
        assertEquals("SUCCESS", register("old@example.test", "오래된").get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE USER_SEQ=?", Integer.class, seq("old@example.test")));
        assertEquals(1, withdrawn.purgeExpired(now));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_WITHDRAWN_SANCTION", Integer.class));
    }

    // 패키지 밖이라 해시는 리플렉션으로 계산한다(같은 키·같은 정규화)
    String hashOf(String email) throws Exception {
        var method = WithdrawnSanctions.class.getDeclaredMethod("hash", String.class);
        method.setAccessible(true);
        return (String) method.invoke(org.springframework.test.util.AopTestUtils.getUltimateTargetObject(withdrawn), email);
    }

    @Test void logoutInvalidatesEveryIssuedToken() throws Exception {
        String phone = login("a@example.test"), laptop = login("a@example.test");
        assertEquals(200, status(get("/api/v1/me/account"), phone));
        JsonNode result = call(post("/api/v1/auth/logout"), phone, null);
        assertEquals("SUCCESS", result.get("resultCode").asText());
        assertTrue(result.at("/data/loggedOutEverywhere").asBoolean());
        assertEquals(1, jdbc.queryForObject("SELECT TOKEN_VERSION FROM TB_USER WHERE USER_SEQ=?", Integer.class, a));
        assertEquals(401, status(get("/api/v1/me/account"), phone), "로그아웃한 토큰은 더 쓸 수 없다");
        assertEquals(401, status(get("/api/v1/me/account"), laptop), "같은 회원의 다른 기기 토큰도 끊긴다");
        assertEquals(200, status(get("/api/v1/me/account"), login("a@example.test")), "다시 로그인하면 된다");
        assertEquals(200, status(get("/api/v1/me/account"), login("b@example.test")), "다른 회원에게는 영향이 없다");
        // 토큰 없이 불러도 오류 없이 끝난다(logout-all도 같은 동작)
        assertEquals("SUCCESS", call(post("/api/v1/auth/logout-all"), null, null).get("resultCode").asText());
    }

    @Test void transientDbErrorIsNotTreatedAsMissingColumn() {
        // 연결 끊김 같은 일시 오류: "컬럼 없음"으로 보지 않고 던진다(검증은 401, 발급은 중단). 비교도 멈추지 않는다.
        var flaky = new FailingJdbc(new org.springframework.dao.QueryTimeoutException("timeout", new java.sql.SQLTimeoutException("timeout", "HYT00", 0)));
        TokenVersions transientVersions = new TokenVersions(flaky);
        assertThrows(org.springframework.dao.DataAccessException.class, () -> transientVersions.current(a));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> transientVersions.bump(a));
        flaky.failure = null;
        assertEquals(7L, transientVersions.current(a), "일시 오류 뒤에도 곧바로 다시 비교한다(5분 멈춤 없음)");

        // 33번 미적용(MariaDB 1054·42S22): 비교하지 않는다(null)
        var missing = new FailingJdbc(new org.springframework.jdbc.BadSqlGrammarException("select", "SELECT TOKEN_VERSION",
                new java.sql.SQLSyntaxErrorException("Unknown column 'TOKEN_VERSION'", "42S22", 1054)));
        TokenVersions missingVersions = new TokenVersions(missing);
        assertNull(missingVersions.current(a));
        assertFalse(missingVersions.bump(a));
        missing.failure = null;
        assertNull(missingVersions.current(a), "컬럼 없음이면 5분 동안 다시 묻지 않는다");

        // 실제 H2에서 없는 컬럼 오류(42122)도 같은 판단을 받는다
        var h2Missing = assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.queryForObject("SELECT NO_SUCH_COLUMN FROM TB_USER WHERE USER_SEQ=?", Long.class, a));
        assertTrue(com.toady.web.core.util.SqlErrors.missingColumn(h2Missing));
        assertFalse(com.toady.web.core.util.SqlErrors.notApplied(new org.springframework.dao.QueryTimeoutException("timeout")));
    }

    /** 지정한 오류를 던지거나(failure가 있으면) 토큰 버전 7을 돌려주는 가짜 JdbcTemplate. */
    static class FailingJdbc extends JdbcTemplate {
        RuntimeException failure;
        FailingJdbc(RuntimeException failure) { this.failure = failure; }
        @Override public <T> T query(String sql, org.springframework.jdbc.core.ResultSetExtractor<T> rse, Object... args) {
            if (failure != null) throw failure;
            @SuppressWarnings("unchecked") T seven = (T) Long.valueOf(7L);
            return seven;
        }
        @Override public int update(String sql, Object... args) {
            if (failure != null) throw failure;
            return 1;
        }
    }

    @Test void renewedTokenKeepsTheVersionItWasValidatedWith() throws Exception {
        String token = login("a@example.test");
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        var auth = jwtProvider.getAuthentication(request);
        // 검증 직후 다른 기기에서 로그아웃해 버전이 올라간 상황(로그아웃·갱신 경합)
        jdbc.update("UPDATE TB_USER SET TOKEN_VERSION=TOKEN_VERSION+1 WHERE USER_SEQ=?", a);
        String renewed = jwtProvider.renewToken(request, auth);
        assertEquals(401, status(get("/api/v1/me/account"), renewed), "갱신 토큰은 DB를 다시 읽지 않고 이전 버전을 이어받아 로그아웃 뒤 살아남지 않는다");
        assertEquals(200, status(get("/api/v1/me/account"), login("a@example.test")));
    }

    @Test void withdrawingAgainClearsEarlierRecordsEvenWithoutCurrentSanctions() throws Exception {
        // 예전 탈퇴 때 남은 기록(그 뒤 제재가 풀렸거나 차단이 해제됨)
        LocalDateTime now = LocalDateTime.now(KST);
        jdbc.update("INSERT INTO TB_WITHDRAWN_SANCTION (EMAIL_HASH, KIND, BLOCKER_SEQ, REASON, END_DT, EXPIRE_DT, REG_DT) VALUES (?, 'BLOCKED_BY', ?, NULL, NULL, ?, ?)",
                hashOf("a@example.test"), b, Timestamp.valueOf(now.plusDays(100)), Timestamp.valueOf(now.minusDays(30)));
        assertEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), login("a@example.test"), Map.of("password", "Original1!", "phrase", "탈퇴합니다")).get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_WITHDRAWN_SANCTION", Integer.class), "지금 남길 것이 없어도 같은 이메일의 예전 기록은 지운다");
        assertEquals("SUCCESS", register("a@example.test", "가람셋").get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE BLOCKED_USER_SEQ=?", Integer.class, seq("a@example.test")), "풀린 차단이 되살아나지 않는다");
    }

    @Test void gmailVariantsShareOneHashButOtherDomainsKeepDotsAndPlus() throws Exception {
        String base = hashOf("ab@gmail.com");
        for (String variant : new String[]{"a.b@gmail.com", "A.B+promo@gmail.com", " ab+x.y@GoogleMail.com ", "a..b@googlemail.com"}) {
            assertEquals(base, hashOf(variant), variant);
        }
        assertEquals(hashOf("kim@outlook.com"), hashOf("kim+toady@outlook.com"), "+태그가 같은 받은편지함인 서비스는 뗀다");
        assertNotEquals(hashOf("a.b@example.test"), hashOf("ab@example.test"), "다른 도메인의 점은 다른 주소다");
        assertNotEquals(hashOf("a+b@example.test"), hashOf("a@example.test"), "모르는 도메인의 +는 별개 주소일 수 있어 떼지 않는다");
        assertNotEquals(base, hashOf("ab@example.test"));
    }

    @Test void threadUnreadMatchesTheBadgeWhenTheSenderIsBlocked() throws Exception {
        String tokenA = login("a@example.test"), tokenB = login("b@example.test");
        Map<String, Object> body = new HashMap<>();
        body.put("toUserSeq", b); body.put("content", "안녕하세요"); body.put("contextId", "apt/H1/P1");
        assertEquals("SUCCESS", call(post("/api/v1/me/messages"), tokenA, body).get("resultCode").asText());
        assertEquals(1, call(get("/api/v1/me/messages/unread"), tokenB, null).at("/data/unread").asLong());
        assertEquals(1, call(get("/api/v1/me/messages"), tokenB, null).at("/data/items/0/unread").asLong());

        call(post("/api/v1/me/blocks"), tokenB, Map.of("userSeq", a));
        assertEquals(0, call(get("/api/v1/me/messages/unread"), tokenB, null).at("/data/unread").asLong(), "배지는 차단한 회원의 쪽지를 세지 않는다");
        assertEquals(0, call(get("/api/v1/me/messages"), tokenB, null).at("/data/items/0/unread").asLong(), "쪽지함 숫자도 배지와 같다");
    }
}
