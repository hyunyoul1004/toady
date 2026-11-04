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

    void sendEmail(String toAddress, String subject, String body) throws MessagingException;

}
