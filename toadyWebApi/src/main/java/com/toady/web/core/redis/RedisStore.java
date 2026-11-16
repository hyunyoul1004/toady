package com.toady.web.core.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Function;

/**
 * Redis 연결. 호스트가 비어 있으면 꺼진 상태로, 모든 호출이 빈 결과를 돌려 호출하는 쪽이 메모리로 처리한다.
 * Redis가 응답하지 않으면 사이트를 막지 않도록 짧은 시간 안에 포기하고 30초 동안 Redis를 건너뛴다(차단기).
 * 다시 붙으면 자동으로 Redis를 쓴다. 그 사이 메모리에 쌓인 기록은 합치지 않는다(요청 제한이 잠깐 느슨해질 뿐).
 */
@Slf4j
@Component
public class RedisStore {
    static final Duration COMMAND_TIMEOUT = Duration.ofMillis(300);
    static final Duration PAUSE_AFTER_FAILURE = Duration.ofSeconds(30);
    /** 키 앞에 붙여 다른 용도와 섞이지 않게 한다. */
    public static final String PREFIX = "toady:";

    private final RedisClient client;
    private volatile StatefulRedisConnection<String, String> connection;
    private volatile long pausedUntil;
    private volatile boolean downLogged;

    @Autowired
    public RedisStore(@Value("${toady.redis.host:}") String host, @Value("${toady.redis.port:6379}") int port,
                      @Value("${toady.redis.password:}") String password) {
        if (host == null || host.isBlank()) {
            client = null;
            log.info("Redis is not configured (REDIS_HOST empty); rate limits and caches stay in memory");
            return;
        }
        RedisURI.Builder uri = RedisURI.builder().withHost(host.trim()).withPort(port).withTimeout(COMMAND_TIMEOUT);
        if (password != null && !password.isBlank()) uri.withPassword(password.toCharArray());
        client = RedisClient.create(uri.build());
        client.setOptions(io.lettuce.core.ClientOptions.builder()
                .socketOptions(io.lettuce.core.SocketOptions.builder().connectTimeout(Duration.ofSeconds(1)).build())
                // 끊긴 동안 명령을 쌓아 두지 않는다(쌓이면 다시 붙을 때 오래된 요청 제한 기록이 한꺼번에 들어간다).
                .disconnectedBehavior(io.lettuce.core.ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build());
    }

    /** 테스트용: Redis 없이 메모리로만 동작한다. */
    public static RedisStore disabled() { return new RedisStore("", 6379, ""); }

    public boolean configured() { return client != null; }

    /** 상태 확인용: OFF(설정 없음) · UP · DOWN. */
    public String status() {
        if (client == null) return "OFF";
        return call(commands -> commands.ping()).filter("PONG"::equals).isPresent() ? "UP" : "DOWN";
    }

    /**
     * Redis 명령을 실행한다. 꺼져 있거나 실패하면 빈 값을 돌려주고, 호출한 쪽은 메모리 동작으로 대신한다.
     * 결과가 null인 명령(없는 키 GET 등)도 빈 값이므로, 실패와 구분이 필요하면 {@link #available()}로 먼저 확인한다.
     */
    public <T> Optional<T> call(Function<RedisCommands<String, String>, T> command) {
        if (!available()) return Optional.empty();
        try {
            T result = command.apply(connect().sync());
            if (downLogged) { downLogged = false; log.info("Redis is reachable again"); }
            return Optional.ofNullable(result);
        } catch (RuntimeException failure) {
            pausedUntil = System.currentTimeMillis() + PAUSE_AFTER_FAILURE.toMillis();
            if (!downLogged) {
                downLogged = true;
                log.warn("Redis call failed; using in-memory fallback for {}s: {}", PAUSE_AFTER_FAILURE.toSeconds(), failure.getClass().getSimpleName());
            }
            return Optional.empty();
        }
    }

    /** 설정돼 있고 최근 실패로 쉬는 중이 아닌지. */
    public boolean available() { return client != null && System.currentTimeMillis() >= pausedUntil; }

    private StatefulRedisConnection<String, String> connect() {
        var current = connection;
        if (current != null && current.isOpen()) return current;
        synchronized (this) {
            if (connection == null || !connection.isOpen()) {
                if (connection != null) connection.closeAsync();
                connection = client.connect();
                connection.setTimeout(COMMAND_TIMEOUT);
            }
            return connection;
        }
    }

    @PreDestroy
    public void close() {
        if (connection != null) connection.close();
        if (client != null) client.shutdown(Duration.ZERO, Duration.ofSeconds(2));
    }
}
