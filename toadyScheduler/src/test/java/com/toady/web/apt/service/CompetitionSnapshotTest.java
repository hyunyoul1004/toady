package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@JdbcTest(properties = "spring.datasource.url=jdbc:h2:mem:competitionSnapshot;MODE=MariaDB;DB_CLOSE_DELAY=-1")
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@Import(ApplyhomeCompetitionPersistenceService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CompetitionSnapshotTest {
    @Autowired ApplyhomeCompetitionPersistenceService service;
    @Autowired JdbcTemplate jdbc;
    List<String> columns = List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO", "RATE");
    List<String> keys = List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "MODEL_NO");
    @BeforeEach void setup() {
        jdbc.execute("DROP TABLE IF EXISTS SNAPSHOT_TEST");
        jdbc.execute("CREATE TABLE SNAPSHOT_TEST (HOUSE_MANAGE_NO VARCHAR(20), PBLANC_NO VARCHAR(20), MODEL_NO VARCHAR(20), RATE VARCHAR(5), PRIMARY KEY(HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO))");
        jdbc.update("INSERT INTO SNAPSHOT_TEST VALUES ('house','notice','old','1'),('other','notice','keep','2')");
    }
    @Test void removesObsoleteRowsAndKeepsOtherAnnouncements() throws Exception {
        var row = new ObjectMapper().readTree("{\"HOUSE_MANAGE_NO\":\"house\",\"PBLANC_NO\":\"notice\",\"MODEL_NO\":\"new\",\"RATE\":\"3\"}");
        service.replace("SNAPSHOT_TEST", columns, keys, "house", "notice", List.of(row));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM SNAPSHOT_TEST WHERE MODEL_NO='old'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM SNAPSHOT_TEST", Integer.class));
        // 빈 응답은 일시 장애일 수 있어 이미 저장한 행을 지우지 않는다.
        service.replace("SNAPSHOT_TEST", columns, keys, "house", "notice", List.of());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM SNAPSHOT_TEST", Integer.class));
    }
    @Test void failedInsertRestoresDeletedRows() throws Exception {
        var row = new ObjectMapper().readTree("{\"HOUSE_MANAGE_NO\":\"house\",\"PBLANC_NO\":\"notice\",\"MODEL_NO\":\"new\",\"RATE\":\"too-long-for-column\"}");
        assertThrows(RuntimeException.class, () -> service.replace("SNAPSHOT_TEST", columns, keys, "house", "notice", List.of(row)));
        assertEquals("1", jdbc.queryForObject("SELECT RATE FROM SNAPSHOT_TEST WHERE MODEL_NO='old'", String.class));
    }
    @Test void tablesWhoseColumnsAreAllKeysCanBeUpsertedTwice() throws Exception {
        jdbc.execute("DROP TABLE IF EXISTS ALL_KEY_TEST");
        jdbc.execute("CREATE TABLE ALL_KEY_TEST (HOUSE_MANAGE_NO VARCHAR(20), PBLANC_NO VARCHAR(20), PRIMARY KEY(HOUSE_MANAGE_NO,PBLANC_NO))");
        List<String> allKeys = List.of("HOUSE_MANAGE_NO", "PBLANC_NO");
        var row = new ObjectMapper().readTree("{\"HOUSE_MANAGE_NO\":\"house\",\"PBLANC_NO\":\"notice\"}");
        service.upsert("ALL_KEY_TEST", allKeys, allKeys, List.of(row));
        // 같은 행을 다시 받아도 오류 없이 한 행으로 남는다.
        service.upsert("ALL_KEY_TEST", allKeys, allKeys, List.of(row));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ALL_KEY_TEST", Integer.class));
    }
}
