package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ApplyhomePageCollectorTest {
    @Test void fetchesEveryPageAndAcceptsAnEmptySuccessfulResult() {
        var row = JsonNodeFactory.instance.objectNode();
        assertEquals(2, ApplyhomePageCollector.fetchAll("example", page -> new ApplyhomeApiPage(List.of(JsonNodeFactory.instance.objectNode().put("id", page)), 1, 2)).size());
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

    @Test void rejectsRepeatedKeysEvenWhenValuesAndTotalCountChangeNothing() {
        assertThrows(IllegalStateException.class, () -> ApplyhomePageCollector.fetchAll("rates", page ->
                new ApplyhomeApiPage(List.of(JsonNodeFactory.instance.objectNode().put("ID", "same").put("RATE", page)), 1, 2), List.of("ID")));
    }
    @Test void acceptsLowercaseKeysAndPreservesTheUrbanDuplicateException() {
        assertEquals(2, ApplyhomePageCollector.fetchAll("rates", page ->
                new ApplyhomeApiPage(List.of(JsonNodeFactory.instance.objectNode().put("id", page)), 1, 2), List.of("ID")).size());
        var row = JsonNodeFactory.instance.objectNode().put("MODEL_NO", "1");
        assertEquals(2, ApplyhomePageCollector.fetchAll("urbanModels", page -> new ApplyhomeApiPage(List.of(row), 1, 2), null).size());
    }
    @Test void rejectsMissingUniqueKeys() {
        assertThrows(IllegalStateException.class, () -> ApplyhomePageCollector.fetchAll("rates", page ->
                new ApplyhomeApiPage(List.of(JsonNodeFactory.instance.objectNode()), 1, 1), List.of("ID")));
    }
}
