package com.toady.web.app.announcement;

import java.util.Arrays;

/**
 * 경쟁률 조회용 유형과 URL 경로값. 공고 유형(AnnouncementType)과 달리 취소 후 재공급(cancel-resupply)이 따로 있다.
 * AnnouncementCompetitionService가 이 값으로 어느 경쟁률 테이블을 읽을지 고른다.
 */
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

    /** 경로값을 경쟁률 유형으로 바꾼다. 모르는 값이면 IllegalArgumentException(→ 400 응답). */
    public static CompetitionDataType fromPath(String path) {
        return Arrays.stream(values())
                .filter(type -> type.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("경쟁률 조회를 지원하지 않는 공고 유형입니다."));
    }
}
