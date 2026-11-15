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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 프로필 카드·쪽지(보내기 규칙·읽음·나가기)·차단·신고. 외부 연결(푸시)은 쓰지 않는다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:community;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class CommunityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired SimpleUserDetailsService details;
    @Autowired com.toady.web.app.community.Community community;
    final ObjectMapper json = new ObjectMapper();
    static int testIndex = 0;
    String ip;
    long a, b, c;
    String tokenA, tokenB, tokenC;

    @BeforeEach void seed() throws Exception {
        ip = "203.0.113." + (++testIndex);
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_PROFILE (USER_SEQ BIGINT PRIMARY KEY, PROFILE_ICON VARCHAR(30) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_BLOCK (USER_SEQ BIGINT NOT NULL, BLOCKED_USER_SEQ BIGINT NOT NULL, REG_DT TIMESTAMP NOT NULL, PRIMARY KEY (USER_SEQ, BLOCKED_USER_SEQ))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_REPORT (REPORT_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, REPORTER_SEQ BIGINT NOT NULL, TARGET_USER_SEQ BIGINT NOT NULL,"
                + " TARGET_TYPE VARCHAR(10) NOT NULL, TARGET_SEQ BIGINT NOT NULL, REASON VARCHAR(20) NOT NULL, DETAIL VARCHAR(500), STATUS VARCHAR(10) DEFAULT 'OPEN' NOT NULL,"
                + " ACTION_NOTE VARCHAR(200), HANDLED_BY VARCHAR(100), HANDLED_DT TIMESTAMP, REG_DT TIMESTAMP NOT NULL, UNIQUE (REPORTER_SEQ, TARGET_TYPE, TARGET_SEQ))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_DM_THREAD (THREAD_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, USER_LOW BIGINT NOT NULL, USER_HIGH BIGINT NOT NULL, STARTED_BY BIGINT NOT NULL,"
                + " CONTEXT_ID VARCHAR(110), LAST_MESSAGE_SEQ BIGINT DEFAULT 0 NOT NULL, LAST_MESSAGE_DT TIMESTAMP, LOW_READ_SEQ BIGINT DEFAULT 0 NOT NULL, HIGH_READ_SEQ BIGINT DEFAULT 0 NOT NULL,"
                + " LOW_LEFT_SEQ BIGINT DEFAULT 0 NOT NULL, HIGH_LEFT_SEQ BIGINT DEFAULT 0 NOT NULL, REG_DT TIMESTAMP NOT NULL, UNIQUE (USER_LOW, USER_HIGH))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_DM_MESSAGE (MESSAGE_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, THREAD_SEQ BIGINT NOT NULL, SENDER_SEQ BIGINT NOT NULL,"
                + " CONTENT VARCHAR(1000) NOT NULL, FLAGGED BOOLEAN DEFAULT FALSE NOT NULL, REG_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_DM_SETTING (USER_SEQ BIGINT PRIMARY KEY, RECEIVE_POLICY VARCHAR(10) DEFAULT 'ALL' NOT NULL, SUSPENDED_UNTIL TIMESTAMP, UPDATE_DT TIMESTAMP NOT NULL)");
        for (String table : new String[]{"TB_USER_BLOCK", "TB_USER_REPORT", "TB_DM_MESSAGE", "TB_DM_THREAD", "TB_DM_SETTING", "TB_USER_PROFILE", "TB_LTTOT_PBLANC_COMMENT", "TB_CERTIFY", "TB_USER"}) {
            jdbc.update("DELETE FROM " + table);
        }
        community.recheck();
        Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusDays(10));
        for (String[] row : new String[][]{{"a@example.test", "가람"}, {"b@example.test", "나래"}, {"c@example.test", "다솜"}}) {
            jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN, CREATE_DT) VALUES (?,'x',?,'Y','N','N',?)", row[0], row[1], old);
        }
        a = seq("a@example.test"); b = seq("b@example.test"); c = seq("c@example.test");
        tokenA = login("a@example.test"); tokenB = login("b@example.test"); tokenC = login("c@example.test");
        story(a, null); // a는 이야기를 하나 남겼다(쪽지를 시작할 수 있음)
    }

    long seq(String email) { return jdbc.queryForObject("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL=?", Long.class, email); }
    String login(String email) { return users.generatorToken((CustomUserDetails) details.loadUserByUsername(email)); }
    long story(long author, Long parent) {
        jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, PARENT_COMMENT_SEQ, CONTENT, CREATE_USER_SEQ, DELETE_YN, REG_DT, RECOMMEND, OPPOSE)"
                + " VALUES ('APT','H1','P1',?,'이야기',?,'N',CURRENT_TIMESTAMP,0,0)", parent, author);
        return jdbc.queryForObject("SELECT MAX(COMMENT_SEQ) FROM TB_LTTOT_PBLANC_COMMENT", Long.class);
    }

    JsonNode call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.with(r -> { r.setRemoteAddr(ip); return r; });
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return response.getContentAsString().isEmpty() ? json.createObjectNode().put("status", response.getStatus()) : json.readTree(response.getContentAsString());
    }
    JsonNode send(String token, long to, String content) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("toUserSeq", to); body.put("content", content); body.put("contextId", "apt/H1/P1");
        return call(post("/api/v1/me/messages"), token, body);
    }
    long unread(String token) throws Exception { return call(get("/api/v1/me/messages/unread"), token, null).at("/data/unread").asLong(); }

    @Test void cardIsPublicWithoutPrivateDataAndShowsWhyMessagingIsClosed() throws Exception {
        JsonNode card = call(get("/api/v1/members/" + b), null, null).get("data");
        assertEquals("나래", card.get("nickname").asText());
        assertFalse(card.toString().contains("@example.test"), "이메일은 카드에 나오지 않는다");
        assertNull(card.get("dm"), "로그인하지 않으면 쪽지 상태를 주지 않는다");

        // b는 이야기를 남기지 않아 a에게 대화를 시작할 수 없다.
        JsonNode dm = call(get("/api/v1/members/" + a), tokenB, null).at("/data/dm");
        assertFalse(dm.get("canSend").asBoolean());
        assertEquals("NO_STORY", dm.get("reason").asText());
        // 갓 가입한 회원은 하루가 지나야 시작할 수 있다.
        jdbc.update("UPDATE TB_USER SET CREATE_DT=? WHERE USER_SEQ=?", Timestamp.valueOf(LocalDateTime.now().minusHours(2)), a);
        assertEquals("TOO_NEW", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        jdbc.update("UPDATE TB_USER SET CREATE_DT=? WHERE USER_SEQ=?", Timestamp.valueOf(LocalDateTime.now().minusDays(3)), a);
        assertTrue(call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/canSend").asBoolean());
    }

    @Test void firstMessageRulesUnreadReplyAndLeave() throws Exception {
        // 첫 쪽지에는 연락처를 넣을 수 없다.
        assertEquals("ERR-401", send(tokenA, b, "010-1234-5678로 연락 주세요").get("resultCode").asText());
        JsonNode sent = send(tokenA, b, "안녕하세요, 운정 단지 이야기 보고 연락드려요");
        assertEquals("SUCCESS", sent.get("resultCode").asText());
        long thread = sent.at("/data/threadSeq").asLong();
        // 답을 받기 전에는 한 통만.
        JsonNode again = send(tokenA, b, "혹시 보셨나요?");
        assertEquals("ERR-403", again.get("resultCode").asText());
        assertTrue(again.get("resultMessage").asText().contains("한 통"));

        assertEquals(1, unread(tokenB));
        JsonNode list = call(get("/api/v1/me/messages"), tokenB, null).get("data");
        assertEquals("가람", list.at("/items/0/other/nickname").asText());
        JsonNode opened = call(get("/api/v1/me/messages/" + thread), tokenB, null).get("data");
        assertEquals(1, opened.get("messages").size());
        assertTrue(opened.get("canSend").asBoolean(), "받은 사람은 이야기가 없어도 답할 수 있다");
        assertEquals(0, unread(tokenB));

        assertEquals("SUCCESS", send(tokenB, a, "네 반가워요").get("resultCode").asText());
        // 대화가 이어진 뒤에는 연락처·여러 통 모두 된다.
        assertEquals("SUCCESS", send(tokenA, b, "오픈채팅 주소 open.kakao.com/x 남겨요").get("resultCode").asText());
        assertEquals("SUCCESS", send(tokenA, b, "한 통 더").get("resultCode").asText());

        // 다른 회원은 대화를 열 수 없다.
        assertEquals("ERR-404", call(get("/api/v1/me/messages/" + thread), tokenC, null).get("resultCode").asText());

        // 나가면 내 쪽지함에서 사라지고, 새 쪽지가 오면 그 뒤부터 다시 보인다.
        assertEquals("SUCCESS", call(delete("/api/v1/me/messages/" + thread), tokenB, null).get("resultCode").asText());
        assertEquals(0, call(get("/api/v1/me/messages"), tokenB, null).at("/data/items").size());
        send(tokenA, b, "다시 왔어요");
        JsonNode back = call(get("/api/v1/me/messages/" + thread), tokenB, null).get("data");
        assertEquals(1, back.get("messages").size());
        assertEquals("다시 왔어요", back.at("/messages/0/content").asText());
    }

    @Test void tradeWordsWarnOnlyTheReceiver() throws Exception {
        JsonNode sent = send(tokenA, b, "청약통장 양도하실 분 계신가요");
        assertEquals("SUCCESS", sent.get("resultCode").asText());
        assertFalse(sent.at("/data/message/flagged").asBoolean(), "보낸 사람에게는 경고를 붙이지 않는다");
        long thread = sent.at("/data/threadSeq").asLong();
        assertTrue(call(get("/api/v1/me/messages/" + thread), tokenB, null).at("/data/messages/0/flagged").asBoolean());
    }

    @Test void blockAndReceivePolicyStopNewMessagesWithoutRevealingTheBlock() throws Exception {
        assertEquals("SUCCESS", call(post("/api/v1/me/blocks"), tokenB, Map.of("userSeq", a)).get("resultCode").asText());
        JsonNode refused = send(tokenA, b, "안녕하세요");
        assertEquals("ERR-403", refused.get("resultCode").asText());
        assertFalse(refused.get("resultMessage").asText().contains("차단"), "상대가 나를 차단했다는 사실은 알리지 않는다");
        assertEquals("가람", call(get("/api/v1/me/blocks"), tokenB, null).at("/data/items/0/nickname").asText());
        call(delete("/api/v1/me/blocks/" + a), tokenB, null);

        call(put("/api/v1/me/messages/settings"), tokenB, Map.of("policy", "NONE"));
        assertEquals("CLOSED", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        call(put("/api/v1/me/messages/settings"), tokenB, Map.of("policy", "KNOWN"));
        assertEquals("KNOWN_ONLY", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        // b의 이야기에 a가 답글을 달면 '이야기로 이어진 사람'이 된다.
        long bStory = story(b, null);
        story(a, bStory);
        assertEquals("SUCCESS", send(tokenA, b, "답글 보고 연락드려요").get("resultCode").asText());
    }

    @Test void reportsUseTheServerSideAuthorAndCountOnce() throws Exception {
        long bStory = story(b, null);
        assertEquals("ERR-401", call(post("/api/v1/me/reports"), tokenB, Map.of("targetType", "COMMENT", "targetSeq", bStory, "reason", "SPAM")).get("resultCode").asText());
        JsonNode first = call(post("/api/v1/me/reports"), tokenA, Map.of("targetType", "COMMENT", "targetSeq", bStory, "reason", "TRADE"));
        assertEquals("SUCCESS", first.get("resultCode").asText());
        assertTrue(call(post("/api/v1/me/reports"), tokenA, Map.of("targetType", "COMMENT", "targetSeq", bStory, "reason", "TRADE")).at("/data/duplicate").asBoolean());
        assertEquals(b, jdbc.queryForObject("SELECT TARGET_USER_SEQ FROM TB_USER_REPORT", Long.class));
        assertEquals("ERR-401", call(post("/api/v1/me/reports"), tokenA, Map.of("targetType", "USER", "targetSeq", b, "reason", "ETC")).get("resultCode").asText(), "기타는 설명이 필요하다");

        // 쪽지는 대화에 참여한 사람이 상대가 보낸 쪽지만 신고할 수 있다.
        long message = send(tokenA, b, "안녕하세요").at("/data/message/messageSeq").asLong();
        assertEquals("ERR-404", call(post("/api/v1/me/reports"), tokenC, Map.of("targetType", "MESSAGE", "targetSeq", message, "reason", "ABUSE")).get("resultCode").asText());
        assertEquals("ERR-401", call(post("/api/v1/me/reports"), tokenA, Map.of("targetType", "MESSAGE", "targetSeq", message, "reason", "ABUSE")).get("resultCode").asText());
        assertEquals("SUCCESS", call(post("/api/v1/me/reports"), tokenB, Map.of("targetType", "MESSAGE", "targetSeq", message, "reason", "ABUSE")).get("resultCode").asText());
    }

    @Test void suspendedMembersCannotSendAndMeEndpointsNeedLogin() throws Exception {
        jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, SUSPENDED_UNTIL, UPDATE_DT) VALUES (?, 'ALL', ?, CURRENT_TIMESTAMP)", a, Timestamp.valueOf(LocalDateTime.now().plusDays(7)));
        assertEquals("SUSPENDED", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        assertEquals(401, mvc.perform(get("/api/v1/me/messages").with(r -> { r.setRemoteAddr(ip); return r; })).andReturn().getResponse().getStatus());
    }
}
