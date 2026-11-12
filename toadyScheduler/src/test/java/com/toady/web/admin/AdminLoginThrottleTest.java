package com.toady.web.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AdminLoginThrottleTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-12T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test void locksAfterFiveFailuresAndUnlocksAfterFifteenMinutes() {
        var throttle = new AdminLoginThrottle(clock);
        for (int i = 0; i < 4; i++) throttle.failed("1.2.3.4");
        assertFalse(throttle.isLocked("1.2.3.4"));
        throttle.failed("1.2.3.4");
        assertTrue(throttle.isLocked("1.2.3.4"));
        assertFalse(throttle.isLocked("5.6.7.8"));
        now.set(now.get().plus(AdminLoginThrottle.LOCK));
        assertFalse(throttle.isLocked("1.2.3.4"));
        // 잠금이 끝나면 실패 횟수도 새로 센다.
        throttle.failed("1.2.3.4");
        assertFalse(throttle.isLocked("1.2.3.4"));
    }

    @Test void failuresOutsideTheWindowAndSuccessResetTheCount() {
        var throttle = new AdminLoginThrottle(clock);
        for (int i = 0; i < 4; i++) throttle.failed("1.2.3.4");
        now.set(now.get().plus(AdminLoginThrottle.WINDOW));
        throttle.failed("1.2.3.4");
        assertFalse(throttle.isLocked("1.2.3.4"));
        for (int i = 0; i < 3; i++) throttle.failed("1.2.3.4");
        throttle.succeeded("1.2.3.4");
        throttle.failed("1.2.3.4");
        assertFalse(throttle.isLocked("1.2.3.4"));
    }

    @Test void trustsXRealIpOnlyFromLoopbackOrPrivateProxies() {
        assertEquals("198.51.100.9", ip("172.18.0.5", "198.51.100.9"));
        assertEquals("198.51.100.9", ip("127.0.0.1", "198.51.100.9"));
        // 외부에서 직접 온 요청의 헤더는 위장일 수 있으므로 무시한다.
        assertEquals("203.0.113.7", ip("203.0.113.7", "198.51.100.9"));
        // IP가 아닌 값(호스트 이름 등)은 받지 않는다.
        assertEquals("172.18.0.5", ip("172.18.0.5", "evil.example.com"));
        assertEquals("172.18.0.5", ip("172.18.0.5", null));
        assertTrue(AdminLoginThrottle.isInternal("fd00::1"));
        assertFalse(AdminLoginThrottle.isInternal("2001:db8::1"));
    }

    private static String ip(String remote, String header) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        if (header != null) request.addHeader("X-Real-IP", header);
        return AdminLoginThrottle.clientIp(request);
    }
}
