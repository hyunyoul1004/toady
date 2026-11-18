package com.toady.web.app.planner.research;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * 외부 공공 API(국토부 실거래 apis.data.go.kr, 카카오 dapi.kakao.com)를 부르는 통로.
 * 공식 제공처에만 요청하며, 테스트에서는 이 인터페이스를 가짜 구현으로 바꿔 실제 외부 통신을 대체한다.
 * 실제 구현은 HttpResearchTransport이고, NeighborhoodResearchService·ComplexLocator·NearbyPlaces 등이 쓴다.
 */
public interface ResearchTransport {
    /** uri로 GET 요청을 보내고 응답 본문을 바이트로 돌려준다. 200이 아니면 ProviderStatus를 던진다. */
    byte[] get(URI uri, Map<String,String> headers) throws Exception;

    /** 제공처가 200이 아닌 상태 코드로 응답함. 429(요청 과다)·4xx는 다시 불러도 같으므로 재시도하지 않는다. */
    final class ProviderStatus extends IOException {
        private final int status;
        public ProviderStatus(int status) { super("Provider unavailable: HTTP " + status); this.status = status; }
        public int status() { return status; }
        /** 잠시 뒤 다시 시도할 만한 상태(5xx)인지. */
        public boolean retryable() { return status >= 500; }
    }
}
