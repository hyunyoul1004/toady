package com.toady.web.core.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.SetArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 외부 API 결과(국토부 실거래·카카오 주변 시설)를 Redis에 보관하는 2단 캐시의 두 번째 단.
 * 각 서비스의 메모리 캐시가 먼저이고, 메모리에 없을 때 여기서 찾는다. 배포·재시작 뒤에도 남아 같은 조회로
 * 외부 호출 한도를 다시 쓰지 않게 한다. Redis가 없거나 실패하면 null(없음)로 처리해 원래대로 외부를 부른다.
 * 값은 JSON을 gzip으로 줄여 저장한다(한 달 실거래가 수백 KB까지 나온다).
 */
@Slf4j
@Component
public class SharedCache {
    /**
     * 압축 후 이보다 큰 값은 Redis에 두지 않는다(메모리 캐시만 쓴다). Redis 96MB를 요청 제한·로그인 실패 기록과 함께 쓰므로,
     * 큰 캐시 값이 메모리를 채워 LRU가 보안 기록을 지우는 일을 줄인다.
     */
    static final int MAX_STORED_CHARS = 256 * 1024;
    private static final ObjectMapper JSON = JsonMapper.builder().addModule(new JavaTimeModule()).build();
    private final RedisStore redis;

    @Autowired
    public SharedCache(RedisStore redis) { this.redis = redis; }

    /** 메모리 캐시만 쓰는 서비스(테스트·기본 생성자)용. */
    public static SharedCache none() { return new SharedCache(RedisStore.disabled()); }

    private static String key(String key) { return RedisStore.PREFIX + "cache:" + key; }

    /** 없거나 읽지 못하면 null. */
    public <T> T get(String key, Class<T> type) {
        if (!redis.available()) return null;
        String stored = redis.call(c -> c.get(key(key))).orElse(null);
        if (stored == null) return null;
        try {
            return JSON.readValue(new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(stored))), type);
        } catch (IOException | IllegalArgumentException unreadable) {
            // 형식이 바뀐 이전 값 등은 버리고 다시 받는다.
            log.debug("Dropping unreadable shared cache value {}: {}", key, unreadable.getClass().getSimpleName());
            redis.call(c -> c.del(key(key)));
            return null;
        }
    }

    public void put(String key, Object value, Duration ttl) {
        if (value == null || !redis.available() || ttl.isNegative() || ttl.isZero()) return;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream zip = new GZIPOutputStream(bytes)) { JSON.writeValue(zip, value); }
            String encoded = Base64.getEncoder().encodeToString(bytes.toByteArray());
            if (encoded.length() > MAX_STORED_CHARS) return;
            redis.call(c -> c.set(key(key), encoded, SetArgs.Builder.px(ttl.toMillis())));
        } catch (IOException unwritable) {
            log.debug("Skipping shared cache write {}: {}", key, unwritable.getClass().getSimpleName());
        }
    }
}
