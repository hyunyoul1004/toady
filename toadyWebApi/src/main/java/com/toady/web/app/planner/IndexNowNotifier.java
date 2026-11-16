package com.toady.web.app.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.core.redis.RedisStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 새 공고 페이지를 IndexNow로 검색엔진에 바로 알린다(Bing 등 IndexNow 참여 검색엔진이 서로 공유한다).
 * RSS와 같은 최근 공고 주소 중 아직 알리지 않은 것만 모아 한 번에 보낸다. 알린 주소는 Redis(없으면 메모리)에 60일 기억한다.
 * 소유 확인 키 파일은 웹 앱의 public/{key}.txt로 www.toady.co.kr 루트에 올라가 있다. 키는 공개 값이다.
 * 운영 주소(https://www.toady.co.kr)일 때만 보낸다(로컬·테스트 주소는 검색엔진에 알릴 수 없다).
 */
@Component
public class IndexNowNotifier {
    private static final Logger log = LoggerFactory.getLogger(IndexNowNotifier.class);
    static final URI ENDPOINT = URI.create("https://api.indexnow.org/indexnow");
    static final String PRODUCTION = "https://www.toady.co.kr";
    static final Duration REMEMBER = Duration.ofDays(60);
    private static final String SENT_KEY = RedisStore.PREFIX + "indexnow:sent";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 실제 전송(테스트에서 바꾼다). 응답 상태 코드를 돌려준다. */
    interface Sender { int post(URI endpoint, String json) throws IOException, InterruptedException; }

    private final AnnouncementFeed feed;
    private final RedisStore redis;
    private final String key;
    private final String domain;
    private final boolean enabled;
    private final Sender sender;
    private final Clock clock;
    /** Redis가 없거나 멈췄을 때 쓰는 기록(주소 → 알린 시각). */
    private final Map<String, Long> sentInMemory = new ConcurrentHashMap<>();

    @Autowired
    public IndexNowNotifier(AnnouncementFeed feed, RedisStore redis, @Value("${toady.indexnow.key:}") String key,
                            @Value("${toady.web.domain}") String domain, @Value("${toady.indexnow.enabled:false}") boolean enabled) {
        this(feed, redis, key, domain, enabled, IndexNowNotifier::httpPost, Clock.systemUTC());
    }

    IndexNowNotifier(AnnouncementFeed feed, RedisStore redis, String key, String domain, boolean enabled, Sender sender, Clock clock) {
        this.feed = feed; this.redis = redis; this.key = key == null ? "" : key.trim();
        this.domain = domain.replaceAll("/+$", ""); this.sender = sender; this.clock = clock;
        // 키는 8~128자 영숫자·하이픈(IndexNow 규격). 운영 주소가 아니면 보내지 않는다.
        this.enabled = enabled && this.key.matches("[A-Za-z0-9-]{8,128}") && PRODUCTION.equals(this.domain);
    }

    /** 새 공고가 들어오는 수집 시각(03:15, 04:15, 20:15) 뒤와 낮 동안 한 시간마다 확인한다. */
    @Scheduled(cron = "${toady.indexnow.cron:0 40 * * * *}", zone = "Asia/Seoul")
    public void notifyNewAnnouncements() {
        if (!enabled) return;
        try {
            int sent = submitPending();
            if (sent > 0) log.info("IndexNow: 새 공고 주소 {}개를 알렸습니다", sent);
        } catch (RuntimeException failure) {
            log.warn("IndexNow 알림을 건너뜁니다: {}", failure.getMessage());
        }
    }

    /** 아직 알리지 않은 최근 공고 주소를 보내고, 받아들여지면(200·202) 알린 것으로 기록한다. 보낸 개수를 돌려준다. */
    int submitPending() {
        if (!enabled) return 0;
        Set<String> sent = sentUrls();
        List<String> pending = new ArrayList<>();
        for (String url : feed.recentUrls()) if (!sent.contains(url)) pending.add(url);
        if (pending.isEmpty()) return 0;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("host", URI.create(domain).getHost());
        body.put("key", key);
        body.put("keyLocation", domain + "/" + key + ".txt");
        body.put("urlList", pending);
        int status;
        try {
            status = sender.post(ENDPOINT, JSON.writeValueAsString(body));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException failure) {
            log.warn("IndexNow 전송 실패(다음에 다시 보냄): {}", failure.getClass().getSimpleName());
            return 0;
        }
        if (status != 200 && status != 202) {
            // 403: 키 파일을 못 찾음, 422: 주소가 키의 host와 다름, 429: 너무 자주 보냄. 기록하지 않고 다음에 다시 보낸다.
            log.warn("IndexNow가 요청을 받지 않았습니다(HTTP {}). 다음에 다시 보냅니다", status);
            return 0;
        }
        remember(pending);
        return pending.size();
    }

    private Set<String> sentUrls() {
        long now = clock.millis();
        long oldest = now - REMEMBER.toMillis();
        if (redis.available()) {
            var fromRedis = redis.call(c -> {
                c.zremrangebyscore(SENT_KEY, io.lettuce.core.Range.create(0, oldest));
                return Set.copyOf(c.zrange(SENT_KEY, 0, -1));
            });
            if (fromRedis.isPresent()) return fromRedis.get();
        }
        sentInMemory.values().removeIf(at -> at < oldest);
        return Set.copyOf(sentInMemory.keySet());
    }

    private void remember(List<String> urls) {
        long now = clock.millis();
        urls.forEach(url -> sentInMemory.put(url, now));
        if (redis.available()) redis.call(c -> {
            for (String url : urls) c.zadd(SENT_KEY, now, url);
            return c.pexpire(SENT_KEY, REMEMBER.toMillis());
        });
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    /** 정해진 IndexNow 주소로만 보낸다(리다이렉트를 따르지 않고 10초 안에 끝낸다). */
    static int httpPost(URI endpoint, String json) throws IOException, InterruptedException {
        if (!ENDPOINT.equals(endpoint)) throw new IOException("Unsupported endpoint");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
