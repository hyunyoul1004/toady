package com.toady.web.app.operation;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 운영팀 이야기(database.sql 27번 TB_COMMENT_OFFICIAL)와 공고 맨 위 고정 글. 테이블이 없으면 운영팀 글이 없는 것으로 본다. */
@Component
public class OfficialComments {
    private final JdbcTemplate jdbc;
    public OfficialComments(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 주어진 이야기 중 운영팀 글 번호와 고정 여부. */
    public java.util.Map<Long, Boolean> officialOf(Collection<Long> commentSeqs) {
        if (commentSeqs == null || commentSeqs.isEmpty()) return java.util.Map.of();
        List<Long> ids = commentSeqs.stream().distinct().limit(500).toList();
        String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try {
            java.util.Map<Long, Boolean> result = new java.util.HashMap<>();
            jdbc.query("SELECT COMMENT_SEQ, PINNED_YN FROM TB_COMMENT_OFFICIAL WHERE COMMENT_SEQ IN (" + marks + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) r -> result.put(r.getLong(1), "Y".equals(r.getString(2))), ids.toArray());
            return result;
        } catch (DataAccessException missing) { return java.util.Map.of(); }
    }

    /** 공고에 고정한 운영팀 원글(보이는 것만, 최신순 3개). */
    public List<Long> pinned(String typeName, String houseManageNo, String pblancNo) {
        try {
            return jdbc.queryForList("SELECT C.COMMENT_SEQ FROM TB_COMMENT_OFFICIAL O JOIN TB_LTTOT_PBLANC_COMMENT C ON C.COMMENT_SEQ=O.COMMENT_SEQ"
                    + " WHERE O.PINNED_YN='Y' AND C.ANNOUNCEMENT_TYPE=? AND C.HOUSE_MANAGE_NO=? AND C.PBLANC_NO=? AND C.DELETE_YN='N' AND C.PARENT_COMMENT_SEQ IS NULL"
                    + " ORDER BY C.COMMENT_SEQ DESC LIMIT 3", Long.class, typeName, houseManageNo, pblancNo);
        } catch (DataAccessException missing) { return List.of(); }
    }
}
