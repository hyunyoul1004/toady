package com.toady.web.app.planner;

import org.springframework.mail.MailSendException;


/**
 * 메일 발송량(Gmail 한 계정의 하루 한도)이 다 찼을 때 던지는 예외.
 * MailSendException을 상속해서 기존 메일 실패 처리 흐름에 그대로 섞인다.
 * PlannerReminder는 이 예외만 따로 잡아 '보내지 못함'으로 보고 오늘 기록을 지운다(나중에 다시 시도할 수 있게).
 */
public class MailQuotaException extends MailSendException {
    public MailQuotaException() { super("메일 발송량 제한에 도달했습니다. 잠시 후 다시 시도해 주세요."); }
}
