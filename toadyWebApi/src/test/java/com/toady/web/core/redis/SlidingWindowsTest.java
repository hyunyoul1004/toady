package com.toady.web.core.redis;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 메모리 동작과, Redis가 응답하지 않을 때 메모리로 대신하는 동작을 검사한다(실제 Redis 검사는 RedisIntegrationTest). */
class SlidingWindowsTest {
    static Clock ticking(AtomicLong millis) {
        return new Clock() {
            @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public Instant instant() { return Instant.ofEpochMilli(millis.get()); }
        };
    }

    static void exercise(SlidingWindows windows, AtomicLong now, String prefix) {
        var limits = List.of(new SlidingWindows.Limit(Duration.ofMinutes(10), 2), new SlidingWindows.Limit(Duration.ofDays(1), 3));
        String key = prefix + "hit";
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).isTrue();
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).isTrue();
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).as("10분 한도").isFalse();
        now.addAndGet(Duration.ofMinutes(11).toMillis());
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).isTrue();
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).as("하루 한도").isFalse();
        now.addAndGet(Duration.ofDays(1).toMillis());
        assertThat(windows.tryHit(key, Duration.ofDays(1), limits)).as("하루가 지나면 다시 허용").isTrue();

        String login = prefix + "login";
        windows.add(login, Duration.ofMinutes(15));
        windows.add(login, Duration.ofMinutes(15));
        assertThat(windows.count(login, Duration.ofMinutes(15))).isEqualTo(2);
        windows.removeLast(login);
        assertThat(windows.count(login, Duration.ofMinutes(15))).isEqualTo(1);
        now.addAndGet(Duration.ofMinutes(16).toMillis());
        assertThat(windows.count(login, Duration.ofMinutes(15))).as("창이 지나면 0").isZero();
        windows.add(login, Duration.ofMinutes(15));
        windows.clear(login);
        assertThat(windows.count(login, Duration.ofMinutes(15))).isZero();

        assertThat(windows.marked(prefix + "trusted")).isFalse();
        windows.mark(prefix + "trusted", Duration.ofDays(30));
        assertThat(windows.marked(prefix + "trusted")).isTrue();
    }

    @Test
    void memoryWindowsCountAndExpire() {
        AtomicLong now = new AtomicLong(1_000_000);
        exercise(SlidingWindows.inMemory(ticking(now)), now, "");
    }

    @Test
    void unreachableRedisFallsBackToMemoryWithoutFailingRequests() {
        // 포트 1에는 아무것도 없다: 연결이 바로 거절되고 이후 30초 동안 Redis를 건너뛴다.
        var redis = new RedisStore("127.0.0.1", 1, "");
        try {
            AtomicLong now = new AtomicLong(1_000_000);
            exercise(new SlidingWindows(redis, ticking(now)), now, "");
            assertThat(redis.available()).isFalse();
            assertThat(redis.status()).isEqualTo("DOWN");
            assertThat(new SharedCache(redis).get("anything", String.class)).isNull();
        } finally {
            redis.close();
        }
    }

    @Test
    void disabledRedisReportsOff() {
        assertThat(RedisStore.disabled().status()).isEqualTo("OFF");
        assertThat(SharedCache.none().get("anything", String.class)).isNull();
    }
}
