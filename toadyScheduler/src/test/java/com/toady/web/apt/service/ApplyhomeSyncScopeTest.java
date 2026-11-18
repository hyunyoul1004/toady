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
        verify(persistence, never()).saveAnnouncement(any(), any(), any(), anyList());
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
        // 주택형 조회가 실패한 공고는 상세도 저장하지 않는다(상세만 남는 공고가 생기지 않게).
        verify(persistence, never()).saveAnnouncement(any(), any(), any(), anyList());
        // 아파트 주택형 하나가 실패해도 오피스텔·무순위·임대·임의공급 목록은 모두 조회한다.
        for (String endpoint : List.of("getUrbtyOfctlLttotPblancDetail", "getRemndrLttotPblancDetail", "getPblPvtRentLttotPblancDetail", "getOPTLttotPblancDetail"))
            verify(api).getPage(eq(endpoint), eq(1), anyMap());
    }

    @Test void stopMessageListsTheFirstFailedAnnouncements() throws Exception {
        var api = mock(ApplyhomeApiClient.class);
        var persistence = mock(ApplyhomeDataPersistenceService.class);
        var lock = mock(ApplyhomeSyncLock.class);
        var mapper = new ObjectMapper();
        var rows = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for (int i = 1; i <= ApplyhomeSyncService.MAX_FAILURES; i++)
            rows.add(mapper.readTree("{\"HOUSE_MANAGE_NO\":\"H" + i + "\",\"PBLANC_NO\":\"1\"}"));
        when(api.getPage(anyString(), anyInt(), anyMap())).thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        when(api.getPage(eq("getAPTLttotPblancDetail"), eq(1), anyMap())).thenReturn(new ApplyhomeApiPage(rows, rows.size(), rows.size()));
        when(api.getPage(eq("getAPTLttotPblancMdl"), eq(1), anyMap())).thenThrow(new IllegalStateException("timeout"));
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(lock).execute(any());
        var service = new ApplyhomeSyncService(api, new ApplyhomeEntityMapper(), persistence, lock);
        var failure = assertThrows(ApplyhomeFailureLimitException.class, () -> service.synchronize(LocalDate.of(2026,1,1), LocalDate.of(2026,1,31)));
        // 관리자 화면에서 어느 공고가 실패했는지 바로 보이도록 앞 5개 키와 나머지 건수를 담는다.
        assertTrue(failure.getMessage().contains("APT:H1/1, APT:H2/1, APT:H3/1, APT:H4/1, APT:H5/1 외 15건"), failure.getMessage());
    }
}
