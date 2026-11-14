package com.toady.web.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** 화면에 전달하는 실행 이력. 진행률은 API 행 수가 아니라 완료된 월별 구간 수로 계산한다. */
public record AdminJob(String id, JobKind kind, String source, LocalDate startDate, LocalDate endDate,
        String status, int totalChunks, int completedChunks, boolean cancelRequested, String requestedBy,
        String message, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt) {
    public boolean isActive() { return Set.of("QUEUED", "RUNNING", "WAITING").contains(status); }
    public String getStatusLabel() {
        return switch (status) {
            case "QUEUED" -> "실행 대기";
            case "RUNNING" -> "수집 중";
            case "WAITING" -> "다른 수집 종료 대기";
            case "SUCCEEDED" -> "완료";
            case "FAILED" -> "실패";
            case "CANCELLED" -> "중지됨";
            case "INTERRUPTED" -> "실행 중단";
            default -> status;
        };
    }
}
