package com.toady.web.app.announcement;

import java.util.Arrays;

/** 공고 유형별 경로와 조회 대상을 구분합니다. */
public enum AnnouncementType {
    APT("apt"),
    URBAN("urban"),
    REMAINING("remaining"),
    PUBLIC_RENTAL("public-rental"),
    OPTIONAL("optional");

    private final String path;

    AnnouncementType(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    /** 경로 문자열을 공고 유형으로 변환하며, 지원하지 않는 값은 거부합니다. */
    public static AnnouncementType fromPath(String path) {
        return Arrays.stream(values())
                .filter(type -> type.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 공고 유형입니다."));
    }
}
