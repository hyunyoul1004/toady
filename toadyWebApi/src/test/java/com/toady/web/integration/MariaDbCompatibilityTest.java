package com.toady.web.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real MariaDB checks: existing rows are preserved; test fixture DML is rolled back. */
class MariaDbCompatibilityTest {
    private Connection connect() throws Exception {
        Map<String, String> settings = new HashMap<>();
        Path file = Path.of(".env.local");
        if (Files.isRegularFile(file)) {
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank() || line.stripLeading().startsWith("#") || !line.contains("=")) continue;
                String[] pair = line.split("=", 2);
                String value = pair[1].trim();
                if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                        || (value.startsWith("'") && value.endsWith("'")))) value = value.substring(1, value.length() - 1);
                settings.put(pair[0].trim(), value);
            }
        }
        for (String key : new String[]{"DB_URL", "DB_USERNAME", "DB_PASSWORD"}) {
            if (System.getenv("TOADY_TEST_" + key) != null) settings.put(key, System.getenv("TOADY_TEST_" + key));
            assertNotNull(settings.get(key), "Missing development DB test configuration: " + key);
        }
        assertTrue(settings.get("DB_URL").startsWith("jdbc:mariadb:"), "MariaDB JDBC URL required");
        return DriverManager.getConnection(settings.get("DB_URL"), settings.get("DB_USERNAME"), settings.get("DB_PASSWORD"));
    }

    @Test void existingUrbanTableHasTheIdPrimaryKeyExpectedByBothApplications() throws Exception {
        try (Connection connection = connect()) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement("""
                    select COLUMN_NAME from information_schema.KEY_COLUMN_USAGE
                    where TABLE_SCHEMA = DATABASE() and TABLE_NAME = 'TB_URBTY_OFCTL_LTTOT_PBLANC_MDL'
                      and CONSTRAINT_NAME = 'PRIMARY'
                    order by ORDINAL_POSITION
                    """); var rows = statement.executeQuery()) {
                assertTrue(rows.next(), "Urban model table primary key is missing");
                assertEquals("ID", rows.getString(1));
                assertFalse(rows.next(), "Expected a single-column ID primary key");
            }
        }
    }

    @Test void ordersMixedDatesOnMariaDbWithoutWritingToExistingTables() throws Exception {
        try (Connection connection = connect(); var sql = connection.createStatement()) {
            connection.setReadOnly(true);
            try (var rows = sql.executeQuery("""
                    SELECT HOUSE_MANAGE_NO FROM (
                        SELECT 'A' AS HOUSE_MANAGE_NO, '2026-11-10' AS RCEPT_BGNDE
                        UNION ALL SELECT 'B', '20261101'
                        UNION ALL SELECT 'C', '20261110'
                        UNION ALL SELECT 'D', NULL
                    ) fixtures
                    ORDER BY CASE WHEN REPLACE(RCEPT_BGNDE, '-', '') IS NULL THEN 1 ELSE 0 END,
                        REPLACE(RCEPT_BGNDE, '-', '') DESC, HOUSE_MANAGE_NO LIMIT 1
                    """)) {
                assertTrue(rows.next());
                assertEquals("A", rows.getString(1));
            }
        }
    }

    @Test void verifiesInsertUpdateDeleteWithRollbackOnDevelopmentUrbanTable() throws Exception {
        String key = "TEST" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long id;
                try (var insert = connection.prepareStatement("""
                        INSERT INTO TB_URBTY_OFCTL_LTTOT_PBLANC_MDL
                            (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, TP, SUPLY_AMOUNT)
                        VALUES (?, ?, NULL, 'TEST', '100')
                        """, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, key);
                    insert.setString(2, key);
                    assertEquals(1, insert.executeUpdate());
                    try (var generated = insert.getGeneratedKeys()) {
                        assertTrue(generated.next());
                        id = generated.getLong(1);
                    }
                }
                try (var update = connection.prepareStatement("UPDATE TB_URBTY_OFCTL_LTTOT_PBLANC_MDL SET SUPLY_AMOUNT = '200' WHERE ID = ? AND HOUSE_MANAGE_NO = ?")) {
                    update.setLong(1, id);
                    update.setString(2, key);
                    assertEquals(1, update.executeUpdate());
                }
                try (var delete = connection.prepareStatement("DELETE FROM TB_URBTY_OFCTL_LTTOT_PBLANC_MDL WHERE ID = ? AND HOUSE_MANAGE_NO = ?")) {
                    delete.setLong(1, id);
                    delete.setString(2, key);
                    assertEquals(1, delete.executeUpdate());
                }
            } finally {
                connection.rollback();
            }
            try (var verify = connection.prepareStatement("SELECT COUNT(*) FROM TB_URBTY_OFCTL_LTTOT_PBLANC_MDL WHERE HOUSE_MANAGE_NO = ?")) {
                verify.setString(1, key);
                try (var rows = verify.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            }
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "toady.mariadb.temporary.enabled", matches = "true")
    void verifiesTransactionalSnapshotReplacementOnMariaDbTemporaryTables() throws Exception {
        String table = "TOADY_TEST_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = connect(); var sql = connection.createStatement()) {
            sql.execute("CREATE TEMPORARY TABLE " + table + """
                    (ID BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, HOUSE_MANAGE_NO VARCHAR(40),
                     PBLANC_NO VARCHAR(40), MODEL_NO VARCHAR(4), GP VARCHAR(30), TP VARCHAR(10),
                     EXCLUSE_AR VARCHAR(17), SUPLY_AMOUNT VARCHAR(20), RCEPT_BGNDE VARCHAR(10))
                    """);
            try {
                sql.executeUpdate("INSERT INTO " + table + """
                        (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,TP,SUPLY_AMOUNT,RCEPT_BGNDE) VALUES
                        ('A','A','1','A','100','2026-11-10'),
                        ('A','A','1','A','200','20261110'),
                        ('B','B','1','B','150','20261101')
                        """);
                connection.setAutoCommit(false);
                sql.executeUpdate("DELETE FROM " + table + " WHERE ID = 1");
                connection.rollback();
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next());
                    assertEquals(3, rows.getInt(1));
                }
                sql.executeUpdate("DELETE FROM " + table + " WHERE ID = 1");
                connection.commit();
                try (var rows = sql.executeQuery("SELECT SUPLY_AMOUNT FROM " + table + """

                         ORDER BY CASE WHEN REPLACE(RCEPT_BGNDE, '-', '') IS NULL THEN 1 ELSE 0 END,
                         REPLACE(RCEPT_BGNDE, '-', '') DESC, HOUSE_MANAGE_NO, PBLANC_NO LIMIT 1
                        """)) {
                    assertTrue(rows.next());
                    assertEquals("200", rows.getString(1));
                }
                try (var rows = sql.executeQuery("SELECT COUNT(*) FROM " + table + "")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
            } finally {
                connection.rollback();
                sql.execute("DROP TEMPORARY TABLE IF EXISTS " + table);
            }
        }
    }
}
