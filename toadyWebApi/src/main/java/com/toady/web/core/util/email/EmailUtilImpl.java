package com.toady.web.core.util.email;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import com.toady.web.app.planner.MailBudget;
import com.toady.web.app.planner.MailQuotaException;

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
    private MailBudget budget;

    /**
     * 보내는 사람. 비우면 SMTP 로그인 계정(Gmail)이 그대로 보낸 사람이 된다.
     * Amazon SES·네이버 웍스 같은 발송 서비스로 옮기면 SMTP 계정이 주소가 아니므로 확인된 발신 주소를 MAIL_FROM에 넣는다.
     */
    @org.springframework.beans.factory.annotation.Value("${toady.mail.from:}")
    private String from;
    @org.springframework.beans.factory.annotation.Value("${toady.mail.from-name:TOADY}")
    private String fromName;

    @Override
    public void sendEmail(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.AUTH); }

    @Override
    public void sendSignupEmail(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.SIGNUP); }

    @Override
    public void sendReminder(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.REMINDER); }

    private void send(String toAddress, String subject, String body, MailBudget.Kind kind) {
        if (!budget.reserve(kind)) throw new MailQuotaException();

        MimeMessage message = sender.createMimeMessage();
        //MimeMessageHelper helper = new MimeMessageHelper(message);

        try {

            // true로서 멀티파트 메세지라는 의미
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (from != null && !from.isBlank()) helper.setFrom(from.trim(), fromName);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(body, true);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("이메일 메시지를 구성할 수 없습니다.", e);
        }

        sender.send(message);
    }
}
