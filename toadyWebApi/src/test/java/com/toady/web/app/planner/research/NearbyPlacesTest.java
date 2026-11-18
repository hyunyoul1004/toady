package com.toady.web.app.planner.research;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NearbyPlacesTest {
    private static byte[] rows(String... rows) { return ("{\"documents\":[" + String.join(",", rows) + "]}").getBytes(StandardCharsets.UTF_8); }
    private static String row(String name, int distance) { return "{\"place_name\":\"" + name + "\",\"distance\":\"" + distance + "\",\"x\":\"126.76\",\"y\":\"37.71\"}"; }

    @Test void picksTheNearestStationElementarySchoolAndMartWithStraightLineDistanceAndKeepsTheKeyOnTheServer() {
        List<String> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        NearbyPlaces service = new NearbyPlaces((uri, headers) -> {
            calls.add(uri.getQuery());
            assertEquals("KakaoAK secret", headers.get("Authorization"));
            assertFalse(uri.toString().contains("secret"), "키는 주소에 넣지 않는다");
            if (uri.getQuery().contains("SW8")) return rows(row("운정중앙역 경의중앙선", 420));
            if (uri.getQuery().contains("SC4")) return rows(row("운정중학교", 150), row("물향기초등학교", 380), row("한빛초등학교", 900));
            return rows();
        }, "secret");
        NearbyPlaces.Result result = service.nearest(37.71701, 126.72578);
        assertEquals("READY", result.status());
        assertEquals(List.of("STATION:운정중앙역 경의중앙선:420", "SCHOOL:물향기초등학교:380"),
            result.places().stream().map(p -> p.kind() + ":" + p.name() + ":" + p.distance()).toList(), "학교는 초등학교만, 마트가 없으면 빼고");
        assertTrue(calls.stream().allMatch(query -> query.contains("sort=distance") && query.contains("radius=3000")));
        service.nearest(37.71702, 126.72579);
        assertEquals(3, calls.size(), "약 10m 안의 같은 지점은 다시 묻지 않는다");
    }

    @Test void slowOrFailingProviderAnswersWithinTheBudgetAndIsNotAskedAgainForAWhile() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        NearbyPlaces slow = new NearbyPlaces((uri, headers) -> { calls.incrementAndGet(); Thread.sleep(5_000); return rows(); }, "k", java.time.Duration.ofMillis(300));
        long started = System.nanoTime();
        assertEquals("UNAVAILABLE", slow.nearest(37.7, 126.7).status());
        assertTrue(System.nanoTime() - started < java.time.Duration.ofSeconds(2).toNanos(), "세 번 호출을 차례로 기다리지 않고 시간 예산 안에 답한다");
        int asked = calls.get();
        long again = System.nanoTime();
        assertEquals("UNAVAILABLE", slow.nearest(37.70001, 126.70001).status());
        assertTrue(System.nanoTime() - again < java.time.Duration.ofMillis(100).toNanos(), "실패한 지점은 잠시 기억해 바로 답한다");
        assertEquals(asked, calls.get(), "실패를 기억하는 동안 같은 지점은 카카오를 다시 부르지 않는다");

        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        NearbyPlaces down = new NearbyPlaces((uri, headers) -> { failures.incrementAndGet(); throw new IllegalStateException("down"); }, "k");
        assertEquals("UNAVAILABLE", down.nearest(37.6, 126.6).status());
        int first = failures.get();
        assertEquals("UNAVAILABLE", down.nearest(37.6, 126.6).status());
        assertEquals(first, failures.get());
    }

    @Test void withoutKeyOrOnProviderFailureItSaysSo() {
        assertEquals("NOT_CONFIGURED", new NearbyPlaces((uri, headers) -> rows(), "").nearest(37.7, 126.7).status());
        assertEquals("UNAVAILABLE", new NearbyPlaces((uri, headers) -> { throw new IllegalStateException("down"); }, "k").nearest(37.7, 126.7).status());
    }
}
