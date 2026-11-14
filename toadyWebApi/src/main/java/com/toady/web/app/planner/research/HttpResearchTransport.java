package com.toady.web.app.planner.research;

import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class HttpResearchTransport implements ResearchTransport {
    static final int TOTAL_SECONDS=6;
    /** 본문 읽기(막힐 수 있는 작업)를 공용 ForkJoin 풀이 아닌 전용 스레드에서 한다(동시 요청이 공용 풀에서 줄 서지 않게). */
    private static final ExecutorService BODY_READERS=Executors.newCachedThreadPool(task -> {
        Thread thread=new Thread(task,"research-body"); thread.setDaemon(true); return thread;
    });
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    @Override public byte[] get(URI uri,Map<String,String> headers) throws Exception {
        if(!"https".equals(uri.getScheme()) || !Set.of("apis.data.go.kr","dapi.kakao.com").contains(uri.getHost())) throw new IOException("Unsupported provider");
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET();
        headers.forEach(request::header);
        // 요청 timeout은 응답 헤더까지만 걸리므로, 본문까지 받는 전체 시간을 따로 제한한다(제공처가 본문 중간에 멈춰도 스레드가 묶이지 않게).
        var stream=new AtomicReference<InputStream>();
        // 헤더를 받는 즉시(본문 읽기 작업이 시작되기 전) 스트림을 잡아 두어, 시간 초과 때 언제든 닫을 수 있게 한다.
        var arrived=client.sendAsync(request.build(),HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            stream.set(response.body());
            return response;
        });
        var pending=arrived.thenApplyAsync(response -> {
            try(var body=response.body()) {
                if(response.statusCode()!=200) throw new UncheckedIOException(new ResearchTransport.ProviderStatus(response.statusCode()));
                byte[] bytes=body.readNBytes(2_000_001);
                if(bytes.length>2_000_000) throw new UncheckedIOException(new IOException("Provider response too large"));
                return bytes;
            } catch(IOException e) { throw new UncheckedIOException(e); }
        },BODY_READERS);
        // 시간 초과 뒤에 늦게 도착한 응답도 읽지 않고 바로 닫는다.
        arrived.thenAccept(response -> { if(pending.isCancelled()) try { response.body().close(); } catch(IOException ignored) { /* 이미 끊김 */ } });
        try {
            return pending.get(TOTAL_SECONDS,TimeUnit.SECONDS);
        } catch(TimeoutException timeout) {
            pending.cancel(true);
            // 본문을 읽고 있던 스트림을 닫아 HttpClient 쪽 읽기도 끝낸다.
            var body=stream.get();
            if(body!=null) try { body.close(); } catch(IOException ignored) { /* 이미 끊긴 연결 */ }
            throw new IOException("Provider timeout");
        } catch(ExecutionException failed) {
            Throwable cause=failed.getCause() instanceof UncheckedIOException unchecked?unchecked.getCause():failed.getCause();
            if(cause instanceof Exception exception) throw exception;
            throw new IOException("Provider unavailable",cause);
        }
    }
}
