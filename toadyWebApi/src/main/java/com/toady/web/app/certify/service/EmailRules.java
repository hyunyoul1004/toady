package com.toady.web.app.certify.service;

import java.util.regex.Pattern;


/** 이메일 주소 공통 규칙. 계정 주소는 ASCII만 허용한다(유니코드 소문자 변환으로 다른 주소와 같아지는 일을 막는다). */
public final class EmailRules {
    private EmailRules() { }
    private static final Pattern ASCII_EMAIL = Pattern.compile("^[\\x21-\\x7E]{3,254}$");
    /** 눈에 보이는 ASCII 문자(0x21~0x7E)만으로 3~254자이고 @가 맨 앞이 아닌지. 형식 검사는 @Email이 따로 한다. */
    public static boolean ascii(String email) { return email != null && ASCII_EMAIL.matcher(email).matches() && email.indexOf('@') > 0; }
}
