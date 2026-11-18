package com.toady.web.app.community;

import com.toady.web.app.community.Community.Member;
import com.toady.web.app.community.Community.Result;
import com.toady.web.app.user.service.ProfileIcons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 차단·신고·쪽지 받는 범위 설정. 신고는 관리자 사이트의 '신고'에서 운영자가 처리한다.
 * CommunityController가 부른다. 테이블: TB_USER_BLOCK, TB_USER_REPORT, TB_DM_SETTING(database.sql 25번).
 * @Transactional 없이 SQL 한 문장씩 실행한다. 중복은 DB의 유니크 키(DuplicateKeyException)로 걸러 낸다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @Service: 서비스 빈
@Service
public class SafetyService {
    public static final Set<String> REPORT_TYPES = Set.of("USER", "COMMENT", "MESSAGE");
    public static final Set<String> REASONS = Set.of("TRADE", "SPAM", "ABUSE", "PRIVACY", "ETC");
    public static final Set<String> POLICIES = Set.of("ALL", "KNOWN", "NONE");
    /** 신고를 도배해 운영을 막지 못하게 한 사람이 하루에 남길 수 있는 신고 수. */
    static final int DAILY_REPORTS = 20;
    static final int MAX_BLOCKS = 500;

    private final JdbcTemplate jdbc;
    private final Community community;
    private final ProfileIcons icons;

    public SafetyService(JdbcTemplate jdbc, Community community, ProfileIcons icons) {
        this.jdbc = jdbc; this.community = community; this.icons = icons;
    }

    // ---------------------------------------------------------------- 차단

