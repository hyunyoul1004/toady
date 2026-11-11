package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.apt.api.ApplyhomeApiClient;
import com.toady.web.apt.api.ApplyhomeApiPage;
import com.toady.web.apt.api.ApplyhomeCompetitionApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplyhomeCompetitionSyncServiceTest {

	@Mock
	private ApplyhomeApiClient announcementApiClient;
	@Mock
	private ApplyhomeCompetitionApiClient competitionApiClient;
	@Mock
	private ApplyhomeCompetitionPersistenceService persistenceService;
	@Mock
	private ApplyhomeSyncLock syncLock;

	@Test
	void queriesAllEightDocumentedResultEndpointsForCurrentAnnouncements() throws Exception {
		// 잠금의 실행 동작만 대체하고, 현재 공고 하나에 대해 문서화된 결과 API를 모두 조회하는지 검증한다.
		JsonNode announcement = new ObjectMapper().readTree("""
				{"HOUSE_MANAGE_NO":"2026000528","PBLANC_NO":"2026000528"}
				""");
		when(announcementApiClient.getPage(anyString(), eq(1), anyMap()))
				.thenReturn(new ApplyhomeApiPage(List.of(announcement), 1, 1));
		when(competitionApiClient.getPage(anyString(), eq(1), anyMap()))
				.thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
		doAnswer(invocation -> {
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(syncLock).execute(any(Runnable.class));

		ApplyhomeCompetitionSyncService service = new ApplyhomeCompetitionSyncService(
				announcementApiClient, competitionApiClient, persistenceService, syncLock);
		service.synchronize(java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 12));

		ArgumentCaptor<String> endpoints = ArgumentCaptor.forClass(String.class);
		verify(competitionApiClient, org.mockito.Mockito.times(8))
				.getPage(endpoints.capture(), eq(1), anyMap());
		assertThat(Set.copyOf(endpoints.getAllValues())).containsExactlyInAnyOrder(
				"getAPTLttotPblancCmpet",
				"getUrbtyOfctlLttotPblancCmpet",
				"getPblPvtRentLttotPblancCmpet",
				"getCancResplLttotPblancCmpet",
				"getRemndrLttotPblancCmpet",
				"getAptLttotPblancScore",
				"getOPTLttotPblancCmpet",
				"getAPTSpsplyReqstStus");
	}
    @Test
    void discoversCancellationFromIllegalResupplyInsteadOfOrdinaryAptAnnouncements() throws Exception {
        var mapper = new ObjectMapper();
        when(announcementApiClient.getPage(anyString(), eq(1), anyMap())).thenAnswer(invocation -> {
            java.util.Map<String, String> filters = invocation.getArgument(2);
            String code = filters.get("cond[HOUSE_SECD::EQ]");
            String id = "06".equals(code) ? "CANCELLED" : "04".equals(code) ? "REMAINING" : "APT";
            // The document tables use lowercase names as well as uppercase response examples.
            JsonNode row = mapper.readTree("{\"house_manage_no\":\"" + id + "\",\"pblanc_no\":\"" + id + "\"}");
            return new ApplyhomeApiPage(List.of(row), 1, 1);
        });
        when(competitionApiClient.getPage(anyString(), eq(1), anyMap()))
                .thenReturn(new ApplyhomeApiPage(List.of(), 0, 0));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(syncLock).execute(any(Runnable.class));
        var service = new ApplyhomeCompetitionSyncService(
                announcementApiClient, competitionApiClient, persistenceService, syncLock);
        service.synchronize(java.time.LocalDate.of(2026, 11, 1), java.time.LocalDate.of(2026, 11, 12));
        verify(competitionApiClient).getPage(eq("getCancResplLttotPblancCmpet"), eq(1),
                eq(java.util.Map.of("cond[HOUSE_MANAGE_NO::EQ]", "CANCELLED", "cond[PBLANC_NO::EQ]", "CANCELLED")));
        verify(competitionApiClient).getPage(eq("getRemndrLttotPblancCmpet"), eq(1),
                eq(java.util.Map.of("cond[HOUSE_MANAGE_NO::EQ]", "REMAINING", "cond[PBLANC_NO::EQ]", "REMAINING")));
    }

    @Test
    void scoreOnlyBackfillCallsOnlyAptDiscoveryAndScoreEndpoint() throws Exception {
        var mapper = new ObjectMapper();
        var announcement = mapper.readTree("{\"HOUSE_MANAGE_NO\":\"2020000001\",\"PBLANC_NO\":\"2020000001\"}");
        var score = mapper.readTree("{\"HOUSE_MANAGE_NO\":\"2020000001\",\"PBLANC_NO\":\"2020000001\",\"MODEL_NO\":\"01\",\"RESIDE_SECD\":\"01\",\"AVRG_SCORE\":\"65.0\"}");
        when(announcementApiClient.getPage(eq("getAPTLttotPblancDetail"), eq(1), anyMap()))
                .thenReturn(new ApplyhomeApiPage(List.of(announcement), 1, 1));
        when(competitionApiClient.getPage(eq("getAptLttotPblancScore"), eq(1), anyMap()))
                .thenReturn(new ApplyhomeApiPage(List.of(score), 1, 1));
        doAnswer(invocation -> { ((Runnable) invocation.getArgument(0)).run(); return null; })
                .when(syncLock).execute(any(Runnable.class));
        var service = new ApplyhomeCompetitionSyncService(announcementApiClient, competitionApiClient, persistenceService, syncLock);
        assertThat(service.synchronizeScores(java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2020, 12, 31))).isEqualTo(1);
        verify(competitionApiClient, org.mockito.Mockito.times(1)).getPage(anyString(), eq(1), anyMap());
        verify(announcementApiClient, org.mockito.Mockito.times(1)).getPage(anyString(), eq(1), anyMap());
        verify(persistenceService).upsert(eq("TB_APT_LTTOT_PBLANC_SCORE"), any(), any(), eq(List.of(score)));
    }

}
