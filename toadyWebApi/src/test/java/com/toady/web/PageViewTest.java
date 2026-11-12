package com.toady.web;

import com.toady.web.app.analytics.PageViewController;
import com.toady.web.core.response.ApiExceptionHandler;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 SQL 저장·중복·입력 제한·장애 응답을 검증한다. 운영 테이블은 사용하지 않는다. */
class PageViewTest {
    JdbcTemplate jdbc;
    MockMvc mvc;
    static final String BODY = """
        {"eventId":"00000000-0000-4000-8000-000000000001","visitorId":"00000000-0000-4000-8000-000000000002",
        "sessionId":"00000000-0000-4000-8000-000000000003","path":"/"}
        """;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:analytics;MODE=MariaDB;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP TABLE IF EXISTS TB_PAGE_VIEW");
        jdbc.execute("CREATE TABLE TB_PAGE_VIEW (EVENT_ID CHAR(36) PRIMARY KEY,VISITOR_ID CHAR(36) NOT NULL,SESSION_ID CHAR(36) NOT NULL,PAGE_PATH VARCHAR(100) NOT NULL,VISIT_DATE DATE NOT NULL,REG_DT TIMESTAMP NOT NULL)");
        mvc = build(true);
    }
    MockMvc build(boolean enabled) {
        // 실제 브라우저처럼 User-Agent를 보낸다(로봇·자동화 요청은 저장하지 않는다).
        return MockMvcBuilders.standaloneSetup(new PageViewController(jdbc, enabled))
                .defaultRequest(post("/").header("User-Agent", BROWSER))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }
    static final String BROWSER = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
    @Test void searchBotsAndLinkPreviewsAreAcceptedButNotStored() throws Exception {
        for (String agent : new String[]{"Mozilla/5.0 (compatible; Googlebot/2.0; +http://www.google.com/bot.html)", "Mozilla/5.0 (compatible; Yeti/1.1; +https://naver.me/spd)",
                "facebookexternalhit/1.1", "Mozilla/5.0 HeadlessChrome/120.0", ""}) {
            mvc.perform(post("/api/v1/analytics/page-views").header("User-Agent", agent).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNoContent());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class), "로봇 요청은 방문으로 세지 않는다");
    }
    @Test void duplicateDeliveryCountsOnceAndUsesServerDate() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class));
        assertEquals(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")), jdbc.queryForObject("SELECT VISIT_DATE FROM TB_PAGE_VIEW", java.sql.Date.class).toLocalDate());
    }
    @Test void invalidPathsAndIdsAreRejectedWithoutWriting() throws Exception {
        for (String body : new String[]{BODY.replace("\"path\":\"/\"", "\"path\":\"/certify?code=secret\""), BODY.replace("00000000-0000-4000-8000-000000000001", "bad"), "{}"}) {
            mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class));
    }
    @Test void externalReferrerHostIsStoredWhenTheColumnExists() throws Exception {
        jdbc.execute("ALTER TABLE TB_PAGE_VIEW ADD COLUMN REFERRER_HOST VARCHAR(100)");
        mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"path\":\"/\"", "\"path\":\"/\",\"referrerHost\":\"m.search.naver.com\"")))
                .andExpect(status().isNoContent());
        assertEquals("m.search.naver.com", jdbc.queryForObject("SELECT REFERRER_HOST FROM TB_PAGE_VIEW", String.class));
        // 전체 URL·검색어·대문자 도메인처럼 정해진 형식이 아니면 저장하지 않는다.
        for (String host : new String[]{"https://www.google.com/search?q=x", "Google.com", "localhost", "a b.com"}) {
            mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON)
                    .content(BODY.replace("000000000001", "000000000009").replace("\"path\":\"/\"", "\"path\":\"/\",\"referrerHost\":\"" + host + "\"")))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class));
    }
    @Test void missingReferrerColumnStillStoresTheView() throws Exception {
        // 운영 DB에 컬럼 추가 SQL을 적용하기 전에 배포돼도 조회 기록이 끊기지 않는다.
        mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"path\":\"/\"", "\"path\":\"/\",\"referrerHost\":\"www.google.com\"")))
                .andExpect(status().isNoContent());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PAGE_VIEW", Integer.class));
    }
    @Test void disabledCollectionDoesNotRequireATableAndFailureIsRetryable() throws Exception {
        jdbc.execute("DROP TABLE TB_PAGE_VIEW");
        build(false).perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/analytics/page-views").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isServiceUnavailable());
    }
}
