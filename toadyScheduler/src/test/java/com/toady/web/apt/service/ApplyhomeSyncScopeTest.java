package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomeApiPage;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ApplyhomeSyncScopeTest {
    @Test void rejectsAnotherFetchedAnnouncementsModelsBeforeReplacingAnySnapshot() throws Exception {
        var api = mock(ApplyhomeApiClient.class);
        var persistence = mock(ApplyhomeDataPersistenceService.class);
        var lock = mock(ApplyhomeSyncLock.class);
        var mapper = new ObjectMapper();
        var a = mapper.readTree("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"A\"}");
        var b = mapper.readTree("{\"HOUSE_MANAGE_NO\":\"B\",\"PBLANC_NO\":\"B\"}");
        var wrongModel = mapper.readTree("{\"house_manage_no\":\"B\",\"pblanc_no\":\"B\",\"model_no\":\"01\"}");
        when(api.getPage(eq("getAPTLttotPblancDetail"), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(a,b),2,2));
        when(api.getPage(eq("getAPTLttotPblancMdl"), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(wrongModel),1,1));
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);
        assertThrows(IllegalStateException.class, () -> service.synchronize(LocalDate.of(2026,1,1), LocalDate.of(2026,1,31)));
        verify(persistence, never()).replaceModels(any(), anyList(), anyList());
        verify(persistence, never()).upsertUrbanModels(anyList(), anyList());
    }

    @Test void oneFailedModelDoesNotSkipTheOtherDatasetTypes() throws Exception {
        var api = mock(ApplyhomeApiClient.class);
        var persistence = mock(ApplyhomeDataPersistenceService.class);
        var lock = mock(ApplyhomeSyncLock.class);
        var mapper = new ObjectMapper();
        var a = mapper.readTree("{\"HOUSE_MANAGE_NO\":\"A\",\"PBLANC_NO\":\"A\"}");
        when(api.getPage(anyString(), anyInt(), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        when(api.getPage(eq("getAPTLttotPblancDetail"), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(a), 1, 1));
        when(api.getPage(eq("getAPTLttotPblancMdl"), eq(1), anyMap())).thenThrow(new IllegalStateException("timeout"));
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);
        var failure = assertThrows(ApplyhomeSyncService.PartialFailureException.class, () -> service.synchronize(LocalDate.of(2026,1,1), LocalDate.of(2026,1,31)));
        assertTrue(failure.getMessage().contains("1건"), failure.getMessage());
        // 아파트 주택형 하나가 실패해도 오피스텔·무순위·임대·임의공급 목록은 모두 조회한다.
        for (String endpoint : List.of("getUrbtyOfctlLttotPblancDetail", "getRemndrLttotPblancDetail", "getPblPvtRentLttotPblancDetail", "getOPTLttotPblancDetail"))
            verify(api).getPage(eq(endpoint), eq(1), anyMap());
    }
}
