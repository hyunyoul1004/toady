package com.toady.web.app.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/** 회원 방문 일자는 하루 한 줄만 남고, 테이블이 없어도 요청을 막지 않는다. 알림 열기·권한 기록은 정해진 이름만 받는다. */
class MemberVisitsTest {
    JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:membervisits;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));

    @Test
    void recordsOneRowPerMemberPerKoreanDay() {
        jdbc.execute("DROP TABLE IF EXISTS TB_MEMBER_VISIT_DAY");
        jdbc.execute("CREATE TABLE TB_MEMBER_VISIT_DAY (USER_SEQ BIGINT NOT NULL, VISIT_DATE DATE NOT NULL, PRIMARY KEY (USER_SEQ, VISIT_DATE))");
        // 한국시간 11-17 23:30(UTC 14:30)과 다음 날 00:10은 다른 날이다.
        var lateNight = new MemberVisits(jdbc, Clock.fixed(Instant.parse("2026-11-17T14:30:00Z"), ZoneId.of("Asia/Seoul")));
        lateNight.record(7); lateNight.record(7); lateNight.record(8);
        var nextDay = new MemberVisits(jdbc, Clock.fixed(Instant.parse("2026-11-17T15:10:00Z"), ZoneId.of("Asia/Seoul")));
        nextDay.record(7);
        // 다른 서버(새 객체)가 같은 날 다시 기록해도 중복 키로 넘어간다.
        new MemberVisits(jdbc, Clock.fixed(Instant.parse("2026-11-17T15:20:00Z"), ZoneId.of("Asia/Seoul"))).record(7);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM TB_MEMBER_VISIT_DAY WHERE USER_SEQ=7", Integer.class));
        assertEquals(java.sql.Date.valueOf("2026-11-18"), jdbc.queryForObject("SELECT MAX(VISIT_DATE) FROM TB_MEMBER_VISIT_DAY", java.sql.Date.class));
    }

    @Test
    void missingTableDoesNotBreakRequests() {
        jdbc.execute("DROP TABLE IF EXISTS TB_MEMBER_VISIT_DAY");
        var visits = new MemberVisits(jdbc, Clock.fixed(Instant.parse("2026-11-17T03:00:00Z"), ZoneId.of("Asia/Seoul")));
        assertDoesNotThrow(() -> visits.record(1));
        // 실패한 뒤 10분 동안은 다시 시도하지 않는다(회원 요청마다 실패하는 INSERT를 보내지 않게).
        jdbc.execute("CREATE TABLE TB_MEMBER_VISIT_DAY (USER_SEQ BIGINT NOT NULL, VISIT_DATE DATE NOT NULL, PRIMARY KEY (USER_SEQ, VISIT_DATE))");
        visits.record(1);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_MEMBER_VISIT_DAY", Integer.class));
    }

    @Test
    void pushEventsAcceptOnlyKnownNames() {
        assertTrue(UsageEventController.validPair("push_open", "tool/push-result"));
        assertTrue(UsageEventController.validPair("push_open", "tool/push-notice"));
        assertFalse(UsageEventController.validPair("push_open", "tool/push-anything"));
        assertFalse(UsageEventController.validPair("push_open", "apt/1/1"));
        assertTrue(UsageEventController.validPair("push_denied", "tool/push"));
        assertTrue(UsageEventController.validPair("push_disable", "tool/push"));
        assertFalse(UsageEventController.validPair("push_disable", "tool/map-radius"));
        assertTrue(UsageEventController.validPair("app_install", "tool/install-accepted"));
        assertTrue(UsageEventController.validPair("app_install", "tool/install-guide"));
        assertFalse(UsageEventController.validPair("app_install", "tool/push"));
    }

    @Test
    void signupPromptEventsAcceptOnlyKnownMoments() {
        // 웹 signupPrompt.js SIGNUP_MOMENTS·importBucket, 관리자 SIGNUP_MOMENT_NAMES와 같은 이름만 받는다.
        for (String moment : java.util.List.of("tool/signup-favorite", "tool/signup-result", "tool/signup-calendar", "tool/signup-story")) {
            assertTrue(UsageEventController.validPair("signup_prompt_view", moment), moment);
            assertTrue(UsageEventController.validPair("signup_prompt_click", moment), moment);
        }
        assertFalse(UsageEventController.validPair("signup_prompt_view", "tool/signup-anything"));
        assertFalse(UsageEventController.validPair("signup_prompt_click", "apt/1/1"));
        assertTrue(UsageEventController.validPair("guest_import", "tool/import-1-2"));
        assertTrue(UsageEventController.validPair("guest_import", "tool/import-6"));
        assertFalse(UsageEventController.validPair("guest_import", "tool/import-17"), "정확한 개수는 받지 않는다");
    }
}
