package com.toady.web.core.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrUtilsTest {
    @Test void generatesCodesWithinStorageAndFormLimits() {
        String code = StrUtils.createCode();
        assertTrue(code.matches("[A-HJ-NP-Z2-9]{8}-[A-HJ-NP-Z2-9]{8}-[A-HJ-NP-Z2-9]{8}"));
        assertTrue(code.length() <= 50);
        assertEquals(24, code.replace("-", "").length());
    }
}
