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

/**
 * 청약홈 공고 상세 페이지를 HTTP로 받아 오는 실제 구현. NoticeDocumentService → 이 클래스 → 청약홈.
 * 청약홈 상세 주소만 허용하고, 응답 크기(2MB)와 전체 시간(6초)을 제한해 외부 사이트가 느려도 API가 묶이지 않게 한다.
 */
// @Component: 스프링 빈으로 등록한다. NoticeDocumentTransport 구현이 이것 하나라 서비스에 자동으로 들어간다.
@Component
public class HttpNoticeDocumentTransport implements NoticeDocumentTransport {
    private static final long TOTAL_SECONDS = 6;
    // 본문 읽기 전용 스레드. 데몬 스레드라 서버 종료를 막지 않는다.
    private static final ExecutorService BODY_READERS = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "notice-body"); thread.setDaemon(true); return thread;
    });
    // 연결 2초 제한. 리다이렉트는 따라가지 않는다(허용하지 않은 주소로 넘어가지 않게).
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    // 다른 호스트·경로로 요청하지 못하게 청약홈 상세 페이지 주소만 받는다.
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
        // 200 응답만 받고, 2MB를 넘으면 버린다(한 바이트 더 읽어 넘었는지 확인한다).
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
