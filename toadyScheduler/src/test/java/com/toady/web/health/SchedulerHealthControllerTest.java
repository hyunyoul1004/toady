package com.toady.web.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SchedulerHealthControllerTest {
    @Test void refusesReadinessUntilInitializationAndDatabaseAreReady() {
        var availability = mock(ApplicationAvailability.class);
        var jdbc = mock(JdbcTemplate.class);
        var controller = new SchedulerHealthController(availability, jdbc);
        when(availability.getReadinessState()).thenReturn(ReadinessState.REFUSING_TRAFFIC);
        assertEquals(503, controller.health().getStatusCode().value());
        verifyNoInteractions(jdbc);
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        assertEquals(200, controller.health().getStatusCode().value());
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database detail"));
        assertEquals(503, controller.health().getStatusCode().value());
        assertFalse(controller.health().getBody().toString().contains("private"));
    }
}
