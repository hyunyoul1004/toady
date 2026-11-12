package com.toady.web.app.announcement.document;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class HttpNoticeDocumentTransport implements NoticeDocumentTransport {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override public byte[] get(URI uri) throws Exception {
        if (!"https".equals(uri.getScheme()) || !"www.applyhome.co.kr".equals(uri.getHost())
                || uri.getPort() != -1 || !uri.getPath().startsWith("/ai/aia/select")) {
            throw new IOException("Unsupported notice source");
        }
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new IOException("Notice source unavailable");
            byte[] bytes = body.readNBytes(2_000_001);
            if (bytes.length > 2_000_000) throw new IOException("Notice source too large");
            return bytes;
        }
    }
}
