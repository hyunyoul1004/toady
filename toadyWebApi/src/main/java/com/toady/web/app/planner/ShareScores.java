package com.toady.web.app.planner;

import com.toady.web.app.announcement.service.AnnouncementService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

/** 공개된 주택형·지역 전체의 최저/최고만 요약한다. 평균들의 평균이나 미공개 점수는 만들지 않는다. */
@Service
public class ShareScores {
    public record Range(String minimum, String maximum) {
        public boolean available() { return !minimum.isEmpty() || !maximum.isEmpty(); }
        public String text() {
            return available() ? "공개 당첨가점 · " + (minimum.isEmpty() ? "" : "최저 " + minimum + "점")
                    + (!minimum.isEmpty() && !maximum.isEmpty() ? " / " : "")
                    + (maximum.isEmpty() ? "" : "최고 " + maximum + "점") : "";
        }
    }
    private final AnnouncementService announcements;
    public ShareScores(AnnouncementService announcements) { this.announcements = announcements; }
    public Range get(String type, String house, String notice) {
        if (!type.equals("apt")) return new Range("", "");
        var rows = announcements.getAptScores(house, notice);
        return range(rows.stream().map(row -> row.getLwetScore()).toList(), rows.stream().map(row -> row.getTopScore()).toList());
    }
    static Range range(List<String> lows, List<String> highs) {
        var low = lows.stream().map(ShareScores::number).filter(java.util.Objects::nonNull).min(BigDecimal::compareTo);
        var high = highs.stream().map(ShareScores::number).filter(java.util.Objects::nonNull).max(BigDecimal::compareTo);
        return new Range(low.map(n -> n.stripTrailingZeros().toPlainString()).orElse(""), high.map(n -> n.stripTrailingZeros().toPlainString()).orElse(""));
    }
    private static BigDecimal number(String value) {
        try {
            if (value == null || !value.trim().matches("[0-9]+(?:\\.[0-9]+)?")) return null;
            BigDecimal number = new BigDecimal(value.trim());
            return number.signum() > 0 && number.compareTo(BigDecimal.valueOf(84)) <= 0 ? number : null;
        } catch (NumberFormatException invalid) { return null; }
    }
}
