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

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import java.time.Clock;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;

/**
 * 위치 탭 지도에 함께 놓을 분양 공고: 청약 중·예정(맞춤 청약과 같은 EligibilityFeed)과
 * 최근 3년 안에 접수를 마쳤지만 아직 입주 전인 분양 완료 APT 공고(아직 매매 실거래가 없는 단지).
 * 위치는 주변 시세 비교와 같은 규칙(AddressLocator: 지번까지 일치할 때만 확정)으로 확인한다.
 * 지구·블록만 있는 주소는 지도에 놓지 않고 unlocated로 따로 돌려준다(화면은 목록으로만 보여 준다).
 * 위치 확인 결과는 하루 동안 기억하고, 시간 안에 못 끝낸 확인은 백그라운드에서 이어 간다(pending).
 */
@Service
public class SaleMapService {
    /** status: OPEN(청약 중) · UPCOMING(청약 예정) · DONE(분양 완료, 입주 전). moveIn: 입주 예정월(YYYY-MM, 없으면 null). */
    /** bcode·dong: 확정한 지번의 법정동(화면이 상세 탭과 같은 주변 시세 비교를 부를 때 쓴다). */
    public record Sale(String id, String type, String houseNm, String status, String start, String end, String moveIn, double lat, double lng, List<EligibilityFeed.Unit> units,
                       String bcode, String dong) { }
    /** 지번으로 위치를 확정하지 못한 공고(지구·블록 주소 등). 지도에는 놓지 않는다. */
    public record Unlocated(String id, String houseNm, String status, String start) { }
    public record Result(int pending, List<Sale> sales, List<Unlocated> unlocated) { }
    private static final Set<String> PROVINCE_PREFIXES = Set.of("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원", "충청", "충북", "충남", "전라", "전북", "전남", "경상", "경북", "경남", "제주");
    /** 분양 완료 공고는 접수 시작일 기준 최근 3년, 시·군·구당 최대 300건까지 본다. */
    static final int PAST_YEARS = 3, MAX_PAST = 300;
    static final String DONE = "DONE";

