package com.toady.web.core.redis;

import com.toady.web.app.planner.research.NearbyPlaces;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis에서 요청 제한 스크립트·공유 캐시를 검사한다. CI는 Redis 서비스를 띄워 TOADY_TEST_REDIS_HOST를 넣는다
 * (로컬에 Redis가 없으면 건너뛴다).
 */
@EnabledIfEnvironmentVariable(named = "TOADY_TEST_REDIS_HOST", matches = ".+")
class RedisIntegrationTest {
    final RedisStore redis = new RedisStore(System.getenv("TOADY_TEST_REDIS_HOST"),
            Integer.parseInt(System.getenv().getOrDefault("TOADY_TEST_REDIS_PORT", "6379")), System.getenv().getOrDefault("TOADY_TEST_REDIS_PASSWORD", ""));
    final String run = "test-" + UUID.randomUUID() + ":";

    @AfterEach
    void close() {
        redis.call(c -> { var keys = c.keys(RedisStore.PREFIX + "*" + run + "*"); return keys.isEmpty() ? 0L : c.del(keys.toArray(String[]::new)); });
        redis.close();
    }

    @Test
    void redisWindowsBehaveLikeMemoryAndAreSharedBetweenInstances() {
        assertThat(redis.status()).isEqualTo("UP");
        AtomicLong now = new AtomicLong(1_000_000);
        SlidingWindowsTest.exercise(new SlidingWindows(redis, SlidingWindowsTest.ticking(now)), now, run);

        // API 두 대(같은 Redis)가 같은 키를 함께 센다. 재시작한 API도 이전 기록을 이어 받는다.
        var first = new SlidingWindows(redis, SlidingWindowsTest.ticking(now));
        var second = new SlidingWindows(redis, SlidingWindowsTest.ticking(now));
        var limits = List.of(new SlidingWindows.Limit(Duration.ofMinutes(10), 2));
        assertThat(first.tryHit(run + "shared", Duration.ofDays(1), limits)).isTrue();
        assertThat(second.tryHit(run + "shared", Duration.ofDays(1), limits)).isTrue();
        assertThat(first.tryHit(run + "shared", Duration.ofDays(1), limits)).isFalse();
        assertThat(redis.call(c -> c.pttl(RedisStore.PREFIX + "win:" + run + "shared")).orElse(-1L)).isPositive();
    }

    /** 실거래 한 달처럼 패키지 안에서만 보이는 레코드도 저장·복원된다. */
    record Month(List<Row> rows, Instant fetchedAt) { }
    record Row(String name, BigDecimal area, LocalDate date, long amount) { }

    @Test
    void sharedCacheRoundTripsRecordsAndExpires() throws Exception {
        var cache = new SharedCache(redis);
        var month = new Month(List.of(new Row("단지", new BigDecimal("84.97"), LocalDate.of(2026, 11, 1), 52000)), Instant.parse("2026-11-17T00:00:00Z"));
        cache.put(run + "month", month, Duration.ofMinutes(5));
        assertThat(cache.get(run + "month", Month.class)).isEqualTo(month);

        var places = new NearbyPlaces.Result("READY", 1000, List.of(new NearbyPlaces.Place("SCHOOL", "초등학교", 320, 37.5, 127.0)));
        cache.put(run + "nearby", places, Duration.ofMillis(300));
        assertThat(cache.get(run + "nearby", NearbyPlaces.Result.class)).isEqualTo(places);
        Thread.sleep(600);
        assertThat(cache.get(run + "nearby", NearbyPlaces.Result.class)).isNull();

        // 읽을 수 없는 값은 버리고 없음으로 처리한다.
        redis.call(c -> c.set(RedisStore.PREFIX + "cache:" + run + "broken", "not-gzip"));
        assertThat(cache.get(run + "broken", Month.class)).isNull();
        assertThat(redis.call(c -> c.exists(RedisStore.PREFIX + "cache:" + run + "broken")).orElse(1L)).isZero();
    }
}
