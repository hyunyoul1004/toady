package com.toady.web.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 화면에 전달하는 실행 이력. 진행률은 API 행 수가 아니라 완료된 월별 구간 수로 계산한다.
 * TB_SCHEDULER_ADMIN_JOB 한 행을 AdminJobRepository가 읽어 만든다.
 * record는 필드·생성자·getter(id(), kind() 등)를 자동으로 만들어 주는 불변 데이터 클래스다.
 */
public record AdminJob(String id, JobKind kind, String source, LocalDate startDate, LocalDate endDate,
        String status, int totalChunks, int completedChunks, boolean cancelRequested, String requestedBy,
        String message, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt) {
    /** 아직 끝나지 않은 상태인지. 진행 중 작업은 다시 실행할 수 없게 막는 데 쓴다. */
    public boolean isActive() { return Set.of("QUEUED", "RUNNING", "WAITING").contains(status); }
    /** DB 상태 코드를 화면용 한국어로 바꾼다. get 접두사라 JSON에 statusLabel로도 함께 나간다. */
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
