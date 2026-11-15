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

/** 프로필 카드·쪽지(보내기 규칙·읽음·나가기)·차단·신고. 새 대화 시작 조건을 켠 설정으로 돌린다. 외부 연결(푸시)은 쓰지 않는다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:community;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    // 새 대화 시작 조건을 켠 상태(운영에서 악용이 보여 켰을 때)를 확인한다. 기본(꺼짐)은 CommunityDefaultsTest.
    "toady.dm.min-account-hours=24", "toady.dm.require-story=true"
})
@AutoConfigureMockMvc
class CommunityTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.app.community.DmNotifier notifier;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
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
        createSection25();
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

    /** 운영과 같은 정의를 쓰도록 database.sql 25번 구역(주석 제외)을 그대로 실행한다. 한 번만 만든다. */
    static boolean section25Created = false;
    void createSection25() throws Exception {
        if (section25Created) return;
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("..", "database.sql"));
        int start = sql.indexOf("-- 25. ");
        int end = sql.indexOf("-- 26. ", start);
        StringBuilder body = new StringBuilder();
        for (String line : sql.substring(start, end < 0 ? sql.length() : end).split("\n")) if (!line.trim().startsWith("--")) body.append(line).append('\n');
        for (String statement : body.toString().split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        section25Created = true;
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

    @Test void blockReasonsDifferForTheBlockerAndLookLikeAClosedInboxForTheBlocked() throws Exception {
        call(post("/api/v1/me/blocks"), tokenA, Map.of("userSeq", b));
        assertEquals("BLOCKED", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        call(delete("/api/v1/me/blocks/" + b), tokenA, null);
        call(post("/api/v1/me/blocks"), tokenB, Map.of("userSeq", a));
        JsonNode blockedView = call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm");
        call(delete("/api/v1/me/blocks/" + a), tokenB, null);
        call(put("/api/v1/me/messages/settings"), tokenB, Map.of("policy", "NONE"));
        JsonNode closedView = call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm");
        assertEquals(closedView.get("reason").asText(), blockedView.get("reason").asText(), "차단당한 쪽과 받지 않기 설정은 구별되지 않는다");
        assertEquals(closedView.get("message").asText(), blockedView.get("message").asText());
    }

    @Test void anExistingTwoWayConversationContinuesAfterTheReceiverClosesTheInbox() throws Exception {
        long thread = send(tokenA, b, "안녕하세요").at("/data/threadSeq").asLong();
        send(tokenB, a, "네 반가워요");
        call(put("/api/v1/me/messages/settings"), tokenB, Map.of("policy", "NONE"));
        assertEquals("SUCCESS", send(tokenA, b, "이어서 여쭤봐요").get("resultCode").asText());
        // 대화에 참여하지 않은 회원은 나가기도 못 한다.
        assertEquals("ERR-404", call(delete("/api/v1/me/messages/" + thread), tokenC, null).get("resultCode").asText());
    }

    @Test void refusedSendsLeaveNoEmptyThreadAndTheDailyNewThreadLimitHolds() throws Exception {
        // 연락처가 든 첫 쪽지는 거절되고 빈 대화방도 남지 않는다(오늘 시작한 대화 수에 세지 않음).
        assertEquals("ERR-401", send(tokenA, b, "010-1234-5678 주세요").get("resultCode").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_DM_THREAD", Integer.class));
        Timestamp old = Timestamp.valueOf(LocalDateTime.now().minusDays(10));
        for (int i = 0; i < 5; i++) {
            jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN, CREATE_DT) VALUES (?,'x',?,'Y','N','N',?)",
                    "limit" + i + "@example.test", "한도" + i, old);
            assertEquals("SUCCESS", send(tokenA, seq("limit" + i + "@example.test"), "안녕하세요").get("resultCode").asText());
        }
        JsonNode sixth = send(tokenA, b, "안녕하세요");
        assertEquals("ERR-403", sixth.get("resultCode").asText());
        assertTrue(sixth.get("resultMessage").asText().contains("하루"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM TB_DM_THREAD", Integer.class));
    }

    @Test void theReceiverIsNotifiedOnceUntilTheyReadTheThread() throws Exception {
        long thread = send(tokenA, b, "안녕하세요").at("/data/threadSeq").asLong();
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.times(1)).notify(b, a, thread);
        send(tokenB, a, "네 반가워요");
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.times(1)).notify(a, b, thread);
        // 받는 사람이 아직 읽지 않은 쪽지가 있으면 연달아 보내도 다시 알리지 않는다.
        send(tokenA, b, "하나 더");
        send(tokenA, b, "또 하나");
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.times(2)).notify(b, a, thread);
        // 읽고 나면 다음 쪽지는 다시 알린다.
        call(get("/api/v1/me/messages/" + thread), tokenB, null);
        send(tokenA, b, "읽으셨네요");
        org.mockito.Mockito.verify(notifier, org.mockito.Mockito.times(3)).notify(b, a, thread);
        // 규칙에 걸려 거절된 보내기는 알리지 않는다.
        org.mockito.Mockito.clearInvocations(notifier);
        assertEquals("ERR-403", send(tokenC, a, "안녕하세요").get("resultCode").asText(), "c는 이야기가 없어 새 대화를 시작할 수 없다");
        org.mockito.Mockito.verifyNoInteractions(notifier);
    }

    @Test void dailyMessageAndReportLimitsHold() throws Exception {
        long thread = send(tokenA, b, "안녕하세요").at("/data/threadSeq").asLong();
        send(tokenB, a, "네");
        // 오늘 이미 48통을 더 보낸 상태(합계 49)면 한 통은 되고, 50통이 차면 막힌다.
        // 서비스는 한국시간 자정부터 세므로 시각도 한국시간으로 넣는다(CI 서버는 UTC라 CURRENT_TIMESTAMP를 쓰면 0~9시 사이에 '어제'가 된다).
        Timestamp nowKst = Timestamp.valueOf(LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")));
        for (int i = 0; i < 48; i++) {
            jdbc.update("INSERT INTO TB_DM_MESSAGE (THREAD_SEQ, SENDER_SEQ, CONTENT, FLAGGED, REG_DT) VALUES (?,?,'이전 쪽지',FALSE,?)", thread, a, nowKst);
        }
        assertEquals("SUCCESS", send(tokenA, b, "50번째").get("resultCode").asText());
        JsonNode over = send(tokenA, b, "51번째");
        assertEquals("ERR-429", over.get("resultCode").asText());
        assertTrue(over.get("resultMessage").asText().contains("50"));

        // 신고는 하루 20건까지(대상마다 한 번).
        for (int i = 0; i < 20; i++) {
            jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ, TARGET_USER_SEQ, TARGET_TYPE, TARGET_SEQ, REASON, STATUS, REG_DT) VALUES (?,?,'COMMENT',?,'SPAM','OPEN',?)", a, b, 1000 + i, nowKst);
        }
        JsonNode report = call(post("/api/v1/me/reports"), tokenA, Map.of("targetType", "USER", "targetSeq", b, "reason", "SPAM"));
        assertEquals("ERR-429", report.get("resultCode").asText());
        assertEquals(20, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_REPORT WHERE REPORTER_SEQ=?", Integer.class, a));
    }

    @Test void withdrawalRemovesMySettingsAndBlocksAndOthersSeeAWithdrawnMember() throws Exception {
        jdbc.update("UPDATE TB_USER SET USER_PASSWORD=? WHERE USER_SEQ=?", encoder.encode("Original1!"), a);
        tokenA = login("a@example.test");   // 비밀번호가 바뀌면 이전 토큰은 무효가 되므로 다시 로그인한다
        long thread = send(tokenA, b, "안녕하세요").at("/data/threadSeq").asLong();
        call(post("/api/v1/me/blocks"), tokenA, Map.of("userSeq", c));
        call(put("/api/v1/me/messages/settings"), tokenA, Map.of("policy", "KNOWN"));
        call(post("/api/v1/me/blocks"), tokenB, Map.of("userSeq", a));   // 다른 사람이 한 차단은 그 사람의 기록이라 남는다
        assertEquals("SUCCESS", call(post("/api/v1/me/account/withdraw"), tokenA, Map.of("password", "Original1!", "phrase", "탈퇴합니다")).get("resultCode").asText());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE USER_SEQ=?", Integer.class, a));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_DM_SETTING WHERE USER_SEQ=?", Integer.class, a));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE USER_SEQ=?", Integer.class, b));
        call(delete("/api/v1/me/blocks/" + a), tokenB, null);
        // 받은 쪽지는 '탈퇴한 회원'으로 남고, 더는 보낼 수 없다.
        JsonNode opened = call(get("/api/v1/me/messages/" + thread), tokenB, null).get("data");
        assertEquals("탈퇴한 회원", opened.at("/other/nickname").asText());
        assertEquals(1, opened.get("messages").size());
        assertFalse(opened.get("canSend").asBoolean());
        assertEquals("탈퇴한 회원", call(get("/api/v1/members/" + a), tokenB, null).at("/data/nickname").asText());
        assertFalse(call(get("/api/v1/members/" + a), tokenB, null).at("/data").has("dm"), "탈퇴한 회원에게는 쪽지 버튼 상태를 주지 않는다");
    }

    @Test void suspendedMembersCannotSendAndMeEndpointsNeedLogin() throws Exception {
        jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, SUSPENDED_UNTIL, UPDATE_DT) VALUES (?, 'ALL', ?, CURRENT_TIMESTAMP)", a, Timestamp.valueOf(LocalDateTime.now().plusDays(7)));
        assertEquals("SUSPENDED", call(get("/api/v1/members/" + b), tokenA, null).at("/data/dm/reason").asText());
        assertEquals(401, mvc.perform(get("/api/v1/me/messages").with(r -> { r.setRemoteAddr(ip); return r; })).andReturn().getResponse().getStatus());
    }
}
