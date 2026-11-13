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

	/** 로컬 HTTP 서버가 순서대로 상태 코드를 돌려준다. */
	private static com.sun.net.httpserver.HttpServer server(java.util.concurrent.atomic.AtomicInteger calls, int... statuses) throws Exception {
		var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			int status = statuses[Math.min(calls.getAndIncrement(), statuses.length - 1)];
			byte[] body = status == 200 ? "{\"data\":[],\"currentCount\":0,\"matchCount\":0}".getBytes(StandardCharsets.UTF_8) : new byte[0];
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
			if (body.length > 0) exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return server;
	}

	@Test
	void retriesServerErrorsAndStopsImmediatelyOnQuotaRejection() throws Exception {
		var calls = new java.util.concurrent.atomic.AtomicInteger();
		var flaky = server(calls, 503, 200);
		try {
			var client = new ApplyhomeApiClient("http://127.0.0.1:" + flaky.getAddress().getPort(), "key", 10);
			client.retryDelaysMs = new long[]{1, 1};
			assertThat(client.getPage("getAPTLttotPblancDetail", 1, Map.of()).matchCount()).isZero();
			assertThat(calls.get()).isEqualTo(2);
		} finally { flaky.stop(0); }

		var quotaCalls = new java.util.concurrent.atomic.AtomicInteger();
		var quota = server(quotaCalls, 429);
		try {
			var client = new ApplyhomeApiClient("http://127.0.0.1:" + quota.getAddress().getPort(), "key", 10);
			client.retryDelaysMs = new long[]{1, 1};
			org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getPage("getAPTLttotPblancDetail", 1, Map.of()))
					.isInstanceOf(ApplyhomeApiClient.ProviderRejectedException.class).hasMessageNotContaining("key=");
			assertThat(quotaCalls.get()).isEqualTo(1);
		} finally { quota.stop(0); }
	}
}
