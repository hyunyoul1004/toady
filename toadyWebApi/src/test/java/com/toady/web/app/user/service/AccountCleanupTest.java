package com.toady.web.app.user.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException;

import static org.junit.jupiter.api.Assertions.*;

/** 탈퇴·비밀번호 변경의 정리 단계: '테이블 없음'만 건너뛰고 나머지 오류는 실패로 돌려야 한다. */
class AccountCleanupTest {
    @Test void onlyMissingTablesAreSkipped() {
        assertTrue(AccountService.missingTable(new BadSqlGrammarException("delete", "DELETE FROM TB_X",
                new SQLSyntaxErrorException("Table 'toady.TB_X' doesn't exist", "42S02", 1146))), "MariaDB 테이블 없음");
        assertTrue(AccountService.missingTable(new BadSqlGrammarException("delete", "DELETE FROM TB_X",
                new SQLSyntaxErrorException("Table \"TB_X\" not found", "42S02", 42104))), "H2 테이블 없음");
        assertFalse(AccountService.missingTable(new BadSqlGrammarException("delete", "DELETE FROM TB_X",
                new SQLSyntaxErrorException("DELETE command denied to user", "42000", 1142))), "권한 없음은 실패로 돌린다");
        assertFalse(AccountService.missingTable(new CannotAcquireLockException("deadlock",
                new SQLException("Deadlock found when trying to get lock", "40001", 1213))), "교착은 실패로 돌린다");
    }
}
