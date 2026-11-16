package com.toady.web.retention;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** H2 MariaDB 모드에서 DELETE ... LIMIT 반복 삭제와 테이블 누락 허용을 확인한다. */
class DataRetentionJobTest {
    private JdbcTemplate jdbc;

    @BeforeEach void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:retention;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE T_LOG (ID INT PRIMARY KEY, VISIT_DATE DATE NOT NULL)");
    }
    @AfterEach void tearDown() { jdbc.execute("DROP ALL OBJECTS"); }

    @Test void deletesInRepeatedBatchesAndContinuesAfterAMissingTable() {
        LocalDate today = LocalDate.of(2026, 11, 12);
        for (int i = 0; i < 7; i++) jdbc.update("INSERT INTO T_LOG VALUES (?, ?)", i, Date.valueOf(today.minusDays(100 + i)));
        jdbc.update("INSERT INTO T_LOG VALUES (99, ?)", Date.valueOf(today));
        var job = new DataRetentionJob(jdbc, 3);
        Map<String, Long> result = job.run(List.of(
                new DataRetentionJob.Target("TB_NOT_CREATED_YET", "VISIT_DATE < ?", List.of(Date.valueOf(today))),
                new DataRetentionJob.Target("T_LOG", "VISIT_DATE < ?", List.of(Date.valueOf(today.minusDays(90))))));
        assertEquals(-1L, result.get("TB_NOT_CREATED_YET"));
        assertEquals(7L, result.get("T_LOG"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM T_LOG", Integer.class));
    }

    @Test void dmThreadStaysWhileAReportedMessageIsKeptAsEvidence() {
        jdbc.execute("CREATE TABLE TB_USER_REPORT (REPORT_SEQ BIGINT PRIMARY KEY, TARGET_TYPE VARCHAR(10), TARGET_SEQ BIGINT, STATUS VARCHAR(10), HANDLED_DT TIMESTAMP)");
        jdbc.execute("CREATE TABLE TB_DM_THREAD (THREAD_SEQ BIGINT PRIMARY KEY, LAST_MESSAGE_DT TIMESTAMP, REG_DT TIMESTAMP NOT NULL)");
        jdbc.execute("CREATE TABLE TB_DM_MESSAGE (MESSAGE_SEQ BIGINT PRIMARY KEY, THREAD_SEQ BIGINT, REG_DT TIMESTAMP NOT NULL)");
        var now = java.time.LocalDateTime.of(2026, 11, 16, 3, 0);
        var old = java.sql.Timestamp.valueOf(now.minusDays(200));
        // 1번 대화: 오래됐고 신고된 쪽지가 있다 → 쪽지·대화방 모두 남긴다. 2번 대화: 오래됐고 신고 없음 → 모두 지운다.
        jdbc.update("INSERT INTO TB_DM_THREAD VALUES (1, ?, ?), (2, ?, ?)", old, old, old, old);
        jdbc.update("INSERT INTO TB_DM_MESSAGE VALUES (10, 1, ?), (11, 1, ?), (20, 2, ?)", old, old, old);
        jdbc.update("INSERT INTO TB_USER_REPORT VALUES (1, 'MESSAGE', 10, 'OPEN', NULL)");
        var targets = DataRetentionJob.targets(now).stream()
                .filter(t -> List.of("TB_DM_MESSAGE", "TB_DM_THREAD").contains(t.table())).toList();
        new DataRetentionJob(jdbc, 100).run(targets);
        assertEquals(List.of(10L), jdbc.queryForList("SELECT MESSAGE_SEQ FROM TB_DM_MESSAGE", Long.class));
        assertEquals(List.of(1L), jdbc.queryForList("SELECT THREAD_SEQ FROM TB_DM_THREAD", Long.class));
    }
}
