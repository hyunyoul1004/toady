package com.toady.web.app.operation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

/** 금칙어 규칙의 SCOPE·ACTION이 비어 있어도(NULL·공백) 오류 없이 기본값(ALL·BLOCK)으로 적용되는지. 스프링 없이 H2만 쓴다. */
class ModerationRulesTest {
    JdbcTemplate jdbc;
    ModerationRules rules;

    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:moderation-unit;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        // 실제 테이블(database.sql 26번)은 NOT NULL이지만, 예전 데이터·수동 입력으로 비어 있는 경우를 흉내 내려고 NULL을 허용한다
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MODERATION_RULE (PATTERN VARCHAR(50), SCOPE VARCHAR(10), ACTION VARCHAR(10), USE_YN VARCHAR(1) DEFAULT 'Y')");
        jdbc.update("DELETE FROM TB_MODERATION_RULE");
        rules = new ModerationRules(jdbc);
    }

    private void rule(String pattern, String scope, String action) {
        jdbc.update("INSERT INTO TB_MODERATION_RULE (PATTERN, SCOPE, ACTION) VALUES (?,?,?)", pattern, scope, action);
        rules.refresh();
    }

    @Test void nullOrBlankScopeAppliesEverywhere() {
        rule("통장매매", null, "BLOCK");
        rule("대리청약", "  ", "BLOCK");
        assertEquals(ModerationRules.Action.BLOCK, assertDoesNotThrow(() -> rules.check("청약 통장 매매합니다", ModerationRules.Scope.STORY)));
        assertEquals(ModerationRules.Action.BLOCK, rules.check("통장매매", ModerationRules.Scope.DM));
        assertEquals(ModerationRules.Action.BLOCK, rules.check("대리 청약 해드려요", ModerationRules.Scope.STORY));
    }

    @Test void nullActionIsTreatedAsBlockAndScopeIgnoresCase() {
        rule("연락주세요", "dm", null);
        assertNull(rules.check("연락 주세요", ModerationRules.Scope.STORY), "쪽지 전용 규칙은 이야기에 적용하지 않는다");
        assertEquals(ModerationRules.Action.BLOCK, rules.check("연락 주세요", ModerationRules.Scope.DM));
    }

    @Test void flagStillOnlyMarksDirectMessages() {
        rule("kakao", "ALL", "FLAG");
        assertNull(rules.check("KaKao 아이디", ModerationRules.Scope.STORY));
        assertEquals(ModerationRules.Action.FLAG, rules.check("KaKao 아이디", ModerationRules.Scope.DM));
    }
}
