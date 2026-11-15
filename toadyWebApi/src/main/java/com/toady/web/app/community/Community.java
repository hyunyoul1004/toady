package com.toady.web.app.community;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 차단·신고·쪽지가 함께 쓰는 공통 부분: 결과 형식, 한국시간, 테이블 준비 여부(database.sql 25번), 회원 상태 조회.
 */
@Component
public class Community {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 화면에 그대로 보여 줄 결과. ok가 아니면 code·message로 이유를 알린다. */
    public record Result(boolean ok, String code, String message, Object data) {
        public static Result done(String message, Object data) { return new Result(true, "SUCCESS", message, data); }
        public static Result fail(String code, String message) { return new Result(false, code, message, null); }
    }

    static final String NOT_READY = "쪽지·차단·신고 기능을 준비하고 있어요. 잠시 뒤에 다시 시도해 주세요.";

    /** 회원 상태. 없으면 null. */
    public record Member(long userSeq, String nickname, LocalDateTime joinedAt, boolean withdrawn, boolean locked, boolean emailVerified) {
        boolean active() { return !withdrawn && !locked; }
    }

    private final JdbcTemplate jdbc;
    private volatile Instant checkedAt = Instant.EPOCH;
    private volatile boolean ready;

    public Community(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    static LocalDateTime now() { return LocalDateTime.now(KST); }
    static Timestamp ts(LocalDateTime time) { return Timestamp.valueOf(time); }

    /** 25번 테이블이 모두 있는가. 5분마다 다시 확인한다(배포 뒤 SQL을 적용하면 재시작 없이 켜진다). */
    public boolean ready() {
        Instant now = Instant.now();
        if (checkedAt.plusSeconds(300).isAfter(now)) return ready;
        boolean found;
        try {
            for (String table : new String[]{"TB_USER_BLOCK", "TB_USER_REPORT", "TB_DM_THREAD", "TB_DM_MESSAGE", "TB_DM_SETTING"}) {
                jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE 1=0", Integer.class);
            }
            found = true;
        } catch (DataAccessException missing) { found = false; }
        ready = found; checkedAt = now;
        return found;
    }

    /** 테스트에서 테이블을 만든 뒤 바로 다시 확인하게 한다. */
    public void recheck() { checkedAt = Instant.EPOCH; }

    public Member member(long userSeq) {
        return jdbc.query("SELECT USER_SEQ, USER_NIC_NAME, CREATE_DT, WITHDRAWAL_YN, LOCK_YN, EMAIL_CERTIFY_YN FROM TB_USER WHERE USER_SEQ=?", rs -> {
            if (!rs.next()) return null;
            Timestamp created = rs.getTimestamp("CREATE_DT");
            return new Member(rs.getLong(1), rs.getString(2), created == null ? null : created.toLocalDateTime(),
                    "Y".equals(rs.getString("WITHDRAWAL_YN")), "Y".equals(rs.getString("LOCK_YN")), "Y".equals(rs.getString("EMAIL_CERTIFY_YN")));
        }, userSeq);
    }

    /** a가 b를 차단했는가. */
    public boolean blocked(long a, long b) {
        try { return count("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE USER_SEQ=? AND BLOCKED_USER_SEQ=?", a, b) > 0; }
        catch (DataAccessException missing) { return false; }
    }

    long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    /** 이야기(답글)로 이어진 적이 있는가: 한쪽 이야기에 다른 쪽이 답글을 단 경우(지운 글 제외). */
    boolean talkedInStories(long a, long b) {
        return count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT c JOIN TB_LTTOT_PBLANC_COMMENT p ON p.COMMENT_SEQ=c.PARENT_COMMENT_SEQ"
                + " WHERE c.DELETE_YN='N' AND p.DELETE_YN='N' AND ((c.CREATE_USER_SEQ=? AND p.CREATE_USER_SEQ=?) OR (c.CREATE_USER_SEQ=? AND p.CREATE_USER_SEQ=?))",
                a, b, b, a) > 0;
    }
}
