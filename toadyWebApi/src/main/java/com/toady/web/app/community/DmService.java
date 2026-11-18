package com.toady.web.app.community;

import com.toady.web.app.community.Community.Member;
import com.toady.web.app.community.Community.Result;
import com.toady.web.app.user.service.ProfileIcons;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 1:1 쪽지. 두 회원 쌍마다 대화방 하나를 두고, 읽은 위치·나간 위치를 각자 쪽지 번호로 기억한다.
 *
 * 보내기 규칙(홍보·불법 거래 통로가 되지 않게):
 * - 새 대화 시작 조건(가입 후 경과 시간·이야기 작성)은 설정(toady.dm.*)으로 켜고 끈다. 이용자를 늘리는 지금은 기본으로 끈다.
 * - 하루에 새 대화는 기본 5명까지.
 * - 상대가 답하기 전에는 한 통만 보낼 수 있고, 그 첫 쪽지에는 링크·전화번호·메신저 아이디·계좌번호를 넣을 수 없다.
 * - 받는 사람이 정한 범위(모두·이야기로 이어진 사람만·받지 않기)와 차단(어느 쪽이든)을 지킨다. 이미 서로 주고받던 대화는 이어진다.
 * - 한 사람이 하루에 보낼 수 있는 쪽지는 기본 50통.
 *
 * 흐름: CommunityController → DmService → JdbcTemplate SQL(TB_DM_THREAD, TB_DM_MESSAGE, TB_USER).
 * 한 사람 쌍은 (작은 번호 USER_LOW, 큰 번호 USER_HIGH)로 저장해 A→B, B→A가 같은 방이 되게 한다.
 * 새 쪽지 알림은 커밋이 끝난 뒤 DmNotifier가 별도 스레드에서 보낸다.
 */
// @Slf4j: log 필드 생성
@Slf4j
// @Service: 서비스 빈
@Service
public class DmService {
    static final int PAGE = 30;
    static final int THREAD_PAGE = 20;
    static final String CLOSED = "새 쪽지를 받지 않는 회원이에요.";
    private static final Pattern CONTEXT = Pattern.compile("^(apt|urban|remaining|public-rental|optional)/[A-Za-z0-9-]{1,40}/[A-Za-z0-9-]{1,40}$");
    /** 공고 유형(화면 주소) → 공고 이름을 읽을 상세 테이블. AccountService의 목록과 같다. */
    private static final Map<String, String> DETAIL_TABLES = Map.of(
            "apt", "TB_APT_LTTOT_PBLANC_DETAIL", "urban", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "remaining", "TB_REMNDR_LTTOT_PBLANC_DETAIL",
            "public-rental", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "optional", "TB_OPT_LTTOT_PBLANC_DETAIL");

    private final JdbcTemplate jdbc;
    private final Community community;
    private final SafetyService safety;
    private final ProfileIcons icons;
    private final DmNotifier notifier;
    /** 관리자 화면 금칙어(database.sql 26번). 테스트에서 직접 만들 때는 없을 수 있다. */
    // @Autowired(required = false): 해당 빈이 있으면 넣고, 없으면 null로 둔다(필드 주입)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.toady.web.app.operation.ModerationRules moderation;
    /** 관리자 화면 서비스 설정(database.sql 27번). 값이 없으면 환경변수 값을 쓴다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.toady.web.app.operation.AppSettings settings;
    private long minAccountHours() { return settings == null ? minAccountHours : settings.number(com.toady.web.app.operation.AppSettings.DM_MIN_ACCOUNT_HOURS, minAccountHours, 0, 720); }
    private boolean requireStory() { return settings == null ? requireStory : settings.flag(com.toady.web.app.operation.AppSettings.DM_REQUIRE_STORY, requireStory); }
    private long dailyNewThreads() { return settings == null ? dailyNewThreads : settings.number(com.toady.web.app.operation.AppSettings.DM_DAILY_NEW_THREADS, dailyNewThreads, 1, 50); }
    private long dailyMessages() { return settings == null ? dailyMessages : settings.number(com.toady.web.app.operation.AppSettings.DM_DAILY_MESSAGES, dailyMessages, 1, 500); }
    /** 새 대화를 시작하려면 가입 후 지나야 하는 시간(0이면 조건 없음) */
    private final long minAccountHours;
    /** 새 대화를 시작하려면 이야기를 하나 이상 남겨야 하는가 */
    private final boolean requireStory;
    private final int dailyNewThreads;
    private final int dailyMessages;

