package com.toady.web.admin;

/**
 * 관리자에서 선택하는 작업 범위. 전체 경쟁률 작업에는 가점이 포함되므로 중복 자동 실행에 유의한다.
 * DB(TB_SCHEDULER_ADMIN_JOB.JOB_KIND, TB_SCHEDULER_ADMIN_SCHEDULE)에는 이 enum의 이름(name())이 문자열로 저장된다.
 */
public enum JobKind {
    ALL("전체 수집"),
    ANNOUNCEMENTS("공고 · 주택형"),
    COMPETITION("경쟁률 · 가점 · 특별공급"),
    SCORES("APT 당첨 가점"),
    /** 접수일·당첨자 발표일 기준으로 결과가 나올 공고만 경쟁률·특별공급·가점을 갱신한다(기간은 일정 기준). */
    SCHEDULED_RESULTS("일정 맞춤 경쟁률 · 가점");

    /** DB의 종류 이름을 읽는다. 이 버전이 모르는 종류(새 버전이 넣은 행)는 null(건너뜀). */
    public static JobKind parse(String name) {
        try { return name == null ? null : valueOf(name); } catch (IllegalArgumentException unknown) { return null; }
    }

    private final String label;
    JobKind(String label) { this.label = label; }
    public String getLabel() { return label; }
}
