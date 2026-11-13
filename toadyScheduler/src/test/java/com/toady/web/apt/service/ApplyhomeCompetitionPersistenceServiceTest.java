package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApplyhomeCompetitionPersistenceServiceTest {
    @Test void acceptsLowercaseDocumentFieldsAndBindsTheirValues() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var statement = mock(PreparedStatement.class);
        JsonNode row = new ObjectMapper().readTree("""
                {"house_manage_no":"2022930001", "pblanc_no":"2022930001", "cmpet_rate":"3.5"}
                """);
        doAnswer(invocation -> {
            Collection<JsonNode> rows = invocation.getArgument(1);
            ParameterizedPreparedStatementSetter<JsonNode> setter = invocation.getArgument(3);
            setter.setValues(statement, rows.iterator().next());
            return new int[][]{{1}};
        }).when(jdbc).batchUpdate(anyString(), anyCollection(), anyInt(), any(ParameterizedPreparedStatementSetter.class));
        var service = new ApplyhomeCompetitionPersistenceService(jdbc);
        service.upsert("TEST_TABLE", List.of("HOUSE_MANAGE_NO", "PBLANC_NO", "CMPET_RATE"),
                List.of("HOUSE_MANAGE_NO", "PBLANC_NO"), List.of(row));
        verify(statement).setString(1, "2022930001");
        verify(statement).setString(2, "2022930001");
        verify(statement).setString(3, "3.5");
    }

    @Test void rejectsMissingKeysBeforeWritingAnyRow() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        var service = new ApplyhomeCompetitionPersistenceService(jdbc);
        JsonNode row = new ObjectMapper().readTree("{\"cmpet_rate\":\"3.5\"}");
        assertThrows(IllegalStateException.class, () -> service.upsert("TEST_TABLE",
                List.of("HOUSE_MANAGE_NO", "CMPET_RATE"), List.of("HOUSE_MANAGE_NO"), List.of(row)));
        verifyNoInteractions(jdbc);
    }

    @Test void emptySnapshotKeepsExistingAnnouncementRows() {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any())).thenReturn(3);
        boolean replaced = new ApplyhomeCompetitionPersistenceService(jdbc).replace("TEST_TABLE",
                List.of("HOUSE_MANAGE_NO", "PBLANC_NO"), List.of("HOUSE_MANAGE_NO", "PBLANC_NO"),
                "house", "notice", List.of());
        org.junit.jupiter.api.Assertions.assertFalse(replaced);
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
        verify(jdbc, never()).update(startsWith("DELETE"), anyString(), anyString());
    }

    @Test void anotherAnnouncementsSnapshotCannotDeleteExistingRows() throws Exception {
        var jdbc = mock(JdbcTemplate.class);
        JsonNode row = new ObjectMapper().readTree("{\"HOUSE_MANAGE_NO\":\"other\",\"PBLANC_NO\":\"notice\"}");
        assertThrows(IllegalStateException.class, () -> new ApplyhomeCompetitionPersistenceService(jdbc)
                .replace("TEST_TABLE", List.of("HOUSE_MANAGE_NO", "PBLANC_NO"),
                        List.of("HOUSE_MANAGE_NO", "PBLANC_NO"), "house", "notice", List.of(row)));
        verifyNoInteractions(jdbc);
    }
}
