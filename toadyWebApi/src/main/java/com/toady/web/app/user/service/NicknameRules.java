package com.toady.web.app.user.service;

import java.util.List;
import java.util.Locale;

/**
 * 닉네임 공통 규칙: 운영자·서비스를 사칭할 수 있는 예약어.
 * 가입(UserService.regUser)과 닉네임 변경(AccountService.changeNickname)이 함께 쓴다. 예약어 목록은 여기 한곳에만 둔다.
 */
public final class NicknameRules {
    private NicknameRules() { }

    /** 닉네임에 들어 있으면 거부하는 말(소문자). 공백·대소문자를 무시하고 포함 여부로 본다. */
    static final List<String> RESERVED = List.of(
            "운영자", "운영진", "운영팀", "관리자", "관리팀", "toady", "토디", "두껍아");
    /** 일반 영단어 안에도 들어가는 말(badminton의 admin)이라 닉네임이 이 말로 시작하거나 끝날 때만 거부한다. */
    static final List<String> RESERVED_EDGE = List.of("admin", "administrator");

    public static final String RESERVED_MESSAGE = "운영자·서비스 이름으로 오해할 수 있는 말은 닉네임에 쓸 수 없어요.";

    /** 예약어를 포함하는지. 공백을 모두 빼고 소문자로 바꿔 비교한다(예: 'Toady 지기', '관 리 자'). */
    public static boolean reserved(String nickname) {
        if (nickname == null) return false;
        String flat = nickname.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return RESERVED.stream().anyMatch(flat::contains)
                || RESERVED_EDGE.stream().anyMatch(word -> flat.startsWith(word) || flat.endsWith(word));
    }
}
