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
		service.synchronizeCurrentAnnouncements();

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
}
