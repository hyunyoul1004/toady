package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomeApiPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 공고·주택형 저장이 공고 하나씩 트랜잭션으로 나뉘어, 한 공고의 값 오류(컬럼 길이 초과)가
 * 같은 유형의 다른 공고와 다음 유형까지 되돌리지 않는지 실제 JPA 저장으로 확인한다.
 */
@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl"
})
@Import(ApplyhomeDataPersistenceService.class)
// 테스트 트랜잭션으로 감싸면 공고별 트랜잭션이 하나로 합쳐지므로 끈다.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApplyhomeSyncPartialSaveTest {
    @Autowired ApplyhomeDataPersistenceService persistence;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @AfterEach void cleanUp() {
        for (String table : List.of("TB_APT_LTTOT_PBLANC_DETAIL", "TB_APT_LTTOT_PBLANC_MDL",
                "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "TB_URBTY_OFCTL_LTTOT_PBLANC_MDL")) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    @Test void oneTooLongValueOnlySkipsItsOwnAnnouncement() throws Exception {
        var api = mock(ApplyhomeApiClient.class);
        when(api.getPage(anyString(), anyInt(), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        // A: 정상, B: 상세 값이 컬럼 길이(HOUSE_SECD 2자)를 넘음, C: 상세는 정상이고 주택형 값이 길이(SUPLY_AR 17자)를 넘음
        page(api, "getAPTLttotPblancDetail", null,
                row("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"정상\",\"HOUSE_SECD\":\"01\"}"),
                row("{\"HOUSE_MANAGE_NO\":\"B\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"상세 오류\",\"HOUSE_SECD\":\"too-long\"}"),
                row("{\"HOUSE_MANAGE_NO\":\"C\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"주택형 오류\",\"HOUSE_SECD\":\"01\"}"));
        page(api, "getAPTLttotPblancMdl", "A",
                row("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"1\",\"MODEL_NO\":\"01\",\"SUPLY_AR\":\"84.9\"}"));
        page(api, "getAPTLttotPblancMdl", "C",
                row("{\"HOUSE_MANAGE_NO\":\"C\",\"PBLANC_NO\":\"1\",\"MODEL_NO\":\"01\",\"SUPLY_AR\":\"area-exceeds-column-length-17\"}"));
        // 다음 유형(오피스텔·도시형)도 이어서 저장되어야 한다.
        page(api, "getUrbtyOfctlLttotPblancDetail", null,
                row("{\"HOUSE_MANAGE_NO\":\"U\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"오피스텔\"}"));
        page(api, "getUrbtyOfctlLttotPblancMdl", "U",
                row("{\"HOUSE_MANAGE_NO\":\"U\",\"PBLANC_NO\":\"1\",\"MODEL_NO\":\"1\",\"TP\":\"A\"}"));

        var lock = mock(ApplyhomeSyncLock.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);

        var failure = assertThrows(ApplyhomePartialFailureException.class,
                () -> service.synchronize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertTrue(failure.getMessage().contains("2건"), failure.getMessage());
        assertTrue(failure.getMessage().contains("B/1") && failure.getMessage().contains("C/1"), failure.getMessage());

        // 상세 저장이 실패한 B와 주택형 저장이 실패한 C는 상세·주택형 모두 남지 않는다(한 트랜잭션).
        assertEquals(List.of("A"), jdbc.queryForList(
                "SELECT HOUSE_MANAGE_NO FROM TB_APT_LTTOT_PBLANC_DETAIL ORDER BY HOUSE_MANAGE_NO", String.class));
        assertEquals(List.of("A"), jdbc.queryForList(
                "SELECT HOUSE_MANAGE_NO FROM TB_APT_LTTOT_PBLANC_MDL ORDER BY HOUSE_MANAGE_NO", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_URBTY_OFCTL_LTTOT_PBLANC_MDL", Integer.class));
        // 주택형은 공고마다 한 번만 조회한다.
        verify(api, times(1)).getPage(eq("getAPTLttotPblancMdl"), anyInt(),
                argThat(filters -> "C".equals(filters.get("cond[HOUSE_MANAGE_NO::EQ]"))));
    }

    @Test void failedModelFetchKeepsTheExistingDetailAndModels() throws Exception {
        // 이미 저장된 공고 A의 상세·주택형
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM) VALUES ('A', '1', '예전 이름')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_MDL (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, SUPLY_AR) VALUES ('A', '1', '01', '59.9')");
        var api = mock(ApplyhomeApiClient.class);
        when(api.getPage(anyString(), anyInt(), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        page(api, "getAPTLttotPblancDetail", null,
                row("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"새 이름\"}"),
                row("{\"HOUSE_MANAGE_NO\":\"N\",\"PBLANC_NO\":\"1\",\"HOUSE_NM\":\"새 공고\"}"));
        when(api.getPage(eq("getAPTLttotPblancMdl"), anyInt(), anyMap())).thenThrow(new IllegalStateException("timeout"));
        var lock = mock(ApplyhomeSyncLock.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);

        var failure = assertThrows(ApplyhomePartialFailureException.class,
                () -> service.synchronize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        assertTrue(failure.getMessage().contains("A/1") && failure.getMessage().contains("N/1"), failure.getMessage());
        // 주택형을 못 받은 공고는 상세도 바꾸지 않는다. 새 공고 N은 주택형 없이 먼저 보이지 않는다.
        assertEquals(List.of("예전 이름"), jdbc.queryForList(
                "SELECT HOUSE_NM FROM TB_APT_LTTOT_PBLANC_DETAIL ORDER BY HOUSE_MANAGE_NO", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_APT_LTTOT_PBLANC_MDL", Integer.class));
    }

    @Test void providerRejectionStillStopsImmediately() throws Exception {
        var api = mock(ApplyhomeApiClient.class);
        when(api.getPage(anyString(), anyInt(), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        page(api, "getAPTLttotPblancDetail", null,
                row("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"1\"}"),
                row("{\"HOUSE_MANAGE_NO\":\"B\",\"PBLANC_NO\":\"1\"}"));
        when(api.getPage(eq("getAPTLttotPblancMdl"), anyInt(), anyMap()))
                .thenThrow(new ApplyhomeApiClient.ProviderRejectedException(429, "quota"));
        var lock = mock(ApplyhomeSyncLock.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);

        assertThrows(ApplyhomeApiClient.ProviderRejectedException.class,
                () -> service.synchronize(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)));
        // 첫 공고에서 거절되면 남은 공고와 다음 유형은 호출하지 않는다.
        verify(api, times(1)).getPage(eq("getAPTLttotPblancMdl"), anyInt(), anyMap());
        verify(api, never()).getPage(eq("getUrbtyOfctlLttotPblancDetail"), anyInt(), anyMap());
    }

    private JsonNode row(String text) throws Exception { return json.readTree(text); }

    /** houseManageNo가 있으면 그 공고의 주택형 조회에만 응답한다. */
    private void page(ApplyhomeApiClient api, String endpoint, String houseManageNo, JsonNode... rows) {
        var response = new ApplyhomeApiPage(List.of(rows), rows.length, rows.length);
        if (houseManageNo == null) {
            when(api.getPage(eq(endpoint), eq(1), anyMap())).thenReturn(response);
        } else {
            when(api.getPage(eq(endpoint), eq(1), argThat(filters -> filters != null
                    && houseManageNo.equals(filters.get("cond[HOUSE_MANAGE_NO::EQ]"))))).thenReturn(response);
        }
    }
}
