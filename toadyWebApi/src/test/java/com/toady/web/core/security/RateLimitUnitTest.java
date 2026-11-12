package com.toady.web.core.security;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;

/** 요청 제한 키와 로그인 실패 제한 규칙. */
class RateLimitUnitTest {
    @Test void ipv6AddressesInTheSame64AreOneKey() {
        assertEquals(RequestLimitFilter.limitKey("2001:db8:1:2:aaaa::1"), RequestLimitFilter.limitKey("2001:db8:1:2:ffff:1:2:3"));
        assertNotEquals(RequestLimitFilter.limitKey("2001:db8:1:2::1"), RequestLimitFilter.limitKey("2001:db8:1:3::1"));
        assertEquals("203.0.113.9", RequestLimitFilter.limitKey("203.0.113.9"));
    }
    @Test void attackerCannotLockOutTheOwnerFromAnotherNetwork() {
        var limiter = new LoginAttemptLimiter(Clock.systemUTC());
        for (int i = 0; i < 5; i++) limiter.failed("198.51.100.7", "victim@example.test");
        assertTrue(limiter.blocked("198.51.100.7", "victim@example.test"), "같은 IP에서 같은 이메일 5회 실패는 막는다");
        assertFalse(limiter.blocked("203.0.113.20", "victim@example.test"), "다른 네트워크의 주인은 로그인할 수 있다");
        for (int i = 0; i < 25; i++) limiter.failed("192.0.2." + i, "victim@example.test");
        assertTrue(limiter.blocked("203.0.113.20", "victim@example.test"), "여러 IP로 나눈 30회 실패는 이메일 단위로 늦춘다");
        limiter.succeeded("203.0.113.20", "victim@example.test");
        assertFalse(limiter.blocked("203.0.113.20", "victim@example.test"));
        // 로그인에 성공한 적 있는 네트워크의 주인은, 다른 IP들로 다시 30회를 채워도 이메일 전체 한도에 막히지 않는다.
        for (int i = 0; i < 30; i++) limiter.failed("192.0.2." + (100 + i), "victim@example.test");
        assertFalse(limiter.blocked("203.0.113.20", "victim@example.test"));
        assertTrue(limiter.blocked("203.0.113.99", "victim@example.test"), "처음 보는 네트워크는 여전히 늦춘다");
    }
}
