package com.toady.web.core.util;

import java.sql.SQLException;

/**
 * DB 오류가 "아직 적용하지 않은 SQL(테이블·컬럼 없음) 또는 권한 미부여"인지 가려낸다.
 * 이런 오류는 배포 순서 때문에 생기는 고정 상태라 건너뛰어도 되지만,
 * 연결 끊김·잠금 대기 시간 초과 같은 일시 오류까지 같은 취급을 하면 보안 검사가 꺼지거나 기록이 사라지므로 구분한다.
 * 오류 코드: MariaDB/MySQL(1146 테이블 없음, 1054 컬럼 없음, 1142·1143 테이블·컬럼 권한 없음),
 * H2(42102·42103·42104 테이블 없음, 42122 컬럼 없음, 90096 권한 없음), SQLSTATE(42S02 테이블, 42S22 컬럼).
 */
public final class SqlErrors {
    private SqlErrors() { }

    /** 테이블이 없다는 오류인가. */
    public static boolean missingTable(Throwable failure) {
        for (SQLException sql : chain(failure)) {
            int code = sql.getErrorCode();
            if (code == 1146 || code == 42102 || code == 42103 || code == 42104 || "42S02".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    /** 컬럼이 없다는 오류인가. */
    public static boolean missingColumn(Throwable failure) {
        for (SQLException sql : chain(failure)) {
            if (sql.getErrorCode() == 1054 || sql.getErrorCode() == 42122 || "42S22".equals(sql.getSQLState())) return true;
        }
        return false;
    }

    /** 테이블·컬럼 권한이 없다는 오류인가(GRANT를 아직 하지 않은 경우). */
    public static boolean denied(Throwable failure) {
        for (SQLException sql : chain(failure)) {
            int code = sql.getErrorCode();
            if (code == 1142 || code == 1143 || code == 90096) return true;
        }
        return false;
    }

    /** 적용 전 SQL·권한 문제라 건너뛰어도 되는 오류인가(테이블·컬럼 없음 또는 권한 없음). */
    public static boolean notApplied(Throwable failure) {
        return missingTable(failure) || missingColumn(failure) || denied(failure);
    }

    // 원인 사슬에 있는 SQLException과 그 getNextException()을 모두 모은다
    private static java.util.List<SQLException> chain(Throwable failure) {
        java.util.List<SQLException> found = new java.util.ArrayList<>();
        java.util.Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                for (SQLException next = sql; next != null && found.size() < 20; next = next.getNextException()) {
                    if (!found.contains(next)) found.add(next);
                }
            }
        }
        return found;
    }
}
