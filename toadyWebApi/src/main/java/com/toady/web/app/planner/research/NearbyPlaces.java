package com.toady.web.app.planner.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.core.redis.SharedCache;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

/**
 * 위치 탭의 '가까운 시설' 요약: 공고 위치에서 가장 가까운 지하철역·초등학교·마트(카카오 로컬 카테고리 검색 MT1: 대형마트·슈퍼 포함).
 * 카카오 REST 키는 서버에만 둔다(AddressLocator와 같은 키·같은 전송 방식). 거리는 카카오가 돌려주는 직선거리(m)다.
 * 같은 지점(소수 넷째 자리, 약 10m)은 하루 동안 기억해 외부 호출을 줄인다.
 * 캐시는 2단이다: 1단 메모리(Guava Cache, 이 서버 안에서만) → 2단 Redis(SharedCache, 재시작·배포 뒤에도 남음) → 둘 다 없으면 카카오 호출.
 * 카카오 세 번 호출은 전용 스레드에서 동시에 하고 요청 스레드는 최대 BUDGET(4초)만 기다린다(SaleMapService와 같은 방식).
 * 실패·시간 초과는 같은 지점을 FAILURE_TTL(2분) 동안 기억해, 카카오 장애 때 요청마다 오래 붙잡히지 않고 바로 UNAVAILABLE을 준다.
 * 안쪽 Api 클래스가 /api/v1/research/nearby 요청을 받아 nearest()를 부른다.
 */
// @Slf4j(Lombok): 'log'라는 로거 필드를 자동으로 만들어 준다.
// @Service: 스프링 빈으로 등록한다.
@Slf4j
@Service
public class NearbyPlaces {
    /** kind: STATION(지하철역) · SCHOOL(초등학교) · MART(대형마트). distance: 직선거리(m). */
    public record Place(String kind, String name, int distance, double lat, double lng) { }
    /** status: READY · NOT_CONFIGURED(키 없음) · UNAVAILABLE(제공처 오류). radius: 찾아본 반경(m). */
    public record Result(String status, int radius, List<Place> places) { }

    static final int RADIUS = 3000;
    /** 카테고리 코드와 이름 조건. 학교(SC4)는 초등학교만 쓴다. */
    record Category(String kind, String code, String nameSuffix) { }
    static final List<Category> CATEGORIES = List.of(
        new Category("STATION", "SW8", null), new Category("SCHOOL", "SC4", "초등학교"), new Category("MART", "MT1", null));

