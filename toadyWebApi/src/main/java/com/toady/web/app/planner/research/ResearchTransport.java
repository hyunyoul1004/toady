package com.toady.web.app.planner.research;

import java.net.URI;
import java.util.Map;

/** 공식 제공처에만 요청하며 테스트에서는 실제 외부 통신을 대체한다. */
public interface ResearchTransport {
    byte[] get(URI uri, Map<String,String> headers) throws Exception;
}
