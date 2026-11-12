package com.toady.web.app.certify.service;

/** 이메일 주소 공통 규칙. 계정 주소는 ASCII만 허용한다(유니코드 소문자 변환으로 다른 주소와 같아지는 일을 막는다). */
public final class EmailRules {
    private EmailRules() { }
    private static final java.util.regex.Pattern ASCII_EMAIL = java.util.regex.Pattern.compile("^[\\x21-\\x7E]{3,254}$");
    public static boolean ascii(String email) { return email != null && ASCII_EMAIL.matcher(email).matches() && email.indexOf('@') > 0; }
}
