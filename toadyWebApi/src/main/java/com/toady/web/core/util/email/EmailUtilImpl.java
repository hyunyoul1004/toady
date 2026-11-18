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
 *
 * JavaMailSender(SMTP, Gmail 계정)로 HTML 메일을 실제로 보낸다.
 * 보내기 전에 MailBudget.reserve()로 종류별 하루 한도(TB_MAIL_ATTEMPT 기록 수, TB_MAIL_BUDGET 행 잠금)를 먼저 잡고, 넘으면 MailQuotaException을 던진다.
 */
// @Component: 스프링이 이 클래스를 빈으로 등록한다. EmailUtilService를 주입받는 곳에 이 객체가 들어간다
@Component
public class EmailUtilImpl implements EmailUtilService {

    // @Autowired: 스프링이 맞는 타입의 빈을 필드에 직접 넣어 준다(필드 주입). 새 코드는 생성자 주입을 쓴다
    @Autowired
    private JavaMailSender sender;

    @Autowired
    private MailBudget budget;

    /**
     * 보내는 사람. 비우면 SMTP 로그인 계정(Gmail)이 그대로 보낸 사람이 된다.
     * Amazon SES·네이버 웍스 같은 발송 서비스로 옮기면 SMTP 계정이 주소가 아니므로 확인된 발신 주소를 MAIL_FROM에 넣는다.
     */
    // @Value: 설정 키 값을 읽는다. 콜론 뒤는 값이 없을 때 쓰는 기본값(여기서는 빈 문자열)
    @org.springframework.beans.factory.annotation.Value("${toady.mail.from:}")
    private String from;
    @org.springframework.beans.factory.annotation.Value("${toady.mail.from-name:TOADY}")
    private String fromName;

    @Override
    public void sendEmail(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.AUTH); }

    @Override
    public void sendSignupEmail(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.SIGNUP); }

    @Override
    public void sendReminder(String toAddress, String subject, String body) { send(toAddress, subject, body, MailBudget.Kind.REMINDER, null); }

    @Override
    public void sendReminder(String toAddress, String subject, String body, String unsubscribeUrl) { send(toAddress, subject, body, MailBudget.Kind.REMINDER, unsubscribeUrl); }

    private void send(String toAddress, String subject, String body, MailBudget.Kind kind) { send(toAddress, subject, body, kind, null); }

    /**
     * 한도 확인 → 메시지 구성 → SMTP 전송 순서로 처리한다.
     * 한도는 전송 전에 차감하므로 SMTP가 실패해도 그 1통은 사용한 것으로 남는다.
     */
    private void send(String toAddress, String subject, String body, MailBudget.Kind kind, String unsubscribeUrl) {
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
            // Gmail 등 메일 앱의 '수신 거부' 버튼(RFC 8058 원클릭 해제). 주소는 https만 넣는다.
            if (unsubscribeUrl != null && unsubscribeUrl.startsWith("https://")) {
                message.setHeader("List-Unsubscribe", "<" + unsubscribeUrl + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException("이메일 메시지를 구성할 수 없습니다.", e);
        }

        sender.send(message);
    }
}
