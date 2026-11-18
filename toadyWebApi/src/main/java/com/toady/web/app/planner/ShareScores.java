package com.toady.web.app.planner;

import com.toady.web.app.announcement.service.AnnouncementService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * 공유 카드·공개 페이지에 쓰는 당첨가점 요약. AnnouncementService.getAptScores()로 APT 주택형별 가점을 읽는다.
 * 공개된 주택형·지역 전체의 최저/최고만 요약한다. 평균들의 평균이나 미공개 점수는 만들지 않는다.
 * PublicAnnouncementPage(공고 공개 페이지·PNG 카드)가 부른다.
 */
// @Service: 스프링이 이 클래스를 하나 만들어(빈) 필요한 곳에 생성자로 넣어 준다.
@Service
public class ShareScores {
    /** 최저·최고 가점 문자열. 값이 없으면 빈 문자열이다. */
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
    /** APT 공고만 가점이 공개되므로 다른 유형은 빈 범위를 돌려준다. */
    public Range get(String type, String house, String notice) {
        if (!type.equals("apt")) return new Range("", "");
        var rows = announcements.getAptScores(house, notice);
        return range(rows.stream().map(row -> row.getLwetScore()).toList(), rows.stream().map(row -> row.getTopScore()).toList());
    }
    static Range range(List<String> lows, List<String> highs) {
        var low = lows.stream().map(ShareScores::number).filter(Objects::nonNull).min(BigDecimal::compareTo);
        var high = highs.stream().map(ShareScores::number).filter(Objects::nonNull).max(BigDecimal::compareTo);
        return new Range(low.map(n -> n.stripTrailingZeros().toPlainString()).orElse(""), high.map(n -> n.stripTrailingZeros().toPlainString()).orElse(""));
    }
    /** 0 초과 84 이하의 숫자만 가점으로 인정한다(가점 만점이 84점). 그 밖의 값은 미공개로 본다. */
    static BigDecimal number(String value) {
        try {
            if (value == null || !value.trim().matches("[0-9]+(?:\\.[0-9]+)?")) return null;
            BigDecimal number = new BigDecimal(value.trim());
            return number.signum() > 0 && number.compareTo(BigDecimal.valueOf(84)) <= 0 ? number : null;
        } catch (NumberFormatException invalid) { return null; }
    }
}
