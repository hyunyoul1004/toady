package com.toady.web.app.operation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 사이트 공지 링크: 사이트 안 경로와 https 주소만 내보내고, 다른 사이트로 가는 '//'·'/\' 주소는 막는다. */
class SiteNoticeLinkTest {
    static boolean safe(String link) { return SiteContentController.SAFE_LINK.matcher(link).matches(); }

    @Test void internalPathsAndHttpsLinksPass() {
        assertTrue(safe("/"));
        assertTrue(safe("/notice/12"));
        assertTrue(safe("/search?q=abc&page=2"));
        assertTrue(safe("https://www.toady.co.kr/notice"));
    }

    @Test void protocolRelativeAndBackslashLinksAreRejected() {
        assertFalse(safe("//evil.com"), "브라우저는 //evil.com을 다른 사이트로 연다");
        assertFalse(safe("//evil.com/path"));
        assertFalse(safe("/\\evil.com"), "일부 브라우저는 /\\를 //처럼 읽는다");
        assertFalse(safe("javascript:alert(1)"));
        assertFalse(safe("http://example.com"));
    }
}
