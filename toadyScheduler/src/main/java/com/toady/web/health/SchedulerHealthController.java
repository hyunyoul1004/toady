package com.toady.web.health;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Spring 초기화와 관리자 스케줄 등록이 끝나고 DB도 연결된 경우에만 준비 완료를 반환한다. */
@RestController
public class SchedulerHealthController {
    private final ApplicationAvailability availability;
    private final JdbcTemplate jdbc;
    public SchedulerHealthController(ApplicationAvailability availability, JdbcTemplate jdbc) {
        this.availability = availability; this.jdbc = jdbc;
    }
    @GetMapping("/internal/health")
    public ResponseEntity<Map<String, String>> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) return down();
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException exception) {
            return down();
        }
    }
    private ResponseEntity<Map<String, String>> down() {
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
