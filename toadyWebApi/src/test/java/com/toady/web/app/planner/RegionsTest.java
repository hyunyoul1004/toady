package com.toady.web.app.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 주소 → 시·도/시·군·구 판정(Regions.of) 결과 대조. 웹(tests/match.test.mjs)도 같은 샘플 파일로 regionOf 결과를 확인하므로
 * 두 쪽 규칙이 어긋나면 어느 한쪽 테스트가 실패한다.
 */
class RegionsTest {
    private static List<String[]> samples() throws IOException {
        try (InputStream in = RegionsTest.class.getResourceAsStream("/region-samples.tsv")) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return text.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).map(line -> line.split("\t", -1)).toList();
        }
    }
    private static String value(String cell) { return "-".equals(cell) ? null : cell; }

    @Test void sharedSamplesGiveTheSameRegionAsTheWeb() throws IOException {
        List<String[]> rows = samples();
        assertTrue(rows.size() >= 40, "샘플 파일을 읽어야 한다");
        for (String[] row : rows) {
            assertEquals(4, row.length, String.join(" | ", row));
            var found = Regions.of(value(row[0]), value(row[1]));
            String label = row[0] + " / " + row[1];
            assertEquals(value(row[2]), found.map(region -> region.sido().name()).orElse(null), label);
            assertEquals(value(row[3]), found.map(Regions.Region::district).orElse(null), label);
        }
    }

    @Test void specialCityNamesAreTheSameDistrictAsTheCity() {
        for (String name : List.of("수원", "고양", "용인", "창원", "화성")) {
            assertEquals(name + "시", Regions.canonical(name + "특례시"));
        }
        var gyeonggi = Regions.bySegment("경기도").orElseThrow();
        assertEquals("화성시", Regions.district(gyeonggi, "화성특례시").orElseThrow(), "공개 주소 조각도 같은 시로");
        assertTrue(Regions.district(gyeonggi, "특례시").isEmpty());
        assertEquals("서귀포시", Regions.canonical("서귀포시"), "다른 이름은 그대로");
    }

    @Test void mergedSpecialCityIsSplitBackIntoTheFormerProvinces() {
        assertEquals(List.of("전라남도", "광주광역시"), Regions.merged("전남광주통합특별시").stream().map(Regions.Sido::name).toList());
        assertTrue(Regions.merged("광주특별시").isEmpty(), "시·도 하나뿐이면 통합 이름이 아니다");
        assertTrue(Regions.merged("서울특별시").isEmpty());
        // 통합 대상 시·도끼리 시·군·구 이름이 겹치면 다음 낱말로 옛 시·도를 정할 수 없다(광주 자치구와 전남 시·군은 겹치지 않는다).
        var gwangju = Regions.DISTRICTS.get("광주");
        assertTrue(Regions.DISTRICTS.get("전남").stream().noneMatch(gwangju::contains));
    }
}
