package com.toady.web.core.redis;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 요청 제한·로그인 실패에 쓰는 '최근 N분 안의 기록' 저장소.
 * Redis가 있으면 키마다 정렬 집합(점수=시각)에 기록해 재시작·배포 뒤에도 유지하고 API 여러 대가 함께 센다.
 * Redis가 없거나 응답하지 않으면 같은 동작을 메모리에서 한다(그동안의 기록은 Redis와 합치지 않는다).
 */
@Component
public class SlidingWindows {
    /**
     * 오래된 기록을 지운 뒤 창마다 한도를 확인하고, 모두 여유가 있을 때만 이번 기록을 더한다(한 번에 실행되어 동시 요청에도 정확).
     * ARGV: 지금(ms), 보관 기간(ms), 이번 기록 이름, 그 뒤로 (창 ms, 한도) 쌍.
     */
    static final String TRY_HIT = """
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - tonumber(ARGV[2]))
            for i = 4, #ARGV, 2 do
              if redis.call('ZCOUNT', KEYS[1], '(' .. (now - tonumber(ARGV[i])), '+inf') >= tonumber(ARGV[i + 1]) then return 0 end
            end
            redis.call('ZADD', KEYS[1], now, ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """;
    /** 오래된 기록을 지우고 창 안의 개수를 센다. ARGV: 지금, 창(ms). */
    static final String COUNT = """
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - tonumber(ARGV[2]))
            return redis.call('ZCARD', KEYS[1])
            """;

    private final RedisStore redis;
    private final Clock clock;
    private final Cache<String, Deque<Long>> memory = CacheBuilder.newBuilder()
            .maximumSize(200_000).expireAfterAccess(1, TimeUnit.DAYS).build();
    private final Cache<String, Boolean> marks = CacheBuilder.newBuilder()
            .maximumSize(200_000).expireAfterWrite(30, TimeUnit.DAYS).build();
    /** 같은 밀리초에 들어온 기록도 서로 다른 이름이 되게 한다. */
    private final AtomicLong sequence = new AtomicLong();
    private final String instance = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    public SlidingWindows(RedisStore redis) { this(redis, Clock.systemUTC()); }
    public SlidingWindows(RedisStore redis, Clock clock) { this.redis = redis; this.clock = clock; }

    /** Redis 없이 메모리로만 동작한다(단위 테스트·기본 생성자). */
    public static SlidingWindows inMemory(Clock clock) { return new SlidingWindows(RedisStore.disabled(), clock); }

    public record Limit(Duration window, int max) { }

    private static String key(String key) { return RedisStore.PREFIX + "win:" + key; }
    private String member(long now) { return now + ":" + instance + ":" + sequence.incrementAndGet(); }

    /** 창마다 한도 안이면 기록하고 true. 하나라도 차 있으면 기록하지 않고 false. retention은 가장 긴 창 이상이어야 한다. */
    public boolean tryHit(String key, Duration retention, List<Limit> limits) {
        long now = clock.millis();
        if (redis.available()) {
            List<String> args = new ArrayList<>(List.of(Long.toString(now), Long.toString(retention.toMillis()), member(now)));
            for (Limit limit : limits) { args.add(Long.toString(limit.window().toMillis())); args.add(Integer.toString(limit.max())); }
            var result = redis.call(c -> c.<Long>eval(TRY_HIT, ScriptOutputType.INTEGER, new String[]{key(key)}, args.toArray(String[]::new)));
            if (result.isPresent()) return result.get() == 1L;
        }
        Deque<Long> times = deque(key);
        synchronized (times) {
            trim(times, now - retention.toMillis());
            for (Limit limit : limits) {
                long from = now - limit.window().toMillis();
                if (times.stream().filter(time -> time > from).count() >= limit.max()) return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** 창 안의 기록 수(창보다 오래된 기록은 지운다). */
    public int count(String key, Duration window) {
        long now = clock.millis();
        if (redis.available()) {
            var result = redis.call(c -> c.<Long>eval(COUNT, ScriptOutputType.INTEGER, new String[]{key(key)}, Long.toString(now), Long.toString(window.toMillis())));
            if (result.isPresent()) return result.get().intValue();
        }
        Deque<Long> times = memory.getIfPresent(key);
        if (times == null) return 0;
        synchronized (times) {
            trim(times, now - window.toMillis());
            return times.size();
        }
    }

    /** 기록 하나를 더한다. retention 동안 새 기록이 없으면 키가 사라진다. */
    public void add(String key, Duration retention) {
        long now = clock.millis();
        if (redis.available() && redis.call(c -> {
            c.zadd(key(key), now, member(now));
            return c.pexpire(key(key), retention.toMillis());
        }).isPresent()) return;
        Deque<Long> times = deque(key);
        synchronized (times) { times.addLast(now); }
    }

    /** 가장 최근 기록 하나를 지운다(미리 센 시도를 되돌릴 때). */
    public void removeLast(String key) {
        if (redis.available() && redis.call(c -> c.zpopmax(key(key))).isPresent()) return;
        Deque<Long> times = memory.getIfPresent(key);
        if (times != null) synchronized (times) { times.pollLast(); }
    }

    public void clear(String key) {
        if (redis.available() && redis.call(c -> c.del(key(key))).isPresent()) return;
        memory.invalidate(key);
    }

    /** 일정 기간 기억할 표시(예: 로그인에 성공한 네트워크). */
    public void mark(String key, Duration ttl) {
        if (redis.available() && redis.call(c -> c.set(RedisStore.PREFIX + "mark:" + key, "1", SetArgs.Builder.px(ttl.toMillis()))).isPresent()) return;
        marks.put(key, Boolean.TRUE);
    }

    public boolean marked(String key) {
        if (redis.available()) {
            var result = redis.call(c -> c.exists(RedisStore.PREFIX + "mark:" + key));
            if (result.isPresent()) return result.get() > 0;
        }
        return marks.getIfPresent(key) != null;
    }

    private Deque<Long> deque(String key) {
        try { return memory.get(key, ArrayDeque::new); } catch (ExecutionException impossible) { return new ArrayDeque<>(); }
    }
    private static void trim(Deque<Long> times, long olderOrEqual) {
        while (!times.isEmpty() && times.peekFirst() <= olderOrEqual) times.pollFirst();
    }
}
