package com.toady.web.app.user.service;

import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.constants.ResultCode;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.security.service.SimpleUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 로그인한 회원의 '내 정보': 계정 요약, 닉네임·비밀번호 변경, 탈퇴.
 * 닉네임·비밀번호를 바꾸면 토큰의 내용(닉네임, 비밀번호 기반 확인값)이 달라지므로 새 토큰을 돌려준다.
 * 비밀번호를 바꾸면 다른 기기의 기존 토큰은 확인값이 맞지 않아 자동으로 로그아웃된다.
 */
@Slf4j
@Service
public class AccountService {
    /** 가입과 같은 규칙: 닉네임은 한글·영문·숫자 2~15자, 비밀번호는 6~20자이며 영문과 숫자 또는 특수문자를 함께 쓴다. */
    static final Pattern NICKNAME = Pattern.compile("^[0-9a-zA-Zㄱ-ㅎ가-힣]{2,15}$");
    static final Pattern PASSWORD = Pattern.compile("^(?=.*[A-Za-z])((?=.*\\d)|(?=.*\\W)).{6,20}$");
    /** 탈퇴를 실수로 누르지 않게 그대로 입력받는 확인 문구. */
    public static final String WITHDRAW_PHRASE = "탈퇴합니다";

    /** 결과: 성공이면 새 토큰(탈퇴는 null), 실패면 화면에 보여 줄 코드·문구. */
    public record Outcome(boolean ok, String code, String message, String accessToken) {
        static Outcome fail(ResultCode code, String message) { return new Outcome(false, code.getCode(), message, null); }
        static Outcome done(String message, String token) { return new Outcome(true, ResultCode.SUCCESS.getCode(), message, token); }
    }

    private final UserRepository users;
    private final UserService userService;
    private final SimpleUserDetailsService details;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final ProfileIcons profileIcons;

    public AccountService(UserRepository users, UserService userService, SimpleUserDetailsService details, PasswordEncoder encoder, JdbcTemplate jdbc, ProfileIcons profileIcons) {
        this.users = users; this.userService = userService; this.details = details; this.encoder = encoder; this.jdbc = jdbc; this.profileIcons = profileIcons;
    }

