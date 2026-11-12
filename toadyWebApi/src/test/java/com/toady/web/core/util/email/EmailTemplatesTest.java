package com.toady.web.core.util.email;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailTemplatesTest {
    @Test void verificationHasAnEscapedActionLinkAndNoExternalImages() {
        String mail = EmailTemplates.accountVerification("<script>name</script>", "https://example.test/certify?a=1&b=2", "https://example.test");
        assertTrue(mail.contains("이메일 인증하기"));
        assertTrue(mail.contains("24시간"));
        assertTrue(mail.contains("href=\"https://example.test/certify?a=1&amp;b=2\""));
        assertTrue(mail.contains("&lt;script&gt;name&lt;/script&gt;"));
        assertFalse(mail.contains("<script>"));
        assertFalse(mail.contains("<img"));
    }
    @Test void passwordMailContainsTheCopyableCodeAndCorrectExpiry() {
        String mail = EmailTemplates.passwordReset("member", "ABCDEFGH23456789ABCDEFGH", "https://example.test");
        assertTrue(mail.contains("ABCDEFGH23456789ABCDEFGH"));
        assertTrue(mail.contains("10분"));
        assertTrue(mail.contains("width=device-width"));
    }
}
