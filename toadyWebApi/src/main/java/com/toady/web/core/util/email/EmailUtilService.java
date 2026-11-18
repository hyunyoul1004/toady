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
 *
 * 메일 발송 창구. 구현은 EmailUtilImpl 하나이고, 종류(인증·가입·알림)마다 MailBudget의 하루 한도가 따로 걸린다.
 * 서비스 쪽은 이 인터페이스에만 의존해서 테스트에서는 가짜 구현으로 바꿔 끼울 수 있다.
 */
public interface EmailUtilService {

    /** 관심 일정 알림도 같은 Gmail을 사용하며 별도 할당량을 적용한다. */
    default void sendReminder(String toAddress, String subject, String body) throws MessagingException { sendEmail(toAddress, subject, body); }
    /** 수신 해제 주소를 List-Unsubscribe(원클릭 해제) 헤더로 함께 보낸다. 구현이 헤더를 지원하지 않으면 본문 링크만 남는다. */
    default void sendReminder(String toAddress, String subject, String body, String unsubscribeUrl) throws MessagingException { sendReminder(toAddress, subject, body); }

    /** 인증 코드·비밀번호 찾기 등 계정 관련 메일. AUTH 한도를 쓴다. */
    void sendEmail(String toAddress, String subject, String body) throws MessagingException;

    /** 회원가입 인증 메일. 하루 상한을 따로 두어 대량 가입 시도가 비밀번호 찾기 메일까지 막지 않게 한다. */
    default void sendSignupEmail(String toAddress, String subject, String body) throws MessagingException { sendEmail(toAddress, subject, body); }

}
