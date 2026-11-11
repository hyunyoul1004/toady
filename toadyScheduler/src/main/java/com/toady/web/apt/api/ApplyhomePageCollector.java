package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/** Do not persist a partial snapshot when an upstream result changes during pagination. */
public final class ApplyhomePageCollector {
    private ApplyhomePageCollector() {
    }

    public static List<JsonNode> fetchAll(String endpoint, IntFunction<ApplyhomeApiPage> fetch) {
        List<JsonNode> rows = new ArrayList<>();
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
