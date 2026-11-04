package com.toady.web.core.util.email;

/** SMTP 이메일 전송 실패를 호출 계층에 전달합니다. */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
