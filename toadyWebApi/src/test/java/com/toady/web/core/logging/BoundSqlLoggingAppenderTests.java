package com.toady.web.core.logging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** SQL 자리표시자 계산과 바인딩 값 삽입 동작을 검증합니다. */
class BoundSqlLoggingAppenderTests {
    @Test
    void interpolatesTypedValuesWithoutReplacingQuestionMarksInsideLiterals() {
        String sql = "select '?' as marker where name = ? and created_at >= ?";
        Map<Integer, BoundSqlLoggingAppender.BoundValue> values = Map.of(
                1, new BoundSqlLoggingAppender.BoundValue("VARCHAR", "O'Reilly"),
                2, new BoundSqlLoggingAppender.BoundValue("DATE", "2026-11-02")
        );

        assertEquals(
                "select '?' as marker where name = 'O''Reilly' and created_at >= '2026-11-02'",
                BoundSqlLoggingAppender.interpolate(sql, values)
        );
    }

    @Test
    void countsOnlyPlaceholdersOutsideSqlLiteralsAndComments() {
        String sql = "select '?' where a = ? and b = \"?\" /* ? */ -- ?\n and c = ?";

        assertEquals(2, BoundSqlLoggingAppender.countParameters(sql));
    }
}
