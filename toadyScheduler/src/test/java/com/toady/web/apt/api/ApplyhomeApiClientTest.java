package com.toady.web.apt.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyhomeApiClientTest {

	@Test
	void decodesAlreadyEncodedServiceKeyBeforeBuildingRequestUri() {
		// 환경 변수의 서비스 키가 이미 URL 인코딩된 경우에도 요청에서 이중 인코딩되지 않아야 한다.
		String encodedServiceKey = "sample%2Fkey%2Bvalue%3D%3D";

		URI uri = ApplyhomeApiClient.buildUri(
				"https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1",
				"getAPTLttotPblancDetail",
				encodedServiceKey,
				1000,
				1,
				Map.of("cond[RCRIT_PBLANC_DE::GTE]", "2026-01-01"));

		assertThat(uri.getRawQuery()).contains("%2F", "%2B", "%3D", "cond%5BRCRIT_PBLANC_DE::GTE%5D");
		assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
				.contains("serviceKey=" + URLDecoder.decode(encodedServiceKey, StandardCharsets.UTF_8))
				.contains("cond[RCRIT_PBLANC_DE::GTE]=2026-01-01");
	}

}
