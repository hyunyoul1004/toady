package com.toady.web.health;

import com.toady.web.admin.SchedulerSecurityConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = SchedulerHealthController.class, properties = "applyhome.admin.enabled=false")
@Import(SchedulerSecurityConfig.class)
class SchedulerHealthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ApplicationAvailability availability;
    @MockitoBean JdbcTemplate jdbc;

    @Test void disabledAdminStillAllowsReadinessButBlocksAdminRoutes() throws Exception {
        when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
        when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        mvc.perform(get("/internal/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
        // 관리 기능을 끄면 내부 API 경로도 보안 단계에서 막는다(토큰을 보내도 마찬가지).
        mvc.perform(get("/internal/admin/state").header("X-Admin-Token", "x".repeat(40))).andExpect(status().isForbidden());
        mvc.perform(post("/internal/admin/jobs")).andExpect(status().isForbidden());
    }

    /** 관리 기능을 켜도 예전 화면 경로나 임의 경로는 열리지 않는다. */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @WebMvcTest(controllers = SchedulerHealthController.class, properties = "applyhome.admin.enabled=true")
    @Import(SchedulerSecurityConfig.class)
    class AdminEnabled {
        @Autowired MockMvc mvc;
        @MockitoBean ApplicationAvailability availability;
        @MockitoBean JdbcTemplate jdbc;

        @Test void deniesEverythingOutsideHealthAndTheInternalApi() throws Exception {
            when(availability.getReadinessState()).thenReturn(ReadinessState.ACCEPTING_TRAFFIC);
            when(jdbc.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
            mvc.perform(get("/internal/health")).andExpect(status().isOk());
            for (String path : java.util.List.of("/", "/admin", "/admin/login", "/admin/metrics", "/random")) {
                mvc.perform(get(path)).andExpect(status().isForbidden());
            }
            mvc.perform(post("/admin/login").param("username", "admin").param("password", "anything-long-enough"))
                    .andExpect(status().isForbidden());
        }
    }
}
