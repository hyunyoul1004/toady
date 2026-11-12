package com.toady.web.core.util;

import java.security.SecureRandom;

/** 계정 인증에 사용하는 추측하기 어려운 일회용 코드를 생성한다. */
public final class StrUtils {
    private static final SecureRandom RANDOM = new SecureRandom();
    // 혼동하기 쉬운 0/O, 1/I를 제외한다.
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private StrUtils() { }

    public static String createCode() {
        StringBuilder code = new StringBuilder(26);
        for (int index = 0; index < 24; index++) {
            if (index == 8 || index == 16) code.append('-');
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
