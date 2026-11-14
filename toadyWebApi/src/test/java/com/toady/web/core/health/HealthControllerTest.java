package com.toady.web.core.health;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final HealthController controller = new HealthController(jdbcTemplate);

    @Test
    void reportsReadyWhenDatabaseIsReachable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
        var response = controller.health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    void reportsUnavailableWithoutExposingDatabaseFailure() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("private connection details"));
        var response = controller.health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals(java.util.Map.of("status", "DOWN"), response.getBody());
    }
}

class InternalPathFilterTest {
    @Test
    void internalPathsRefuseRequestsThatCameThroughAProxy() throws Exception {
        var filter = new com.toady.web.core.security.InternalPathFilter();
        var proxied = new org.springframework.mock.web.MockHttpServletRequest("GET", "/internal/schema");
        proxied.addHeader("X-Real-IP", "203.0.113.5");
        var proxiedResponse = new org.springframework.mock.web.MockHttpServletResponse();
        var chain = new org.springframework.mock.web.MockFilterChain();
        filter.doFilter(proxied, proxiedResponse, chain);
        assertEquals(404, proxiedResponse.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(chain.getRequest(), "앞단을 거친 요청은 컨트롤러까지 가지 않는다");

        var direct = new org.springframework.mock.web.MockHttpServletRequest("GET", "/internal/health");
        var directChain = new org.springframework.mock.web.MockFilterChain();
        filter.doFilter(direct, new org.springframework.mock.web.MockHttpServletResponse(), directChain);
        org.junit.jupiter.api.Assertions.assertNotNull(directChain.getRequest(), "서버 안에서 직접 부르면 통과");

        var api = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/announcements");
        api.addHeader("X-Real-IP", "203.0.113.5");
        var apiChain = new org.springframework.mock.web.MockFilterChain();
        filter.doFilter(api, new org.springframework.mock.web.MockHttpServletResponse(), apiChain);
        org.junit.jupiter.api.Assertions.assertNotNull(apiChain.getRequest(), "일반 API는 영향 없음");

        // 컨트롤러는 디코딩한 경로로 찾으므로 '%69'(i)로 인코딩해도 같은 내부 경로로 본다.
        var encoded = new org.springframework.mock.web.MockHttpServletRequest("GET", "/%69nternal/schema");
        encoded.addHeader("X-Real-IP", "203.0.113.5");
        var encodedResponse = new org.springframework.mock.web.MockHttpServletResponse();
        var encodedChain = new org.springframework.mock.web.MockFilterChain();
        filter.doFilter(encoded, encodedResponse, encodedChain);
        assertEquals(404, encodedResponse.getStatus());
        org.junit.jupiter.api.Assertions.assertNull(encodedChain.getRequest(), "인코딩한 내부 경로도 막는다");
    }
}
