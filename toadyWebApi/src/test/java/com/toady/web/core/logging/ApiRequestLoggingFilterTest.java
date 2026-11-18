package com.toady.web.core.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 요청 로그에 캘린더 구독 토큰(주소 자체가 열람 권한)이 남지 않는지 확인한다. */
class ApiRequestLoggingFilterTest {
    @Test
    void masksTheCalendarFeedTokenButKeepsOtherPaths() {
        String token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde";
        assertEquals("/api/v1/public/calendar/***.ics", ApiRequestLoggingFilter.maskedPath("/api/v1/public/calendar/" + token + ".ics"));
        assertEquals("/api/v1/public/calendar/***", ApiRequestLoggingFilter.maskedPath("/api/v1/public/calendar/" + token));
        // 생활권 공개 캘린더와 다른 주소는 그대로 남긴다
        assertEquals("/api/v1/public/areas/unjeong/calendar.ics", ApiRequestLoggingFilter.maskedPath("/api/v1/public/areas/unjeong/calendar.ics"));
        assertEquals("/api/v1/me/account", ApiRequestLoggingFilter.maskedPath("/api/v1/me/account"));
        assertEquals("", ApiRequestLoggingFilter.maskedPath(null));
    }
}
