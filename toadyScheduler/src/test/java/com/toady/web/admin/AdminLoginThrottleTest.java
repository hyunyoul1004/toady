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

    @Test void countsInFlightAttemptsSoConcurrentRequestsCannotBypassTheLimit() throws Exception {
        var throttle = new AdminLoginThrottle(clock);
        int threads = 40;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var start = new java.util.concurrent.CountDownLatch(1);
        var allowed = new java.util.concurrent.atomic.AtomicInteger();
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    if (throttle.tryBegin("1.2.3.4")) allowed.incrementAndGet();
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        // 비밀번호 확인이 끝나기 전에 동시에 들어온 요청도 상한까지만 통과한다.
        assertEquals(AdminLoginThrottle.MAX_FAILURES, allowed.get());
        for (int i = 0; i < AdminLoginThrottle.MAX_FAILURES; i++) throttle.failed("1.2.3.4");
        assertTrue(throttle.isLocked("1.2.3.4"));
        assertFalse(throttle.tryBegin("1.2.3.4"));
    }

    @Test void finishedOrAbandonedAttemptsReleaseTheirSlot() {
        var throttle = new AdminLoginThrottle(clock);
        for (int i = 0; i < 4; i++) { assertTrue(throttle.tryBegin("1.2.3.4")); throttle.failed("1.2.3.4"); }
        assertTrue(throttle.tryBegin("1.2.3.4"));
        // 실패 4회 + 진행 중 1회면 같은 IP의 추가 시도는 결과가 나올 때까지 막는다.
        assertFalse(throttle.tryBegin("1.2.3.4"));
        throttle.abandoned("1.2.3.4");
        assertFalse(throttle.isLocked("1.2.3.4"));
        assertTrue(throttle.tryBegin("1.2.3.4"));
        throttle.succeeded("1.2.3.4");
        for (int i = 0; i < AdminLoginThrottle.MAX_FAILURES; i++) assertTrue(throttle.tryBegin("1.2.3.4"));
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
