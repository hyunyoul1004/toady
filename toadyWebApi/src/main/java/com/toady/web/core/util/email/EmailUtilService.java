package com.toady.web.core.util.email;

import jakarta.mail.MessagingException;

/**
 * packageName    : com.toady.web.core.util.email
 * fileName       : EmailUtilService
 * author         : youn
 * date           : 2022/06/11
 * description    : email 전송 서비스.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
public interface EmailUtilService {

    /** 관심 일정 알림도 같은 Gmail을 사용하며 별도 할당량을 적용한다. */
    default void sendReminder(String toAddress, String subject, String body) throws MessagingException { sendEmail(toAddress, subject, body); }

    void sendEmail(String toAddress, String subject, String body) throws MessagingException;

    /** 회원가입 인증 메일. 하루 상한을 따로 두어 대량 가입 시도가 비밀번호 찾기 메일까지 막지 않게 한다. */
    default void sendSignupEmail(String toAddress, String subject, String body) throws MessagingException { sendEmail(toAddress, subject, body); }

}
