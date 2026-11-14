package com.toady.web.app.push;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** TB_PUSH_SUBSCRIPTION 접근. 엔드포인트 원문은 길고 그 자체가 발송 권한이라 SHA-256 해시를 키로 쓴다. */
@Repository
public class PushSubscriptions {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private volatile Instant readyCheckedAt = Instant.EPOCH; private volatile boolean ready;

    public PushSubscriptions(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Row(String hash, String endpoint, String p256dh, String auth, Long userSeq, boolean favorites,
            List<String> favoriteIds, List<String> areas, LocalDateTime registeredAt, LocalDateTime lastDigestAt, LocalDate lastAttemptDate) {
        WebPushSender.Target target() { return new WebPushSender.Target(endpoint, p256dh, auth); }
    }
    public record Scope(Long userSeq, UUID visitorId, boolean favorites, List<String> favoriteIds, List<String> areas) { }

    static String hash(String endpoint) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    static LocalDateTime now(Clock clock) { return LocalDateTime.now(clock.withZone(KST)); }

    /** database.sql 13번 미적용 DB에서는 구독 화면을 숨긴다. 5분마다 다시 확인한다. */
    public boolean ready() {
        Instant now = Instant.now();
        if (readyCheckedAt.plusSeconds(300).isAfter(now)) return ready;
        try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE 1=0", Integer.class); ready = true; }
        catch (DataAccessException missing) { ready = false; }
        readyCheckedAt = now;
        return ready;
    }

    public void upsert(String endpoint, String p256dh, String auth, Scope scope, Clock clock) {
        String hash = hash(endpoint);
        Timestamp now = Timestamp.valueOf(now(clock));
        String ids = scope.userSeq() == null && !scope.favoriteIds().isEmpty() ? String.join(",", scope.favoriteIds()) : null;
        String areas = String.join(",", scope.areas());
        // 회원 구독에는 익명 방문자 ID를 저장하지 않는다(익명 통계를 회원과 연결하지 않음). 로그아웃 상태로 다시 저장하면 그때 익명 값으로 바뀐다.
        String visitor = scope.userSeq() != null || scope.visitorId() == null ? null : scope.visitorId().toString();
        // 같은 브라우저가 로그아웃 상태로 다시 저장하면 회원 연결을 끊어, 공용 기기에서 이전 회원의 관심 공고가 알림으로 오지 않게 한다.
        String update = "UPDATE TB_PUSH_SUBSCRIPTION SET ENDPOINT=?,P256DH=?,AUTH=?,USER_SEQ=?,VISITOR_ID=?,FAVORITES_ENABLED=?,FAVORITE_IDS=?,AREA_SLUGS=?,UPDATED_DT=? WHERE ENDPOINT_HASH=?";
        Object[] values = {endpoint, p256dh, auth, scope.userSeq(), visitor, scope.favorites(), ids, areas, now, hash};
        if (jdbc.update(update, values) > 0) return;
        try {
            jdbc.update("INSERT INTO TB_PUSH_SUBSCRIPTION (ENDPOINT_HASH,ENDPOINT,P256DH,AUTH,USER_SEQ,VISITOR_ID,FAVORITES_ENABLED,FAVORITE_IDS,AREA_SLUGS,REG_DT,UPDATED_DT) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                hash, endpoint, p256dh, auth, scope.userSeq(), visitor, scope.favorites(), ids, areas, now, now);
        } catch (DuplicateKeyException raced) { jdbc.update(update, values); }
    }

    public Optional<Row> find(String endpoint) {
        return jdbc.query("SELECT * FROM TB_PUSH_SUBSCRIPTION WHERE ENDPOINT_HASH=?", (r, n) -> map(r), hash(endpoint)).stream().findFirst();
    }
    public boolean delete(String endpoint) { return deleteHash(hash(endpoint)); }
    public boolean deleteHash(String hash) { return jdbc.update("DELETE FROM TB_PUSH_SUBSCRIPTION WHERE ENDPOINT_HASH=?", hash) > 0; }

    /** 마지막 전달 성공(없으면 등록)과 마지막 재등록이 모두 기준보다 오래된 구독을 지운다. */
    int removeStale(LocalDateTime before) {
        Timestamp at = Timestamp.valueOf(before);
        return jdbc.update("DELETE FROM TB_PUSH_SUBSCRIPTION WHERE COALESCE(LAST_SUCCESS_DT,REG_DT)<? AND UPDATED_DT<?", at, at);
    }

    /** 오늘 아직 묶음 발송을 시도하지 않은 구독. */
    List<Row> pending(LocalDate today) {
        return jdbc.query("SELECT * FROM TB_PUSH_SUBSCRIPTION WHERE LAST_ATTEMPT_DATE IS NULL OR LAST_ATTEMPT_DATE<? ORDER BY REG_DT", (r, n) -> map(r), Date.valueOf(today));
    }
    /** 여러 서버가 동시에 실행돼도 하루에 한 번만 보내도록 행 단위로 선점한다. */
    boolean claim(String hash, LocalDate today) {
        return jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET LAST_ATTEMPT_DATE=? WHERE ENDPOINT_HASH=? AND (LAST_ATTEMPT_DATE IS NULL OR LAST_ATTEMPT_DATE<?)",
            Date.valueOf(today), hash, Date.valueOf(today)) == 1;
    }
    void release(String hash, LocalDate previous) {
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET LAST_ATTEMPT_DATE=? WHERE ENDPOINT_HASH=?", previous == null ? null : Date.valueOf(previous), hash);
    }
    void delivered(String hash, LocalDateTime at, boolean digest) {
        if (digest) jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET LAST_SUCCESS_DT=?,LAST_DIGEST_DT=? WHERE ENDPOINT_HASH=?", Timestamp.valueOf(at), Timestamp.valueOf(at), hash);
        else jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET LAST_SUCCESS_DT=? WHERE ENDPOINT_HASH=?", Timestamp.valueOf(at), hash);
    }

    private static Row map(ResultSet r) throws SQLException {
        long user = r.getLong("USER_SEQ");
        boolean guest = r.wasNull();
        Timestamp digest = r.getTimestamp("LAST_DIGEST_DT");
        Date attempt = r.getDate("LAST_ATTEMPT_DATE");
        return new Row(r.getString("ENDPOINT_HASH"), r.getString("ENDPOINT"), r.getString("P256DH"), r.getString("AUTH"), guest ? null : user,
            r.getBoolean("FAVORITES_ENABLED"), split(r.getString("FAVORITE_IDS")), split(r.getString("AREA_SLUGS")),
            r.getTimestamp("REG_DT").toLocalDateTime(), digest == null ? null : digest.toLocalDateTime(), attempt == null ? null : attempt.toLocalDate());
    }
    static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).filter(s -> !s.isBlank()).toList(); }
}
