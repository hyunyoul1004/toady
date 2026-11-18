package com.toady.web.core.util.email;

/**
 * 메일을 보내지 못했을 때(SMTP 실패, MailGate 대기 시간 초과 등) 호출부에 알리는 예외.
 * RuntimeException이라 throws 선언 없이 위로 올라가고, 트랜잭션 안이면 롤백을 일으킨다.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