    private final EligibilityFeed feed;
    private final AnnouncementSearchService search;
    private final AddressLocator locator;
    private final Clock clock;
    private final Cache<String, Optional<AddressLocator.Location>> located = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(1, TimeUnit.DAYS).build();
    private final Cache<String, Boolean> failed = CacheBuilder.newBuilder().maximumSize(5000).expireAfterWrite(10, TimeUnit.MINUTES).build();
    /** 시·군·구별 분양 완료 공고(하루에 한 번 바뀌는 자료라 1시간 기억한다). */
    private final Cache<String, List<EligibilityFeed.Item>> past = CacheBuilder.newBuilder().maximumSize(300).expireAfterWrite(1, TimeUnit.HOURS).build();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(3, 3, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(500), task -> {
        Thread thread = new Thread(task, "sale-locate"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.DiscardPolicy());

    @org.springframework.beans.factory.annotation.Autowired
    public SaleMapService(EligibilityFeed feed, AnnouncementSearchService search, AddressLocator locator) {
        this(feed, search, locator, Clock.system(ZoneId.of("Asia/Seoul")));
    }
    SaleMapService(EligibilityFeed feed, AnnouncementSearchService search, AddressLocator locator, Clock clock) {
        this.feed = feed; this.search = search; this.locator = locator; this.clock = clock;
        pool.allowCoreThreadTimeOut(true);
    }
    @PreDestroy void close() { pool.shutdownNow(); }

    /** 같은 시·군·구(법정동 코드 앞 5자리)에 있는 분양 공고. region: '경기 파주시'처럼 주소 글자로 먼저 좁힌다. */
    public Result sales(String bcode, String region) {
        if (!locator.enabled()) return new Result(0, List.of(), List.of());
        String lawd5 = bcode.substring(0, 5);
        List<String> names = municipalityNames(region);
        LocalDate today = LocalDate.now(clock);
        @SuppressWarnings("unchecked")
        List<EligibilityFeed.Item> items = (List<EligibilityFeed.Item>) feed.load(today).get("items");
        // 주소에 시·군·구 이름이 들어 있는 공고만 위치를 확인한다(전국 공고를 모두 조회하지 않게).
        List<EligibilityFeed.Item> nearby = new ArrayList<>(items.stream().filter(item -> inRegion(item.address(), names)).toList());
        Set<String> current = nearby.stream().map(EligibilityFeed.Item::id).collect(java.util.stream.Collectors.toSet());
        if (!names.isEmpty()) nearby.addAll(pastItems(names, today).stream().filter(item -> !current.contains(item.id())).toList());

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
        List<Unlocated> unlocated = new ArrayList<>();
        int pending = 0;
        for (EligibilityFeed.Item item : nearby) {
            Optional<AddressLocator.Location> location = located.getIfPresent(key(item));
            if (location == null) { if (inFlight.contains(key(item))) pending++; continue; }
            if (location.isEmpty()) { unlocated.add(new Unlocated(item.id(), item.houseNm(), item.status(), item.start())); continue; }
            AddressLocator.Location at = location.get();
            if (!at.bcode().startsWith(lawd5) || !Double.isFinite(at.lat()) || !Double.isFinite(at.lng())) continue;
            sales.add(new Sale(item.id(), item.type(), item.houseNm(), item.status(), item.start(), item.end(), item.moveIn(), at.lat(), at.lng(), item.units(), at.bcode(), at.dong()));
        }
        return new Result(pending, sales, unlocated);
    }

    /** 최근 3년 안에 접수를 마친 같은 시·군·구의 APT 분양 공고 중 아직 입주 전인 것(상태 DONE). */
    private List<EligibilityFeed.Item> pastItems(List<String> names, LocalDate today) {
        String cacheKey = String.join(" ", names) + "/" + today;
        List<EligibilityFeed.Item> cached = past.getIfPresent(cacheKey);
        if (cached != null) return cached;
        AnnouncementSearch query = new AnnouncementSearch();
        // 가장 좁은 이름(시·군·구의 마지막 단어)으로 찾고, 주소에 모든 이름이 들어 있는지 다시 확인한다.
        query.setKeyword(names.get(names.size() - 1));
        query.setStartDate(today.minusYears(PAST_YEARS));
        query.setEndDate(today);
        query.setSize(MAX_PAST);
        var page = search.searchAll(query, List.of("apt"));
        List<AnnouncementListItem> finished = pastCandidates(page.getContent(), names, today);
        List<EligibilityFeed.Item> built = feed.items(finished, finished.stream().collect(java.util.stream.Collectors.toMap(
                source -> source.type() + "/" + source.houseManageNo() + "/" + source.pblancNo(), source -> DONE, (a, b) -> a)))
            .stream().filter(item -> beforeMoveIn(item.moveIn(), YearMonth.from(today))).toList();
        past.put(cacheKey, built);
        return built;
    }

    /** 주소가 시·군·구 이름을 모두 담고, 접수(마지막 날)가 오늘 이전에 끝난 공고. 같은 주택관리번호는 최신 공고 하나만 남긴다. */
    static List<AnnouncementListItem> pastCandidates(List<AnnouncementListItem> sources, List<String> names, LocalDate today) {
        Map<String, AnnouncementListItem> latest = new LinkedHashMap<>();
        for (AnnouncementListItem source : sources) {
            if (!inRegion(source.hssplyAdres(), names)) continue;
            LocalDate start = PlannerSummary.date(source.rceptBgnde() == null ? "" : source.rceptBgnde().trim());
            LocalDate end = PlannerSummary.date(source.rceptEndde() == null ? "" : source.rceptEndde().trim());
            LocalDate last = end == null ? start : end;
            if (last == null || !last.isBefore(today)) continue;
            latest.merge(source.type() + "/" + source.houseManageNo(), source,
                (kept, next) -> String.valueOf(next.pblancNo()).compareTo(String.valueOf(kept.pblancNo())) > 0 ? next : kept);
        }
        return List.copyOf(latest.values());
    }

    /** 입주 예정월이 이번 달 이후면 입주 전으로 본다. 입주 예정월이 없으면(미제공) 지도에 둔다. */
    static boolean beforeMoveIn(String moveIn, YearMonth now) {
        if (moveIn == null || !moveIn.matches("\\d{4}-\\d{2}")) return true;
        return !YearMonth.parse(moveIn).isBefore(now);
    }

    static boolean inRegion(String address, List<String> names) {
        return address != null && (names.isEmpty() || names.stream().allMatch(address::contains));
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
        /** 지도 위 분양 공고(같은 시·군·구): 청약 중·예정과 분양 완료(입주 전). */
        @GetMapping("/api/v1/research/sales")
        public Result sales(@RequestParam @Pattern(regexp = "[0-9]{10}") String bcode,
                            @RequestParam(required = false) @Pattern(regexp = "[가-힣]{2,10}( [가-힣]{1,10}){0,2}") String region) {
            return service.sales(bcode, region);
        }
    }
}
