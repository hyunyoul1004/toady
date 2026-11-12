package com.toady.web.app.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.research.AddressLocator;
import com.toady.web.app.planner.research.NeighborhoodResearchService;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 주변 시세 비교 미리 계산: 주소 확인 → 실거래 → 대표 요약 저장 → 목록·조회 API·공개 페이지에 노출. 외부 응답은 흉내 낸다. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:planneraudit;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.planner.mail-limit=4", "toady.planner.auth-reserve=1"
})
@AutoConfigureMockMvc
class PriceGapSnapshotsTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired EligibilityFeed feed; @Autowired PriceGapStore store;
    @MockitoBean EmailUtilService mail;

    static final String KAKAO = "{\"documents\":[{\"address_type\":\"REGION_ADDR\",\"x\":\"127.03\",\"y\":\"37.5\",\"address_name\":\"서울 강남구 역삼동 123-4\","
        + "\"address\":{\"address_name\":\"서울 강남구 역삼동 123-4\",\"b_code\":\"1168010100\",\"region_3depth_name\":\"역삼동\"}}]}";
    static byte[] trade(String month) {
        return ("<response><header><resultCode>000</resultCode></header><body><totalCount>1</totalCount><items><item>"
            + "<aptNm>신축 아파트</aptNm><umdNm>역삼동</umdNm><dealAmount>58,900</dealAmount><excluUseAr>84.95</excluUseAr>"
            + "<floor>12</floor><buildYear>2020</buildYear><dealYear>" + month.substring(0, 4) + "</dealYear><dealMonth>" + month.substring(4)
            + "</dealMonth><dealDay>1</dealDay><cdealType></cdealType></item></items></body></response>").getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_PRICE_GAP (ANNOUNCEMENT_ID VARCHAR(110) PRIMARY KEY,STATUS VARCHAR(12) NOT NULL,BCODE CHAR(10),DONG VARCHAR(40),AREA SMALLINT,SUPPLY_MANWON INT,BASIS VARCHAR(4),SCOPE VARCHAR(10),BASIS_COUNT INT,CONFIDENCE VARCHAR(8),Q1_MANWON INT,Q3_MANWON INT,MONTHS TINYINT,COMPUTED_DT TIMESTAMP NOT NULL)");
        for (String table : java.util.List.of("TB_PRICE_GAP", "TB_APT_LTTOT_PBLANC_MDL", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_REMNDR_LTTOT_PBLANC_DETAIL")) jdbc.update("DELETE FROM " + table);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_AREA_CODE_NM,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('G1','N1','역삼 새 단지','서울특별시 강남구 역삼동 123-4','서울',?,?)", today.toString(), today.plusDays(2).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_AREA_CODE_NM,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('G2','N2','지구 단지','서울특별시 강동구 고덕강일지구 3블록','서울',?,?)", today.toString(), today.plusDays(2).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_MDL (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,HOUSE_TY,LTTOT_TOP_AMOUNT) VALUES ('G1','N1','01','084.9500A','50000'),('G2','N2','01','084.9800','60000')");
        feed.invalidate();
    }

    @Test void precomputesOnlyPreciseLocationsAndExposesTheSummary() throws Exception {
        AtomicInteger kakaoCalls = new AtomicInteger(), molitCalls = new AtomicInteger();
        var transport = (com.toady.web.app.planner.research.ResearchTransport) (uri, headers) -> {
            if (uri.getHost().equals("dapi.kakao.com")) { kakaoCalls.incrementAndGet(); return KAKAO.getBytes(StandardCharsets.UTF_8); }
            molitCalls.incrementAndGet();
            return trade(uri.getQuery().split("DEAL_YMD=")[1].substring(0, 6));
        };
        var snapshots = new PriceGapSnapshots(jdbc, store, feed, new AddressLocator(transport, "kakao-key"), new NeighborhoodResearchService(transport, "molit-key"));
        var report = snapshots.run();
        assertEquals(1, report.get("READY"), report.toString());
        assertEquals(1, report.get("NO_LOCATION"), "지구·블록 주소는 위치를 확정하지 않는다");
        assertEquals(1, kakaoCalls.get(), "지번이 없는 주소는 카카오를 부르지 않는다");
        assertEquals(6, molitCalls.get(), "최근 6개월을 시·군·구 단위로 한 번씩");

        var gap = store.forIds(java.util.List.of("apt/G1/N1")).get("apt/G1/N1");
        assertNotNull(gap);
        assertEquals("new", gap.basis()); assertEquals("DONG", gap.scope()); assertEquals("high", gap.confidence());
        assertEquals(58900 - 50000, gap.diffLow());
        assertEquals("약 9,000만원 낮음", gap.text());
        assertNull(store.forIds(java.util.List.of("apt/G2/N2")).get("apt/G2/N2"), "READY가 아니면 노출하지 않는다");

        // 이미 계산한 공고는 다시 계산하지 않는다(외부 호출 절약).
        snapshots.run();
        assertEquals(1, kakaoCalls.get());

        // 목록(맞춤 청약·줍줍)과 비교표용 API, 공개 페이지에 같은 요약이 나온다.
        feed.invalidate();
        var items = json.readTree(mvc.perform(get("/api/v1/public/eligibility/announcements")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).path("items");
        var g1 = java.util.stream.StreamSupport.stream(items.spliterator(), false).filter(item -> item.path("id").asText().equals("apt/G1/N1")).findFirst().orElseThrow();
        assertEquals("below", g1.path("gap").path("tone").asText());
        mvc.perform(post("/api/v1/planner/price-gaps").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"apt/G1/N1\",\"apt/G2/N2\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].text").value("약 9,000만원 낮음"));
        PublicHtml.clearCache();
        String page = mvc.perform(get("/api/v1/public/announcements/apt/G1/N1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(page.contains("주변 신축 시세 대비 약 9,000만원 낮음") && page.contains("참고 비교"), page);
    }

    @Test void missingTableOrKeysAreHarmless() {
        jdbc.execute("DROP TABLE IF EXISTS TB_PRICE_GAP");
        assertTrue(store.forIds(java.util.List.of("apt/G1/N1")).isEmpty(), "테이블이 없으면 빈 결과");
        var noKey = new PriceGapSnapshots(jdbc, store, feed, new AddressLocator((uri, h) -> { throw new AssertionError("호출하지 않는다"); }, ""),
            new NeighborhoodResearchService((uri, h) -> { throw new AssertionError("호출하지 않는다"); }, ""));
        assertDoesNotThrow(noKey::scheduled, "키가 없으면 아무것도 하지 않는다");
    }

    @Test void textAndRoundingMatchTheScreenRules() {
        assertEquals("1.2억", PriceGapRules.roughEok(12345));
        assertEquals("5,000만원", PriceGapRules.roughEok(4800));
        assertEquals("약 1.4억~2.2억 낮음", PriceGapRules.text(55000 - 41110, 63000 - 41110));
        assertEquals("비슷", PriceGapRules.text(-3000, 2000));
        assertEquals("약 8,000만원~1.2억 높음", PriceGapRules.text(-12000, -8000));
    }
}
