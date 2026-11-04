package com.toady.web.apt.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;
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

/** 청약홈 API를 호출하고 페이지 응답을 검증한다. */
@Component
public class ApplyhomeApiClient {

	private final RestClient restClient;
	private final String baseUrl;
	private final String serviceKey;
	private final int perPage;

	public ApplyhomeApiClient(
			@Value("${applyhome.api.base-url}") String baseUrl,
			@Value("${applyhome.api.service-key:}") String serviceKey,
			@Value("${applyhome.api.per-page:1000}") int perPage) {
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

	/**
	 * 필터가 적용된 API 페이지 한 건을 요청한다.
	 *
	 * @param endpoint 청약홈 API의 리소스 경로
	 * @param page 조회할 페이지 번호(1부터 시작)
	 * @param filters API에 전달할 검색 조건
	 * @return 데이터 행과 페이지/검색 결과 건수를 포함한 응답
	 */
	public ApplyhomeApiPage getPage(String endpoint, int page, Map<String, String> filters) {
		if (serviceKey.isBlank()) {
			throw new IllegalStateException("APPLYHOME_API_KEY is required to call the Applyhome API");
		}
		if (perPage < 1) {
			throw new IllegalStateException("applyhome.api.per-page must be greater than zero");
		}

		URI uri = buildUri(baseUrl, endpoint, serviceKey, perPage, page, filters);

		JsonNode response;
		try {
			response = restClient.get()
					.uri(uri)
					.retrieve()
					.body(JsonNode.class);
		} catch (RestClientResponseException exception) {
			throw new IllegalStateException("Applyhome API returned HTTP "
					+ exception.getStatusCode().value() + " for endpoint=" + endpoint);
		} catch (RestClientException exception) {
			throw new IllegalStateException("Applyhome API request failed for endpoint=" + endpoint);
		}
		if (response == null || !response.path("data").isArray()) {
			throw new IllegalStateException("Applyhome API returned an invalid response for " + endpoint);
		}

		List<JsonNode> data = new ArrayList<>();
		response.path("data").forEach(data::add);
		int currentCount = response.path("currentCount").asInt(data.size());
		long matchCount = response.path("matchCount").asLong(-1);
		if (currentCount < 0 || currentCount != data.size() || matchCount < 0) {
			throw new IllegalStateException("Applyhome API response has invalid pagination counts for " + endpoint);
		}

		return new ApplyhomeApiPage(List.copyOf(data), currentCount, matchCount);
	}

	/** 이미 인코딩된 서비스 키를 처리한 뒤 요청 파라미터를 한 번만 인코딩한다. */
	static URI buildUri(String baseUrl, String endpoint, String serviceKey, int perPage, int page,
			Map<String, String> filters) {
		Map<String, String> uriVariables = new LinkedHashMap<>();
		uriVariables.put("serviceKey", UriUtils.decode(serviceKey, StandardCharsets.UTF_8));

		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
				.pathSegment(endpoint)
				.queryParam("page", page)
				.queryParam("perPage", perPage)
				.queryParam("serviceKey", "{serviceKey}");
		filters.forEach((name, value) -> {
			String variable = "filter" + uriVariables.size();
			builder.queryParam(name, "{" + variable + "}");
			uriVariables.put(variable, value);
		});

		return builder.encode()
				.buildAndExpand(uriVariables)
				.toUri();
	}
}
