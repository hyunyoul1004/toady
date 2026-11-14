package com.toady.web.app.planner.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실거래 단지(시·군·구 + 법정동 + 지번)의 지도 좌표. 국토부 실거래 자료에는 좌표가 없어 카카오 주소 검색으로 확인한다.
 * - 응답의 법정동 코드가 같은 시·군·구이고 지번(본번·부번·산 여부)과 동 이름이 같을 때만 좌표로 쓴다. 비슷한 주소로 확정하지 않는다.
 * - 결과는 TB_APT_COMPLEX_LOCATION(database.sql 22번)에 남겨 다시 묻지 않는다. 테이블이 없으면 메모리에만 둔다.
 * - 찾지 못한 지번은 30일 동안 다시 묻지 않고, 제공처 오류는 10분 뒤에 다시 시도한다.
 */
@Slf4j
@Component
public class ComplexLocator {
    public record Point(double lat, double lng) { }
    /** key: 시군구코드|법정동|지번. query: 카카오에 보낼 주소. */
    public record Target(String key, String lawd5, String dong, String jibun, String query) { }
    static final int NOT_FOUND_RETRY_DAYS = 30;
    private static final Pattern JIBUN = Pattern.compile("^(산)?\\s*(\\d+)(?:-(\\d+))?$");