    private final ResearchTransport transport;
    private final String key;
    private final ObjectMapper json = new ObjectMapper();
    private final Cache<String, Result> cache = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(1, TimeUnit.DAYS).build();
    /** 요청 하나가 카카오를 기다리는 최대 시간과 실패를 기억하는 시간. */
    static final java.time.Duration BUDGET = java.time.Duration.ofSeconds(4), FAILURE_TTL = java.time.Duration.ofMinutes(2);
    private final Cache<String, Boolean> failed = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(FAILURE_TTL).build();
    private final long budgetNanos;
    /** 카카오 호출 전용 스레드. 큐가 차면 거절되고 그 요청은 UNAVAILABLE이 된다(요청 스레드가 줄줄이 묶이지 않게). */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(6, 6, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(60), task -> {
        Thread thread = new Thread(task, "nearby-places"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    /** 메모리 다음 단의 공유 캐시(Redis). 배포 뒤에도 같은 지점의 카카오 호출(요청당 3회)을 아낀다. */
    private SharedCache shared = SharedCache.none();
    // @Autowired(required=false) 메서드 주입: SharedCache 빈이 있으면 넣고, 없으면 기본값(none, 아무것도 저장 안 함)을 그대로 쓴다.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void useSharedCache(SharedCache shared) { this.shared = shared; }

    // @Value: 카카오 REST 키(toady.research.kakao-rest-key)를 설정에서 읽는다. 없으면 빈 문자열이고 기능이 꺼진다.
    @org.springframework.beans.factory.annotation.Autowired
    public NearbyPlaces(ResearchTransport transport, @Value("${toady.research.kakao-rest-key:}") String key) { this(transport, key, BUDGET); }
    /** 테스트에서 기다리는 시간을 줄일 때 쓴다. */
    NearbyPlaces(ResearchTransport transport, String key, java.time.Duration budget) {
        this.transport = transport; this.key = key == null ? "" : key.trim(); this.budgetNanos = budget.toNanos();
        pool.allowCoreThreadTimeOut(true);
    }
    // @PreDestroy: 서버가 꺼질 때 호출 스레드를 정리한다.
    @jakarta.annotation.PreDestroy void close() { pool.shutdownNow(); }

    /**
     * 좌표를 소수 넷째 자리로 반올림해 캐시 키로 쓰고, 메모리 → Redis → 카카오 순으로 찾는다.
     * 카카오는 카테고리마다 한 번씩(지하철역·학교·마트, 총 3번) 동시에 부르고 모두 합쳐 BUDGET 안에 끝나야 한다.
     * 하나라도 실패하거나 시간을 넘기면 UNAVAILABLE을 돌려주고, 그 지점은 FAILURE_TTL 동안 다시 부르지 않는다(성공 캐시에는 넣지 않는다).
     */
    public Result nearest(double lat, double lng) {
        if (key.isBlank()) return new Result("NOT_CONFIGURED", RADIUS, List.of());
        double y = Math.round(lat * 10000) / 10000.0, x = Math.round(lng * 10000) / 10000.0;
        String cacheKey = y + "," + x;
        Result cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        cached = shared.get("nearby:" + cacheKey, Result.class);
        if (cached != null) { cache.put(cacheKey, cached); return cached; }
        if (failed.getIfPresent(cacheKey) != null) return unavailable();
        List<Future<Optional<Place>>> calls = new ArrayList<>();
        // 각 호출이 실제로 스레드에서 시작했는지. 대기열에서만 기다리다 시간을 넘긴 경우는 카카오 탓이 아니므로 실패로 기억하지 않는다.
        List<java.util.concurrent.atomic.AtomicBoolean> started = new ArrayList<>();
        try {
            for (Category category : CATEGORIES) {
                URI uri = URI.create("https://dapi.kakao.com/v2/local/search/category.json?category_group_code=" + category.code()
                    + "&x=" + x + "&y=" + y + "&radius=" + RADIUS + "&sort=distance&size=15");
                var began = new java.util.concurrent.atomic.AtomicBoolean();
                started.add(began);
                calls.add(pool.submit(() -> { began.set(true); return pick(json.readTree(transport.get(uri, Map.of("Authorization", "KakaoAK " + key))), category); }));
            }
            long deadline = System.nanoTime() + budgetNanos;
            List<Place> places = new ArrayList<>();
            // 결과는 CATEGORIES 순서(지하철역·학교·마트)대로 담는다.
            for (Future<Optional<Place>> call : calls) call.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS).ifPresent(places::add);
            Result result = new Result("READY", RADIUS, List.copyOf(places));
            cache.put(cacheKey, result);
            shared.put("nearby:" + cacheKey, result, java.time.Duration.ofDays(1));
            return result;
        } catch (InterruptedException stop) {
            Thread.currentThread().interrupt();
            stop(calls);
            return unavailable();
        } catch (RejectedExecutionException busy) {
            // 이 서버의 호출 스레드가 모자란 경우라 지점 실패로 기억하지 않는다.
            stop(calls);
            return unavailable();
        } catch (TimeoutException late) {
            stop(calls);
            // 모든 호출이 시작했는데도 늦었으면 카카오가 느린 것이다. 대기열에서 기다리다 늦은 경우는 이 서버가 바빴던 것이라 기억하지 않는다.
            if (started.stream().allMatch(java.util.concurrent.atomic.AtomicBoolean::get)) failed.put(cacheKey, Boolean.TRUE);
            log.debug("Nearby places timed out (all started: {})", started.stream().allMatch(java.util.concurrent.atomic.AtomicBoolean::get));
            return unavailable();
        } catch (Exception failure) {
            // 제공처 오류(ExecutionException)
            stop(calls);
            failed.put(cacheKey, Boolean.TRUE);
            log.debug("Nearby places failed: {}", failure.getClass().getSimpleName());
            return unavailable();
        }
    }
    /**
     * 남은 호출을 멈춘다. 이미 시작한 호출은 끼어들지(interrupt) 않는다. 전송 쪽이 6초 안에 스스로 끝내며 연결·본문을 정리하기 때문이다
     * (끼어들면 그 정리를 건너뛰어 본문 읽기 스레드가 남을 수 있다). 아직 시작하지 않은 호출은 취소 뒤 purge로 대기열에서 바로 뺀다.
     */
    private void stop(List<Future<Optional<Place>>> calls) {
        calls.forEach(call -> call.cancel(false));
        pool.purge();
    }
    private static Result unavailable() { return new Result("UNAVAILABLE", RADIUS, List.of()); }

    /** 거리순 응답에서 조건(이름 끝)에 맞는 첫 장소. 좌표·거리가 없는 행은 건너뛴다. */
    static Optional<Place> pick(JsonNode body, Category category) {
        for (JsonNode row : body.path("documents")) {
            String name = row.path("place_name").asText("").trim();
            if (name.isEmpty() || (category.nameSuffix() != null && !name.endsWith(category.nameSuffix()))) continue;
            int distance = row.path("distance").asInt(-1);
            double lat = row.path("y").asDouble(Double.NaN), lng = row.path("x").asDouble(Double.NaN);
            if (distance < 0 || !Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            return Optional.of(new Place(category.kind(), name, distance, lat, lng));
        }
        return Optional.empty();
    }

    /** 주변 시설 API. 서비스 클래스 안에 작은 컨트롤러를 static 중첩 클래스로 두었다. */
    // @RestController: 스프링이 중첩 클래스도 찾아 컨트롤러로 등록한다. @Validated: @DecimalMin 같은 파라미터 검증을 켠다.
    @RestController
    @Validated
    static class Api {
        private final NearbyPlaces service;
        Api(NearbyPlaces service) { this.service = service; }
        /** 공고 위치(확정 지번 좌표)에서 가장 가까운 지하철역·초등학교·대형마트. */
        // @RequestParam의 @DecimalMin·@DecimalMax: 한국 범위(위도 33~39, 경도 124~132) 밖 좌표는 400으로 막는다.
        @GetMapping("/api/v1/research/nearby")
        public Result nearby(@RequestParam @DecimalMin("33") @DecimalMax("39") double lat,
                             @RequestParam @DecimalMin("124") @DecimalMax("132") double lng) {
            return service.nearest(lat, lng);
        }
    }
}