    // 생성자 주입. @Value로 받은 값은 관리자 설정(AppSettings)이 없을 때 쓰는 기본값이다
    public DmService(JdbcTemplate jdbc, Community community, SafetyService safety, ProfileIcons icons, DmNotifier notifier,
                     @org.springframework.beans.factory.annotation.Value("${toady.dm.min-account-hours:0}") long minAccountHours,
                     @org.springframework.beans.factory.annotation.Value("${toady.dm.require-story:false}") boolean requireStory,
                     @org.springframework.beans.factory.annotation.Value("${toady.dm.daily-new-threads:5}") int dailyNewThreads,
                     @org.springframework.beans.factory.annotation.Value("${toady.dm.daily-messages:50}") int dailyMessages) {
        this.jdbc = jdbc; this.community = community; this.safety = safety; this.icons = icons; this.notifier = notifier;
        this.minAccountHours = Math.max(0, minAccountHours); this.requireStory = requireStory;
        this.dailyNewThreads = Math.max(1, dailyNewThreads); this.dailyMessages = Math.max(1, dailyMessages);
    }

    /** 대화방 한 줄. me 기준으로 내 읽은·나간 위치를 고른다. */
    record Thread(long seq, long low, long high, long startedBy, String contextId, long lastSeq, long lowRead, long highRead, long lowLeft, long highLeft) {
        long other(long me) { return me == low ? high : low; }
        long read(long me) { return me == low ? lowRead : highRead; }
        long left(long me) { return me == low ? lowLeft : highLeft; }
        String side(long me) { return me == low ? "LOW" : "HIGH"; }
        boolean has(long me) { return me == low || me == high; }
    }

    private static Thread map(ResultSet rs) throws SQLException {
        return new Thread(rs.getLong("THREAD_SEQ"), rs.getLong("USER_LOW"), rs.getLong("USER_HIGH"), rs.getLong("STARTED_BY"), rs.getString("CONTEXT_ID"),
                rs.getLong("LAST_MESSAGE_SEQ"), rs.getLong("LOW_READ_SEQ"), rs.getLong("HIGH_READ_SEQ"), rs.getLong("LOW_LEFT_SEQ"), rs.getLong("HIGH_LEFT_SEQ"));
    }

