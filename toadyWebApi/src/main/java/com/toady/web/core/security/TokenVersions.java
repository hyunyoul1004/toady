package com.toady.web.core.security;

import com.toady.web.core.util.SqlErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 회원별 토큰 버전(database.sql 33번 TB_USER.TOKEN_VERSION).
 * 로그인할 때 토큰에 지금 버전을 넣고, 요청마다 DB 값과 비교한다. 로그아웃하면 버전을 1 올려 그 전에 발급한 토큰을 모두 무효로 만든다.
 * 비밀번호 버전(credentialVersion)과 같은 방식이며, 토큰을 서버에 따로 저장하지 않아도 강제 로그아웃이 된다.
 * 컬럼이 아직 없으면(또는 컬럼 권한이 없으면) 비교하지 않고(기존 동작 그대로) 로그아웃은 화면에서만 처리된다. 5분마다 다시 확인한다.
 * 그 밖의 DB 오류(연결 끊김·시간 초과 등)는 "컬럼 없음"으로 보지 않고 그대로 올린다.
 * 일시 오류를 컬럼 없음으로 보면 5분 동안 로그아웃한 토큰이 통과하고, 그사이 발급한 토큰에 버전 0이 들어가 나중에 거부되기 때문이다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @Component: JwtProviderImpl·AuthController가 쓴다
@Component
public class TokenVersions {
    private final JdbcTemplate jdbc;
    // 컬럼이 없다고 판단한 시각. 이 뒤 5분 동안은 조회하지 않는다(요청마다 SQL 오류가 나지 않게)
    private volatile Instant missingSince;

    public TokenVersions(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * 지금 버전. 컬럼이 없으면 null(알 수 없음 → 호출하는 쪽은 비교하지 않는다).
     * 그 밖의 DB 오류는 DataAccessException 그대로 던진다(검증은 거부, 발급은 중단).
     */
    public Long current(long userSeq) {
        if (paused()) return null;
        try {
            return jdbc.query("SELECT TOKEN_VERSION FROM TB_USER WHERE USER_SEQ=?", rs -> rs.next() ? rs.getLong(1) : 0L, userSeq);
        } catch (DataAccessException failure) {
            if (!columnUnavailable(failure)) throw failure;
            markMissing(failure);
            return null;
        }
    }

    /** 버전을 1 올린다(이 회원의 모든 토큰 무효). 바꿨으면 true, 컬럼이 없으면 false. 그 밖의 DB 오류는 던진다. */
    public boolean bump(long userSeq) {
        try {
            return jdbc.update("UPDATE TB_USER SET TOKEN_VERSION=TOKEN_VERSION+1 WHERE USER_SEQ=?", userSeq) > 0;
        } catch (DataAccessException failure) {
            if (!columnUnavailable(failure)) throw failure;
            markMissing(failure);
            return false;
        }
    }

    /** 테스트에서 컬럼을 만든 뒤 바로 다시 확인하게 한다. */
    public void recheck() { missingSince = null; }

    /** 33번 미적용(컬럼 없음) 또는 컬럼 권한 미부여인가. 둘 다 배포 순서 문제라 고정 상태다. */
    static boolean columnUnavailable(Throwable failure) {
        return SqlErrors.missingColumn(failure) || SqlErrors.denied(failure);
    }

    private boolean paused() {
        Instant since = missingSince;
        return since != null && since.plusSeconds(300).isAfter(Instant.now());
    }

    private void markMissing(DataAccessException failure) {
        if (missingSince == null) log.warn("토큰 버전 컬럼을 읽지 못해 서버 로그아웃 없이 동작합니다(database.sql 33번 확인): {}", failure.getMostSpecificCause().getMessage());
        missingSince = Instant.now();
    }
}
