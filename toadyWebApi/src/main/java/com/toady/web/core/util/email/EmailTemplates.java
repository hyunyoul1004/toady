package com.toady.web.core.util.email;

import org.springframework.web.util.HtmlUtils;

/** 외부 이미지 없이 읽을 수 있는 공통 메일 레이아웃. 사용자 입력과 URL은 HTML 이스케이프한다. */
public final class EmailTemplates {
    private EmailTemplates() { }

    public static String accountVerification(String nickname, String url, String domain) {
        String body = """
                <p style="margin:0 0 12px;color:#334155;font-size:16px;line-height:1.8;">%s님, 안녕하세요.</p>
                <p style="margin:0;color:#64748b;font-size:15px;line-height:1.8;">TOADY 회원가입을 마무리하려면 아래 버튼을 눌러 이메일을 인증해 주세요.</p>
                <table role="presentation" cellspacing="0" cellpadding="0" style="margin:28px 0;"><tr><td bgcolor="#0f172a" style="border-radius:10px;">
                  <a href="%s" style="display:inline-block;padding:16px 28px;color:#ffffff;font-size:15px;font-weight:700;text-decoration:none;">이메일 인증하기 &rarr;</a>
                </td></tr></table>
                <p style="margin:0;color:#475569;font-size:13px;line-height:1.8;">이 링크는 <strong>발급 후 24시간</strong> 동안 유효합니다.<br>만료된 경우 로그인 화면에서 인증 메일을 다시 받을 수 있습니다.</p>
                <div style="margin-top:24px;padding-top:20px;border-top:1px solid #e2e8f0;">
                  <p style="margin:0 0 8px;color:#94a3b8;font-size:12px;line-height:1.7;">버튼이 열리지 않으면 아래 주소를 복사해 브라우저에 붙여 넣어 주세요.</p>
                  <a href="%s" style="color:#475569;font-size:12px;line-height:1.7;word-break:break-all;">%s</a>
                </div>
                """.formatted(html(nickname), html(url), html(url), html(url));
        return frame("이메일을 인증해 주세요", "TOADY 가입을 완료하는 마지막 단계입니다. 인증 링크는 24시간 동안 유효합니다.", body, domain);
    }

    public static String passwordReset(String nickname, String code, String domain) {
        String body = """
                <p style="margin:0 0 12px;color:#334155;font-size:16px;line-height:1.8;">%s님, 안녕하세요.</p>
                <p style="margin:0;color:#64748b;font-size:15px;line-height:1.8;">비밀번호 변경 화면에 아래 인증코드를 입력해 주세요.</p>
                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="margin:28px 0;"><tr><td align="center" bgcolor="#f1f5f9" style="padding:24px 12px;border:1px solid #e2e8f0;border-radius:12px;">
                  <p style="margin:0 0 10px;color:#64748b;font-size:11px;letter-spacing:2px;">VERIFICATION CODE</p>
                  <code style="display:block;color:#0f172a;font-family:Consolas,Menlo,monospace;font-size:16px;font-weight:700;letter-spacing:1px;line-height:1.8;word-break:break-all;">%s</code>
                </td></tr></table>
                <p style="margin:0;color:#475569;font-size:13px;line-height:1.8;">인증코드는 <strong>발급 후 10분</strong> 동안 사용할 수 있습니다.<br>계정 보호를 위해 이 코드를 다른 사람에게 알려주지 마세요.</p>
                """.formatted(html(nickname), html(code));
        return frame("비밀번호를 재설정해 주세요", "비밀번호 재설정 인증코드를 안내합니다. 10분 안에 입력해 주세요.", body, domain);
    }

    private static String frame(String title, String preview, String body, String domain) {
        return """
                <!doctype html>
                <html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title></head>
                <body style="margin:0;padding:0;background-color:#f3f5f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Malgun Gothic',Arial,sans-serif;">
                  <div style="display:none;max-height:0;overflow:hidden;mso-hide:all;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" bgcolor="#f3f5f8"><tr><td align="center" style="padding:32px 12px;">
                    <table role="presentation" width="560" cellspacing="0" cellpadding="0" style="width:100%%;max-width:560px;">
                      <tr><td style="padding:0 12px 20px;color:#0f172a;font-size:20px;font-weight:800;letter-spacing:3px;">TOADY<span style="display:block;margin-top:6px;color:#94a3b8;font-size:11px;font-weight:400;letter-spacing:0;">내 집 마련의 시작, 두껍아</span></td></tr>
                      <tr><td bgcolor="#ffffff" style="padding:32px 24px;border:1px solid #e2e8f0;border-radius:16px;">
                        <p style="margin:0 0 12px;color:#2563eb;font-size:11px;font-weight:700;letter-spacing:2px;">ACCOUNT &amp; SECURITY</p>
                        <h1 style="margin:0 0 24px;color:#0f172a;font-size:24px;font-weight:700;line-height:1.5;letter-spacing:-1px;">%s</h1>
                        %s
                      </td></tr>
                      <tr><td style="padding:22px 12px 0;color:#94a3b8;font-size:12px;line-height:1.8;">
                        요청한 적이 없다면 이 메일을 무시해 주세요.<br>이 메일은 계정 확인을 위해 발송된 자동 안내 메일입니다.<br>
                        <a href="%s" style="display:inline-block;margin-top:12px;color:#64748b;text-decoration:none;">TOADY 바로가기 &rarr;</a>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(html(title), html(preview), html(title), body, html(domain));
    }
    private static String html(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value); }
}
