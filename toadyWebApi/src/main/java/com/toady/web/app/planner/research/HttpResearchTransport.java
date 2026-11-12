package com.toady.web.app.planner.research;

import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.io.IOException;

@Component
public class HttpResearchTransport implements ResearchTransport {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Override public byte[] get(URI uri,Map<String,String> headers) throws Exception {
        if(!"https".equals(uri.getScheme()) || !java.util.Set.of("apis.data.go.kr","dapi.kakao.com").contains(uri.getHost())) throw new IOException("Unsupported provider");
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET();
        headers.forEach(request::header);
        var response=client.send(request.build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()!=200) throw new IOException("Provider unavailable");
            byte[] bytes=body.readNBytes(2_000_001);
            if(bytes.length>2_000_000) throw new IOException("Provider response too large");
            return bytes;
        }
    }
}
