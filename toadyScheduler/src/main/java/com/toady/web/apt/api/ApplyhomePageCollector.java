package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.function.IntFunction;

/** Do not persist a partial snapshot when an upstream result changes during pagination. */
public final class ApplyhomePageCollector {
    private ApplyhomePageCollector() {
    }

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
