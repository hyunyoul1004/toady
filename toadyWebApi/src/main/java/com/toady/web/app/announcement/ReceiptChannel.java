package com.toady.web.app.announcement;

import java.net.URI;
import java.util.Locale;

/**
 * 청약 신청을 어디서 받는지(접수처) 판정한다. 웹 features/announcement/receiptChannel.js와 같은 규칙이다(바꾸면 함께 고친다).
 * "LH": 한국토지주택공사가 LH청약플러스(apply.lh.or.kr)에서 직접 접수하는 공고(신혼희망타운·공공분양 본청약 등).
 * 청약홈 공개 API에는 있지만 청약홈 청약 캘린더에는 나오지 않는다(2026-11 청약홈 캘린더와 대조).
 * 판정 근거(셋 다 맞아야 한다): ① 사업주체명(BSNS_MBY_NM)에 '한국토지주택공사' ② 홈페이지(HMPG_ADRES)가 apply.lh.or.kr
 * ③ 특별공급 접수일(SPSPLY_RCEPT_BGNDE)이 없음. 청약홈에서 받는 LH 공공분양(예: 고덕신도시 아테라)은 특별공급 접수일이 있고
 * 홈페이지가 분양 홍보 사이트라 여기에 걸리지 않는다. 확실하지 않으면 null(청약홈 접수로 본다).
 * 쓰는 곳: AnnouncementListMapper(목록 응답 receiptChannel).
 */
public final class ReceiptChannel {
    public static final String LH = "LH";
    private static final String LH_HOST = "apply.lh.or.kr";

    private ReceiptChannel() {
    }

    /** APT 공고의 접수처. LH청약플러스 접수면 "LH", 아니면 null. */
    public static String of(String businessOwner, String homepage, String specialSupplyStart) {
        String owner = businessOwner == null ? "" : businessOwner.replaceAll("\\s+", "");
        String host = hostOf(homepage);
        boolean hasSpecial = specialSupplyStart != null && !specialSupplyStart.isBlank();
        boolean lhHost = LH_HOST.equals(host) || ("www." + LH_HOST).equals(host);
        return owner.contains("한국토지주택공사") && lhHost && !hasSpecial ? LH : null;
    }

    static String hostOf(String value) {
        if (value == null || value.isBlank()) return "";
        String raw = value.trim();
        try {
            String host = URI.create(raw.matches("(?i)^https?://.*") ? raw : "https://" + raw).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
