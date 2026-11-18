package com.toady.web.app.user.service;

import com.toady.web.core.util.SqlErrors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * 탈퇴 후 같은 이메일로 다시 가입해 제재·차단을 피하지 못하게 하는 기록(database.sql 32번 TB_WITHDRAWN_SANCTION).
 * 탈퇴할 때 진행 중인 제재(이야기 쓰기 정지·쪽지 보내기 정지)와 '다른 회원이 나를 차단한 기록'을
 * 이메일 원문이 아니라 HMAC-SHA256(정규화한 이메일, 서버 비밀 키) 값에 묶어 남기고, 같은 이메일로 새로 가입하면 새 계정에 다시 건다.
 * 기록은 제재가 끝나는 시각과 탈퇴 후 1년 중 이른 때에 지운다(매일 04:50 정리 + 탈퇴·가입 때 함께 정리).
 * 테이블·컬럼이 없거나 권한이 없으면 아무것도 하지 않고 탈퇴·가입은 그대로 진행한다(SQL 적용 전 배포 대비).
 * 그 밖의 DB 오류(연결 끊김·잠금 시간 초과 등)는 삼키지 않고 올려 탈퇴·가입 트랜잭션을 되돌린다.
 * 삼키면 제재 기록 없이 탈퇴가 끝나거나, 제재가 빠진 채 재가입이 끝나 회피 수단이 되기 때문이다.
 * 시각은 다른 제재 테이블과 같이 한국 시간(KST)으로 저장한다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @Component: 빈으로 등록한다. AccountService(탈퇴)·UserService(가입)가 쓴다
@Component
public class WithdrawnSanctions {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 기록을 남겨 두는 최대 기간(무기한 제재여도 탈퇴 후 1년이 지나면 지운다). */
    static final int MAX_KEEP_DAYS = 365;
    static final String STORY_BAN = "STORY_BAN";
    static final String DM_SUSPEND = "DM_SUSPEND";
    static final String BLOCKED_BY = "BLOCKED_BY";

    private final JdbcTemplate jdbc;
    private final byte[] key;

    /**
     * 키는 환경변수 WITHDRAWN_HASH_KEY(Base64)를 따로 둘 수 있고(application.yml 없이 바로 읽는다), 없으면 JWT 서명 키를 쓴다.
     * 키를 바꾸면 이전 기록과 맞지 않게 되므로(재가입 때 찾지 못함) 정해 둔 뒤에는 바꾸지 않는다.
     */
    public WithdrawnSanctions(JdbcTemplate jdbc,
                              @Value("${toady.security.withdrawn-hash-key:${WITHDRAWN_HASH_KEY:${toady.security.secret-key}}}") String key) {
        this.jdbc = jdbc;
        byte[] decoded;
        try { decoded = io.jsonwebtoken.io.Decoders.BASE64.decode(key); }
        catch (RuntimeException notBase64) { decoded = key.getBytes(StandardCharsets.UTF_8); }
        this.key = decoded;
    }

    /**
     * '+태그'를 떼어도 같은 받은편지함으로 가는 것이 확실한 메일 서비스. 이 도메인만 '+' 뒤를 떼어 낸다.
     * 모든 도메인에서 떼지 않는 이유: 회사·학교 메일 등은 '+'가 들어간 주소가 별개의 사람일 수 있어,
     * 다른 사람에게 제재를 잘못 이어 걸 위험이 있다. 여기 없는 도메인은 대소문자·공백만 맞춘다.
     */
    private static final java.util.Set<String> PLUS_TAG_DOMAINS = java.util.Set.of(
            "gmail.com", "outlook.com", "hotmail.com", "live.com", "msn.com",
            "icloud.com", "me.com", "mac.com", "proton.me", "protonmail.com", "pm.me", "fastmail.com");

    /**
     * 제재 기록용 이메일 정규화(해시에만 쓴다. 로그인·가입 이메일 자체는 바꾸지 않는다).
     * 앞뒤 공백 제거·소문자에 더해, Gmail은 아이디의 점(.)을 무시하고 googlemail.com이 gmail.com과 같은 받은편지함이므로
     * 점을 빼고 도메인을 gmail.com으로 맞춘다. '+태그'는 PLUS_TAG_DOMAINS에서만 뗀다.
     * 예: A.B+x@googlemail.com → ab@gmail.com
     */
    static String normalize(String email) {
        String value = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        int at = value.lastIndexOf('@');
        if (at <= 0 || at == value.length() - 1) return value;
        String local = value.substring(0, at), domain = value.substring(at + 1);
        if ("googlemail.com".equals(domain)) domain = "gmail.com";
        if (PLUS_TAG_DOMAINS.contains(domain)) {
            int plus = local.indexOf('+');
            if (plus > 0) local = local.substring(0, plus);
        }
        if ("gmail.com".equals(domain)) {
            String withoutDots = local.replace(".", "");
            if (!withoutDots.isEmpty()) local = withoutDots;
        }
        return local + "@" + domain;
    }

