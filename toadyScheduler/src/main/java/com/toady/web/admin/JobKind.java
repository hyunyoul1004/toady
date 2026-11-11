package com.toady.web.admin;

/** 관리자에서 선택하는 작업 범위. 전체 경쟁률 작업에는 가점이 포함되므로 중복 자동 실행에 유의한다. */
public enum JobKind {
    ALL("전체 수집", "공고·주택형을 수집한 뒤 경쟁률·가점·특별공급을 수집합니다."),
    ANNOUNCEMENTS("공고 · 주택형", "5개 공고 유형의 상세 정보와 주택형을 갱신합니다."),
    COMPETITION("경쟁률 · 가점 · 특별공급", "전체 8개 결과 API를 수집합니다."),
    SCORES("APT 당첨 가점", "과거 가점 복원에 필요한 API만 수집합니다.");

    private final String label;
    private final String description;
    JobKind(String label, String description) { this.label = label; this.description = description; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
}
