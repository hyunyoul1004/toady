package com.toady.web.health;

import com.toady.web.admin.AdminSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SchedulerHealthController.class, properties = "applyhome.admin.enabled=false")
@Import(AdminSecurityConfig.class)
class SchedulerHealthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApplicationAvailability availability;
    @MockitoBean JdbcTemplate jdbc;
    @Test void disabledAdminStillAllowsReadinessButBlocksAdminRoutes() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/internal/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
    }
}
