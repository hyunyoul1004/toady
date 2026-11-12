package com.toady.web.app.announcement.document;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NoticeDocumentServiceTest {
    private static final String QUERY = "houseManageNo=2026000472&pblancNo=2026000472&atchmnflSeqNo=2012076&atchmnflSn=4";
    private static final String URL = "https://static.applyhome.co.kr/ai/aia/getAtchmnfl.do?" + QUERY;
    private static final String HTML = "<html><meta charset='utf-8'><a href='" + URL.replace("&", "&amp;") + "'><span>모집공고문</span> 보기</a></html>";

    @Test void extractsOfficialMatchingAnnouncementAndDeduplicates() throws Exception {
        var result = NoticeDocumentService.parse(HTML + HTML, "2026000472", "2026000472");
        assertEquals(1, result.size()); assertEquals(URL, result.getFirst().url());
        assertEquals("모집공고문 보기", result.getFirst().title());
        assertNotNull(NoticeDocumentService.safeAttachment("/ai/aia/getAtchmnfl.do?" + QUERY, "2026000472", "2026000472"));
    }
    @Test void rejectsUnrelatedFilesAndUnsafeOrAmbiguousLinks() throws Exception {
        assertNull(NoticeDocumentService.safeAttachment(URL, "111", "2026000472"));
        for (String url : new String[]{URL.replace("static.applyhome.co.kr", "evil.example"), URL.replace("https:", "http:"),
                URL + "&pblancNo=111", URL + "&redirect=123", URL.replace("getAtchmnfl.do", "other.do"), "javascript:alert(1)"}) {
            assertNull(NoticeDocumentService.safeAttachment(url, "2026000472", "2026000472"));
        }
        assertTrue(NoticeDocumentService.parse(HTML.replace("모집공고문", "다른 서류"), "2026000472", "2026000472").isEmpty());
    }
    @Test void usesSharedTemplatesForEverySupportedType() {
        for (String type : new String[]{"apt", "remaining", "urban", "public-rental", "optional"}) {
            var uri = NoticeDocumentService.source(type, "123", "456");
            assertEquals("www.applyhome.co.kr", uri.getHost());
            assertEquals("houseManageNo=123&pblancNo=456", uri.getQuery());
        }
        assertThrows(IllegalArgumentException.class, () -> NoticeDocumentService.source("apt", "../1", "456"));
    }
    @Test void cachesSuccessfulLookupsAndReportsFailuresWithoutInventingDownloadLinks() {
        AtomicInteger calls = new AtomicInteger();
        var service = new NoticeDocumentService(uri -> { calls.incrementAndGet(); return HTML.getBytes(StandardCharsets.UTF_8); });
        assertEquals("READY", service.get("apt", "2026000472", "2026000472").status());
        assertEquals("READY", service.get("apt", "2026000472", "2026000472").status());
        assertEquals(1, calls.get());
        var failing = new NoticeDocumentService(uri -> { throw new java.io.IOException("unavailable"); });
        assertEquals("UNAVAILABLE", failing.get("apt", "123", "456").status());
        assertTrue(failing.get("apt", "123", "456").documents().isEmpty());
        var empty = new NoticeDocumentService(uri -> "<html>no attachment</html>".getBytes(StandardCharsets.UTF_8));
        assertEquals("EMPTY", empty.get("apt", "123", "456").status());
    }
}
