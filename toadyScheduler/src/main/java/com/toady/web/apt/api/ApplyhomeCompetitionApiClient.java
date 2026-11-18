package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 청약접수 경쟁률 및 신청현황 API(ApplyhomeInfoCmpetRtSvc)를 페이지 단위로 조회한다.
 * 경쟁률·특별공급 신청현황·당첨 가점을 받는다. 호출: ApplyhomeCompetitionSyncService.
 * 인증키·재시도·주소 만들기는 ApplyhomeApiClient의 공용 메서드를 그대로 쓴다.
 */
// @Component: 빈으로 등록한다. 생성자의 @Value로 application.yml의 주소·키·페이지 크기를 받는다.
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

	/** 한 페이지를 요청하고 응답 형식(data 배열, 건수)이 맞는지 확인해 돌려준다. */
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

	/** 공고 API와 같은 방식으로 요청 주소를 만든다(테스트에서 직접 확인할 수 있게 남겨 둔 통로). */
	static URI buildUri(String baseUrl, String endpoint, String serviceKey, int perPage, int page,
			Map<String, String> filters) {
		return ApplyhomeApiClient.buildUri(baseUrl, endpoint, serviceKey, perPage, page, filters);
	}
}