    // 두 회원의 대화방을 찾는다. lock이면 FOR UPDATE로 행을 잠근다(트랜잭션 안에서만 의미 있다)
    private Thread pair(long a, long b, boolean lock) {
        List<Thread> found = jdbc.query("SELECT * FROM TB_DM_THREAD WHERE USER_LOW=? AND USER_HIGH=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> map(rs), Math.min(a, b), Math.max(a, b));
        return found.isEmpty() ? null : found.get(0);
    }

    private Thread thread(long seq, boolean lock) {
        List<Thread> found = jdbc.query("SELECT * FROM TB_DM_THREAD WHERE THREAD_SEQ=?" + (lock ? " FOR UPDATE" : ""), (rs, n) -> map(rs), seq);
        return found.isEmpty() ? null : found.get(0);
    }

    // 이 대화방에서 sender가 보낸 쪽지 수
    private long sentIn(long threadSeq, long sender) {
        return community.count("SELECT COUNT(*) FROM TB_DM_MESSAGE WHERE THREAD_SEQ=? AND SENDER_SEQ=?", threadSeq, sender);
    }

    /**
     * 보내기 중(대화방 행을 잠근 뒤) 쓰는 '보낸 적 있는가'. 잠금 읽기라 일관 읽기 스냅샷이 아니라 최신 커밋을 본다.
     * (MariaDB 기본 REPEATABLE READ에서 일반 SELECT는 먼저 잡힌 스냅샷을 읽어, 동시에 보낸 쪽지를 못 볼 수 있다.)
     */
    private boolean sentLocked(long threadSeq, long sender) {
        return !jdbc.queryForList("SELECT MESSAGE_SEQ FROM TB_DM_MESSAGE WHERE THREAD_SEQ=? AND SENDER_SEQ=? ORDER BY MESSAGE_SEQ LIMIT 1 FOR UPDATE",
                Long.class, threadSeq, sender).isEmpty();
    }

    // ---------------------------------------------------------------- 보낼 수 있는가

    /** 보낼 수 있는지와 이유. reason은 화면이 버튼 모양을 고르는 데 쓴다. */
    public record Gate(boolean ok, String reason, String message) {
        static Gate yes() { return new Gate(true, "OK", null); }
        static Gate no(String reason, String message) { return new Gate(false, reason, message); }
        Map<String, Object> view() {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("canSend", ok); view.put("reason", reason); view.put("message", message);
            return view;
        }
    }

    Gate gate(long me, long other, Thread thread, LocalDateTime now) { return gate(me, other, thread, now, false); }

    /** locking: 보내기 중이면 true(대화방 행을 잠근 상태에서 최신 값으로 판단). */
    Gate gate(long me, long other, Thread thread, LocalDateTime now, boolean locking) {
        if (!community.ready()) return Gate.no("NOT_READY", Community.NOT_READY);
        if (me == other) return Gate.no("SELF", "나에게는 쪽지를 보낼 수 없어요.");
        Member target = community.member(other);
        if (target == null || !target.active()) return Gate.no("UNAVAILABLE", "탈퇴했거나 이용이 제한된 회원이라 쪽지를 보낼 수 없어요.");
        SafetyService.Setting mine = safety.setting(me);
        if (mine.suspended(now)) {
            return Gate.no("SUSPENDED", "운영 정책 위반으로 " + mine.suspendedUntil().toLocalDate() + "까지 쪽지를 보낼 수 없어요.");
        }
        if (community.blocked(me, other)) return Gate.no("BLOCKED", "차단한 회원이에요. 차단을 풀면 쪽지를 보낼 수 있어요.");
        // 상대가 나를 차단한 사실은 알리지 않는다: '새 쪽지를 받지 않는 회원'과 같은 이유·문구로 보인다.
        if (community.blocked(other, me)) return Gate.no("CLOSED", CLOSED);

        boolean otherReplied = thread != null && (locking ? sentLocked(thread.seq(), other) : sentIn(thread.seq(), other) > 0);
        if (otherReplied) return Gate.yes();   // 서로 주고받은 대화는 받는 범위와 관계없이 이어진다.

        String policy = safety.setting(other).policy();
        if ("NONE".equals(policy)) return Gate.no("CLOSED", CLOSED);
        if ("KNOWN".equals(policy) && !community.talkedInStories(me, other)) {
            return Gate.no("KNOWN_ONLY", "이야기로 이어진 회원에게만 쪽지를 받는 회원이에요. 이 회원의 이야기에 답글로 먼저 인사해 보세요.");
        }
        if (thread != null && (locking ? sentLocked(thread.seq(), me) : sentIn(thread.seq(), me) > 0)) {
            return Gate.no("WAITING", "상대가 답하기 전에는 쪽지를 한 통만 보낼 수 있어요.");
        }

        // 새 대화 시작 조건
        Member self = community.member(me);
        if (self == null || !self.active()) return Gate.no("UNAVAILABLE", "다시 로그인해 주세요.");
        if (!self.emailVerified()) return Gate.no("VERIFY", "이메일 인증을 마치면 쪽지를 보낼 수 있어요.");
        long minAccountHours = minAccountHours();
        if (minAccountHours > 0 && self.joinedAt() != null && self.joinedAt().plusHours(minAccountHours).isAfter(now)) {
            String wait = minAccountHours == 24 ? "하루가" : minAccountHours % 24 == 0 ? minAccountHours / 24 + "일이" : minAccountHours + "시간이";
            return Gate.no("TOO_NEW", "가입하고 " + wait + " 지나면 쪽지를 보낼 수 있어요.");
        }
        if (requireStory() && community.count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N'", me) == 0) {
            return Gate.no("NO_STORY", "공고 ‘이야기’에 글을 하나 이상 남기면 쪽지를 보낼 수 있어요.");
        }
        // 보내기 중에는 방금 만든(아직 쪽지 없는) 이 대화방은 세지 않는다.
        if (community.count("SELECT COUNT(*) FROM TB_DM_THREAD WHERE STARTED_BY=? AND REG_DT>=? AND THREAD_SEQ<>?", me,
                Community.ts(now.toLocalDate().atStartOfDay()), thread == null ? 0L : thread.seq()) >= dailyNewThreads()) {
            return Gate.no("LIMIT", "새 대화는 하루에 " + dailyNewThreads() + "명까지 시작할 수 있어요. 내일 다시 시도해 주세요.");
        }
        return Gate.yes();
    }

    /** 프로필 카드·새 쪽지 화면용: 기존 대화방 번호와 보낼 수 있는지. */
    public Map<String, Object> status(long me, long other) {
        Map<String, Object> view = new LinkedHashMap<>();
        Thread thread = community.ready() ? pair(me, other, false) : null;
        view.put("threadSeq", thread == null || thread.lastSeq() <= thread.left(me) ? null : thread.seq());
        view.putAll(gate(me, other, thread, Community.now()).view());
        return view;
    }

    // ---------------------------------------------------------------- 보내기

    /**
     * 보내기. 대화방이 없으면 먼저 만들고(INSERT, 동시에 만들면 유일키로 하나만 남음) 그 행을 잠근 뒤 규칙을 최신 값으로 확인한다.
     * 먼저 잠금 조회를 하면 MariaDB가 빈 구간에 잠금을 걸어, 두 요청이 동시에 첫 쪽지를 보낼 때 교착(1213)이 난다.
     * 규칙에 걸리면 이 트랜잭션을 되돌려(방금 만든 빈 대화방도 남기지 않는다) 안내만 돌려준다.
     */
    // @Transactional: 대화방 생성·쪽지 저장·읽음 위치 갱신을 하나로 묶는다. 잠금(FOR UPDATE)은 이 트랜잭션이 끝날 때 풀린다
    @Transactional
    public Result send(long me, long other, String content, String contextId) {
        LocalDateTime now = Community.now();
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        if (me == other) return Result.fail("ERR-403", "나에게는 쪽지를 보낼 수 없어요.");
        // 내용 문제(빈 글·길이)는 대화방을 만들기 전에 거른다. 연락처 규칙은 상대가 답했는지 본 뒤에 판단한다.
        DmRules.Problem early = DmRules.check(content, false);
        if (early != null) return Result.fail("ERR-401", DmRules.message(early));
        // 보내는 회원 행을 먼저 잠가, 같은 회원이 여러 상대에게 동시에 보낸 요청이 하루 한도(새 대화·쪽지 수)를 함께 넘지 못하게 한다.
        // 대화방보다 먼저, 항상 자기 행만 잠그므로 두 회원이 서로에게 동시에 보내도 교착이 생기지 않는다.
        jdbc.queryForList("SELECT USER_SEQ FROM TB_USER WHERE USER_SEQ=? FOR UPDATE", Long.class, me);
        if (pair(me, other, false) == null) {
            String context = contextId != null && CONTEXT.matcher(contextId).matches() ? contextId : null;
            try {
                jdbc.update("INSERT INTO TB_DM_THREAD (USER_LOW, USER_HIGH, STARTED_BY, CONTEXT_ID, REG_DT) VALUES (?,?,?,?,?)",
                        Math.min(me, other), Math.max(me, other), me, context, Community.ts(now));
            } catch (DuplicateKeyException raced) { /* 두 사람이 동시에 첫 쪽지를 보냄: 이미 만들어진 방을 쓴다 */ }
        }
        Thread thread = pair(me, other, true);
        Gate gate = gate(me, other, thread, now, true);
        if (!gate.ok()) return rollback(Result.fail("ERR-403", gate.message()));
        boolean firstUnanswered = !sentLocked(thread.seq(), other);
        DmRules.Problem problem = DmRules.check(content, firstUnanswered);
        if (problem != null) return rollback(Result.fail("ERR-401", DmRules.message(problem)));
        var extra = moderation == null ? null : moderation.check(content, com.toady.web.app.operation.ModerationRules.Scope.DM);
        if (extra == com.toady.web.app.operation.ModerationRules.Action.BLOCK) return rollback(Result.fail("ERR-401", "운영 정책상 보낼 수 없는 표현이 들어 있어요. 내용을 고쳐 다시 보내 주세요."));
        if (community.count("SELECT COUNT(*) FROM TB_DM_MESSAGE WHERE SENDER_SEQ=? AND REG_DT>=?", me, Community.ts(now.toLocalDate().atStartOfDay())) >= dailyMessages()) {
            return rollback(Result.fail("ERR-429", "오늘은 쪽지를 더 보낼 수 없어요(하루 " + dailyMessages() + "통)."));
        }
        String text = content.strip();
        long receiverUnreadBefore = unreadIn(thread, other);
        boolean flagged = DmRules.flagged(text) || extra == com.toady.web.app.operation.ModerationRules.Action.FLAG;
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        final long threadSeq = thread.seq();
        // INSERT 후 DB가 만든 MESSAGE_SEQ(AUTO_INCREMENT)를 돌려받기 위해 PreparedStatement를 직접 만든다
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO TB_DM_MESSAGE (THREAD_SEQ, SENDER_SEQ, CONTENT, FLAGGED, REG_DT) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, threadSeq); statement.setLong(2, me); statement.setString(3, text);
            statement.setBoolean(4, flagged); statement.setTimestamp(5, Community.ts(now));
            return statement;
        }, key);
        long messageSeq = generated(key);
        String side = thread.side(me);
        jdbc.update("UPDATE TB_DM_THREAD SET LAST_MESSAGE_SEQ=?, LAST_MESSAGE_DT=?, " + side + "_READ_SEQ=? WHERE THREAD_SEQ=?",
                messageSeq, Community.ts(now), messageSeq, threadSeq);
        // 받는 사람에게 이 대화의 안 읽은 쪽지가 없던 경우에만 알린다(연달아 보내도 알림은 한 번).
        if (receiverUnreadBefore == 0) afterCommit(() -> notifier.notify(other, me, threadSeq));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threadSeq", threadSeq);
        data.put("message", messageView(messageSeq, me, text, flagged, now, me));
        return Result.done("쪽지를 보냈어요.", data);
    }

    // GeneratedKeyHolder에서 새 쪽지 번호를 꺼낸다
    private static long generated(GeneratedKeyHolder key) {
        // H2는 모든 컬럼을, MariaDB는 키 하나를 돌려준다.
        Map<String, Object> keys = key.getKeys();
        if (keys != null && keys.size() > 1) {
            Object value = keys.getOrDefault("MESSAGE_SEQ", keys.get("message_seq"));
            if (value instanceof Number number) return number.longValue();
        }
        Number number = key.getKey();
        if (number == null) throw new IllegalStateException("쪽지 번호를 받지 못했습니다.");
        return number.longValue();
    }

    /** 규칙에 걸린 보내기는 되돌린다(방금 만든 빈 대화방이 남아 '오늘 시작한 대화'로 세지 않게). */
    private static Result rollback(Result result) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return result;
    }

    /**
     * 트랜잭션이 커밋된 뒤에 task를 실행하도록 예약한다. 롤백되면 실행하지 않는다.
     * 저장이 확정되기 전에 알림이 나가 '쪽지가 없는 알림'이 생기지 않게 하기 위해서다.
     */
    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) { task.run(); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { task.run(); }
        });
    }

    // user가 이 방에서 아직 읽지 않은 상대 쪽지 수(읽은 위치와 나간 위치 중 큰 값 뒤부터 센다).
    // 메뉴 배지(unread)와 같은 기준으로, user가 차단한 회원이 보낸 쪽지는 세지 않는다(쪽지함 숫자와 배지가 어긋나지 않게).
    private long unreadIn(Thread thread, long user) {
        return community.count("SELECT COUNT(*) FROM TB_DM_MESSAGE WHERE THREAD_SEQ=? AND SENDER_SEQ<>? AND MESSAGE_SEQ>?" + NOT_BLOCKED_SENDER,
                thread.seq(), user, Math.max(thread.read(user), thread.left(user)), user);
    }

    /** 받는 사람(마지막 ? 자리)이 차단한 회원이 보낸 쪽지는 빼는 조건. 배지와 쪽지함이 함께 쓴다. */
    static final String NOT_BLOCKED_SENDER = " AND SENDER_SEQ NOT IN (SELECT BLOCKED_USER_SEQ FROM TB_USER_BLOCK WHERE USER_SEQ=?)";

    // ---------------------------------------------------------------- 읽기

    /** 안 읽은 쪽지 수(메뉴 배지). 테이블이 없으면 0. */
    public long unread(long me) {
        if (!community.ready()) return 0;
        try {
            // 내가 차단한 회원이 차단 전에 보낸 안 읽은 쪽지는 배지에 세지 않는다.
            String notBlocked = NOT_BLOCKED_SENDER.replace(" SENDER_SEQ", " m.SENDER_SEQ");
            return community.count("SELECT COUNT(*) FROM TB_DM_MESSAGE m JOIN TB_DM_THREAD t ON t.THREAD_SEQ=m.THREAD_SEQ WHERE t.USER_LOW=? AND m.SENDER_SEQ<>?"
                    + " AND m.MESSAGE_SEQ>t.LOW_READ_SEQ AND m.MESSAGE_SEQ>t.LOW_LEFT_SEQ" + notBlocked, me, me, me)
                    + community.count("SELECT COUNT(*) FROM TB_DM_MESSAGE m JOIN TB_DM_THREAD t ON t.THREAD_SEQ=m.THREAD_SEQ WHERE t.USER_HIGH=? AND m.SENDER_SEQ<>?"
                    + " AND m.MESSAGE_SEQ>t.HIGH_READ_SEQ AND m.MESSAGE_SEQ>t.HIGH_LEFT_SEQ" + notBlocked, me, me, me);
        } catch (DataAccessException failure) {
            log.warn("안 읽은 쪽지 수를 세지 못함: {}", failure.getMostSpecificCause().getMessage());
            return 0;
        }
    }

    /** 쪽지함: 최근 대화 순 20개씩. 내가 나간 뒤 새 쪽지가 없는 대화는 빼고 보여 준다. */
    public Map<String, Object> threads(long me, int page) {
        int safePage = Math.max(0, Math.min(page, 500));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ready", community.ready());
        if (!community.ready()) { view.put("items", List.of()); view.put("hasMore", false); return view; }
        List<Thread> rows = jdbc.query("SELECT * FROM TB_DM_THREAD WHERE (USER_LOW=? AND LAST_MESSAGE_SEQ>LOW_LEFT_SEQ) OR (USER_HIGH=? AND LAST_MESSAGE_SEQ>HIGH_LEFT_SEQ)"
                + " ORDER BY LAST_MESSAGE_SEQ DESC LIMIT ? OFFSET ?", (rs, n) -> map(rs), me, me, THREAD_PAGE + 1, safePage * THREAD_PAGE);
        boolean more = rows.size() > THREAD_PAGE;
        if (more) rows = rows.subList(0, THREAD_PAGE);
        Map<Long, String> found = icons.of(rows.stream().map(row -> row.other(me)).toList());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Thread row : rows) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("threadSeq", row.seq());
            item.put("other", otherView(row.other(me), found.get(row.other(me))));
            jdbc.query("SELECT SENDER_SEQ, CONTENT, REG_DT FROM TB_DM_MESSAGE WHERE MESSAGE_SEQ=?", rs -> {
                item.put("lastMine", rs.getLong(1) == me);
                item.put("lastPreview", clip(rs.getString(2), 80));
                item.put("lastAt", rs.getTimestamp(3).toLocalDateTime());
            }, row.lastSeq());
            item.putIfAbsent("lastPreview", "");
            item.put("unread", unreadIn(row, me));
            item.put("contextId", row.contextId());
            item.put("houseNm", houseName(row.contextId()));
            items.add(item);
        }
        view.put("items", items);
        view.put("page", safePage);
        view.put("hasMore", more);
        return view;
    }

    /**
     * 대화 보기. before가 없으면 최신 30통을 주고 읽음으로 표시한다. before를 주면 그 앞 30통(위로 더 보기).
     * 대화방에 참여하지 않은 회원에게는 없는 대화처럼 보인다.
     */
    @Transactional
    public Result open(long me, long threadSeq, Long before) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        Thread row = thread(threadSeq, false);
        if (row == null || !row.has(me)) return Result.fail("ERR-404", "대화를 찾을 수 없어요.");
        long left = row.left(me);
        List<Map<String, Object>> messages = new ArrayList<>();
        jdbc.query("SELECT MESSAGE_SEQ, SENDER_SEQ, CONTENT, FLAGGED, REG_DT FROM TB_DM_MESSAGE WHERE THREAD_SEQ=? AND MESSAGE_SEQ>?"
                + (before != null ? " AND MESSAGE_SEQ<?" : "") + " ORDER BY MESSAGE_SEQ DESC LIMIT ?", rs -> {
            messages.add(messageView(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getBoolean(4), rs.getTimestamp(5).toLocalDateTime(), me));
        }, before != null ? new Object[]{threadSeq, left, before, PAGE + 1} : new Object[]{threadSeq, left, PAGE + 1});
        boolean more = messages.size() > PAGE;
        if (more) messages.remove(messages.size() - 1);
        Collections.reverse(messages);
        if (before == null && row.lastSeq() > row.read(me)) {
            jdbc.update("UPDATE TB_DM_THREAD SET " + row.side(me) + "_READ_SEQ=? WHERE THREAD_SEQ=? AND " + row.side(me) + "_READ_SEQ<?",
                    row.lastSeq(), threadSeq, row.lastSeq());
        }
        long other = row.other(me);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threadSeq", threadSeq);
        data.put("other", otherView(other, icons.of(other)));
        data.put("contextId", row.contextId());
        data.put("houseNm", houseName(row.contextId()));
        data.put("messages", messages);
        data.put("hasMore", more);
        data.put("blocked", community.blocked(me, other));
        data.put("otherReplied", sentIn(threadSeq, other) > 0);
        data.putAll(gate(me, other, row, Community.now()).view());
        return Result.done("성공", data);
    }

    /** 대화방 나가기: 지금까지의 쪽지를 내 쪽지함에서 지운다. 상대 쪽에는 그대로 남고, 새 쪽지가 오면 다시 보인다. */
    @Transactional
    public Result leave(long me, long threadSeq) {
        if (!community.ready()) return Result.fail("ERR-999", Community.NOT_READY);
        Thread row = thread(threadSeq, true);
        if (row == null || !row.has(me)) return Result.fail("ERR-404", "대화를 찾을 수 없어요.");
        String side = row.side(me);
        jdbc.update("UPDATE TB_DM_THREAD SET " + side + "_LEFT_SEQ=?, " + side + "_READ_SEQ=GREATEST(" + side + "_READ_SEQ, ?) WHERE THREAD_SEQ=?",
                row.lastSeq(), row.lastSeq(), threadSeq);
        return Result.done("대화를 쪽지함에서 지웠어요. 상대에게는 그대로 남아 있어요.", null);
    }

    private Map<String, Object> otherView(long userSeq, String icon) {
        Member member = community.member(userSeq);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userSeq", userSeq);
        boolean gone = member == null || member.withdrawn();
        view.put("nickname", gone ? "탈퇴한 회원" : member.nickname());
        view.put("withdrawn", gone);
        view.put("profileIcon", gone ? null : icon);
        return view;
    }

    private static Map<String, Object> messageView(long seq, long sender, String content, boolean flagged, LocalDateTime at, long me) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("messageSeq", seq);
        view.put("mine", sender == me);
        view.put("content", content);
        // 경고는 받는 사람에게만 붙인다.
        view.put("flagged", flagged && sender != me);
        view.put("sentAt", at);
        return view;
    }

    /** 대화가 시작된 공고 id로 단지 이름을 읽는다. 테이블 이름은 DETAIL_TABLES의 고정 값만 쓴다. */
    String houseName(String contextId) {
        if (contextId == null) return null;
        String[] id = contextId.split("/");
        String table = id.length == 3 ? DETAIL_TABLES.get(id[0]) : null;
        if (table == null) return null;
        try {
            return jdbc.query("SELECT HOUSE_NM FROM " + table + " WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, id[1], id[2]);
        } catch (DataAccessException missing) { return null; }
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").trim();
        return flat.length() > max ? flat.substring(0, max) + "…" : flat;
    }
}
