package com.toady.web.admin;

import java.time.LocalDateTime;

/** DB의 자동 실행 설정과 화면 조회 시 계산한 다음 실행 시각. 비활성 상태의 nextRun은 null이다. */
public record AdminSchedule(JobKind kind, boolean enabled, String cronExpression, int lookbackDays,
        LocalDateTime updatedAt, LocalDateTime nextRun) {
}
