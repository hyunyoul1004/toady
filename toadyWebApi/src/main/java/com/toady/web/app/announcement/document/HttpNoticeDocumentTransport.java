package com.toady.web.app.announcement.document;

import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class HttpNoticeDocumentTransport implements NoticeDocumentTransport {
    private static final long TOTAL_SECONDS = 6;
    private static final ExecutorService BODY_READERS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "notice-body"); thread.setDaemon(true); return thread;
    });
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override public byte[] get(URI uri) throws Exception {
        if (!"https".equals(uri.getScheme()) || !"www.applyhome.co.kr".equals(uri.getHost())
                || uri.getPort() != -1 || !uri.getPath().startsWith("/ai/aia/select")) {
            throw new IOException("Unsupported notice source");
        }
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4)).GET().build();
        // 요청 timeout은 응답 헤더까지만 걸리므로 본문까지 받는 전체 시간을 따로 제한한다.
        // 청약홈이 본문 중간에 멈춰도 요청 스레드(와 같은 공고를 기다리는 캐시 로더)가 묶이지 않게 한다.
        var stream = new AtomicReference<InputStream>();
        var arrived = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            stream.set(response.body());
            return response;
        });
        var pending = arrived.thenApplyAsync(response -> {
            try (var body = response.body()) {
                if (response.statusCode() != 200) throw new UncheckedIOException(new IOException("Notice source unavailable"));
                byte[] bytes = body.readNBytes(2_000_001);
                if (bytes.length > 2_000_000) throw new UncheckedIOException(new IOException("Notice source too large"));
                return bytes;
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }, BODY_READERS);
        // 시간 초과 뒤에 늦게 도착한 응답도 읽지 않고 바로 닫는다.
        arrived.thenAccept(response -> { if (pending.isCancelled()) try { response.body().close(); } catch (IOException ignored) { /* 이미 끊김 */ } });
        try {
            return pending.get(TOTAL_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException | InterruptedException stopped) {
            // 시간 초과·요청 스레드 중단 모두 본문 읽기를 끝내 읽기 스레드가 남지 않게 한다.
            pending.cancel(true);
            var body = stream.get();
            if (body != null) try { body.close(); } catch (IOException ignored) { /* 이미 끊긴 연결 */ }
            if (stopped instanceof InterruptedException) { Thread.currentThread().interrupt(); throw stopped; }
            throw new IOException("Notice source timeout");
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause() instanceof UncheckedIOException unchecked ? unchecked.getCause() : failed.getCause();
            if (cause instanceof Exception exception) throw exception;
            throw new IOException("Notice source unavailable", cause);
        }
    }
}
