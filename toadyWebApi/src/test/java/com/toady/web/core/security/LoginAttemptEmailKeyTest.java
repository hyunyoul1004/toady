package com.toady.web.core.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/** 로그인 실패 제한 키는 정규화한(앞뒤 공백 제거·소문자) 이메일 기준이라 대소문자를 바꿔도 같은 계정으로 센다. */
class LoginAttemptEmailKeyTest {
    @Test void caseAndSpacingVariantsShareTheSameFailureCount() {
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(Clock.systemUTC());
        String[] variants = {"Me@Example.test", "me@example.test", " ME@EXAMPLE.TEST ", "mE@example.TEST", "me@EXAMPLE.test"};
        for (int i = 0; i < LoginAttemptLimiter.PER_EMAIL_IP; i++) assertTrue(limiter.tryAcquire("192.0.2.10", variants[i]));
        assertTrue(limiter.blocked("192.0.2.10", "ME@example.test"), "대소문자를 바꾼 주소도 같은 IP·이메일 한도에 걸린다");
    }
}