    /** 차단. 이미 차단했으면 같은 성공 결과를 돌려준다. */
    public Result block(long me, long target) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        if (me == target) return Result.fail("ERR-401", "나 자신은 차단할 수 없어요.");
        Member member = community.member(target);
        if (member == null) return Result.fail("ERR-404", "회원을 찾을 수 없어요.");
        if (community.count("SELECT COUNT(*) FROM TB_USER_BLOCK WHERE USER_SEQ=?", me) >= MAX_BLOCKS) {
            return Result.fail("ERR-401", "차단은 " + MAX_BLOCKS + "명까지 할 수 있어요. 내 정보에서 정리한 뒤 다시 시도해 주세요.");
        }
        try {
            jdbc.update("INSERT INTO TB_USER_BLOCK (USER_SEQ, BLOCKED_USER_SEQ, REG_DT) VALUES (?,?,?)", me, target, Community.ts(Community.now()));
        } catch (DuplicateKeyException already) { /* 이미 차단함: 같은 결과 */ }
        return Result.done("차단했어요. 서로 쪽지를 보낼 수 없고, 이 회원의 이야기는 접어서 보여요.", null);
    }

    /** 차단 풀기. 차단한 적이 없어도 성공으로 본다. */
    public Result unblock(long me, long target) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        jdbc.update("DELETE FROM TB_USER_BLOCK WHERE USER_SEQ=? AND BLOCKED_USER_SEQ=?", me, target);
        return Result.done("차단을 풀었어요.", null);
    }

    /** 내가 차단한 회원(최근 순). 테이블이 없으면 빈 목록. */
    public List<Map<String, Object>> blocks(long me) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> seqs = new ArrayList<>();
        try {
            jdbc.query("SELECT b.BLOCKED_USER_SEQ, b.REG_DT, u.USER_NIC_NAME, u.WITHDRAWAL_YN FROM TB_USER_BLOCK b LEFT JOIN TB_USER u ON u.USER_SEQ=b.BLOCKED_USER_SEQ"
                    + " WHERE b.USER_SEQ=? ORDER BY b.REG_DT DESC LIMIT ?", rs -> {
                Map<String, Object> item = new LinkedHashMap<>();
                long seq = rs.getLong(1);
                item.put("userSeq", seq);
                item.put("nickname", "Y".equals(rs.getString(4)) ? "탈퇴한 회원" : rs.getString(3));
                item.put("blockedAt", rs.getTimestamp(2).toLocalDateTime());
                seqs.add(seq); items.add(item);
            }, me, MAX_BLOCKS);
        } catch (DataAccessException missing) { return List.of(); }
        Map<Long, String> found = icons.of(seqs);
        items.forEach(item -> item.put("profileIcon", found.get((Long) item.get("userSeq"))));
        return items;
    }

    // ---------------------------------------------------------------- 신고

    /**
     * 신고. 대상의 작성자(신고된 회원)는 서버가 정한다(화면이 보낸 회원 번호를 믿지 않는다).
     * 쪽지는 그 대화에 참여한 사람이 상대가 보낸 쪽지만 신고할 수 있다.
     */
    public Result report(long me, String type, Long targetSeq, String reason, String detail) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        String kind = type == null ? "" : type.trim().toUpperCase();
        String why = reason == null ? "" : reason.trim().toUpperCase();
        if (!REPORT_TYPES.contains(kind) || targetSeq == null || targetSeq <= 0) return Result.fail("ERR-401", "신고할 대상을 찾을 수 없어요.");
        if (!REASONS.contains(why)) return Result.fail("ERR-401", "신고 이유를 골라 주세요.");
        String note = detail == null ? null : detail.strip();
        if (note != null && note.isEmpty()) note = null;
        if (note != null && note.length() > 500) return Result.fail("ERR-401", "설명은 500자까지 쓸 수 있어요.");
        if ("ETC".equals(why) && note == null) return Result.fail("ERR-401", "'기타'를 고르면 어떤 문제인지 짧게 적어 주세요.");

        Long author = switch (kind) {
            case "USER" -> community.member(targetSeq) == null ? null : targetSeq;
            case "COMMENT" -> jdbc.query("SELECT CREATE_USER_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=? AND DELETE_YN='N'",
                    rs -> rs.next() ? rs.getLong(1) : null, targetSeq);
            default -> jdbc.query("SELECT m.SENDER_SEQ FROM TB_DM_MESSAGE m JOIN TB_DM_THREAD t ON t.THREAD_SEQ=m.THREAD_SEQ"
                    + " WHERE m.MESSAGE_SEQ=? AND (t.USER_LOW=? OR t.USER_HIGH=?)", rs -> rs.next() ? rs.getLong(1) : null, targetSeq, me, me);
        };
        if (author == null) return Result.fail("ERR-404", "신고할 대상을 찾을 수 없어요. 이미 지워졌을 수 있어요.");
        if (author == me) return Result.fail("ERR-401", "내가 쓴 글이나 나 자신은 신고할 수 없어요.");

        LocalDateTime now = Community.now();
        if (community.count("SELECT COUNT(*) FROM TB_USER_REPORT WHERE REPORTER_SEQ=? AND REG_DT>=?", me, Community.ts(now.toLocalDate().atStartOfDay())) >= DAILY_REPORTS) {
            return Result.fail("ERR-429", "오늘은 신고를 더 남길 수 없어요. 급한 문제는 문의 메일로 알려 주세요.");
        }
        try {
            jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ, TARGET_USER_SEQ, TARGET_TYPE, TARGET_SEQ, REASON, DETAIL, STATUS, REG_DT) VALUES (?,?,?,?,?,?,'OPEN',?)",
                    me, author, kind, targetSeq, why, note, Community.ts(now));
        } catch (DuplicateKeyException already) {
            return Result.done("이미 신고했어요. 운영자가 확인하고 있어요.", Map.of("duplicate", true));
        }
        log.info("신고 접수: type={}, targetSeq={}, reason={}", kind, targetSeq, why);
        return Result.done("신고했어요. 운영자가 확인한 뒤 조치할게요.", Map.of("duplicate", false));
    }

    // ---------------------------------------------------------------- 쪽지 설정

    /** 쪽지 설정: 받는 범위와 운영자가 정한 쪽지 정지 종료 시각. */
    public record Setting(String policy, LocalDateTime suspendedUntil) {
        boolean suspended(LocalDateTime now) { return suspendedUntil != null && suspendedUntil.isAfter(now); }
    }

    /** 설정을 읽는다. 행이나 테이블이 없으면 기본값(ALL, 정지 없음). */
    public Setting setting(long userSeq) {
        try {
            return jdbc.query("SELECT RECEIVE_POLICY, SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=?", rs -> {
                if (!rs.next()) return new Setting("ALL", null);
                String policy = POLICIES.contains(rs.getString(1)) ? rs.getString(1) : "ALL";
                return new Setting(policy, rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toLocalDateTime());
            }, userSeq);
        } catch (DataAccessException missing) { return new Setting("ALL", null); }
    }

    /** 받는 범위 저장. UPDATE 후 행이 없으면 INSERT하는 upsert다. */
    public Result setPolicy(long me, String policy) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        String value = policy == null ? "" : policy.trim().toUpperCase();
        if (!POLICIES.contains(value)) return Result.fail("ERR-401", "받는 범위를 골라 주세요.");
        var now = Community.ts(Community.now());
        if (jdbc.update("UPDATE TB_DM_SETTING SET RECEIVE_POLICY=?, UPDATE_DT=? WHERE USER_SEQ=?", value, now, me) == 0) {
            try { jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, UPDATE_DT) VALUES (?,?,?)", me, value, now); }
            catch (DuplicateKeyException raced) { jdbc.update("UPDATE TB_DM_SETTING SET RECEIVE_POLICY=?, UPDATE_DT=? WHERE USER_SEQ=?", value, now, me); }
        }
        return Result.done(switch (value) {
            case "KNOWN" -> "이야기로 이어진 회원과 이미 대화한 회원에게만 쪽지를 받아요.";
            case "NONE" -> "새 쪽지를 받지 않아요. 이미 주고받던 대화는 이어져요.";
            default -> "모든 회원에게 쪽지를 받아요.";
        }, Map.of("policy", value));
    }
}
