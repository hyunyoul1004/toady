package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.app.planner.research.AddressLocator;
import jakarta.annotation.PreDestroy;
import jakarta.validation.constraints.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * 위치 탭 지도에 함께 놓을 '청약 중·예정' 공고. 맞춤 청약과 같은 공고 목록(EligibilityFeed)을 쓰고,
 * 위치는 주변 시세 비교와 같은 규칙(AddressLocator: 지번까지 일치할 때만 확정)으로 확인한다. 지구·블록만 있는 주소는 지도에 놓지 않는다.
 * 위치 확인 결과는 하루 동안 기억하고, 시간 안에 못 끝낸 확인은 백그라운드에서 이어 간다(pending).
 */
@Service
public class SaleMapService {
    public record Sale(String id, String type, String houseNm, String status, String start, String end, double lat, double lng, List<EligibilityFeed.Unit> units) { }
    public record Result(int pending, List<Sale> sales) { }
    private static final Set<String> PROVINCE_PREFIXES = Set.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충청", "충북", "충남", "전라", "전북", "전남", "경상", "경북", "경남", "제주");

    private final EligibilityFeed feed;
    private final AddressLocator locator;
    private final Clock clock;
    private final Cache<String, Optional<AddressLocator.Location>> located = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(1, TimeUnit.DAYS).build();
    private final Cache<String, Boolean> failed = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(10, TimeUnit.MINUTES).build();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500), task -> {
        Thread thread = new Thread(task, "sale-locate"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());

    @org.springframework.beans.factory.annotation.Autowired
    public SaleMapService(EligibilityFeed feed, AddressLocator locator) {
        this(feed, locator, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    SaleMapService(EligibilityFeed feed, AddressLocator locator, Clock clock) {
        this.feed = feed; this.locator = locator; this.clock = clock;
        pool.allowCoreThreadTimeOut(true);
    }
    @PreDestroy void close() { pool.shutdownNow(); }

    /** 같은 시·군·구(법정동 코드 앞 5자리)에 있는 청약 중·예정 공고. region: '경기 파주시'처럼 주소 글자로 먼저 좁힌다. */
    public Result sales(String bcode, String region) {
        if (!locator.enabled()) return new Result(0, List.of());
        String lawd5 = bcode.substring(0, 5);
        List<String> names = municipalityNames(region);
        @SuppressWarnings("unchecked")
        List<EligibilityFeed.Item> items = (List<EligibilityFeed.Item>) feed.load(LocalDate.now(clock)).get("items");
        // 주소에 시·군·구 이름이 들어 있는 공고만 위치를 확인한다(전국 공고를 모두 조회하지 않게).
        List<EligibilityFeed.Item> nearby = items.stream()
            .filter(item -> item.address() != null && (names.isEmpty() || names.stream().anyMatch(name -> item.address().contains(name))))
            .toList();
        List<Future<?>> waiting = new ArrayList<>();
        for (EligibilityFeed.Item item : nearby) {
            String key = key(item);
            if (located.getIfPresent(key) != null || failed.getIfPresent(key) != null || !inFlight.add(key)) continue;
            try {
                waiting.add(pool.submit(() -> {
                    try { located.put(key, locator.locate(item.address())); }
                    catch (Exception provider) { failed.put(key, Boolean.TRUE); }
                    finally { inFlight.remove(key); }
                }));
            } catch (RejectedExecutionException full) { inFlight.remove(key); }
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        for (Future<?> future : waiting) {
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            try { future.get(left, TimeUnit.NANOSECONDS); }
            catch (TimeoutException late) { break; }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); break; }
            catch (ExecutionException ignored) { /* 개별 실패는 위에서 기록 */ }
        }
        List<Sale> sales = new ArrayList<>();
        int pending = 0;
        for (EligibilityFeed.Item item : nearby) {
            Optional<AddressLocator.Location> location = located.getIfPresent(key(item));
            if (location == null) { if (inFlight.contains(key(item))) pending++; continue; }
            if (location.isEmpty()) continue;
            AddressLocator.Location at = location.get();
            if (!at.bcode().startsWith(lawd5) || !Double.isFinite(at.lat()) || !Double.isFinite(at.lng())) continue;
            sales.add(new Sale(item.id(), item.type(), item.houseNm(), item.status(), item.start(), item.end(), at.lat(), at.lng(), item.units()));
        }
        return new Result(pending, sales);
    }

    /** 공고 주소가 정정되면 다시 확인하도록 주소까지 키에 넣는다. */
    private static String key(EligibilityFeed.Item item) { return item.id() + "|" + item.address(); }

    /** '경기 파주시' → ['파주시'], '경기 수원시 영통구' → ['수원시', '영통구'], '세종특별자치시' → ['세종']. */
    static List<String> municipalityNames(String region) {
        if (region == null || region.isBlank()) return List.of();
        List<String> words = new ArrayList<>(List.of(region.trim().split("\\s+")));
        if (words.size() > 1 && PROVINCE_PREFIXES.stream().anyMatch(words.get(0)::startsWith)) words.remove(0);
        return words.stream().map(word -> word.startsWith("세종") ? "세종" : word).toList();
    }

    @RestController
    @Validated
    static class Api {
        private final SaleMapService service;
        Api(SaleMapService service) { this.service = service; }
        /** 지도 위 '청약 중·예정' 공고(같은 시·군·구). */
        @GetMapping("/api/v1/research/sales")
        public Result sales(@RequestParam @Pattern(regexp = "[0-9]{10}") String bcode,
                            @RequestParam(required = false) @Pattern(regexp = "[가-힣]{2,10}( [가-힣]{1,10}){0,2}") String region) {
            return service.sales(bcode, region);
        }
    }
}
