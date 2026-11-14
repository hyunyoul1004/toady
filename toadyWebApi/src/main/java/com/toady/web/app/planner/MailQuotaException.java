package com.toady.web.app.planner;

import org.springframework.mail.MailSendException;


public class MailQuotaException extends MailSendException {
    public MailQuotaException() { super("메일 발송량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요."); }
}
