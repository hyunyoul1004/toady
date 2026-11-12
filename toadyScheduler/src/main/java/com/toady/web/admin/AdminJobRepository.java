package com.toady.web.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 실행 이력과 활성 작업 소유권을 JDBC로 관리한다.
 * 이력 갱신은 수집 데이터의 저장 트랜잭션과 분리되어 실패·중지 상태도 독립적으로 남길 수 있다.
 */
@Repository
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminJobRepository {
    private final JdbcTemplate jdbc;
    public AdminJobRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final RowMapper<AdminJob> MAPPER = (rs, n) -> new AdminJob(
            rs.getString("JOB_ID"), JobKind.valueOf(rs.getString("JOB_KIND")), rs.getString("JOB_SOURCE"),
            rs.getDate("START_DATE").toLocalDate(), rs.getDate("END_DATE").toLocalDate(), rs.getString("STATUS"),
            rs.getInt("TOTAL_CHUNKS"), rs.getInt("COMPLETED_CHUNKS"), rs.getBoolean("CANCEL_REQUESTED"),
            rs.getString("REQUESTED_BY"), rs.getString("MESSAGE"), rs.getTimestamp("CREATED_AT").toLocalDateTime(),
            dateTime(rs.getTimestamp("STARTED_AT")), dateTime(rs.getTimestamp("FINISHED_AT")));

    private static LocalDateTime dateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    /**
     * ACTIVE_SLOT=1의 UNIQUE 제약으로 인스턴스 전체에서 활성 작업 하나만 허용한다.
     * 자동 실행의 TRIGGER_KEY는 종류와 한국시간의 분 단위 값으로 같은 분의 중복 등록을 막는다.
     * 수동 실행은 TRIGGER_KEY를 null로 두어 같은 기간을 다시 요청할 수 있다.
     */
    public void insert(String id, JobKind kind, String source, LocalDate start, LocalDate end,
            int chunks, String user, LocalDateTime now) {
        jdbc.update("""
                INSERT INTO TB_SCHEDULER_ADMIN_JOB
                (JOB_ID, JOB_KIND, JOB_SOURCE, START_DATE, END_DATE, STATUS, TOTAL_CHUNKS,
                 COMPLETED_CHUNKS, CANCEL_REQUESTED, REQUESTED_BY, MESSAGE, CREATED_AT, HEARTBEAT_AT, ACTIVE_SLOT, TRIGGER_KEY)
                VALUES (?, ?, ?, ?, ?, 'QUEUED', ?, 0, FALSE, ?, '실행을 준비하고 있습니다.', ?, ?, 1, ?)
                """, id, kind.name(), source, start, end, chunks, user, now, now, "SCHEDULE".equals(source)
                ? kind.name() + ":" + now.withSecond(0).withNano(0) : null);
    }
    /** 화면에 표시할 최근 50건만 읽는다. 이전 이력을 삭제하거나 전체 이력 건수를 의미하지는 않는다. */
    public List<AdminJob> recent() {
        return jdbc.query("SELECT * FROM TB_SCHEDULER_ADMIN_JOB ORDER BY CREATED_AT DESC, JOB_ID LIMIT 50", MAPPER);
    }
    public Optional<AdminJob> find(String id) {
        return jdbc.query("SELECT * FROM TB_SCHEDULER_ADMIN_JOB WHERE JOB_ID = ?", MAPPER, id).stream().findFirst();
    }
    public Optional<AdminJob> active() {
        return jdbc.query("SELECT * FROM TB_SCHEDULER_ADMIN_JOB WHERE ACTIVE_SLOT = 1", MAPPER).stream().findFirst();
    }
    /** 이미 종료·복구된 작업은 늦게 도착한 실행기의 상태 갱신으로 덮어쓰지 않는다. */
    public boolean update(String id, String status, int completed, String message, LocalDateTime now) {
        return jdbc.update("""
                UPDATE TB_SCHEDULER_ADMIN_JOB SET STATUS = ?, COMPLETED_CHUNKS = ?, MESSAGE = ?,
                 HEARTBEAT_AT = ?, STARTED_AT = COALESCE(STARTED_AT, ?)
                WHERE JOB_ID = ? AND ACTIVE_SLOT = 1
                """, status, completed, message, now, now, id) == 1;
    }
    // 종료된 행의 슬롯은 null로 반환한다. UNIQUE 제약은 유지하면서 여러 종료 이력을 보관할 수 있다.
    public void finish(String id, String status, String message, LocalDateTime now) {
        jdbc.update("""
                UPDATE TB_SCHEDULER_ADMIN_JOB SET STATUS = ?, MESSAGE = ?, FINISHED_AT = ?,
                 HEARTBEAT_AT = ?, ACTIVE_SLOT = NULL WHERE JOB_ID = ? AND ACTIVE_SLOT = 1
                """, status, message, now, now, id);
    }
    /** 수집을 직접 중단하지 않고 요청만 기록한다. 실행기가 구간 경계에서 확인한다. */
    public boolean cancel(String id) {
        return jdbc.update("UPDATE TB_SCHEDULER_ADMIN_JOB SET CANCEL_REQUESTED = TRUE WHERE JOB_ID = ? AND ACTIVE_SLOT = 1", id) == 1;
    }
    public void heartbeat(String id, LocalDateTime now) {
        jdbc.update("UPDATE TB_SCHEDULER_ADMIN_JOB SET HEARTBEAT_AT = ? WHERE JOB_ID = ? AND ACTIVE_SLOT = 1", now, id);
    }
    /** 이 프로세스가 실행 중인 작업(currentId)을 제외하고, 생존 신호가 cutoff 이전에 멈춘 활성 작업을 중단 상태로 정리한다. */
    public int interruptLeftovers(String currentId, LocalDateTime cutoff, LocalDateTime now) {
        return jdbc.update("""
                UPDATE TB_SCHEDULER_ADMIN_JOB SET STATUS = 'INTERRUPTED', ACTIVE_SLOT = NULL,
                 MESSAGE = '스케줄러가 재시작되어 작업이 중단되었습니다. 기간을 확인한 뒤 다시 실행하세요.', FINISHED_AT = ?
                WHERE ACTIVE_SLOT = 1 AND JOB_ID <> ? AND HEARTBEAT_AT < ?
                """, now, currentId == null ? "" : currentId, cutoff);
    }
    /** 생존 신호가 오래 끊긴 작업의 슬롯을 반환한다. 과거 데이터 삭제나 작업 자동 재실행은 하지 않는다. */
    public void recoverStale(LocalDateTime cutoff, LocalDateTime now) {
        jdbc.update("""
                UPDATE TB_SCHEDULER_ADMIN_JOB SET STATUS = 'INTERRUPTED', ACTIVE_SLOT = NULL,
                 MESSAGE = '실행 프로세스의 응답이 끊겼습니다. 기간을 확인한 뒤 다시 실행하세요.', FINISHED_AT = ?
                WHERE ACTIVE_SLOT = 1 AND HEARTBEAT_AT < ?
                """, now, cutoff);
    }
}
