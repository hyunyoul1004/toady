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
        List<String> calls = new ArrayList<>();
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
        assertTrue(calls.getFirst().contains("sort=distance") && calls.getFirst().contains("radius=3000"));
        service.nearest(37.71702, 126.72579);
        assertEquals(3, calls.size(), "약 10m 안의 같은 지점은 다시 묻지 않는다");
    }

    @Test void withoutKeyOrOnProviderFailureItSaysSo() {
        assertEquals("NOT_CONFIGURED", new NearbyPlaces((uri, headers) -> rows(), "").nearest(37.7, 126.7).status());
        assertEquals("UNAVAILABLE", new NearbyPlaces((uri, headers) -> { throw new IllegalStateException("down"); }, "k").nearest(37.7, 126.7).status());
    }
}
