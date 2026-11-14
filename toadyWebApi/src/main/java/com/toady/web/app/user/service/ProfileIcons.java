package com.toady.web.app.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회원 프로필 캐릭터(화면이 준비한 캐릭터 중 하나의 키). 이미지는 받지 않고 키만 TB_USER_PROFILE에 둔다.
 * 테이블이 아직 없는 DB(database.sql 23번 미적용)에서는 모두 '고르지 않음'으로 보고, 고르기만 안내와 함께 막는다.
 */
@Slf4j
@Service
public class ProfileIcons {
    /** 화면(ProfileAvatar.jsx)의 캐릭터 키와 같아야 한다. */
    public static final Set<String> KEYS = Set.of("toad", "house", "cat", "dog", "rabbit", "bear", "panda", "penguin", "chick", "fox", "hamster", "pig");

    private final JdbcTemplate jdbc;
    public ProfileIcons(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public String of(long userSeq) {
        return of(List.of(userSeq)).get(userSeq);
    }

    /** 여러 회원의 캐릭터를 한 번에 읽는다(댓글 목록). 없는 회원은 결과에 넣지 않는다. */
    public Map<Long, String> of(Collection<Long> userSeqs) {
        List<Long> ids = userSeqs.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList();
        if (ids.isEmpty()) return Map.of();
        try {
            String marks = ids.stream().map(id -> "?").collect(Collectors.joining(","));
            Map<Long, String> found = new HashMap<>();
            jdbc.query("SELECT USER_SEQ, PROFILE_ICON FROM TB_USER_PROFILE WHERE USER_SEQ IN (" + marks + ")",
                    rs -> { String key = rs.getString(2); if (KEYS.contains(key)) found.put(rs.getLong(1), key); }, ids.toArray());
            return found;
        } catch (DataAccessException missing) {
            return Map.of();
        }
    }

    /** 캐릭터를 바꾼다. null이면 고르지 않은 상태(닉네임 첫 글자)로 되돌린다. 저장소가 없으면 false. */
    public boolean set(long userSeq, String key) {
        if (key != null && !KEYS.contains(key)) throw new IllegalArgumentException("Unknown profile icon");
        try {
            if (key == null) { jdbc.update("DELETE FROM TB_USER_PROFILE WHERE USER_SEQ=?", userSeq); return true; }
            Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            if (jdbc.update("UPDATE TB_USER_PROFILE SET PROFILE_ICON=?, UPDATE_DT=? WHERE USER_SEQ=?", key, now, userSeq) == 0) {
                jdbc.update("INSERT INTO TB_USER_PROFILE (USER_SEQ, PROFILE_ICON, UPDATE_DT) VALUES (?,?,?)", userSeq, key, now);
            }
            return true;
        } catch (DataAccessException missing) {
            log.warn("프로필 캐릭터를 저장하지 못했습니다(TB_USER_PROFILE, database.sql 23번 적용 여부 확인): {}", missing.getMostSpecificCause().getMessage());
            return false;
        }
    }
}
