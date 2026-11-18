package com.toady.web.admin;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 중지·거절·실패로 끝난 작업 문구에 일부 실패 내역이 남는지(운영자가 빠진 공고가 있던 기간을 알 수 있게). */
class AdminJobMessageTest {
    @Test void partialFailuresAreKeptInEveryEndingMessage() {
        String message = AdminJobService.withNotes("청약홈 API가 요청을 거절해 중단했습니다(HTTP 429).", List.of("2026-01-01~2026-01-31: 주택형 3건 실패"), "2026-02-01~2026-02-28: 주택형 1건 실패");
        assertTrue(message.contains("2026-01-01~2026-01-31: 주택형 3건 실패"), message);
        assertTrue(message.contains("2026-02-01~2026-02-28: 주택형 1건 실패"), message);
        assertEquals("작업을 중지했습니다.", AdminJobService.withNotes("작업을 중지했습니다.", List.of(), null), "일부 실패가 없으면 문구 그대로");
        String longNote = "가".repeat(2000);
        assertEquals(901, AdminJobService.withNotes("실패", List.of(longNote), null).length(), "900자에서 자르고 말줄임표");
    }

    @Test void remainingPeriodIsTheUnfinishedOlderChunks() {
        var chunks = AdminJobService.chunks(java.time.LocalDate.of(2026, 1, 15), java.time.LocalDate.of(2026, 4, 10));
        // 최근 구간부터 돌므로 2개(4월·3월)를 끝냈으면 1월 15일~2월 28일이 남는다.
        assertEquals(new AdminJobService.DateRange(java.time.LocalDate.of(2026, 1, 15), java.time.LocalDate.of(2026, 2, 28)),
                AdminJobService.remainingRange(chunks, 2));
        assertTrue(AdminJobService.remainingNote(chunks, 2).contains("남은 기간: 2026-01-15 ~ 2026-02-28(2개 구간)"));
        // 끝낸 구간이 없거나 모두 끝냈으면 전체 기간을 다시 돈다.
        var whole = new AdminJobService.DateRange(java.time.LocalDate.of(2026, 1, 15), java.time.LocalDate.of(2026, 4, 10));
        assertEquals(whole, AdminJobService.remainingRange(chunks, 0));
        assertEquals(whole, AdminJobService.remainingRange(chunks, 4));
        assertEquals("", AdminJobService.remainingNote(chunks, 4));
        assertTrue(AdminJobService.remainingNote(chunks, 0).contains("전체 기간"));
    }
}
