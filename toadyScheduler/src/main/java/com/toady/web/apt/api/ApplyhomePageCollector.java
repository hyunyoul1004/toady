package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.function.IntFunction;

/**
 * 청약홈 API의 모든 페이지를 차례로 받아 하나의 목록으로 합친다.
 * 페이지를 넘기는 도중 원본 건수(matchCount)가 바뀌거나, 행이 모자라거나 남거나, 같은 키가 두 번 나오면 예외를 던진다.
 * 원본이 바뀌는 중에 받은 반쪽짜리 결과를 DB에 저장하지 않기 위해서다.
 * 호출: ApplyhomeSyncService, ApplyhomeCompetitionSyncService.
 */
public final class ApplyhomePageCollector {
    private ApplyhomePageCollector() {
    }

    /** 전체 JSON 행 기준으로 중복을 검사하며 모든 페이지를 받는다. fetch는 페이지 번호를 받아 한 페이지를 돌려주는 함수다. */
    public static List<JsonNode> fetchAll(String endpoint, IntFunction<ApplyhomeApiPage> fetch) {
        return fetchAll(endpoint, fetch, List.of());
    }

    /** null 키 목록은 원본 중복을 허용하는 도시형 주택형 전용이다. 빈 목록은 전체 JSON 중복을 검사한다. */
    public static List<JsonNode> fetchAll(String endpoint, IntFunction<ApplyhomeApiPage> fetch, List<String> keyColumns) {
        List<JsonNode> rows = new ArrayList<>();
        Set<Object> seen = new HashSet<>();
        Long expected = null;
        for (int page = 1; ; page++) {
            ApplyhomeApiPage response = fetch.apply(page);
            if (response.matchCount() < 0 || response.currentCount() != response.data().size()) {
                throw new IllegalStateException("Invalid page counts for " + endpoint);
            }
            if (expected == null) expected = response.matchCount();
            if (expected != response.matchCount()) {
                throw new IllegalStateException("Applyhome matchCount changed during pagination: " + endpoint);
            }
            if (keyColumns != null) {
                for (JsonNode row : response.data()) {
                    Object key = keyColumns.isEmpty() ? row : keyColumns.stream().map(column -> {
                        JsonNode value = row.get(column);
                        if (value == null) value = row.get(column.toLowerCase(Locale.ROOT));
                        if (value == null || value.isNull() || value.asText().isBlank()) {
                            throw new IllegalStateException("Missing unique-key field for " + endpoint + ": " + column);
                        }
                        return value.asText();
                    }).toList();
                    if (!seen.add(key)) throw new IllegalStateException("Duplicate row key during pagination: " + endpoint);
                }
            }
            rows.addAll(response.data());
            if (rows.size() > expected) {
                throw new IllegalStateException("Applyhome returned more rows than matchCount: " + endpoint);
            }
            if (rows.size() == expected) return List.copyOf(rows);
            if (response.currentCount() == 0) {
                throw new IllegalStateException("Applyhome returned fewer rows than matchCount: " + endpoint);
            }
        }
    }
}
