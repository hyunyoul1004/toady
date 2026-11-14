package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 청약접수 경쟁률 및 신청현황 API를 페이지 단위로 조회한다. */
@Component
public class ApplyhomeCompetitionApiClient {

	private final RestClient restClient;
	private final String baseUrl;
	private final String serviceKey;
	private final int perPage;

	public ApplyhomeCompetitionApiClient(
			@Value("${applyhome.competition.api.base-url}") String baseUrl,
			@Value("${applyhome.api.service-key:}") String serviceKey,
			@Value("${applyhome.competition.api.per-page:1000}") int perPage) {
		this.baseUrl = baseUrl;
		this.serviceKey = serviceKey;
		this.perPage = perPage;
		this.restClient = ApplyhomeApiClient.createRestClient();
	}

	/** 재시도 간격(테스트에서 줄인다). 통신 오류·5xx만 다시 시도한다. */
	long[] retryDelaysMs = {1000, 3000};

	public ApplyhomeApiPage getPage(String endpoint, int page, Map<String, String> filters) {
		if (serviceKey.isBlank()) {
			throw new IllegalStateException("APPLYHOME_API_KEY is required to call the Applyhome competition API");
		}
		if (perPage < 1 || page < 1) {
			throw new IllegalArgumentException("Competition API page and per-page values must be greater than zero");
		}

		// 일시 오류(통신·5xx)는 공고 API와 같은 간격으로 다시 시도해, 잠깐의 장애로 실패 건수가 쌓여 작업 전체가 멈추지 않게 한다.
		JsonNode response = ApplyhomeApiClient.fetchWithRetry(restClient,
				buildUri(baseUrl, endpoint, serviceKey, perPage, page, filters), endpoint, retryDelaysMs, "Applyhome competition API");

		if (response == null || !response.path("data").isArray()) {
			throw new IllegalStateException("Applyhome competition API returned an invalid response for " + endpoint);
		}

		List<JsonNode> data = new ArrayList<>();
		response.path("data").forEach(data::add);
		int currentCount = response.path("currentCount").asInt(-1);
		long matchCount = response.path("matchCount").asLong(-1);
		if (currentCount < 0 || currentCount != data.size() || matchCount < 0) {
			throw new IllegalStateException("Applyhome competition API returned invalid pagination counts for "
					+ endpoint);
		}
		return new ApplyhomeApiPage(List.copyOf(data), currentCount, matchCount);
	}

	static URI buildUri(String baseUrl, String endpoint, String serviceKey, int perPage, int page,
			Map<String, String> filters) {
		return ApplyhomeApiClient.buildUri(baseUrl, endpoint, serviceKey, perPage, page, filters);
	}
}
