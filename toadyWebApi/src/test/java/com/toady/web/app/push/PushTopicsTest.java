package com.toady.web.app.push;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PushTopicsTest {
    private static AnnouncementListItem item(String region, String address) {
        return new AnnouncementListItem("remaining", "H", "P", "단지", "04", "무순위", "000", region, address, "",
            "2026-11-20", "2026-11-20", null, null, null, null, null, null, null, null, null, null, null, null, "10", 0);
    }

    @Test void validSlugsAreLivingAreasOrTopics() {
        assertTrue(PushTopics.valid("zupzup-capital"));
        assertTrue(PushTopics.valid("zupzup-all"));
        assertTrue(PushTopics.valid("unjeong"));
        assertFalse(PushTopics.valid("zupzup-busan"));
        assertEquals("/match/zupzup", PushTopics.url("zupzup-all"));
        assertEquals("/areas/unjeong", PushTopics.url("unjeong"));
    }

    @Test void capitalTopicKeepsOnlySeoulGyeonggiIncheon() {
        var capital = PushTopics.bySlug("zupzup-capital").orElseThrow();
        assertTrue(capital.includes(item("서울", "서울특별시 강동구 1")));
        assertTrue(capital.includes(item(null, "경기도 화성시 1")));
        assertFalse(capital.includes(item("부산", "부산광역시 해운대구 1")));
        assertTrue(PushTopics.bySlug("zupzup-all").orElseThrow().includes(item("부산", "부산광역시 1")));
    }

    @Test void digestActionLinksTopicPage() {
        var message = PushMessage.digest(List.of(
            new PushMessage.Line("AREA", "remaining/A/1", "줍줍 1", "새 공고", "zupzup-capital", "수도권 줍줍"),
            new PushMessage.Line("AREA", "remaining/B/2", "줍줍 2", "새 공고", "zupzup-capital", "수도권 줍줍"))).orElseThrow();
        assertEquals("/match/zupzup", message.actions().get(0).url());
        assertTrue(message.body().contains("수도권 줍줍 새 공고 2건"));
    }
}
