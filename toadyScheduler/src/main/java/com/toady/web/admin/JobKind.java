package com.toady.web.admin;

/** 관리자에서 선택하는 작업 범위. 전체 경쟁률 작업에는 가점이 포함되므로 중복 자동 실행에 유의한다. */
public enum JobKind {
    ALL("전체 수집"),
    ANNOUNCEMENTS("공고 · 주택형"),
    COMPETITION("경쟁률 · 가점 · 특별공급"),
    SCORES("APT 당첨 가점");

    private final String label;
    JobKind(String label) { this.label = label; }
    public String getLabel() { return label; }
}
