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
