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
import java.util.LinkedHashMap;
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

    public AccountService(UserRepository users, UserService userService, SimpleUserDetailsService details, PasswordEncoder encoder, JdbcTemplate jdbc) {
        this.users = users; this.userService = userService; this.details = details; this.encoder = encoder; this.jdbc = jdbc;
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
     * 이메일·닉네임은 다시 가입할 수 있게 익명 값으로 바꾸고, 관심 공고·알림 구독·캘린더 구독은 삭제한다.
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
