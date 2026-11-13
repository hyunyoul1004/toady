package com.toady.web.app.planner.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.util.concurrent.TimeUnit;

/**
 * 위치 탭의 '가까운 시설' 요약: 공고 위치에서 가장 가까운 지하철역·초등학교·대형마트(카카오 로컬 카테고리 검색).
 * 카카오 REST 키는 서버에만 둔다(AddressLocator와 같은 키·같은 전송 방식). 거리는 카카오가 돌려주는 직선거리(m)다.
 * 같은 지점(소수 넷째 자리, 약 10m)은 하루 동안 기억해 외부 호출을 줄인다.
 */
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

    public NearbyPlaces(ResearchTransport transport, @Value("${toady.research.kakao-rest-key:}") String key) {
        this.transport = transport; this.key = key == null ? "" : key.trim();
    }

    public Result nearest(double lat, double lng) {
        if (key.isBlank()) return new Result("NOT_CONFIGURED", RADIUS, List.of());
        double y = Math.round(lat * 10000) / 10000.0, x = Math.round(lng * 10000) / 10000.0;
        String cacheKey = y + "," + x;
        Result cached = cache.getIfPresent(cacheKey);
        if (cached != null) return cached;
        try {
            List<Place> places = new ArrayList<>();
            for (Category category : CATEGORIES) {
                URI uri = URI.create("https://dapi.kakao.com/v2/local/search/category.json?category_group_code=" + category.code()
                    + "&x=" + x + "&y=" + y + "&radius=" + RADIUS + "&sort=distance&size=15");
                JsonNode body = json.readTree(transport.get(uri, Map.of("Authorization", "KakaoAK " + key)));
                pick(body, category).ifPresent(places::add);
            }
            Result result = new Result("READY", RADIUS, List.copyOf(places));
            cache.put(cacheKey, result);
            return result;
        } catch (Exception failure) {
            log.debug("Nearby places failed: {}", failure.getClass().getSimpleName());
            return new Result("UNAVAILABLE", RADIUS, List.of());
        }
    }

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

    @RestController
    @Validated
    static class Api {
        private final NearbyPlaces service;
        Api(NearbyPlaces service) { this.service = service; }
        /** 공고 위치(확정 지번 좌표)에서 가장 가까운 지하철역·초등학교·대형마트. */
        @GetMapping("/api/v1/research/nearby")
        public Result nearby(@RequestParam @DecimalMin("33") @DecimalMax("39") double lat,
                             @RequestParam @DecimalMin("124") @DecimalMax("132") double lng) {
            return service.nearest(lat, lng);
        }
    }
}
