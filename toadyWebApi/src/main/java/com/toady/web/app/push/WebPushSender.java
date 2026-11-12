package com.toady.web.app.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * VAPID 키는 환경변수로만 받는다. 키가 없거나 서로 맞지 않으면 푸시 기능 전체를 끈 상태로 시작한다(값은 출력하지 않음).
 * 엔드포인트는 사용자가 보낸 주소라 서버가 임의 주소로 요청하지 않도록 주요 브라우저 푸시 서비스 호스트만 허용한다.
 */
@Component
public class WebPushSender {
    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);
    /** 크롬·엣지(FCM/WNS), 파이어폭스(Mozilla autopush), 사파리·iOS(Apple) 푸시 서비스. */
    private static final Pattern HOSTS = Pattern.compile("^(fcm\\.googleapis\\.com|android\\.googleapis\\.com|updates\\.push\\.services\\.mozilla\\.com|push\\.services\\.mozilla\\.com|[a-z0-9-]+\\.push\\.apple\\.com|web\\.push\\.apple\\.com|[a-z0-9-]+\\.notify\\.windows\\.com)$");
    static final Duration TTL = Duration.ofHours(20);

    public enum Result { ACCEPTED, GONE, RETRY, REJECTED }
    public record Target(String endpoint, String p256dh, String auth) { }

    /** 테스트에서 실제 푸시 서비스로 보내지 않도록 HTTP 전송만 바꿔 끼운다. */
    public interface Transport { int post(URI uri, Map<String, String> headers, byte[] body) throws IOException, InterruptedException; }

    private final ECPrivateKey privateKey; private final String publicKey; private final String subject;
    private final Transport transport; private final Clock clock;

    @Autowired
    public WebPushSender(@Value("${toady.push.vapid-public-key:}") String publicKey, @Value("${toady.push.vapid-private-key:}") String privateKey,
            @Value("${toady.push.subject:}") String subject, @Value("${toady.web.domain}") String domain) {
        this(publicKey, privateKey, subject.isBlank() ? domain : subject, httpTransport(), Clock.systemUTC());
    }
    WebPushSender(String publicKey, String privateKey, String subject, Transport transport, Clock clock) {
        ECPrivateKey loaded = null; String normalized = null;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            try {
                ECPublicKey pub = WebPushCrypto.publicKey(WebPushCrypto.b64(publicKey));
                ECPrivateKey key = WebPushCrypto.privateKey(WebPushCrypto.b64(privateKey));
                if (WebPushCrypto.matches(key, pub)) { loaded = key; normalized = WebPushCrypto.b64(WebPushCrypto.uncompressed(pub)); }
                else log.warn("웹 푸시 VAPID 공개키와 개인키가 서로 맞지 않아 푸시를 끕니다.");
            } catch (GeneralSecurityException | IllegalArgumentException invalid) {
                log.warn("웹 푸시 VAPID 키 형식이 올바르지 않아 푸시를 끕니다: {}", invalid.getClass().getSimpleName());
            }
        }
        this.privateKey = loaded; this.publicKey = normalized; this.transport = transport; this.clock = clock;
        this.subject = subject.startsWith("mailto:") || subject.startsWith("https://") ? subject : "https://www.toady.co.kr";
    }

    public boolean enabled() { return privateKey != null; }
    public String publicKey() { return publicKey; }

    /** https 기본 포트의 알려진 푸시 서비스 주소인지 확인한다. */
    public static boolean allowedEndpoint(String endpoint) {
        if (endpoint == null || endpoint.length() > 1000) return false;
        try {
            URI uri = new URI(endpoint);
            return "https".equals(uri.getScheme()) && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getHost() != null && HOSTS.matcher(uri.getHost().toLowerCase(Locale.ROOT)).matches();
        } catch (Exception invalid) { return false; }
    }

    public Result send(Target target, String json, String topic) {
        if (!enabled() || !allowedEndpoint(target.endpoint())) return Result.REJECTED;
        try {
            URI uri = URI.create(target.endpoint());
            byte[] body = WebPushCrypto.encrypt(json.getBytes(StandardCharsets.UTF_8), WebPushCrypto.b64(target.p256dh()), WebPushCrypto.b64(target.auth()));
            String audience = uri.getScheme() + "://" + uri.getHost();
            String jwt = WebPushCrypto.vapidJwt(audience, subject, clock.instant().plus(Duration.ofHours(12)).getEpochSecond(), privateKey);
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("Authorization", "vapid t=" + jwt + ", k=" + publicKey);
            headers.put("Content-Encoding", "aes128gcm");
            headers.put("Content-Type", "application/octet-stream");
            headers.put("TTL", String.valueOf(TTL.toSeconds()));
            headers.put("Urgency", "normal");
            // 같은 Topic의 미전달 메시지는 새 메시지로 교체되어 재시도해도 쌓이지 않는다(영문·숫자 32자 이하).
            if (topic != null && topic.matches("[A-Za-z0-9_-]{1,32}")) headers.put("Topic", topic);
            int status = transport.post(uri, headers, body);
            if (status >= 200 && status < 300) return Result.ACCEPTED;
            if (status == 404 || status == 410) return Result.GONE;
            if (status == 429 || status >= 500) return Result.RETRY;
            log.warn("웹 푸시 발송 거절: host={}, status={}", uri.getHost(), status);
            return Result.REJECTED;
        } catch (GeneralSecurityException | IllegalArgumentException invalid) {
            // 브라우저가 보낸 키가 손상된 구독은 다시 보내도 실패한다.
            return Result.GONE;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); return Result.RETRY;
        } catch (IOException network) {
            return Result.RETRY;
        }
    }

    static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        return (uri, headers, body) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofByteArray(body));
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        };
    }
}
