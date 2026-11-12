package com.toady.web.app.planner;

import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.*;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 회원별 캘린더 구독 주소. 주소 자체가 열람 권한이므로 256비트 무작위 값을 쓰고, 재발급하면 이전 주소는 즉시 끊긴다.
 * 구독 주소는 구글 캘린더의 비공개 주소처럼 다시 보여줄 수 있어야 해서 원문으로 보관한다.
 */
@RestController
public class CalendarFeed {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc; private final PlannerService planner; private final UserRepository users; private final String domain;
    public CalendarFeed(JdbcTemplate jdbc, PlannerService planner, UserRepository users, @Value("${toady.web.domain}") String domain) {
        this.jdbc = jdbc; this.planner = planner; this.users = users; this.domain = domain.replaceAll("/+$", "");
    }

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
    static String newToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @GetMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER")
    public ResData<?> get(@AuthenticationPrincipal CustomUserDetails user) { return ResData.of(view(token(user.getUserSeq())), "SUCCESS", "성공"); }

    @PostMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER") @Transactional
    public ResData<?> issue(@AuthenticationPrincipal CustomUserDetails user) {
        String token = newToken();
        var now = java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        // MariaDB·H2 공통으로 동작하도록 갱신 후 없으면 추가한다. 회원당 한 행이라 기존 주소는 바로 무효가 된다.
        if (jdbc.update("UPDATE TB_CALENDAR_FEED SET FEED_TOKEN=?,REG_DT=?,LAST_READ_DT=NULL WHERE USER_SEQ=?", token, now, user.getUserSeq()) == 0)
            jdbc.update("INSERT INTO TB_CALENDAR_FEED (USER_SEQ,FEED_TOKEN,REG_DT) VALUES (?,?,?)", user.getUserSeq(), token, now);
        return ResData.of(view(token), "SUCCESS", "캘린더 구독 주소를 만들었습니다.");
    }

    @DeleteMapping("/api/v1/me/calendar-feed") @Secured("ROLE_USER")
    public ResData<?> revoke(@AuthenticationPrincipal CustomUserDetails user) {
        jdbc.update("DELETE FROM TB_CALENDAR_FEED WHERE USER_SEQ=?", user.getUserSeq());
        return ResData.of(view(null), "SUCCESS", "캘린더 구독을 해지했습니다.");
    }

    @GetMapping("/api/v1/public/calendar/{token}.ics")
    public ResponseEntity<String> feed(@PathVariable String token) {
        if (!token.matches("[A-Za-z0-9_-]{43}")) return ResponseEntity.notFound().build();
        Long user = jdbc.query("SELECT USER_SEQ FROM TB_CALENDAR_FEED WHERE FEED_TOKEN=?", (r, n) -> r.getLong(1), token).stream().findFirst().orElse(null);
        // 탈퇴·잠금 계정의 주소는 남아 있어도 일정을 내보내지 않는다.
        if (user == null || users.findById(user).filter(account -> !"Y".equals(account.getWithdrawalYn()) && !"Y".equals(account.getLockYn())).isEmpty())
            return ResponseEntity.notFound().build();
        jdbc.update("UPDATE TB_CALENDAR_FEED SET LAST_READ_DT=? WHERE USER_SEQ=?", java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul"))), user);
        String ics = CalendarIcs.build(planner.list(user).stream().map(PlannerService.Favorite::summary).toList(), domain, Instant.now());
        return ResponseEntity.ok().header("Content-Type", "text/calendar;charset=UTF-8")
            .header("Content-Disposition", "inline; filename=\"toady.ics\"").header("X-Robots-Tag", "noindex")
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate()).body(ics);
    }
}
