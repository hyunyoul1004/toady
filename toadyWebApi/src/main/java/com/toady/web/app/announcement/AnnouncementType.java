package com.toady.web.app.announcement;

import java.util.Arrays;

/**
 * 청약홈 공고 유형 5가지와 URL 경로값(apt, urban …)을 짝지어 둔다.
 * 컨트롤러가 {type} 경로값을 fromPath로 바꿔 서비스에 넘기고, 서비스는 유형마다 다른 저장소(테이블)를 고른다.
 * 댓글·운영자 안내 테이블에는 경로값이 아니라 name()(APT, URBAN …)이 저장된다.
 */
public enum AnnouncementType {
    APT("apt"),
    URBAN("urban"),
    REMAINING("remaining"),
    PUBLIC_RENTAL("public-rental"),
    OPTIONAL("optional");

    // URL에 쓰는 소문자 경로값
    private final String path;

    AnnouncementType(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    /** 경로 문자열을 공고 유형으로 바꾼다. 모르는 값이면 IllegalArgumentException을 던지고, AnnouncementApiExceptionHandler가 400으로 응답한다. */
    public static AnnouncementType fromPath(String path) {
        return Arrays.stream(values())
                .filter(type -> type.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 공고 유형입니다."));
    }
}
