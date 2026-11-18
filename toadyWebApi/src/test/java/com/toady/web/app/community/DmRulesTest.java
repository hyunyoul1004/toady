package com.toady.web.app.community;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 쪽지 내용 규칙: 첫 쪽지의 연락처·링크 차단과 거래 의심 문구 경고. 일상 표현은 걸리지 않아야 한다. */
class DmRulesTest {
    @Test void everydayHousingTalkIsNotTreatedAsContact() {
        for (String text : new String[]{
                "2026-11-16에 특공 접수했어요", "분양가 3억5000만원이면 괜찮네요", "101동 1203호 근처예요",
                "84㎡ 경쟁률 12.5:1 이었어요", "온라인으로 신청했어요", "텔레비전 소음은 어떤가요", "청약홈 공고 2026000530 봤어요",
                "라인이 예쁘네요", "전용 59타입 가점 64점", "공고번호 2025000123 확인해보세요", "15억이면 1500000000원",
                "온라인 아이디 만들었어요", "가이드라인으로 정했어요", "공고 2025000123 2025000124 둘 다 봤어요"}) {
            assertNull(DmRules.check(text, true), text);
        }
    }

    @Test void contactsAndLinksAreBlockedOnlyBeforeTheOtherSideReplies() {
        for (String text : new String[]{
                "010-1234-5678로 연락 주세요", "01012345678", "010 1234 5678", "02-123-4567",
                "https://example.com 보세요", "www.example.kr", "open.kakao.com/o/abc", "오픈채팅 들어오세요",
                "카톡 아이디 toady 입니다", "국민 123456-78-901234 로 보내 주세요", "1234567890123",
                "+82 10 1234 5678", "031)123-4567", "110 123 456789", "naver . com", "카톡 주세요 abc123", "텔레그램 @abcde",
                "메일 toady@example.com 으로 주세요"}) {
            assertEquals(DmRules.Problem.CONTACT, DmRules.check(text, true), text);
            assertNull(DmRules.check(text, false), "대화가 이어진 뒤에는 막지 않는다: " + text);
        }
    }

    @Test void emptyAndTooLongMessagesAreRejected() {
        assertEquals(DmRules.Problem.EMPTY, DmRules.check("   ", false));
        assertEquals(DmRules.Problem.EMPTY, DmRules.check(null, false));
        assertEquals(DmRules.Problem.TOO_LONG, DmRules.check("가".repeat(DmRules.MAX_LENGTH + 1), false));
        assertNull(DmRules.check("가".repeat(DmRules.MAX_LENGTH), false));
    }

    @Test void tradeWordsAreFlaggedButOrdinaryWordsAreNot() {
        for (String text : new String[]{"청약통장 양도하실 분", "통장 삽니다", "분양권 전매 가능해요", "프리미엄 드립니다", "선입금 부탁드려요", "명의 빌려 주실 분",
                "청약통장 팔아요", "청약통장 사요", "청약통장 넘겨 드려요"}) {
            assertTrue(DmRules.flagged(text), text);
        }
        for (String text : new String[]{"통장 가입 기간이 궁금해요", "전매 제한 기간이 몇 년이죠?", "피곤하네요", "입금은 계약 때 하나요?",
                "청약통장 있는데 사용 기간이 궁금해요", "청약통장 가입했는데 사실 잘 몰라요", "청약통장 사람마다 다르죠"}) {
            assertFalse(DmRules.flagged(text), text);
        }
    }

    /** 운영자가 숨긴 공고에서 시작한 대화는 단지 이름 대신 일반 문구를 보여 준다(DB를 읽지 않는다). */
    @Test void hiddenAnnouncementNameIsNotShownInDirectMessages() {
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var dm = new DmService(jdbc, null, null, null, null, 0, false, 5, 50);
        var hidden = org.mockito.Mockito.mock(com.toady.web.app.planner.HiddenAnnouncements.class);
        org.mockito.Mockito.when(hidden.hiddenId("apt/H1/N1")).thenReturn(true);
        dm.hidden = hidden;
        assertEquals(DmService.HIDDEN_HOUSE_NAME, dm.houseName("apt/H1/N1"));
        org.mockito.Mockito.verifyNoInteractions(jdbc);
        assertNull(dm.houseName(null));
    }
}
