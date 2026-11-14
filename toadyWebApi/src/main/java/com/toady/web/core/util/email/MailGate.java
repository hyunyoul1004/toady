package com.toady.web.core.util.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 메일을 보내는 요청(가입·가입 메일 재발송·비밀번호 찾기)의 동시 실행 수를 트랜잭션 밖에서 제한한다.
 * 이 요청들은 트랜잭션 연결 하나를 잡은 채 발송 한도 확인(MailBudget, REQUIRES_NEW)에 연결을 하나 더 쓰고 SMTP 전송까지 기다린다.
 * 제한이 없으면 요청이 몰릴 때 모든 연결이 두 번째 연결을 서로 기다려 다른 API까지 멈추므로, 연결을 잡기 전에 순서를 기다리게 한다.
 */
@Component
public class MailGate {
    private final Semaphore permits;
    private final long waitMillis;

    public MailGate(@Value("${toady.security.mail-concurrency:4}") int concurrency,
                    @Value("${toady.security.mail-gate-wait-ms:8000}") long waitMillis) {
        if (concurrency < 1) throw new IllegalArgumentException("메일 동시 처리 수는 1 이상이어야 합니다.");
        this.permits = new Semaphore(concurrency, true);
        this.waitMillis = Math.max(0, waitMillis);
    }

    /** 차례를 기다렸다가 실행한다. 기다리는 시간이 지나면 발송 실패(EmailDeliveryException)로 돌려 호출부의 기존 처리를 따른다. */
    public <T> T run(Supplier<T> work) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EmailDeliveryException("메일 요청 대기 중 중단되었습니다.", interrupted);
        }
        if (!acquired) throw new EmailDeliveryException("메일 요청이 몰려 처리하지 못했습니다.", null);
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }
}