    /** 이메일을 정규화(normalize)한 뒤 HMAC-SHA256 16진수 64자로 바꾼다. 원래 이메일은 되돌릴 수 없다. */
    String hash(String email) {
        String normalized = normalize(email);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not hash withdrawn email", failure);
        }
    }

    /**
     * 탈퇴 직전(이메일을 익명 값으로 바꾸기 전)에 부른다. 진행 중인 제재와 나를 차단한 회원을 남긴다.
     * 테이블·권한이 없으면 기록 없이 탈퇴를 진행하고, 그 밖의 DB 오류는 던져 탈퇴를 되돌린다.
     */
    public void remember(long userSeq, String email) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDateTime maxKeep = now.plusDays(MAX_KEEP_DAYS);
        String hash = hash(email);
        List<Object[]> rows = new ArrayList<>();
        try {
            // 이야기 쓰기 정지: 해제되지 않았고 아직 끝나지 않은 것 중 가장 늦게 끝나는 것 하나(무기한이 있으면 무기한)
            jdbc.query("SELECT REASON, END_DT FROM TB_USER_SANCTION WHERE USER_SEQ=? AND KIND='STORY_BAN' AND CANCELED_DT IS NULL AND (END_DT IS NULL OR END_DT>?)"
                    + " ORDER BY CASE WHEN END_DT IS NULL THEN 0 ELSE 1 END, END_DT DESC LIMIT 1", rs -> {
                Timestamp end = rs.getTimestamp(2);
                rows.add(row(hash, STORY_BAN, rs.getString(1), end, 0L, now, maxKeep));
            }, userSeq, Timestamp.valueOf(now));
        } catch (DataAccessException failure) { skipOnly(failure, "탈퇴 제재 기록: 이야기 정지 조회 건너뜀"); }
        try {
            jdbc.query("SELECT SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=? AND SUSPENDED_UNTIL>?", rs -> {
                rows.add(row(hash, DM_SUSPEND, null, rs.getTimestamp(1), 0L, now, maxKeep));
            }, userSeq, Timestamp.valueOf(now));
        } catch (DataAccessException failure) { skipOnly(failure, "탈퇴 제재 기록: 쪽지 정지 조회 건너뜀"); }
        try {
            // 다른 회원이 나를 차단한 기록. 차단한 사람의 기록이라 탈퇴 후에도 남아 있고, 재가입한 새 계정에도 이어 준다.
            jdbc.query("SELECT USER_SEQ FROM TB_USER_BLOCK WHERE BLOCKED_USER_SEQ=?", rs -> {
                rows.add(row(hash, BLOCKED_BY, null, null, rs.getLong(1), now, maxKeep));
            }, userSeq);
        } catch (DataAccessException failure) { skipOnly(failure, "탈퇴 제재 기록: 차단 조회 건너뜀"); }
        try {
            purgeExpired(now);
            // 같은 이메일로 탈퇴를 되풀이하면 예전 기록을 지우고 지금 상태로 다시 쓴다.
            // 지금 남길 것이 없어도 먼저 지운다. 그사이 풀린 제재·차단이 다음 재가입 때 되살아나지 않게 한다.
            jdbc.update("DELETE FROM TB_WITHDRAWN_SANCTION WHERE EMAIL_HASH=?", hash);
            if (rows.isEmpty()) return;
            jdbc.batchUpdate("INSERT INTO TB_WITHDRAWN_SANCTION (EMAIL_HASH, KIND, BLOCKER_SEQ, REASON, END_DT, EXPIRE_DT, REG_DT) VALUES (?,?,?,?,?,?,?)", rows);
            log.info("탈퇴 회원 제재·차단 기록 {}건 보관: userSeq={}", rows.size(), userSeq);
        } catch (DataAccessException failure) {
            skipOnly(failure, "탈퇴 회원 제재 기록을 남기지 못함(database.sql 32번 확인)");
        }
    }

    /** 32번 미적용(테이블·컬럼 없음)이나 권한 미부여면 경고만 남기고, 그 밖의 오류는 다시 던진다. */
    private static void skipOnly(DataAccessException failure, String message) {
        if (!SqlErrors.notApplied(failure)) throw failure;
        log.warn("{}: {}", message, failure.getMostSpecificCause().getMessage());
    }

    // 한 줄. 보관 기한은 제재가 끝나는 시각과 1년 뒤 중 이른 때
    private static Object[] row(String hash, String kind, String reason, Timestamp end, long blocker, LocalDateTime now, LocalDateTime maxKeep) {
        LocalDateTime expire = end == null || end.toLocalDateTime().isAfter(maxKeep) ? maxKeep : end.toLocalDateTime();
        return new Object[]{hash, kind, blocker, reason, end, Timestamp.valueOf(expire), Timestamp.valueOf(now)};
    }

    /**
     * 새 계정이 만들어진 직후(같은 트랜잭션) 부른다. 같은 이메일의 기록 중 아직 끝나지 않은 제재를 새 계정에 다시 걸고,
     * 탈퇴 전 계정을 차단했던 회원의 차단을 새 계정에도 건다. 건 개수를 돌려준다.
     * 테이블·권한이 없으면 건너뛰고(가입은 진행), 그 밖의 DB 오류는 던져 가입을 되돌린다(제재 없이 가입되지 않게).
     */
    public int reapply(long userSeq, String email) {
        LocalDateTime now = LocalDateTime.now(KST);
        Timestamp nowTs = Timestamp.valueOf(now);
        String hash = hash(email);
        record Kept(String kind, long blocker, String reason, Timestamp end) { }
        List<Kept> kept;
        try {
            kept = jdbc.query("SELECT KIND, BLOCKER_SEQ, REASON, END_DT FROM TB_WITHDRAWN_SANCTION WHERE EMAIL_HASH=? AND EXPIRE_DT>?",
                    (rs, i) -> new Kept(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getTimestamp(4)), hash, nowTs);
        } catch (DataAccessException failure) {
            skipOnly(failure, "탈퇴 회원 제재 기록을 읽지 못함(database.sql 32번 확인)");
            return 0;
        }
        int applied = 0;
        for (Kept item : kept) {
            try {
                switch (item.kind()) {
                    case STORY_BAN -> {
                        if (item.end() != null && !item.end().after(nowTs)) continue;
                        jdbc.update("INSERT INTO TB_USER_SANCTION (USER_SEQ, KIND, REASON, MEMO, START_DT, END_DT, ADMIN_NAME, REG_DT) VALUES (?,?,?,?,?,?,?,?)",
                                userSeq, STORY_BAN, item.reason() == null ? "ETC" : item.reason(), "탈퇴 전 계정의 제재를 재가입 계정에 이어 적용",
                                nowTs, item.end(), "SYSTEM", nowTs);
                        applied++;
                    }
                    case DM_SUSPEND -> {
                        if (item.end() == null || !item.end().after(nowTs)) continue;
                        if (jdbc.update("UPDATE TB_DM_SETTING SET SUSPENDED_UNTIL=?, UPDATE_DT=? WHERE USER_SEQ=?", item.end(), nowTs, userSeq) == 0) {
                            jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, SUSPENDED_UNTIL, UPDATE_DT) VALUES (?, 'ALL', ?, ?)", userSeq, item.end(), nowTs);
                        }
                        applied++;
                    }
                    case BLOCKED_BY -> {
                        // 차단한 회원이 그사이 탈퇴했으면 잇지 않는다
                        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_SEQ=? AND WITHDRAWAL_YN='N'", Integer.class, item.blocker());
                        if (active == null || active == 0) continue;
                        try {
                            jdbc.update("INSERT INTO TB_USER_BLOCK (USER_SEQ, BLOCKED_USER_SEQ, REG_DT) VALUES (?,?,?)", item.blocker(), userSeq, nowTs);
                            applied++;
                        } catch (DuplicateKeyException already) { /* 이미 차단돼 있음 */ }
                    }
                    default -> { }
                }
            } catch (DataAccessException failure) {
                skipOnly(failure, "재가입 계정에 탈퇴 전 제재를 다시 걸지 못함(" + item.kind() + ")");
            }
        }
        if (applied > 0) log.info("재가입 계정에 탈퇴 전 제재·차단 {}건 적용: userSeq={}", applied, userSeq);
        return applied;
    }

    /** 보관 기한이 지난 기록을 지운다. 지운 줄 수(테이블·권한이 없으면 0, 그 밖의 오류는 던진다). */
    public int purgeExpired(LocalDateTime now) {
        try { return jdbc.update("DELETE FROM TB_WITHDRAWN_SANCTION WHERE EXPIRE_DT<=?", Timestamp.valueOf(now)); }
        catch (DataAccessException failure) {
            if (!SqlErrors.notApplied(failure)) throw failure;
            return 0;
        }
    }

    /** 매일 04:50(한국시간) 보관 기한이 지난 기록을 지운다. 스케줄러의 보관 정리(04:40)와 겹치지 않게 10분 뒤에 둔다. */
    // @Scheduled(cron): SchedulingConfig의 @EnableScheduling으로 예약 실행된다
    @Scheduled(cron = "${toady.withdrawn-sanction.purge-cron:0 50 4 * * *}", zone = "Asia/Seoul")
    public void purgeDaily() {
        int removed;
        try { removed = purgeExpired(LocalDateTime.now(KST)); }
        catch (DataAccessException failure) {
            log.warn("탈퇴 회원 제재 기록 정리 실패(다음 날 다시 시도): {}", failure.getMostSpecificCause().getMessage());
            return;
        }
        if (removed > 0) log.info("보관 기한이 지난 탈퇴 회원 제재 기록 {}건 삭제", removed);
    }
}
