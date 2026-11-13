package com.toady.web.app.planner.research;

import java.net.URI;
import java.util.Map;

/** 공식 제공처에만 요청하며 테스트에서는 실제 외부 통신을 대체한다. */
public interface ResearchTransport {
    byte[] get(URI uri, Map<String,String> headers) throws Exception;

    /** 제공처가 200이 아닌 상태 코드로 응답함. 429(요청 과다)·4xx는 다시 불러도 같으므로 재시도하지 않는다. */
    final class ProviderStatus extends java.io.IOException {
        private final int status;
        public ProviderStatus(int status) { super("Provider unavailable: HTTP " + status); this.status = status; }
        public int status() { return status; }
        /** 잠시 뒤 다시 시도할 만한 상태(5xx)인지. */
        public boolean retryable() { return status >= 500; }
    }
}
