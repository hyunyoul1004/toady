package com.toady.web.app.announcement;

import com.toady.web.app.announcement.dto.AnnouncementListItem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 웹 tests/lh-receipt.test.mjs와 같은 사례(2026-11 청약홈 캘린더와 대조한 실제 공고). */
class ReceiptChannelTest {
    @Test
    void LH청약플러스_접수_판정() {
        // 안산신길2 A-6블록 신혼희망타운(2026820013): 청약홈 캘린더에 없음
        assertThat(ReceiptChannel.of("한국토지주택공사 경기남부지역본부", "https://apply.lh.or.kr/", null)).isEqualTo("LH");
        assertThat(ReceiptChannel.of("한국토지주택공사경기남부지역본부", "apply.lh.or.kr", "")).isEqualTo("LH");
        // 고덕신도시 아테라(2026000124): LH 사업이지만 청약홈 접수(특별공급 접수일 있음, 분양 홈페이지)
        assertThat(ReceiptChannel.of("한국토지주택공사 경기남부지역본부", "https://www.arteragd.co.kr", "2026-04-27")).isNull();
        assertThat(ReceiptChannel.of("한국토지주택공사 경기남부지역본부", "https://apply.lh.or.kr", "2026-11-16")).isNull();
        // 삼도이동 토지임대부(2026000434): 제주개발공사, 청약홈 접수
        assertThat(ReceiptChannel.of("제주특별자치도개발공사", "https://samdo.jpdc.co.kr", "2026-11-16")).isNull();
        assertThat(ReceiptChannel.of("한국토지주택공사", "https://apply.lh.or.kr.example.com", null)).isNull();
        assertThat(ReceiptChannel.of(null, null, null)).isNull();
    }

    @Test
    void 예전_생성자는_접수처_없이_만든다() {
        AnnouncementListItem item = new AnnouncementListItem("apt", "H", "P", "단지", "01", "APT", "100", "서울", "", "", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, "10", 0L);
        assertThat(item.receiptChannel()).isNull();
        AnnouncementListItem lh = new AnnouncementListItem("apt", "H", "P", "단지", "01", "APT", "100", "서울", "", "", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, "10", 0L, "LH");
        assertThat(lh.withCommentCount(3).receiptChannel()).isEqualTo("LH");
    }
}
