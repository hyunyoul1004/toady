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
}
