package com.toady.web.app.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 관리자 화면에서 정한 금칙어(database.sql 26번 TB_MODERATION_RULE). 1분마다 다시 읽는다.
 * 띄어쓰기·대소문자를 무시한 부분 일치만 쓴다(관리자 RuleService.normalize와 같은 규칙). 테이블이 없으면 금칙어가 없는 것으로 본다.
 */
@Component
public class ModerationRules {
    private static final Logger log = LoggerFactory.getLogger(ModerationRules.class);
    private static final long REFRESH_MS = 60_000;
    public enum Scope { STORY, DM }
    public enum Action { BLOCK, FLAG }
    record Rule(String compact, String scope, Action action) { }

    private final JdbcTemplate jdbc;
    private volatile List<Rule> rules = List.of();
    private volatile long loadedAt;

    public ModerationRules(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 걸리는 규칙 중 가장 강한 처리(BLOCK이 FLAG보다 먼저). 걸리지 않으면 null. */
    public Action check(String text, Scope scope) {
        String compact = normalize(text);
        if (compact.isEmpty()) return null;
        Action found = null;
        for (Rule rule : current()) {
            if (!rule.scope().equals("ALL") && !rule.scope().equals(scope.name())) continue;
            if (!compact.contains(rule.compact())) continue;
            if (rule.action() == Action.BLOCK) return Action.BLOCK;
            // 쪽지 표시(FLAG)는 쪽지에만 쓴다.
            if (scope == Scope.DM) found = Action.FLAG;
        }
        return found;
    }

    /** 관리자에서 바꾼 뒤 바로 반영해야 하는 테스트용. */
    void refresh() { loadedAt = 0; }

    private List<Rule> current() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < REFRESH_MS) return rules;
        synchronized (this) {
            if (now - loadedAt < REFRESH_MS) return rules;
            try {
                rules = jdbc.query("SELECT PATTERN, SCOPE, ACTION FROM TB_MODERATION_RULE WHERE USE_YN='Y'", (r, i) -> {
                    String compact = normalize(r.getString(1));
                    Action action = "FLAG".equals(r.getString(3)) ? Action.FLAG : Action.BLOCK;
                    return compact.length() < 2 ? null : new Rule(compact, r.getString(2), action);
                }).stream().filter(java.util.Objects::nonNull).toList();
            } catch (DataAccessException missing) {
                if (!rules.isEmpty()) log.warn("금칙어를 다시 읽지 못해 이전 목록을 씁니다: {}", missing.getMostSpecificCause().getMessage());
            }
            loadedAt = now;
            return rules;
        }
    }

    public static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
