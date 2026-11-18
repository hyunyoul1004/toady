package com.toady.web.admin;

import java.time.LocalDateTime;

/**
 * DB의 자동 실행 설정과 화면 조회 시 계산한 다음 실행 시각. 비활성 상태의 nextRun은 null이다.
 * TB_SCHEDULER_ADMIN_SCHEDULE 한 행에 해당하며, AdminScheduleManager가 만들어 내부 API로 관리자 사이트에 내려준다.
 * cronExpression에는 cron 여러 개를 '|'로 이어 둘 수 있다.
 */
public record AdminSchedule(JobKind kind, boolean enabled, String cronExpression, int lookbackDays,
        LocalDateTime updatedAt, LocalDateTime nextRun) {
}
