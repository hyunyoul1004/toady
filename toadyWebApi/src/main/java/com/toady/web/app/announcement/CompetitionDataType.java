package com.toady.web.app.announcement;

import java.util.Arrays;

/** 경쟁률 테이블별 공고 유형 경로입니다. */
public enum CompetitionDataType {
    APT("apt"),
    URBAN("urban"),
    PUBLIC_RENTAL("public-rental"),
    CANCEL_RESUPPLY("cancel-resupply"),
    REMAINING("remaining"),
    OPTIONAL("optional");

    private final String path;

    CompetitionDataType(String path) {
        this.path = path;
    }

    public static CompetitionDataType fromPath(String path) {
        return Arrays.stream(values())
                .filter(type -> type.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("경쟁률 조회를 지원하지 않는 공고 유형입니다."));
    }
}
