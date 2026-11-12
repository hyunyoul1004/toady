package com.toady.web.core.health;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 배포 확인용 준비 상태. 데이터베이스 접속 실패의 세부 정보는 공개하지 않습니다. */
@RestController
@RequiredArgsConstructor
public class HealthController {
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/internal/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(Map.of("status", "UP"));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }

    /** API가 쓰는 운영 테이블(database.sql 구역 번호). 배포 확인 스크립트가 빠진 테이블을 경고로 알려 준다. */
    static final List<String> REQUIRED_TABLES = List.of(
            "TB_USER", "TB_CERTIFY", "TB_LTTOT_PBLANC_COMMENT", "TB_PAGE_VIEW", "TB_FAVORITE", "TB_MAIL_BUDGET", "TB_MAIL_ATTEMPT",
            "TB_REMINDER_DELIVERY", "TB_USAGE_EVENT", "TB_CALENDAR_FEED", "TB_RATE_POLL", "TB_PUSH_SUBSCRIPTION", "TB_PUSH_AREA_SEEN");

    /**
     * 빠진 테이블 목록. 앞단 nginx가 /internal을 외부로 내보내지 않아 서버 안(127.0.0.1:8080)에서만 호출된다.
     * 일부 기능은 테이블이 없으면 숨겨지도록 만들어져 있어, 배포를 막지 않고 경고만 하기 위한 용도다.
     */
    @GetMapping("/internal/schema")
    public ResponseEntity<Map<String, Object>> schema() {
        try {
            List<String> missing = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<List<String>>) connection -> {
                var meta = connection.getMetaData();
                List<String> absent = new ArrayList<>();
                for (String table : REQUIRED_TABLES) {
                    boolean found = false;
                    for (String name : List.of(table, table.toLowerCase(java.util.Locale.ROOT))) {
                        try (var rows = meta.getTables(connection.getCatalog(), connection.getSchema(), name, new String[]{"TABLE"})) {
                            if (rows.next()) { found = true; break; }
                        }
                    }
                    if (!found) absent.add(table);
                }
                return absent;
            });
            return ResponseEntity.ok(Map.of("missing", missing == null ? List.of() : missing));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
