package com.toady.web.app.planner;

import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 회원별 캘린더 구독 주소. 주소 자체가 열람 권한이므로 256비트 무작위 값을 쓰고, 재발급하면 이전 주소는 즉시 끊긴다.
 * 구독 주소는 구글 캘린더의 비공개 주소처럼 다시 보여줄 수 있어야 해서 원문으로 보관한다.
 * 테이블: TB_CALENDAR_FEED(회원당 한 행). 나의 청약 화면의 '캘린더 구독' 버튼이 발급·해지 API를 부르고,
 * 캘린더 앱은 /calendar/{token}.ics(웹 컨테이너 nginx가 /api/v1/public/calendar/...로 넘김)를 주기적으로 가져간다.
 */
// @RestController: JSON(ResData)과 .ics 텍스트를 응답하는 컨트롤러.
@RestController
public class CalendarFeed {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc; private final PlannerService planner; private final UserRepository users; private final String domain;
    public CalendarFeed(JdbcTemplate jdbc, PlannerService planner, UserRepository users, @Value("${toady.web.domain}") String domain) {
        this.jdbc = jdbc; this.planner = planner; this.users = users; this.domain = domain.replaceAll("/+$", "");
    }

    /** 화면에 줄 구독 정보: https 주소, 아이폰용 webcal 주소, 구글 캘린더 추가 링크. */
    Map<String, Object> view(String token) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("subscribed", token != null);
        if (token != null) {
            String https = domain + "/calendar/" + token + ".ics";
            String webcal = https.replaceFirst("^https?://", "webcal://");
            body.put("url", https);
            body.put("webcalUrl", webcal);
            body.put("googleUrl", "https://calendar.google.com/calendar/r?cid=" + URLEncoder.encode(webcal, StandardCharsets.UTF_8));
        }
        return body;
    }
    String token(long user) {
        return jdbc.query("SELECT FEED_TOKEN FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", (r, n) -> r.getString(1), user).stream().findFirst().orElse(null);
    }
    /** 32바이트(256비트) 무작위 값을 URL에 쓸 수 있는 Base64(43글자)로 만든다. 추측으로 다른 사람 주소를 찾을 수 없다. */
    static String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // @GetMapping: 내 구독 주소 조회. @Secured("ROLE_USER"): 로그인한 회원만.
    // @AuthenticationPrincipal: 로그인한 사용자 정보를 바로 받는다.
    @GetMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER")
    public ResData<?> get(@AuthenticationPrincipal CustomUserDetails user) { return ResData.of(view(token(user.getUserSeq())), "SUCCESS", "성공"); }

    // @PostMapping: 구독 주소 발급(재발급). @Transactional: 갱신·추가 SQL을 한 트랜잭션으로 묶는다.
    @PostMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER") @Transactional
    public ResData<?> issue(@AuthenticationPrincipal CustomUserDetails user) {
        String token = newToken();
        var now = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        // MariaDB·H2 공통으로 동작하도록 갱신 후 없으면 추가한다. 회원당 한 행이라 기존 주소는 바로 무효가 된다.
        if (jdbc.update("UPDATE TB_CALENDAR_FEED SET FEED_TOKEN=?,REG_DT=?,LAST_READ_DT=NULL WHERE USER_SEQ=?", token, now, user.getUserSeq()) == 0) {
            try { jdbc.update("INSERT INTO TB_CALENDAR_FEED (USER_SEQ,FEED_TOKEN,REG_DT) VALUES (?,?,?)", user.getUserSeq(), token, now); }
            catch (DuplicateKeyException raced) {
                // 처음 발급 요청이 동시에 두 번 오면 먼저 들어간 행을 이번 토큰으로 바꾼다(마지막 요청의 주소가 유효).
                jdbc.update("UPDATE TB_CALENDAR_FEED SET FEED_TOKEN=?,REG_DT=?,LAST_READ_DT=NULL WHERE USER_SEQ=?", token, now, user.getUserSeq());
            }
        }
        return ResData.of(view(token), "SUCCESS", "캘린더 구독 주소를 만들었습니다.");
    }

    // @DeleteMapping: 구독 해지. 행을 지우면 기존 주소는 바로 404가 된다.
    @DeleteMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER")
    public ResData<?> revoke(@AuthenticationPrincipal CustomUserDetails user) {
        jdbc.update("DELETE FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", user.getUserSeq());
        return ResData.of(view(null), "SUCCESS", "캘린더 구독을 해지했습니다.");
    }

    // 로그인 없이 캘린더 앱이 부르는 공개 주소. 토큰 자체가 열쇠라 형식이 맞고 DB에 있을 때만 일정을 준다.
    // 개인 일정이라 검색 차단(noindex)하고, 캐시는 private 5분(공유 캐시에 남지 않게).
    @GetMapping("/api/v1/public/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) return ResponseEntity.notFound().build();
        Long user = jdbc.query("SELECT USER_SEQ FROM TB_CALENDAR_FEED WHERE FEED_TOKEN=?", (r, n) -> r.getLong(1), token).stream().findFirst().orElse(null);
        // 탈퇴·잠금 계정의 주소는 남아 있어도 일정을 내보내지 않는다.
        if (user == null || users.findById(user).filter(account -> !"Y".equals(account.getWithdrawalYn()) && !"Y".equals(account.getLockYn())).isEmpty())
            return ResponseEntity.notFound().build();
        jdbc.update("UPDATE TB_CALENDAR_FEED SET LAST_READ_DT=? WHERE USER_SEQ=?", Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul"))), user);
        String ics = CalendarIcs.build(planner.list(user).stream().map(PlannerService.Favorite::summary).toList(), domain, Instant.now());
        return ResponseEntity.ok().header("Content-Type", "text/calendar;charset=UTF-8")
            .header("Content-Disposition", "inline; filename=\"toady.ics\"").header("X-Robots-Tag", "noindex")
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).body(ics);
    }
}
