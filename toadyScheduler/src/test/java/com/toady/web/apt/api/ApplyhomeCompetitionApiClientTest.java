package com.toady.web.apt.api;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyhomeCompetitionApiClientTest {

	@Test
	void buildsCompetitionRequestWithAnnouncementFilters() {
		// 공고 식별 조건 두 개가 특수문자 인코딩 후에도 API 필터로 전달되는지 확인한다.
		URI uri = ApplyhomeCompetitionApiClient.buildUri(
				"https://api.odcloud.kr/api/ApplyhomeInfoCmpetRtSvc/v1",
				"getAPTLttotPblancCmpet",
				"sample%2Fkey%2Bvalue%3D%3D",
				1000,
				1,
				Map.of(
						"cond[HOUSE_MANAGE_NO::EQ]", "2026000528",
						"cond[PBLANC_NO::EQ]", "2026000528"));

		assertThat(uri.getPath())
				.isEqualTo("/api/ApplyhomeInfoCmpetRtSvc/v1/getAPTLttotPblancCmpet");
		assertThat(URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8))
				.contains("serviceKey=sample/key+value==")
				.contains("cond[HOUSE_MANAGE_NO::EQ]=2026000528")
				.contains("cond[PBLANC_NO::EQ]=2026000528");
	}
}
