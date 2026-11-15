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
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/**
 * 기본 설정(이용자를 늘리는 시기): 새 대화 시작 조건(가입 후 시간·이야기 작성)이 꺼져 있어
 * 방금 가입하고 이야기를 쓰지 않은 회원도 바로 쪽지를 보낼 수 있다. 악용을 막는 핵심 규칙은 그대로다.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:community-defaults;MODE=MariaDB;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.dm.daily-new-threads=2"
})
@AutoConfigureMockMvc
class CommunityDefaultsTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.core.util.email.EmailUtilService email;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.toady.web.app.community.DmNotifier notifier;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired SimpleUserDetailsService details;
    @Autowired com.toady.web.app.community.Community community;
    final ObjectMapper json = new ObjectMapper();
    long a, b, c, d;
    String tokenA;

    @BeforeEach void seed() throws Exception {
        String sql = java.nio.file.Files.readString(java.nio.file.Path.of("..", "database.sql"));
        int start = sql.indexOf("-- 25. "), end = sql.indexOf("-- 26. ", start);
        StringBuilder body = new StringBuilder();
        for (String line : sql.substring(start, end < 0 ? sql.length() : end).split("\n")) if (!line.trim().startsWith("--")) body.append(line).append('\n');
        for (String statement : body.toString().split(";")) if (!statement.isBlank()) jdbc.execute(statement);
        for (String table : new String[]{"TB_USER_BLOCK", "TB_USER_REPORT", "TB_DM_MESSAGE", "TB_DM_THREAD", "TB_DM_SETTING", "TB_LTTOT_PBLANC_COMMENT", "TB_CERTIFY", "TB_USER"}) {
            jdbc.update("DELETE FROM " + table);
        }
        community.recheck();
        // a는 방금 가입했고 이야기를 쓰지 않았다(한국시간 기준 지금).
        Timestamp justNow = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        for (String name : new String[]{"a", "b", "c", "d"}) {
            jdbc.update("INSERT INTO TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN, CREATE_DT) VALUES (?,'x',?,'Y','N','N',?)",
                    name + "@example.test", "회원" + name, justNow);
        }
        a = seq("a"); b = seq("b"); c = seq("c"); d = seq("d");
        tokenA = users.generatorToken((CustomUserDetails) details.loadUserByUsername("a@example.test"));
    }

    long seq(String name) { return jdbc.queryForObject("SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL=?", Long.class, name + "@example.test"); }

    JsonNode call(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.with(r -> { r.setRemoteAddr("198.18.0.7"); return r; }).header("Authorization", "Bearer " + tokenA);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andReturn().getResponse();
        response.setCharacterEncoding("UTF-8");
        return json.readTree(response.getContentAsString());
    }
    JsonNode send(long to, String content) throws Exception { return call(post("/api/v1/me/messages"), Map.of("toUserSeq", to, "content", content)); }

    @Test void aNewMemberWithoutStoriesCanStartRightAwayButCoreRulesStillApply() throws Exception {
        assertTrue(call(get("/api/v1/members/" + b), null).at("/data/dm/canSend").asBoolean(), "가입 직후·이야기 없이도 보낼 수 있다");
        assertEquals("SUCCESS", send(b, "안녕하세요, 이야기 보고 연락드려요").get("resultCode").asText());
        // 핵심 규칙은 그대로: 답 받기 전 한 통, 첫 쪽지 연락처 금지, 하루 새 대화 수(여기서는 설정값 2명)
        assertEquals("WAITING", call(get("/api/v1/members/" + b), null).at("/data/dm/reason").asText());
        assertEquals("ERR-401", send(c, "010-1234-5678로 연락 주세요").get("resultCode").asText());
        assertEquals("SUCCESS", send(c, "안녕하세요").get("resultCode").asText());
        JsonNode third = send(d, "안녕하세요");
        assertEquals("ERR-403", third.get("resultCode").asText());
        assertTrue(third.get("resultMessage").asText().contains("2명"), "하루 한도 안내는 설정값을 그대로 보여 준다");
    }
}
