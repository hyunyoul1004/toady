package com.toady.web.core.util.email;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MailGateTest {
    @Test void runsWorkAndReleasesTheTurn() {
        var gate = new MailGate(1, 100);
        assertEquals("a", gate.run(() -> "a"));
        assertEquals("b", gate.run(() -> "b"), "끝난 요청은 차례를 돌려준다");
        assertThrows(IllegalStateException.class, () -> gate.run(() -> { throw new IllegalStateException(); }));
        assertEquals("c", gate.run(() -> "c"), "실패한 요청도 차례를 돌려준다");
    }

    @Test void requestsBeyondTheLimitFailAsMailDeliveryAfterWaiting() throws Exception {
        var gate = new MailGate(1, 50);
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        Thread holder = new Thread(() -> gate.run(() -> { started.countDown(); try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { } return null; }));
        holder.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        // 호출부(가입·비밀번호 찾기)는 이미 이 예외를 발송 실패로 처리한다.
        assertThrows(EmailDeliveryException.class, () -> gate.run(() -> "late"));
        release.countDown();
        holder.join();
    }
}
