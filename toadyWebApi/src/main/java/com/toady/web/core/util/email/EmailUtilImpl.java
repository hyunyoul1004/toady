package com.toady.web.core.util.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * packageName    : com.toady.web.core.util
 * fileName       : EmailUtil
 * author         : youn
 * date           : 2022/06/11
 * description    : Email 전송 클래스.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/06/11        youn       최초 생성
 */
@Component
public class EmailUtilImpl implements EmailUtilService {

    @Autowired
    private JavaMailSender sender;

    @Autowired
    private com.toady.web.app.planner.MailBudget budget;

    @Override
    public void sendEmail(String toAddress, String subject, String body) { send(toAddress, subject, body, false); }

    @Override
    public void sendReminder(String toAddress, String subject, String body) { send(toAddress, subject, body, true); }

    private void send(String toAddress, String subject, String body, boolean reminder) {
        if (!budget.reserve(reminder)) throw new com.toady.web.app.planner.MailQuotaException();

        MimeMessage message = sender.createMimeMessage();
        //MimeMessageHelper helper = new MimeMessageHelper(message);

        try {

            // true로서 멀티파트 메세지라는 의미
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, true);
        } catch (MessagingException e) {
            throw new IllegalStateException("이메일 메시지를 구성할 수 없습니다.", e);
        }

        sender.send(message);
    }
}
