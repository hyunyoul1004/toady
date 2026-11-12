package com.toady.web.app.planner;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShareScoresTest {
    @Test void summarizesExtremesWithoutAveragingRegionsOrTreatingMissingScoresAsZero() {
        var range=ShareScores.range(Arrays.asList("56", "61.0", "-", "0", "999", null), List.of("74.0", "70", "미공개"));
        assertEquals("56",range.minimum()); assertEquals("74",range.maximum());
        assertEquals("공개 당첨가점 · 최저 56점 / 최고 74점",range.text());
    }
    @Test void missingAndPartialScoresRemainExplicit() {
        assertFalse(ShareScores.range(List.of("0","-","NaN"),List.of("0","85")).available());
        assertEquals("공개 당첨가점 · 최고 84점",ShareScores.range(List.of(),List.of("84")).text());
    }
}
