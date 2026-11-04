package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(Duration.ofSeconds(60));
		this.restClient = RestClient.builder()
				.requestFactory(requestFactory)
				.build();
	}

	public ApplyhomeApiPage getPage(String endpoint, int page, Map<String, String> filters) {
		if (serviceKey.isBlank()) {
			throw new IllegalStateException("APPLYHOME_API_KEY is required to call the Applyhome competition API");
		}
		if (perPage < 1 || page < 1) {
			throw new IllegalArgumentException("Competition API page and per-page values must be greater than zero");
		}

		JsonNode response;
		try {
			response = restClient.get()
					.uri(buildUri(baseUrl, endpoint, serviceKey, perPage, page, filters))
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("Applyhome competition API returned HTTP "
					+ exception.getStatusCode().value() + " for endpoint=" + endpoint);
		} catch (RestClientException exception) {
			throw new IllegalStateException("Applyhome competition API request failed for endpoint=" + endpoint);
		}

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
		Map<String, String> variables = new LinkedHashMap<>();
		variables.put("serviceKey", UriUtils.decode(serviceKey, StandardCharsets.UTF_8));
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
				.pathSegment(endpoint)
				.queryParam("page", page)
				.queryParam("perPage", perPage)
				.queryParam("serviceKey", "{serviceKey}");
		filters.forEach((name, value) -> {
			String variable = "filter" + variables.size();
			builder.queryParam(name, "{" + variable + "}");
			variables.put(variable, value);
		});
		return builder.encode().buildAndExpand(variables).toUri();
	}
}
