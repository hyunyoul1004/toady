package com.toady.web.app.operation;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자가 정한 회원 제재(database.sql 26번 TB_USER_SANCTION).
 * 이야기 쓰기 정지(STORY_BAN)는 글을 쓸 때마다 확인하고, 경고·닉네임 초기화는 회원에게 안내만 한다.
 * 회원에게는 종류·기간·사유 항목만 보여 주고 관리자 메모는 보내지 않는다. 테이블이 없으면 제재가 없는 것으로 본다.
 */
@Component
public class Sanctions {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    static final Map<String, String> REASONS = Map.of("TRADE", "통장·전매 거래", "SPAM", "홍보·광고", "ABUSE", "욕설·괴롭힘",
            "PRIVACY", "개인정보 노출", "NICKNAME", "부적절한 닉네임", "ETC", "운영 정책 위반");
    private final JdbcTemplate jdbc;

    public Sanctions(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 지금 이야기 쓰기가 막힌 회원이면 안내 문구, 아니면 null. */
    public String storyBanMessage(long userSeq) {
        LocalDateTime now = LocalDateTime.now(KST);
        try {
            List<Timestamp> ends = jdbc.query("SELECT END_DT FROM TB_USER_SANCTION WHERE USER_SEQ=? AND KIND='STORY_BAN' AND CANCELED_DT IS NULL AND (END_DT IS NULL OR END_DT>?) ORDER BY SANCTION_SEQ DESC LIMIT 1",
                    (r, i) -> r.getTimestamp(1), userSeq, Timestamp.valueOf(now));
            if (ends.isEmpty()) return null;
            Timestamp end = ends.get(0);
            return end == null ? "운영 정책 위반으로 이야기를 쓸 수 없는 계정이에요. 문의는 dukkeoba@gmail.com으로 보내 주세요."
                    : "운영 정책 위반으로 " + end.toLocalDateTime().format(UNTIL) + "까지 이야기를 쓸 수 없어요.";
        } catch (DataAccessException missing) { return null; }
    }

    /** 회원에게 보여 줄 안내: 아직 확인하지 않은 경고·닉네임 초기화와, 진행 중인 이야기 쓰기 정지. */
    public List<Map<String, Object>> notices(long userSeq) {
        LocalDateTime now = LocalDateTime.now(KST);
        try {
            return jdbc.query("SELECT SANCTION_SEQ, KIND, REASON, START_DT, END_DT, SEEN_DT FROM TB_USER_SANCTION WHERE USER_SEQ=? AND CANCELED_DT IS NULL"
                    + " AND ((KIND='STORY_BAN' AND (END_DT IS NULL OR END_DT>?)) OR (KIND<>'STORY_BAN' AND SEEN_DT IS NULL)) ORDER BY SANCTION_SEQ DESC LIMIT 10", (r, i) -> {
                Map<String, Object> notice = new LinkedHashMap<>();
                notice.put("seq", r.getLong(1));
                notice.put("kind", r.getString(2));
                notice.put("reason", REASONS.getOrDefault(r.getString(3), "운영 정책 위반"));
                notice.put("startAt", r.getTimestamp(4) == null ? null : r.getTimestamp(4).toLocalDateTime().toString());
                notice.put("endAt", r.getTimestamp(5) == null ? null : r.getTimestamp(5).toLocalDateTime().toString());
                notice.put("seen", r.getTimestamp(6) != null);
                return notice;
            }, userSeq, Timestamp.valueOf(now));
        } catch (DataAccessException missing) { return List.of(); }
    }

    /** 회원이 안내를 확인했다. 본인 것만 바뀐다. */
    public boolean markSeen(long userSeq, long sanctionSeq) {
        try {
            return jdbc.update("UPDATE TB_USER_SANCTION SET SEEN_DT=? WHERE SANCTION_SEQ=? AND USER_SEQ=? AND SEEN_DT IS NULL",
                    Timestamp.valueOf(LocalDateTime.now(KST)), sanctionSeq, userSeq) > 0;
        } catch (DataAccessException missing) { return false; }
    }
}
