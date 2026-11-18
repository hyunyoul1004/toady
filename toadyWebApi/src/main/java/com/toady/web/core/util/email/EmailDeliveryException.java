package com.toady.web.core.util.email;

/**
 * 메일을 보내지 못했을 때(SMTP 실패, MailGate 대기 시간 초과 등) 호출부에 알리는 예외.
 * RuntimeException이라 throws 선언 없이 위로 올라가고, 트랜잭션 안이면 롤백을 일으킨다.
 */
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    /** 원인을 따라가며 하루 메일 발송량(MailBudget)이 다 찬 실패인지 본다. 호출부가 502·500 대신 '잠시 후 다시' 안내(429)를 고를 때 쓴다. */
    public static boolean quotaReached(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof com.toady.web.app.planner.MailQuotaException) return true;
        }
        return false;
    }
}
