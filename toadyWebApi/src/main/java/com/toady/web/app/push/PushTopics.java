package com.toady.web.app.push;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.planner.LivingAreas;
import java.util.List;
import java.util.Optional;

/**
 * 생활권 외의 '새 공고' 알림 주제. 구독의 AREA_SLUGS에 생활권 slug와 함께 저장하고,
 * 새 공고 판정(TB_PUSH_AREA_SEEN)도 같은 방식으로 주제 slug 단위로 기록한다.
 */
public final class PushTopics {
    private PushTopics() { }
    public record Topic(String slug, String label, String url, boolean capitalOnly) {
        /** 무순위·잔여세대·임의공급 공고 중 이 주제에 들어가는 공고인지. */
        public boolean includes(AnnouncementListItem item) {
            if (!capitalOnly) return true;
            String region = item.regionName() == null ? "" : item.regionName().strip();
            String address = item.hssplyAdres() == null ? "" : item.hssplyAdres().strip();
            return CAPITAL.stream().anyMatch(prefix -> region.startsWith(prefix) || address.startsWith(prefix));
        }
    }
    static final List<String> CAPITAL = List.of("서울", "경기", "인천");
    public static final List<Topic> ALL = List.of(
        new Topic("zupzup-capital", "수도권 줍줍", "/match/zupzup", true),
        new Topic("zupzup-all", "전국 줍줍", "/match/zupzup", false));

    public static Optional<Topic> bySlug(String slug) { return ALL.stream().filter(topic -> topic.slug().equals(slug)).findFirst(); }
    /** 구독 범위에 저장할 수 있는 slug: 생활권 또는 알림 주제. */
    public static boolean valid(String slug) { return LivingAreas.bySlug(slug).isPresent() || bySlug(slug).isPresent(); }
    /** 알림의 '새 공고 모아보기' 버튼 주소. */
    public static String url(String slug) { return bySlug(slug).map(Topic::url).orElse("/areas/" + slug); }
}
