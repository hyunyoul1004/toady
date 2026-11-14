package com.toady.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 내 정보: 요약, 닉네임·비밀번호 변경(새 토큰, 다른 기기 로그아웃), 탈퇴(개인 정보 정리·재가입 가능). */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:account;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class AccountSettingsTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired UserService users;
    @Autowired SimpleUserDetailsService details;
    final ObjectMapper json = new ObjectMapper();
    long seq;
    /** 요청 제한(같은 IP 10분에 10번)이 테스트끼리 섞이지 않게 테스트마다 다른 주소를 쓴다. */
    static int testIndex = 0;
    String ip;

    @BeforeEach void seed() {
        ip = "198.51.100." + (++testIndex);
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_FAVORITE (USER_SEQ BIGINT, ANNOUNCEMENT_TYPE VARCHAR(20), HOUSE_MANAGE_NO VARCHAR(40), PBLANC_NO VARCHAR(40), EMAIL_ENABLED BOOLEAN)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_CALENDAR_FEED (USER_SEQ BIGINT, FEED_TOKEN VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_PROFILE (USER_SEQ BIGINT PRIMARY KEY, PROFILE_ICON VARCHAR(30) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.update("DELETE FROM TB_USER_PROFILE"); jdbc.update("DELETE FROM TB_LTTOT_PBLANC_COMMENT");
        jdbc.update("DELETE FROM TB_FAVORITE"); jdbc.update("DELETE FROM TB_CALENDAR_FEED");
        jdbc.update("DELETE FROM TB_CERTIFY"); jdbc.update("DELETE FROM TB_USER");
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN) VALUES ('me@example.test',?,'두껍이','Y','N','N')", encoder.encode("Original1!"));
        jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN) VALUES ('other@example.test','x','다른사람','Y','N','N')");
        seq = jdbc.queryForObject("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL='me@example.test'", Long.class);
        jdbc.update("INSERT INTO TB_FAVORITE VALUES (?, 'apt', 'H1', 'P1', TRUE)", seq);
        jdbc.update("INSERT INTO TB_CALENDAR_FEED VALUES (?, 'feed-token')", seq);
    }

    String login(String email) { return users.generatorToken((CustomUserDetails) details.loadUserByUsername(email)); }
    JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString().isEmpty() ? json.createObjectNode().put("status", response.getStatus()) : json.readTree(response.getContentAsString()).deepCopy();
    }
    int status(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) throws Exception {
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test void summaryNeedsLoginAndShowsMyAccount() throws Exception {
        assertEquals(401, status(get("/api/v1/me/account"), null));
        JsonNode data = call(get("/api/v1/me/account"), login("me@example.test"), null).get("data");
        assertEquals("me@example.test", data.get("email").asText());
        assertEquals("두껍이", data.get("nickname").asText());
        assertEquals(1, data.get("favorites").asInt());
        assertEquals(1, data.get("calendarFeed").asInt());
    }

    @Test void nicknameChangeValidatesAndReturnsAFreshToken() throws Exception {
        String token = login("me@example.test");
        assertNotEquals("SUCCESS", call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "a b")).get("resultCode").asText(), "공백·특수문자는 쓸 수 없다");
        assertNotEquals("SUCCESS", call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "다른사람")).get("resultCode").asText(), "다른 회원의 닉네임은 쓸 수 없다");
        assertNotEquals("SUCCESS", call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "두껍이")).get("resultCode").asText(), "같은 닉네임");
        JsonNode ok = call(patch("/api/v1/me/account/nickname"), token, Map.of("nickname", "새두껍이"));
        assertEquals("SUCCESS", ok.get("resultCode").asText(), ok.toString());
        assertEquals("새두껍이", jdbc.queryForObject("SELECT USER_NIC_NAME FROM TB_USER WHERE USER_SEQ=?", String.class, seq));
        String fresh = ok.get("data").get("accessToken").asText();
        assertEquals(200, status(get("/api/v1/me/account"), fresh));
        assertEquals(200, status(get("/api/v1/me/account"), token), "닉네임만 바꾸면 기존 토큰도 계속 쓸 수 있다");
    }

    @Test void passwordChangeNeedsTheCurrentPasswordAndSignsOutOtherDevices() throws Exception {
        String token = login("me@example.test"), otherDevice = login("me@example.test");
        assertNotEquals("SUCCESS", call(post("/api/v1/me/account/password"), token,
                Map.of("currentPassword", "wrong", "newPassword", "Changed2!", "newPasswordConfirm", "Changed2!")).get("resultCode").asText());
        assertNotEquals("SUCCESS", call(post("/api/v1/me/account/password"), token,
                Map.of("currentPassword", "Original1!", "newPassword", "Changed2!", "newPasswordConfirm", "Changed3!")).get("resultCode").asText());
        assertNotEquals("SUCCESS", call(post("/api/v1/me/account/password"), token,
                Map.of("currentPassword", "Original1!", "newPassword", "123456", "newPasswordConfirm", "123456")).get("resultCode").asText(), "규칙에 맞지 않는 비밀번호");
        JsonNode ok = call(post("/api/v1/me/account/password"), token,
                Map.of("currentPassword", "Original1!", "newPassword", "Changed2!", "newPasswordConfirm", "Changed2!"));
        assertEquals("SUCCESS", ok.get("resultCode").asText(), ok.toString());
        assertTrue(encoder.matches("Changed2!", jdbc.queryForObject("SELECT USER_PASSWORD FROM TB_USER WHERE USER_SEQ=?", String.class, seq)));
        assertEquals(401, status(get("/api/v1/me/account"), otherDevice), "다른 기기의 기존 토큰은 로그아웃된다");
        assertEquals(200, status(get("/api/v1/me/account"), ok.get("data").get("accessToken").asText()), "지금 기기는 새 토큰으로 이어진다");
    }

    @Test void withdrawalNeedsPasswordAndPhraseThenErasesPersonalData() throws Exception {
        String token = login("me@example.test");
        assertNotEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), token, Map.of("password", "wrong", "phrase", "탈퇴합니다")).get("resultCode").asText());
        assertNotEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), token, Map.of("password", "Original1!", "phrase", "탈퇴")).get("resultCode").asText());
        assertEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), token, Map.of("password", "Original1!", "phrase", "탈퇴합니다")).get("resultCode").asText());
        var row = jdbc.queryForMap("SELECT USER_EMAIL, USER_NIC_NAME, WITHDRAWAL_YN FROM TB_USER WHERE USER_SEQ=?", seq);
        assertEquals("Y", row.get("WITHDRAWAL_YN"));
        assertFalse(String.valueOf(row.get("USER_EMAIL")).contains("me@example.test"), "이메일은 익명 값으로 바뀐다");
        assertTrue(String.valueOf(row.get("USER_NIC_NAME")).startsWith("탈퇴회원_"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=?", Integer.class, seq));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", Integer.class, seq));
        assertEquals(401, status(get("/api/v1/me/account"), token), "탈퇴한 계정의 토큰은 더 쓸 수 없다");
        // 같은 이메일·닉네임으로 다시 가입할 수 있다.
        assertNull(jdbc.query("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL='me@example.test' OR USER_NIC_NAME='두껍이'", (r, n) -> r.getLong(1)).stream().findFirst().orElse(null));
    }

    @Test void repeatedChangeAttemptsAreRateLimited() throws Exception {
        String token = login("me@example.test");
        String last = "";
        for (int i = 0; i < 11; i++) last = call(post("/api/v1/me/account/password"), token,
                Map.of("currentPassword", "guess" + i, "newPassword", "Changed2!", "newPasswordConfirm", "Changed2!")).get("resultCode").asText();
        assertEquals("ERR-429", last, "현재 비밀번호를 계속 대입해 볼 수 없다");
    }

    @Test void profileIconIsChosenFromThePresetListAndShownOnComments() throws Exception {
        String token = login("me@example.test");
        assertNotEquals("SUCCESS", call(patch("/api/v1/me/account/profile-icon"), token, Map.of("icon", "https://evil.example/x.png")).get("resultCode").asText(), "준비된 캐릭터만 고를 수 있다");
        assertEquals("SUCCESS", call(patch("/api/v1/me/account/profile-icon"), token, Map.of("icon", "toad")).get("resultCode").asText());
        assertEquals("toad", call(get("/api/v1/me/account"), token, null).get("data").get("profileIcon").asText());
        assertEquals("SUCCESS", call(patch("/api/v1/me/account/profile-icon"), token, Map.of("icon", "cat")).get("resultCode").asText(), "다시 고르면 바뀐다");
        assertEquals("cat", jdbc.queryForObject("SELECT PROFILE_ICON FROM TB_USER_PROFILE WHERE USER_SEQ=?", String.class, seq));
        // 댓글 목록 응답에 작성자 캐릭터가 실린다.
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('APT','H1','P1','안녕',0,0,'N',?,CURRENT_TIMESTAMP)", seq);
        var page = new com.toady.web.app.announcement.dto.AnnouncementCommentResponse(1L, "APT", "H1", "P1", null, "x", 0, 0, "N", seq, "두껍이", null, null, null);
        assertNull(page.profileIcon(), "JPQL용 생성자는 캐릭터 없이 만든다");
        assertEquals("cat", page.withProfileIcon("cat").profileIcon());
        assertEquals("SUCCESS", call(patch("/api/v1/me/account/profile-icon"), token, java.util.Collections.singletonMap("icon", null)).get("resultCode").asText(), "고르지 않음으로 되돌릴 수 있다");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_PROFILE WHERE USER_SEQ=?", Integer.class, seq));
    }

    @Test void myCommentsListsWhatIWroteNewestFirstWithAWayBackToTheAnnouncement() throws Exception {
        String token = login("me@example.test");
        for (int i = 1; i <= 12; i++) jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,PARENT_COMMENT_SEQ,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('APT','H1','P1',NULL,?,0,0,?,?,CURRENT_TIMESTAMP)",
                "이야기" + i, i == 3 ? "Y" : "N", seq);
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT) VALUES ('REMAINING','R1','R2','남의 글',0,0,'N',(SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL='other@example.test'),CURRENT_TIMESTAMP)");
        JsonNode first = call(get("/api/v1/me/account/comments"), token, null).get("data");
        assertEquals(11, first.get("total").asInt(), "지운 이야기와 남의 이야기는 빠진다");
        assertEquals(10, first.get("items").size());
        assertTrue(first.get("hasMore").asBoolean());
        assertEquals("이야기12", first.get("items").get(0).get("content").asText(), "최신순");
        assertEquals("apt/H1/P1", first.get("items").get(0).get("announcementId").asText(), "화면이 그 공고로 이동할 수 있는 주소");
        JsonNode second = call(get("/api/v1/me/account/comments").param("page", "1"), token, null).get("data");
        assertEquals(1, second.get("items").size());
        assertFalse(second.get("hasMore").asBoolean());
        assertEquals(401, status(get("/api/v1/me/account/comments"), null));
    }
}
