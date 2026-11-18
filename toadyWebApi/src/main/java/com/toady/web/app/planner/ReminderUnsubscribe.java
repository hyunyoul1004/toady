package com.toady.web.app.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 관심 일정 알림 메일의 수신 해제. 로그인하지 않은 기기에서도 메일의 링크 한 번(확인 버튼)으로 끌 수 있게 한다.
 * 링크에는 회원 번호와 서명(HMAC)만 담는다. 메일 보안 검사기가 링크를 미리 열어도 해제되지 않도록 GET은 확인 화면만 보여 주고,
 * 실제 해제는 POST(확인 버튼, 메일 앱의 원클릭 해제 RFC 8058)로만 한다.
 */
// @RestController: 이 클래스의 메서드가 응답 본문을 직접 만든다. 여기서는 produces로 HTML을 돌려준다.
@RestController
public class ReminderUnsubscribe {
    private static final Logger log = LoggerFactory.getLogger(ReminderUnsubscribe.class);
    static final String PATH = "/api/v1/public/reminders/unsubscribe";
    private final PlannerService planner;
    private final byte[] key;
    private final String domain;

    // @Value로 JWT 비밀키(toady.security.secret-key)와 서비스 주소를 설정에서 받는다.
    public ReminderUnsubscribe(PlannerService planner, @Value("${toady.security.secret-key}") String secret, @Value("${toady.web.domain}") String domain) {
        this.planner = planner;
        // JWT 서명 키를 그대로 쓰지 않고 용도 이름을 붙여 따로 만든 키를 쓴다.
        this.key = hmac(secret.getBytes(StandardCharsets.UTF_8), "toady-reminder-unsubscribe-key");
        this.domain = domain.replaceAll("/+$", "");
    }

    /** 메일에 넣는 해제 주소(메일 본문 링크와 List-Unsubscribe 헤더에 함께 쓴다). */
    public String url(long userSeq) { return domain + PATH + "?u=" + userSeq + "&t=" + token(userSeq); }

    // 회원 번호에 HMAC-SHA256 서명을 붙여 URL에 넣기 좋은 Base64로 바꾼다. 비밀키 없이는 다른 회원 토큰을 만들 수 없다.
    String token(long userSeq) { return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(key, "reminder-unsubscribe:v1:" + userSeq)); }

    // MessageDigest.isEqual: 비교 시간이 일정해 글자를 하나씩 맞혀 보는 공격(타이밍 공격)을 막는다.
    boolean valid(Long userSeq, String token) {
        if (userSeq == null || userSeq <= 0 || token == null || token.length() > 100) return false;
        return MessageDigest.isEqual(token(userSeq).getBytes(StandardCharsets.US_ASCII), token.getBytes(StandardCharsets.US_ASCII));
    }

    // @GetMapping: 메일 링크를 열면 확인 화면만 보여 준다(바로 해제하지 않음).
    // @RequestParam(required = false): u(회원 번호)·t(서명)가 없어도 오류 대신 안내 화면을 보여 주려고 선택값으로 받는다.
    @GetMapping(value = PATH, produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> confirm(@RequestParam(required = false) Long u, @RequestParam(required = false) String t) {
        if (!valid(u, t)) return page(HttpStatus.BAD_REQUEST, "링크를 확인할 수 없어요", "메일의 링크가 잘렸거나 바뀐 것 같아요. 로그인한 뒤 나의 청약의 메뉴에서 이메일 알림을 끌 수 있어요.", "");
        String form = "<form method='post' action='" + PublicHtml.esc(PATH) + "'><input type='hidden' name='u' value='" + u + "'><input type='hidden' name='t' value='" + PublicHtml.esc(t) + "'>"
            + "<button type='submit'>관심 일정 메일 모두 끄기</button></form>";
        return page(HttpStatus.OK, "관심 일정 메일을 끌까요?", "저장한 관심 공고의 오늘·내일 일정 메일을 더 이상 보내지 않아요. 관심 공고와 메모는 그대로 남아요.", form);
    }

    /** 확인 버튼과 메일 앱의 원클릭 해제(List-Unsubscribe-Post, 본문 List-Unsubscribe=One-Click)가 함께 쓴다. */
    // @PostMapping: 실제 해제. 폼 전송이나 메일 앱의 원클릭 해제 요청이 여기로 온다.
    @PostMapping(value = PATH, produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> unsubscribe(@RequestParam(required = false) Long u, @RequestParam(required = false) String t) {
        if (!valid(u, t)) return page(HttpStatus.BAD_REQUEST, "링크를 확인할 수 없어요", "로그인한 뒤 나의 청약의 메뉴에서 이메일 알림을 끌 수 있어요.", "");
        planner.disableEmails(u);
        log.info("관심 일정 메일 수신 해제: userSeq={}", u);
        return page(HttpStatus.OK, "관심 일정 메일을 껐어요", "다시 받으려면 나의 청약의 각 관심 공고에서 이메일 알림을 켜 주세요.", "");
    }

    // 로그인 없이 보는 작은 안내 페이지. SPA가 아니라 서버가 HTML을 직접 만든다. 검색에 안 나오게 noindex, 캐시 금지.
    private ResponseEntity<String> page(HttpStatus status, String title, String text, String action) {
        String html = "<!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='robots' content='noindex'>"
            + "<title>" + PublicHtml.esc(title) + " · TOADY</title><style>body{margin:0;background:#f3f6f9;font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#172b3a;word-break:keep-all}"
            + "main{max-width:480px;margin:48px auto;padding:32px 24px;background:#fff;border-radius:16px}h1{font-size:22px;margin:8px 0 12px}p{line-height:1.6;color:#475569}"
            + "button{width:100%;min-height:48px;margin-top:12px;border:0;border-radius:10px;background:#2563eb;color:#fff;font-size:16px;font-weight:700;cursor:pointer}a{color:#2563eb}</style></head>"
            + "<body><main><b style='color:#087f73'>TOADY</b><h1>" + PublicHtml.esc(title) + "</h1><p>" + PublicHtml.esc(text) + "</p>" + action
            + "<p><a href='" + PublicHtml.esc(domain + "/my") + "'>나의 청약으로 가기</a></p></main></body></html>";
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore()).body(html);
    }

    private static byte[] hmac(byte[] secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) { throw new IllegalStateException("수신 해제 서명을 만들 수 없습니다", failure); }
    }
}
