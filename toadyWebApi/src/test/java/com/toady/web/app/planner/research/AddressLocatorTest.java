package com.toady.web.app.planner.research;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 화면 위치 판정(tests/location.test.mjs)과 같은 사례로 서버 판정이 같은지 확인한다. */
class AddressLocatorTest {
    static final String ROW = "{\"address_type\":\"REGION_ADDR\",\"x\":\"126.7\",\"y\":\"37.7\",\"address_name\":\"%s\",\"address\":{\"address_name\":\"%s\",\"b_code\":\"4148012300\",\"region_3depth_name\":\"동패동\"}}";
    private static AddressLocator locator(String found, List<String> calls) {
        return new AddressLocator((uri, headers) -> {
            calls.add(java.net.URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8));
            assertEquals("dapi.kakao.com", uri.getHost());
            assertEquals("KakaoAK test-key", headers.get("Authorization"));
            return ("{\"documents\":[" + (found == null ? "" : ROW.formatted(found, found)) + "]}").getBytes(StandardCharsets.UTF_8);
        }, "test-key");
    }

    @Test void blockOnlyAddressesNeverCallTheProviderOrResolve() throws Exception {
        for (String address : List.of("경기도 구리시 구리갈매역세권 B-1블록", "경기도 파주시 파주운정3지구 주상복합 4블록", "인천광역시 서구 검단신도시 AA-1BL")) {
            List<String> calls = new ArrayList<>();
            assertTrue(locator("경기도 파주시 동패동 400-7", calls).locate(address).isEmpty(), address);
            assertTrue(calls.isEmpty(), "지구·블록 주소는 조회하지 않는다");
        }
    }
    @Test void preciseParcelAndRegionAreRequired() throws Exception {
        String address = "경기도 파주시 운정3지구 (동패동 400-7번지 일원)";
        assertTrue(AddressLocator.parcelCandidates(address).contains("경기도 파주시 동패동 400-7"));
        var found = locator("경기도 파주시 동패동 400-7", new ArrayList<>()).locate(address).orElseThrow();
        assertEquals("4148012300", found.bcode()); assertEquals("동패동", found.dong());
        assertTrue(locator("경기도 파주시 동패동 400", new ArrayList<>()).locate(address).isEmpty(), "지번이 다르면 확정하지 않는다");
        assertTrue(locator("경기도 구리시 갈매동 400-7", new ArrayList<>()).locate(address).isEmpty(), "시·군이 다르면 확정하지 않는다");
        assertTrue(new AddressLocator((uri, h) -> { throw new AssertionError("키가 없으면 호출하지 않는다"); }, "").locate(address).isEmpty());
    }
    @Test void newTownNamesAreNotTreatedAsMunicipalities() {
        // '검단신도시'·'혁신도시'는 시·군·구가 아니므로 괄호 안 지번이 있으면 정확한 위치를 확인할 수 있어야 한다.
        String address = "인천광역시 서구 검단신도시 AA21블록 (불로동 123)";
        assertEquals(List.of("서구"), AddressLocator.municipality(address));
        assertTrue(AddressLocator.parcelCandidates(address).contains("인천광역시 서구 불로동 123"));
        assertTrue(AddressLocator.sameRegion(address, "인천 서구 불로동 123"));
        assertTrue(AddressLocator.parcelCandidates("경기도 파주시 운정신도시 A1블록").isEmpty(), "지번이 없으면 여전히 확정하지 않는다");
    }
    @Test void provinceAliasesMatchAndSameNamedCountiesDoNot() {
        assertTrue(AddressLocator.sameRegion("경기도 구리시 갈매동 100", "경기 구리시 갈매동 100"));
        assertFalse(AddressLocator.sameRegion("강원특별자치도 고성군 간성읍 100", "경남 고성군 고성읍 100"));
        assertTrue(AddressLocator.sameRegion("부산광역시 중구 중앙대로 100", "부산 중구 중앙대로 100"));
        assertTrue(AddressLocator.sameRegion("세종특별자치시 한누리대로 100", "세종특별자치시 한누리대로 100"));
    }
}
