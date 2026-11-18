package com.toady.web.app.operation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 관리자 운영 기능(database.sql 26번)이 서비스에 적용되는지: 이야기 쓰기 정지, 금칙어, 공고 숨김·안내, 회원 안내. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:operations;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class OperationRulesTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.toady.web.core.util.email.EmailUtilService mail;
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired ModerationRules rules; @Autowired AnnouncementOverrides overrides;
    static final String COMMENTS = "/api/v1/apt/announcements/OP1/N1/comments";
    long me; String bearer;

    @BeforeEach void setup() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_USER_SANCTION (SANCTION_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, USER_SEQ BIGINT NOT NULL, KIND VARCHAR(20) NOT NULL, REASON VARCHAR(20) NOT NULL,"
                + " MEMO VARCHAR(300), START_DT TIMESTAMP NOT NULL, END_DT TIMESTAMP, CANCELED_DT TIMESTAMP, SEEN_DT TIMESTAMP, ADMIN_NAME VARCHAR(100) NOT NULL, REG_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MODERATION_RULE (RULE_SEQ BIGINT AUTO_INCREMENT PRIMARY KEY, PATTERN VARCHAR(50) NOT NULL, SCOPE VARCHAR(10) DEFAULT 'ALL' NOT NULL,"
                + " ACTION VARCHAR(10) DEFAULT 'BLOCK' NOT NULL, USE_YN VARCHAR(1) DEFAULT 'Y' NOT NULL, NOTE VARCHAR(200), ADMIN_NAME VARCHAR(100) NOT NULL, REG_DT TIMESTAMP NOT NULL, UPDATE_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_ANNOUNCEMENT_OVERRIDE (ANNOUNCEMENT_TYPE VARCHAR(20) NOT NULL, HOUSE_MANAGE_NO VARCHAR(40) NOT NULL, PBLANC_NO VARCHAR(40) NOT NULL,"
                + " HIDDEN_YN VARCHAR(1) DEFAULT 'N' NOT NULL, NOTICE VARCHAR(500), ADMIN_NAME VARCHAR(100) NOT NULL, UPDATE_DT TIMESTAMP NOT NULL, PRIMARY KEY (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO))");
        for (String table : new String[]{"TB_USER_SANCTION", "TB_MODERATION_RULE", "TB_ANNOUNCEMENT_OVERRIDE", "TB_LTTOT_PBLANC_COMMENT", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_USER"}) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('OP1','N1','운영 시험 단지','경기도 파주시 동패동','2026-11-20','2026-11-21')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('OP2','N2','운영 다른 단지','경기도 파주시 야당동','2026-11-20','2026-11-21')");
        UserEntity user = users.save(UserEntity.builder().userEmail("op@example.test").userNicName("운영시험").userPassword(passwords.encode("Password123!"))
                .emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
        me = user.getUserSeq();
        String body = mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userEmail", "op@example.test", "userPassword", "Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        bearer = "Bearer " + json.readTree(body).path("data").path("accessToken").asText();
        rules.refresh(); overrides.refresh();
    }

    private String write(String content) throws Exception {
        return mvc.perform(post(COMMENTS).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", content))))
                .andReturn().getResponse().getContentAsString();
    }
    private void sanction(String kind, LocalDateTime end) {
        jdbc.update("INSERT INTO TB_USER_SANCTION (USER_SEQ, KIND, REASON, START_DT, END_DT, ADMIN_NAME, REG_DT) VALUES (?,?,'TRADE',?,?,'operator',?)",
                me, kind, Timestamp.valueOf(LocalDateTime.now()), end == null ? null : Timestamp.valueOf(end), Timestamp.valueOf(LocalDateTime.now()));
    }

    @Test void storyBanBlocksWritingUntilItEndsOrIsLifted() throws Exception {
        sanction("STORY_BAN", LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(3));
        String blocked = write("안녕하세요");
        assertTrue(blocked.contains("이야기를 쓸 수 없어요"), blocked);
        // 회원 안내에 진행 중인 정지가 보이고, 관리자 메모는 보내지 않는다.
        String notices = mvc.perform(get("/api/v1/me/notices").header("Authorization", bearer)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(notices.contains("STORY_BAN") && notices.contains("통장·전매 거래") && !notices.contains("MEMO"), notices);
        jdbc.update("UPDATE TB_USER_SANCTION SET CANCELED_DT=CURRENT_TIMESTAMP");
        assertTrue(write("다시 쓸 수 있어요").contains("SUCCESS"));
        // 끝난 정지는 막지 않는다.
        sanction("STORY_BAN", LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMinutes(1));
        assertTrue(write("기간이 끝났어요").contains("SUCCESS"));
    }

    @Test void warningsAreShownOnceUntilTheMemberConfirms() throws Exception {
        sanction("WARNING", null);
        long seq = jdbc.queryForObject("SELECT SANCTION_SEQ FROM TB_USER_SANCTION", Long.class);
        String before = mvc.perform(get("/api/v1/me/notices").header("Authorization", bearer)).andReturn().getResponse().getContentAsString();
        assertTrue(before.contains("WARNING"), before);
        assertTrue(write("경고는 쓰기를 막지 않아요").contains("SUCCESS"));
        mvc.perform(post("/api/v1/me/notices/" + seq + "/seen").header("Authorization", bearer)).andExpect(status().isOk());
        String after = mvc.perform(get("/api/v1/me/notices").header("Authorization", bearer)).andReturn().getResponse().getContentAsString();
        assertFalse(after.contains("WARNING"), after);
        mvc.perform(get("/api/v1/me/notices")).andExpect(status().is4xxClientError());
    }

    @Test void blockedWordsStopStoriesIgnoringSpacesAndCase() throws Exception {
        jdbc.update("INSERT INTO TB_MODERATION_RULE (PATTERN, SCOPE, ACTION, ADMIN_NAME, REG_DT, UPDATE_DT) VALUES ('통장 매매', 'ALL', 'BLOCK', 'operator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO TB_MODERATION_RULE (PATTERN, SCOPE, ACTION, ADMIN_NAME, REG_DT, UPDATE_DT) VALUES ('KAKAO', 'DM', 'FLAG', 'operator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        rules.refresh();
        assertTrue(write("청약통장매매 하실 분").contains("쓸 수 없는 표현"));
        assertTrue(write("통장 가입 기간이 궁금해요").contains("SUCCESS"), "일상 표현은 막지 않는다");
        assertNull(rules.check("kakao 아이디", ModerationRules.Scope.STORY), "쪽지 전용 표시 규칙은 이야기에 적용하지 않는다");
        assertEquals(ModerationRules.Action.FLAG, rules.check("Ka kao 아이디", ModerationRules.Scope.DM));
        jdbc.update("UPDATE TB_MODERATION_RULE SET USE_YN='N'"); rules.refresh();
        assertTrue(write("청약통장매매 하실 분").contains("SUCCESS"), "멈춘 금칙어는 적용하지 않는다");
    }

    @Test void hiddenAnnouncementsLeaveListsAndSearchButOpenWithANotice() throws Exception {
        jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE VALUES ('APT','OP1','N1','Y','청약홈에서 취소된 공고입니다.','operator',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE VALUES ('APT','OP2','N2','N','1순위 접수일이 바뀌었습니다.','operator',CURRENT_TIMESTAMP)");
        overrides.refresh();
        String list = mvc.perform(get("/api/v1/announcements").param("keyword", "파주")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(list.contains("운영 다른 단지") && !list.contains("운영 시험 단지"), list);
        String suggestions = mvc.perform(get("/api/v1/announcements/suggestions").param("keyword", "운영")).andReturn().getResponse().getContentAsString();
        assertTrue(suggestions.contains("OP2") && !suggestions.contains("OP1"), suggestions);
        mvc.perform(get("/api/v1/apt/announcements/OP1/N1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.operatorNotice.hidden").value(true)).andExpect(jsonPath("$.data.operatorNotice.message").value("청약홈에서 취소된 공고입니다."));
        mvc.perform(get("/api/v1/apt/announcements/OP2/N2")).andExpect(jsonPath("$.data.operatorNotice.hidden").value(false));
        assertTrue(write("숨긴 공고에 이야기").contains("숨긴 공고"));
        mvc.perform(get("/api/v1/public/announcements/apt/OP1/N1")).andExpect(status().isGone());
        String sitemap = mvc.perform(get("/api/v1/public/sitemaps/apt/0.xml")).andReturn().getResponse().getContentAsString();
        assertFalse(sitemap.contains("OP1"), sitemap);
    }

    @Test void hiddenAnnouncementsBlockEditingStoriesButStillAllowDeletingThem() throws Exception {
        String created = write("숨기기 전에 쓴 이야기");
        long seq = json.readTree(created).path("data").path("commentSeq").asLong();
        assertTrue(seq > 0, created);
        jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE VALUES ('APT','OP1','N1','Y',NULL,'operator',CURRENT_TIMESTAMP)");
        overrides.refresh();
        // 숨긴 공고에는 고치기도 쓰기와 같은 안내로 막는다.
        mvc.perform(patch(COMMENTS + "/" + seq).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("content", "숨긴 뒤 고친 내용")))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.resultMessage").value(org.hamcrest.Matchers.containsString("숨긴 공고")));
        assertEquals("숨기기 전에 쓴 이야기", jdbc.queryForObject("SELECT CONTENT FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=?", String.class, seq));
        // 본인 글 지우기는 숨긴 공고에서도 된다.
        mvc.perform(delete(COMMENTS + "/" + seq).header("Authorization", bearer)).andExpect(status().isOk());
        assertEquals("Y", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=?", String.class, seq));
    }

    @Test void suggestionsFillTheLimitEvenWhenNewerAnnouncementsAreHidden() throws Exception {
        // OP1(숨김)과 OP3(숨김)이 OP2보다 접수일이 늦다. 읽은 뒤 거르면 limit=1에서 결과가 비어 버린다.
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET RCEPT_BGNDE='2026-12-01' WHERE HOUSE_MANAGE_NO='OP1'");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('OP3','N3','운영 세번째 단지','경기도 파주시 목동동','2026-12-05','2026-12-06')");
        jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE VALUES ('APT','OP1','N1','Y',NULL,'operator',CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE VALUES ('APT','OP3','N3','Y',NULL,'operator',CURRENT_TIMESTAMP)");
        overrides.refresh();
        mvc.perform(get("/api/v1/announcements/suggestions").param("keyword", "운영").param("limit", "1")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].houseManageNo").value("OP2"));
    }
}
