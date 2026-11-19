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
    @Test void renamedDistrictsMatchTheCurrentNamesKakaoReturns() throws Exception {
        // 2026년 인천 개편·군위 편입 전 주소의 공고도 카카오가 돌려주는 지금 이름과 같은 곳으로 본다(tests/location.test.mjs와 같은 사례).
        assertTrue(AddressLocator.sameRegion("인천광역시 중구 사동 23-4번지 일원", "인천 제물포구 사동 23-4"));
        assertTrue(AddressLocator.sameRegion("인천광역시 중구 운서동 2850", "인천 영종구 운서동 2850"));
        assertTrue(AddressLocator.sameRegion("인천광역시 동구 송림동 1", "인천 제물포구 송림동 1"));
        assertTrue(AddressLocator.sameRegion("인천광역시 서구 청라동 1-1", "인천 서해구 청라동 1-1"));
        assertTrue(AddressLocator.sameRegion("인천광역시 서구 당하동 1093", "인천 검단구 당하동 1093"));
        assertTrue(AddressLocator.sameRegion("경상북도 군위군 군위읍 동부리 1", "대구 군위군 군위읍 동부리 1"));
        // 이름이 바뀌지 않은 곳·다른 시·도의 같은 이름은 그대로 다른 지역이다.
        assertFalse(AddressLocator.sameRegion("인천광역시 중구 사동 23-4", "인천 서해구 사동 23-4"));
        assertFalse(AddressLocator.sameRegion("서울특별시 중구 중림동 1", "인천 제물포구 중림동 1"));
        assertFalse(AddressLocator.sameRegion("대구광역시 서구 내당동 1", "인천 서해구 내당동 1"));
        assertFalse(AddressLocator.sameRegion("인천광역시 제물포구 도화동 1", "인천 중구 도화동 1"), "지금 이름 주소는 예전 이름 결과로 확정하지 않는다");
        // 실제 확정: 공급 주소는 예전 이름, 카카오 결과는 지금 이름
        var found = locator("인천 제물포구 사동 23-4", new ArrayList<>()).locate("인천광역시 중구 사동 23-4번지 일원");
        assertTrue(found.isPresent(), "개편 전 주소도 정확한 지번이면 확정한다");
    }
    @Test void newTownNamesAreNotTreatedAsMunicipalities() {
        // '검단신도시'·'혁신도시'는 시·군·구가 아니므로 괄호 안 지번이 있으면 정확한 위치를 확인할 수 있어야 한다.
        String address = "인천광역시 서구 검단신도시 AA21블록 (불로동 123)";
        assertEquals(List.of("서구"), AddressLocator.municipality(address));
        assertTrue(AddressLocator.parcelCandidates(address).contains("인천광역시 서구 불로동 123"));
        assertTrue(AddressLocator.sameRegion(address, "인천 서구 불로동 123"));
        assertTrue(AddressLocator.parcelCandidates("경기도 파주시 운정신도시 A1블록").isEmpty(), "지번이 없으면 여전히 확정하지 않는다");
    }
    /** 일반구가 있는 시: 괄호 속 지번 앞에 시·도 + 시·군·구 전부를 붙여야 같은 시·군·구 비교를 통과한다(tests/location.test.mjs와 같은 사례). */
    @Test void parenthesizedParcelsKeepTheDistrictOfCity() {
        assertEquals(List.of("경기도 수원시 영통구 이의동 1234"), AddressLocator.parcelCandidates("경기도 수원시 영통구 이의동 일원 (이의동 1234)"));
        assertEquals(List.of("경기도 고양시 일산동구 식사동 123"), AddressLocator.parcelCandidates("경기도 고양시 일산동구 식사지구 A1블록 (식사동 123)"));
        assertEquals(List.of("경상북도 포항시 북구 양덕동 12"), AddressLocator.parcelCandidates("경상북도 포항시 북구 신도시 (양덕동 12)"));
        assertEquals(List.of("이의동 1234", "경기도 수원시 영통구 이의동 1234", "경기도 수원시 영통구 이의동"), AddressLocator.addressCandidates("경기도 수원시 영통구 이의동 일원 (이의동 1234)"));
        // 용인시 수지구는 '지구'로 끝나도 일반구다. 택지 지구 이름(두 글자 이상 + 지구)·신도시는 여전히 뺀다.
        assertEquals(List.of("용인시", "수지구"), AddressLocator.municipality("경기도 용인시 수지구 풍덕천로 1 (풍덕천동 12-3)"));
        assertEquals(List.of("하남시"), AddressLocator.municipality("경기도 하남시 감일지구 B4블록 (감일동 100)"));
        assertEquals(List.of("고양시", "일산동구"), AddressLocator.municipality("경기도 고양시 일산동구 식사지구 A1블록"));
        assertEquals(List.of("서구"), AddressLocator.municipality("인천광역시 서구 검단신도시 AA13블록"));
        assertTrue(AddressLocator.sameRegion("경기도 용인시 수지구 풍덕천로 1 (풍덕천동 12-3)", "경기 용인시 수지구 풍덕천동 12-3"));
        assertFalse(AddressLocator.sameRegion("경기도 용인시 수지구 풍덕천로 1", "경기 용인시 기흥구 풍덕천동 12-3"), "같은 시의 다른 구는 다른 지역이다");
        assertTrue(AddressLocator.parcelCandidates("경기도 하남시 감일지구 B4블록 (감일동 100)").contains("경기도 하남시 감일동 100"));
        // 개편 전 이름(인천 중구)도 그대로 지금 이름 결과와 같은 곳으로 본다.
        assertEquals(List.of("인천광역시 중구 사동 23-4"), AddressLocator.parcelCandidates("인천광역시 중구 사동 23-4"));
        assertTrue(AddressLocator.sameRegion("인천광역시 중구 영종대로 123 (운서동 12-3)", "인천 영종구 운서동 12-3"));
    }
    @Test void provinceAliasesMatchAndSameNamedCountiesDoNot() {
        assertTrue(AddressLocator.sameRegion("경기도 구리시 갈매동 100", "경기 구리시 갈매동 100"));
        assertFalse(AddressLocator.sameRegion("강원특별자치도 고성군 간성읍 100", "경남 고성군 고성읍 100"));
        assertTrue(AddressLocator.sameRegion("부산광역시 중구 중앙대로 100", "부산 중구 중앙대로 100"));
        assertTrue(AddressLocator.sameRegion("세종특별자치시 한누리대로 100", "세종특별자치시 한누리대로 100"));
    }
    /** 통합특별시 표기는 Regions와 같게 다음 낱말로 옛 시·도를 정한다(늘 '전남'으로 보지 않는다). */
    @Test void mergedSpecialCityUsesTheSameProvinceAsRegions() {
        assertEquals("광주", AddressLocator.province("전남광주통합특별시 북구 운암동 100"));
        assertEquals("전남", AddressLocator.province("전남광주통합특별시 목포시 옥암동 100"));
        assertEquals(java.util.Set.of("전남", "광주"), AddressLocator.provinces("전남광주통합특별시 어느지구 A1블록"));
        assertNull(AddressLocator.province("전남광주통합특별시 어느지구 A1블록"), "옛 시·도를 못 정하면 하나로 확정하지 않는다");
        assertTrue(AddressLocator.sameRegion("전남광주통합특별시 북구 운암동 100", "광주 북구 운암동 100"));
        assertTrue(AddressLocator.sameRegion("광주광역시 북구 운암동 100", "전남광주통합특별시 북구 운암동 100"));
        assertTrue(AddressLocator.sameRegion("전남광주통합특별시 목포시 옥암동 100", "전남 목포시 옥암동 100"));
        assertFalse(AddressLocator.sameRegion("전남광주통합특별시 북구 운암동 100", "전남 목포시 옥암동 100"));
        assertFalse(AddressLocator.sameRegion("전남광주통합특별시 북구 운암동 100", "대구 북구 운암동 100"), "다른 시·도의 같은 이름 구는 여전히 다르다");
        assertEquals(List.of("북구"), AddressLocator.municipality("전남광주통합특별시 북구 운암동 100"), "통합특별시 이름을 시·군·구로 보지 않는다");
        assertEquals(List.of(), AddressLocator.municipality("전남광주통합특별시 어느지구 A1블록"));
        // 기존 시·도 판정은 그대로다.
        assertEquals("경기", AddressLocator.province("경기도 파주시 동패동 1"));
        assertEquals("전남", AddressLocator.province("전라남도 순천시 1"));
        assertNull(AddressLocator.province("파주시 동패동 1"));
        assertFalse(AddressLocator.sameRegion("부산광역시 중구 중앙대로 100", "서울 중구 중앙대로 100"));
    }
}