    /** 공고 유형(댓글 테이블의 enum 이름) → 화면 주소의 유형과 상세 테이블 */
    private static final Map<String, String[]> TYPES = Map.of(
            "APT", new String[]{"apt", "TB_APT_LTTOT_PBLANC_DETAIL"},
            "URBAN", new String[]{"urban", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL"},
            "REMAINING", new String[]{"remaining", "TB_REMNDR_LTTOT_PBLANC_DETAIL"},
            "PUBLIC_RENTAL", new String[]{"public-rental", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL"},
            "OPTIONAL", new String[]{"optional", "TB_OPT_LTTOT_PBLANC_DETAIL"});
    static final int MY_COMMENTS_PAGE = 10;

    /** 프로필 캐릭터를 바꾼다(null이면 닉네임 첫 글자로 되돌림). */
    public Outcome changeProfileIcon(long userSeq, String icon) {
        String key = icon == null || icon.isBlank() ? null : icon.trim();
        if (key != null && !ProfileIcons.KEYS.contains(key)) return Outcome.fail(ResultCode.VALID_ERROR, "고를 수 없는 캐릭터예요.");
        if (!profileIcons.set(userSeq, key)) return Outcome.fail(ResultCode.SYSTEM_ERROR, "프로필 캐릭터 기능을 준비하고 있어요. 잠시 뒤에 다시 시도해 주세요.");
        return Outcome.done(key == null ? "프로필을 닉네임 첫 글자로 되돌렸어요." : "프로필 캐릭터를 바꿨어요. 내가 쓴 이야기에도 함께 보여요.", null);
    }

    /**
     * 내가 쓴 이야기(삭제하지 않은 것) 최신순 10개씩. 공고 이름과, 화면에서 그 공고의 이야기 탭으로 갈 수 있는 정보를 함께 준다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> myComments(long userSeq, int page) {
        int safePage = Math.max(0, Math.min(page, 1000));
        Long total = count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N'", userSeq);
        List<Map<String, Object>> items = new ArrayList<>();
        if (total != null && total > 0) {
            jdbc.query("SELECT COMMENT_SEQ, ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, PARENT_COMMENT_SEQ, CONTENT, REG_DT, UPDATE_DT"
                            + " FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N' ORDER BY COMMENT_SEQ DESC LIMIT ? OFFSET ?",
                    rs -> {
                        String[] type = TYPES.get(rs.getString("ANNOUNCEMENT_TYPE"));
                        if (type == null) return;
                        String content = rs.getString("CONTENT");
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("commentSeq", rs.getLong("COMMENT_SEQ"));
                        item.put("announcementId", type[0] + "/" + rs.getString("HOUSE_MANAGE_NO") + "/" + rs.getString("PBLANC_NO"));
                        item.put("reply", rs.getObject("PARENT_COMMENT_SEQ") != null);
                        item.put("content", content == null ? "" : content.length() > 300 ? content.substring(0, 300) + "…" : content);
                        item.put("regDt", rs.getTimestamp("REG_DT") == null ? null : rs.getTimestamp("REG_DT").toLocalDateTime());
                        item.put("edited", rs.getTimestamp("UPDATE_DT") != null);
                        item.put("detailTable", type[1]);
                        items.add(item);
                    }, userSeq, MY_COMMENTS_PAGE, safePage * MY_COMMENTS_PAGE);
            // 공고 이름은 유형별 상세 테이블에서 한 번씩 읽는다(공고가 지워졌으면 이름 없이 둔다).
            for (Map<String, Object> item : items) {
                String table = (String) item.remove("detailTable");
                String[] id = ((String) item.get("announcementId")).split("/");
                try {
                    item.put("houseNm", jdbc.query("SELECT HOUSE_NM FROM " + table + " WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? LIMIT 1",
                            rs -> rs.next() ? rs.getString(1) : null, id[1], id[2]));
                } catch (DataAccessException missing) { item.put("houseNm", null); }
            }
        }
        attachReplyCounts(userSeq, items);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("items", items);
        view.put("total", total == null ? 0 : total);
        view.put("page", safePage);
        view.put("hasMore", total != null && (long) (safePage + 1) * MY_COMMENTS_PAGE < total);
        return view;
    }

    /** 내 이야기마다 다른 회원이 단 답글 수와 마지막 답글 번호(지운 답글·내 답글은 세지 않는다). */
    private void attachReplyCounts(long userSeq, List<Map<String, Object>> items) {
        List<Object> seqs = items.stream().filter(item -> !Boolean.TRUE.equals(item.get("reply"))).map(item -> item.get("commentSeq")).collect(java.util.stream.Collectors.toList());
        items.forEach(item -> { item.put("replyCount", 0L); item.put("lastReplySeq", null); });
        if (seqs.isEmpty()) return;
        Map<Long, long[]> counts = new java.util.HashMap<>();
        String marks = String.join(",", java.util.Collections.nCopies(seqs.size(), "?"));
        List<Object> args = new ArrayList<>(seqs); args.add(userSeq);
        try {
            jdbc.query("SELECT PARENT_COMMENT_SEQ, COUNT(*), MAX(COMMENT_SEQ) FROM TB_LTTOT_PBLANC_COMMENT WHERE PARENT_COMMENT_SEQ IN (" + marks + ")"
                    + " AND DELETE_YN='N' AND CREATE_USER_SEQ<>? GROUP BY PARENT_COMMENT_SEQ",
                    rs -> { counts.put(rs.getLong(1), new long[]{rs.getLong(2), rs.getLong(3)}); }, args.toArray());
        } catch (DataAccessException missing) { return; }
        items.forEach(item -> {
            long[] found = counts.get((Long) item.get("commentSeq"));
            if (found != null) { item.put("replyCount", found[0]); item.put("lastReplySeq", found[1]); }
        });
    }

    static final int REPLY_LIMIT = 20;
    /**
     * 내 이야기에 다른 회원이 단 답글 최신순 20개와 새 답글 수.
     * afterSeq(이 기기에서 마지막으로 본 답글 번호)보다 큰 답글이 새 답글이다. 처음(afterSeq 없음)에는 최근 7일 답글을 새 답글로 본다.
     * 번호로 비교하므로 서버·DB 시간대와 관계없이 순서가 맞다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> replies(long userSeq, Long afterSeq) {
        LocalDateTime weekAgo = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(7);
        List<Map<String, Object>> items = new ArrayList<>();
        List<Long> authors = new ArrayList<>();
        long unread = 0;
        try {
            String from = " FROM TB_LTTOT_PBLANC_COMMENT c JOIN TB_LTTOT_PBLANC_COMMENT p ON p.COMMENT_SEQ=c.PARENT_COMMENT_SEQ"
                    + " WHERE p.CREATE_USER_SEQ=? AND p.DELETE_YN='N' AND c.DELETE_YN='N' AND c.CREATE_USER_SEQ<>?";
            Long counted = afterSeq != null
                    ? jdbc.queryForObject("SELECT COUNT(*)" + from + " AND c.COMMENT_SEQ>?", Long.class, userSeq, userSeq, afterSeq)
                    : jdbc.queryForObject("SELECT COUNT(*)" + from + " AND c.REG_DT>=?", Long.class, userSeq, userSeq, weekAgo);
            unread = counted == null ? 0 : counted;
            jdbc.query("SELECT c.COMMENT_SEQ, c.PARENT_COMMENT_SEQ, c.ANNOUNCEMENT_TYPE, c.HOUSE_MANAGE_NO, c.PBLANC_NO, c.CONTENT, c.REG_DT, c.CREATE_USER_SEQ, u.USER_NIC_NAME, p.CONTENT AS PARENT_CONTENT"
                            + from.replace(" WHERE", " LEFT JOIN TB_USER u ON u.USER_SEQ=c.CREATE_USER_SEQ WHERE") + " ORDER BY c.COMMENT_SEQ DESC LIMIT ?",
                    rs -> {
                        String[] type = TYPES.get(rs.getString("ANNOUNCEMENT_TYPE"));
                        if (type == null) return;
                        long seq = rs.getLong("COMMENT_SEQ");
                        LocalDateTime regDt = rs.getTimestamp("REG_DT") == null ? null : rs.getTimestamp("REG_DT").toLocalDateTime();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("commentSeq", seq);
                        item.put("parentSeq", rs.getLong("PARENT_COMMENT_SEQ"));
                        item.put("announcementId", type[0] + "/" + rs.getString("HOUSE_MANAGE_NO") + "/" + rs.getString("PBLANC_NO"));
                        item.put("nickname", rs.getString("USER_NIC_NAME"));
                        item.put("content", clip(rs.getString("CONTENT"), 120));
                        item.put("parentContent", clip(rs.getString("PARENT_CONTENT"), 60));
                        item.put("regDt", regDt);
                        item.put("isNew", afterSeq != null ? seq > afterSeq : regDt != null && !regDt.isBefore(weekAgo));
                        item.put("detailTable", type[1]);
                        authors.add(rs.getLong("CREATE_USER_SEQ"));
                        items.add(item);
                    }, userSeq, userSeq, REPLY_LIMIT);
        } catch (DataAccessException missing) {
            log.warn("받은 답글을 읽지 못함: {}", missing.getMostSpecificCause().getMessage());
        }
        Map<Long, String> icons = profileIcons.of(authors);
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            item.put("profileIcon", icons.get(authors.get(i)));
            String table = (String) item.remove("detailTable");
            String[] id = ((String) item.get("announcementId")).split("/");
            try {
                item.put("houseNm", jdbc.query("SELECT HOUSE_NM FROM " + table + " WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? LIMIT 1",
                        rs -> rs.next() ? rs.getString(1) : null, id[1], id[2]));
            } catch (DataAccessException missing) { item.put("houseNm", null); }
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("unread", unread);
        view.put("latestSeq", items.isEmpty() ? null : items.get(0).get("commentSeq"));
        view.put("items", items);
        return view;
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }

    /** 내 정보 화면의 요약. 관심 공고·이야기·알림 구독 수는 테이블이 없으면 null(화면은 '—'). */
    @Transactional(readOnly = true)
    public Map<String, Object> summary(long userSeq) {
        UserEntity user = users.findById(userSeq).orElseThrow();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("email", user.getUserEmail());
        view.put("nickname", user.getUserNicName());
        view.put("createdAt", user.getCreateDt());
        view.put("favorites", count("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=?", userSeq));
        view.put("emailAlerts", count("SELECT COUNT(*) FROM TB_FAVORITE WHERE USER_SEQ=? AND EMAIL_ENABLED=TRUE", userSeq));
        view.put("comments", count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N'", userSeq));
        view.put("pushDevices", count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE USER_SEQ=?", userSeq));
        view.put("calendarFeed", count("SELECT COUNT(*) FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", userSeq));
        view.put("profileIcon", profileIcons.of(userSeq));
        view.put("withdrawPhrase", WITHDRAW_PHRASE);
        return view;
    }

    @Transactional
    public Outcome changeNickname(long userSeq, String requested) {
        String nickname = requested == null ? "" : requested.trim();
        if (!NICKNAME.matcher(nickname).matches()) {
            return Outcome.fail(ResultCode.VALID_ERROR, "닉네임은 한글·영문·숫자로 2~15자까지 쓸 수 있어요(공백·특수문자 제외).");
        }
        UserEntity user = lockedUser(userSeq);
        if (nickname.equals(user.getUserNicName())) return Outcome.fail(ResultCode.VALID_ERROR, "지금 쓰고 있는 닉네임이에요.");
        UserEntity owner = users.findByUserNicName(nickname);
        if (owner != null && !owner.getUserSeq().equals(userSeq) && !userService.releaseStaleNickname(owner)) {
            return Outcome.fail(ResultCode.ALREADY_NIC_NAME, ResultCode.ALREADY_NIC_NAME.getMessage());
        }
        user.setUserNicName(nickname);
        user.setUpdateDt(LocalDateTime.now());
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException raced) {
            // 같은 닉네임을 동시에 고른 경우 유니크 제약이 먼저 막는다.
            return Outcome.fail(ResultCode.ALREADY_NIC_NAME, ResultCode.ALREADY_NIC_NAME.getMessage());
        }
        log.info("닉네임 변경: userSeq={}", userSeq);
        return Outcome.done("닉네임을 바꿨어요. 내가 쓴 이야기에도 새 닉네임으로 보여요.", token(user));
    }

    @Transactional
    public Outcome changePassword(long userSeq, String current, String next, String confirm) {
        UserEntity user = lockedUser(userSeq);
        if (current == null || current.length() > 72 || !encoder.matches(current, user.getUserPassword())) {
            return Outcome.fail(ResultCode.BAD_CREDENTIALS_ERROR, "현재 비밀번호가 맞지 않아요.");
        }
        if (next == null || !next.equals(confirm)) return Outcome.fail(ResultCode.PASSWORD_MISMATCH_ERROR, "새 비밀번호와 확인이 서로 달라요.");
        if (!PASSWORD.matcher(next).matches()) {
            return Outcome.fail(ResultCode.PASSWORD_PATTERN_VALID1_ERROR, "새 비밀번호는 6~20자로, 영문과 숫자 또는 특수문자를 함께 써 주세요.");
        }
        if (encoder.matches(next, user.getUserPassword())) return Outcome.fail(ResultCode.VALID_ERROR, "지금 비밀번호와 다른 비밀번호를 써 주세요.");
        user.setUserPassword(encoder.encode(next));
        user.setUpdateDt(LocalDateTime.now());
        users.saveAndFlush(user);
        // 아직 쓰지 않은 비밀번호 재설정 코드는 폐기한다(바뀐 비밀번호를 예전 메일의 코드로 다시 바꾸지 못하게).
        tryUpdate("UPDATE TB_CERTIFY SET CERTIFY_YN='Y', UPDATE_DT=? WHERE CREATE_USER_SEQ=? AND CERTIFY_TYPE='FIND_PW' AND CERTIFY_YN='N'", LocalDateTime.now(), userSeq);
        log.info("비밀번호 변경: userSeq={}", userSeq);
        return Outcome.done("비밀번호를 바꿨어요. 다른 기기에서는 다시 로그인해야 해요.", token(user));
    }

    /**
     * 탈퇴: 비밀번호와 확인 문구가 맞으면 계정을 탈퇴 처리하고 개인 정보를 지운다.
     * 이메일·닉네임은 다시 가입할 수 있게 익명 값으로 바꾸고, 관심 공고·알림 구독·캘린더 구독·프로필 캐릭터는 삭제한다.
     * 작성한 이야기는 다른 사람의 답글 흐름이 끊기지 않게 남기되 '탈퇴회원'으로 보인다.
     */
    @Transactional
    public Outcome withdraw(long userSeq, String password, String phrase) {
        UserEntity user = lockedUser(userSeq);
        if (password == null || password.length() > 72 || !encoder.matches(password, user.getUserPassword())) {
            return Outcome.fail(ResultCode.BAD_CREDENTIALS_ERROR, "비밀번호가 맞지 않아요.");
        }
        if (!WITHDRAW_PHRASE.equals(phrase == null ? "" : phrase.trim())) {
            return Outcome.fail(ResultCode.VALID_ERROR, "확인 문구 '" + WITHDRAW_PHRASE + "'를 그대로 입력해 주세요.");
        }
        LocalDateTime now = LocalDateTime.now();
        // 가입 규칙으로는 만들 수 없는 값이라 실제 이메일·닉네임과 겹치지 않는다(.invalid는 메일이 가지 않는 예약 도메인).
        user.setUserEmail("withdrawn-" + userSeq + "@withdrawn.invalid");
        user.setUserNicName("탈퇴회원_" + userSeq);
        user.setUserPassword(encoder.encode(UUID.randomUUID().toString()));
        user.setWithdrawalYn("Y");
        user.setWithdrawalDt(now);
        user.setUpdateDt(now);
        users.saveAndFlush(user);
        tryUpdate("DELETE FROM TB_FAVORITE WHERE USER_SEQ=?", userSeq);
        tryUpdate("DELETE FROM TB_PUSH_SUBSCRIPTION WHERE USER_SEQ=?", userSeq);
        tryUpdate("DELETE FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", userSeq);
        tryUpdate("DELETE FROM TB_USER_PROFILE WHERE USER_SEQ=?", userSeq);
        tryUpdate("UPDATE TB_CERTIFY SET CERTIFY_YN='Y', UPDATE_DT=? WHERE CREATE_USER_SEQ=? AND CERTIFY_YN='N'", now, userSeq);
        log.info("회원 탈퇴: userSeq={}", userSeq);
        return Outcome.done("탈퇴했어요. 그동안 TOADY를 이용해 주셔서 고마워요.", null);
    }

    private UserEntity lockedUser(long userSeq) {
        UserEntity user = users.findById(userSeq).orElseThrow();
        // 비밀번호 재설정·인증 처리와 같은 계정 잠금으로 직렬화한다.
        return users.findByEmailForUpdate(user.getUserEmail());
    }

    private String token(UserEntity user) {
        return userService.generatorToken((CustomUserDetails) details.loadUserByUsername(user.getUserEmail()));
    }

    private Long count(String sql, long userSeq) {
        try { return jdbc.queryForObject(sql, Long.class, userSeq); }
        catch (DataAccessException missing) { return null; }
    }

    /** 선택 기능 테이블(관심 공고·푸시·캘린더)이 아직 없는 DB에서도 탈퇴·변경은 진행한다. */
    private void tryUpdate(String sql, Object... args) {
        try { jdbc.update(sql, args); }
        catch (DataAccessException missing) { log.warn("계정 정리 중 건너뜀: {}", missing.getMostSpecificCause().getMessage()); }
    }
}