    private final ResearchTransport transport;
    private final JdbcTemplate jdbc;
    private final String key;
    private final ObjectMapper json = new ObjectMapper();
    /** 확인한 결과(좌표 또는 '없음'). */
    private final Cache<String, Optional<Point>> memory = CacheBuilder.newBuilder().maximumSize(200_000).expireAfterWrite(1, TimeUnit.DAYS).build();
    private final Cache<String, Boolean> failed = CacheBuilder.newBuilder().maximumSize(20_000).expireAfterWrite(10, TimeUnit.MINUTES).build();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    /** 카카오 호출은 동시에 4개까지, 대기열은 2,000개까지(넘치면 다음 요청에서 다시 맡긴다). */
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(2000), task -> {
        Thread thread = new Thread(task, "complex-geocode"); thread.setDaemon(true); return thread;
    }, new ThreadPoolExecutor.AbortPolicy()); // 큐가 차면 RejectedExecutionException → inFlight에서 빼고 다음 요청에서 다시 맡긴다(DiscardPolicy는 조용히 버려 '확인 중'이 끝나지 않았다)
    /** 테이블을 쓸 수 없으면 10분 동안 메모리만 쓰고 다시 시도한다(일시적인 DB 오류로 영구히 끄지 않게). */
    private volatile long tableRetryAt = 0;
    private boolean tableReady() { return System.currentTimeMillis() >= tableRetryAt; }

    public ComplexLocator(ResearchTransport transport, JdbcTemplate jdbc, @Value("${toady.research.kakao-rest-key:}") String key) {
        this.transport = transport; this.jdbc = jdbc; this.key = key == null ? "" : key.trim();
        pool.allowCoreThreadTimeOut(true);
    }
    public boolean enabled() { return !key.isBlank(); }
    @PreDestroy void close() { pool.shutdownNow(); }

    /** 이미 확인한 결과. 값이 없으면 아직 모른다(empty Optional은 '찾지 못함'). */
    public Map<String, Optional<Point>> known(Collection<String> keys) {
        Map<String, Optional<Point>> out = new HashMap<>();
        List<String> misses = new ArrayList<>();
        for (String k : new LinkedHashSet<>(keys)) {
            Optional<Point> hit = memory.getIfPresent(k);
            if (hit != null) out.put(k, hit); else misses.add(k);
        }
        if (!misses.isEmpty() && tableReady()) {
            try {
                LocalDateTime retryBefore = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusDays(NOT_FOUND_RETRY_DAYS);
                for (int from = 0; from < misses.size(); from += 200) {
                    List<String> chunk = misses.subList(from, Math.min(misses.size(), from + 200));
                    String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
                    jdbc.query("SELECT COMPLEX_KEY,LATITUDE,LONGITUDE,STATUS,CHECKED_DT FROM TB_APT_COMPLEX_LOCATION WHERE COMPLEX_KEY IN (" + marks + ")", (RowCallbackHandler) r -> {
                        String k = r.getString(1);
                        if ("FOUND".equals(r.getString(4))) {
                            Optional<Point> point = Optional.of(new Point(r.getDouble(2), r.getDouble(3)));
                            memory.put(k, point); out.put(k, point);
                        } else if (r.getTimestamp(5).toLocalDateTime().isAfter(retryBefore)) {
                            memory.put(k, Optional.empty()); out.put(k, Optional.empty());
                        }
                    }, chunk.toArray());
                }
            } catch (DataAccessException unavailable) { disableTable(unavailable); }
        }
        return out;
    }

    /**
     * 모르는 단지의 좌표 확인을 맡기고, 최대 deadline(System.nanoTime 기준)까지 기다린다.
     * 시간 안에 끝나지 않은 확인은 백그라운드에서 이어지고, 다음 조회 때 결과가 반영된다.
     */
    public void locate(List<Target> targets, long deadline) {
        if (!enabled() || targets.isEmpty()) return;
        List<Future<?>> waiting = new ArrayList<>();
        for (Target target : targets) {
            if (memory.getIfPresent(target.key()) != null || failed.getIfPresent(target.key()) != null || !inFlight.add(target.key())) continue;
            try {
                waiting.add(pool.submit(() -> {
                    try { resolve(target); } finally { inFlight.remove(target.key()); }
                }));
            } catch (RejectedExecutionException full) { inFlight.remove(target.key()); }
        }
        for (Future<?> future : waiting) {
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            try { future.get(left, TimeUnit.NANOSECONDS); }
            catch (TimeoutException late) { break; }
            catch (InterruptedException stop) { Thread.currentThread().interrupt(); break; }
            catch (ExecutionException ignored) { /* 개별 실패는 resolve가 기록한다 */ }
        }
    }

    /** 아직 확인 중인지(화면은 잠시 뒤 다시 불러온다). */
    public boolean pending(String key) { return inFlight.contains(key); }

    private void resolve(Target target) {
        try {
            Optional<Point> point = geocode(target);
            memory.put(target.key(), point);
            save(target.key(), point);
        } catch (Exception failure) {
            failed.put(target.key(), Boolean.TRUE);
            log.debug("Complex geocode failed: {}", failure.getClass().getSimpleName());
        }
    }

    Optional<Point> geocode(Target target) throws Exception {
        Matcher wanted = JIBUN.matcher(target.jibun().trim());
        if (!wanted.matches()) return Optional.empty();
        boolean mountain = wanted.group(1) != null;
        String main = wanted.group(2), sub = wanted.group(3) == null ? "" : wanted.group(3);
        // 화면이 보낸 지역 이름(region)이 붙은 질의를 먼저 쓰되, 못 찾으면 '동 + 지번'만으로 한 번 더 찾는다.
        // 결과는 지역 이름과 무관한 키로 30일 저장되므로, 잘못된 region 하나로 '찾지 못함'이 저장되지 않게 한다(검증은 b_code·지번·동으로).
        Optional<Point> found = search(target.query(), target, main, sub, mountain);
        String plain = (target.dong() + " " + target.jibun()).trim();
        if (found.isEmpty() && !plain.equals(target.query().trim())) found = search(plain, target, main, sub, mountain);
        return found;
    }

    private Optional<Point> search(String query, Target target, String main, String sub, boolean mountain) throws Exception {
        URI uri = URI.create("https://dapi.kakao.com/v2/local/search/address.json?size=10&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        JsonNode body = json.readTree(transport.get(uri, Map.of("Authorization", "KakaoAK " + key)));
        for (JsonNode row : body.path("documents")) {
            JsonNode address = row.path("address");
            if (address.isMissingNode() || address.isNull()) continue;
            if (!address.path("b_code").asText("").startsWith(target.lawd5())) continue;
            if (!main.equals(address.path("main_address_no").asText(""))) continue;
            String gotSub = address.path("sub_address_no").asText("");
            if (!sub.equals(gotSub.equals("0") ? "" : gotSub)) continue;
            if (mountain != "Y".equals(address.path("mountain_yn").asText("N"))) continue;
            if (!lastWord(target.dong()).equals(lastWord(address.path("region_3depth_name").asText("")))) continue;
            double lat = address.path("y").asDouble(Double.NaN), lng = address.path("x").asDouble(Double.NaN);
            if (Double.isFinite(lat) && Double.isFinite(lng) && lat > 32 && lat < 39 && lng > 124 && lng < 132) return Optional.of(new Point(lat, lng));
        }
        return Optional.empty();
    }

    private static String lastWord(String value) {
        String[] words = value.trim().split("\\s+");
        return words[words.length - 1];
    }

    private void save(String complexKey, Optional<Point> point) {
        if (!tableReady()) return;
        try {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            jdbc.update("INSERT INTO TB_APT_COMPLEX_LOCATION (COMPLEX_KEY,LATITUDE,LONGITUDE,STATUS,CHECKED_DT) VALUES (?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE LATITUDE=VALUES(LATITUDE),LONGITUDE=VALUES(LONGITUDE),STATUS=VALUES(STATUS),CHECKED_DT=VALUES(CHECKED_DT)",
                complexKey, point.map(Point::lat).orElse(null), point.map(Point::lng).orElse(null), point.isPresent() ? "FOUND" : "NOT_FOUND", now);
        } catch (DataAccessException unavailable) { disableTable(unavailable); }
    }

    private void disableTable(DataAccessException cause) {
        if (tableReady()) log.warn("TB_APT_COMPLEX_LOCATION is unavailable; complex locations are kept in memory for 10 minutes (apply database.sql section 22): {}", cause.getClass().getSimpleName());
        tableRetryAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
    }
}
