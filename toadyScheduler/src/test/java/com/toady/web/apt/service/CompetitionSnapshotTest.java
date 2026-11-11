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
        service.replace("SNAPSHOT_TEST", columns, keys, "house", "notice", List.of());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM SNAPSHOT_TEST", Integer.class));
    }
    @Test void failedInsertRestoresDeletedRows() throws Exception {
        var row = new ObjectMapper().readTree("{\"HOUSE_MANAGE_NO\":\"house\",\"PBLANC_NO\":\"notice\",\"MODEL_NO\":\"new\",\"RATE\":\"too-long-for-column\"}");
        assertThrows(RuntimeException.class, () -> service.replace("SNAPSHOT_TEST", columns, keys, "house", "notice", List.of(row)));
        assertEquals("1", jdbc.queryForObject("SELECT RATE FROM SNAPSHOT_TEST WHERE MODEL_NO='old'", String.class));
    }
}
