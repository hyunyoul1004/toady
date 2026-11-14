package com.toady.web.core.health;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            "TB_REMINDER_DELIVERY", "TB_USAGE_EVENT", "TB_CALENDAR_FEED", "TB_RATE_POLL", "TB_PUSH_SUBSCRIPTION", "TB_PUSH_AREA_SEEN", "TB_PRICE_GAP",
            "TB_APT_COMPLEX_LOCATION", "TB_USER_PROFILE");
    /** 나중에 추가한 컬럼(database.sql 10·15·16번). 빠지면 'TB_X.COLUMN' 형태로 알린다. */
    static final List<String[]> REQUIRED_COLUMNS = List.of(
            new String[]{"TB_PAGE_VIEW", "REFERRER_HOST"}, new String[]{"TB_PUSH_AREA_SEEN", "LAST_SEEN_DT"}, new String[]{"TB_FAVORITE", "STAGE"});

    /**
     * 빠진 테이블 목록. 앞단 nginx가 /internal을 외부로 내보내지 않아 서버 안(127.0.0.1:8080)에서만 호출된다.
     * 일부 기능은 테이블이 없으면 숨겨지도록 만들어져 있어, 배포를 막지 않고 경고만 하기 위한 용도다.
     */
    @GetMapping("/internal/schema")
    public ResponseEntity<Map<String, Object>> schema() {
        try {
            List<String> missing = jdbcTemplate.execute((ConnectionCallback<List<String>>) connection -> {
                var meta = connection.getMetaData();
                List<String> absent = new ArrayList<>();
                for (String table : REQUIRED_TABLES) {
                    boolean found = false;
                    for (String name : List.of(table, table.toLowerCase(Locale.ROOT))) {
                        try (var rows = meta.getTables(connection.getCatalog(), connection.getSchema(), name, new String[]{"TABLE"})) {
                            if (rows.next()) { found = true; break; }
                        }
                    }
                    if (!found) absent.add(table);
                }
                for (String[] column : REQUIRED_COLUMNS) {
                    if (absent.contains(column[0])) continue;
                    boolean found = false;
                    for (String[] name : List.of(column, new String[]{column[0].toLowerCase(Locale.ROOT), column[1].toLowerCase(Locale.ROOT)})) {
                        try (var rows = meta.getColumns(connection.getCatalog(), connection.getSchema(), name[0], name[1])) {
                            if (rows.next()) { found = true; break; }
                        }
                    }
                    if (!found) absent.add(column[0] + "." + column[1]);
                }
                return absent;
            });
            return ResponseEntity.ok(Map.of("missing", missing == null ? List.of() : missing));
        } catch (DataAccessException exception) {
            return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
        }
    }
}
