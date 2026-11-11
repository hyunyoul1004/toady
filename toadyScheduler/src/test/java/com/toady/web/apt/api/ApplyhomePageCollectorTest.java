package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplyhomePageCollectorTest {
    @Test void fetchesEveryPageAndAcceptsAnEmptySuccessfulResult() {
        var row = JsonNodeFactory.instance.objectNode();
        assertEquals(2, ApplyhomePageCollector.fetchAll("example", page -> new ApplyhomeApiPage(List.of(row), 1, 2)).size());
        assertTrue(ApplyhomePageCollector.fetchAll("example", page -> new ApplyhomeApiPage(List.of(), 0, 0)).isEmpty());
    }

    @Test void rejectsChangingCountsAndIncompleteOrExcessSnapshots() {
        var row = JsonNodeFactory.instance.objectNode();
        assertThrows(IllegalStateException.class, () -> ApplyhomePageCollector.fetchAll("example",
                page -> new ApplyhomeApiPage(List.of(row), 1, page == 1 ? 2 : 3)));
        assertThrows(IllegalStateException.class, () -> ApplyhomePageCollector.fetchAll("example",
                page -> page == 1 ? new ApplyhomeApiPage(List.of(row), 1, 2) : new ApplyhomeApiPage(List.of(), 0, 2)));
        assertThrows(IllegalStateException.class, () -> ApplyhomePageCollector.fetchAll("example",
                page -> new ApplyhomeApiPage(List.of(row, row), 2, 1)));
    }
}
