package com.toady.web.app.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.core.redis.RedisStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 새 공고 주소만 한 번씩 IndexNow로 보내고, 받아들여지지 않으면 다음에 다시 보내는지 확인한다. */
class IndexNowNotifierTest {
    static final String KEY = "8ec7088feb88a7a3bb1a59bb4c34ebaf";
    final AnnouncementFeed feed = mock(AnnouncementFeed.class);
    final List<String> bodies = new ArrayList<>();
    final AtomicInteger status = new AtomicInteger(200);

    IndexNowNotifier notifier(String domain, boolean enabled) {
        return new IndexNowNotifier(feed, RedisStore.disabled(), KEY, domain, enabled, (endpoint, json) -> {
            assertThat(endpoint).isEqualTo(IndexNowNotifier.ENDPOINT);
            bodies.add(json);
            return status.get();
        }, Clock.fixed(Instant.parse("2026-11-17T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void sendsOnlyNewAnnouncementUrlsOnce() throws Exception {
        String a = "https://www.toady.co.kr/announcements/apt/2026000001/2026000001";
        String b = "https://www.toady.co.kr/announcements/urban/2026000002/2026000002";
        when(feed.recentUrls()).thenReturn(List.of(a));
        var notifier = notifier("https://www.toady.co.kr/", true);

        assertThat(notifier.submitPending()).isEqualTo(1);
        var body = new ObjectMapper().readTree(bodies.get(0));
        assertThat(body.get("host").asText()).isEqualTo("www.toady.co.kr");
        assertThat(body.get("key").asText()).isEqualTo(KEY);
        assertThat(body.get("keyLocation").asText()).isEqualTo("https://www.toady.co.kr/" + KEY + ".txt");
        assertThat(body.get("urlList").get(0).asText()).isEqualTo(a);

        // 이미 알린 주소는 다시 보내지 않고, 새로 생긴 주소만 보낸다.
        assertThat(notifier.submitPending()).isZero();
        when(feed.recentUrls()).thenReturn(List.of(b, a));
        assertThat(notifier.submitPending()).isEqualTo(1);
        assertThat(new ObjectMapper().readTree(bodies.get(1)).get("urlList")).hasSize(1);
        assertThat(bodies).hasSize(2);
    }

    @Test
    void rejectedSubmissionsAreRetriedNextTime() {
        when(feed.recentUrls()).thenReturn(List.of("https://www.toady.co.kr/announcements/apt/1/1"));
        var notifier = notifier("https://www.toady.co.kr", true);
        status.set(429);
        assertThat(notifier.submitPending()).isZero();
        status.set(202);
        assertThat(notifier.submitPending()).isEqualTo(1);
        assertThat(bodies).hasSize(2);
    }

    @Test
    void staysOffOutsideProductionOrWhenDisabled() {
        when(feed.recentUrls()).thenReturn(List.of("http://localhost:3000/announcements/apt/1/1"));
        assertThat(notifier("http://localhost:3000", true).submitPending()).isZero();
        assertThat(notifier("https://www.toady.co.kr", false).submitPending()).isZero();
        assertThat(bodies).isEmpty();
    }

    @Test
    void keyFileInTheWebAppMatchesTheConfiguredKey() throws Exception {
        var file = java.nio.file.Path.of("..", "toadyReactWeb", "public", KEY + ".txt");
        assertThat(java.nio.file.Files.readString(file).strip()).isEqualTo(KEY);
        assertThat(Duration.ofDays(60)).isEqualTo(IndexNowNotifier.REMEMBER);
    }
}
