package com.toady.web.core.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 메일을 보내는 공개 요청(가입·인증 재발송·비밀번호 찾기)을 요청자 IP별로 제한한다.
 * 계정별 4분 간격만으로는 무작위 이메일 대량 가입으로 하루 메일 한도(Gmail)를 소진시키는 공격을 막지 못하기 때문이다.
 * API는 단일 인스턴스로 운영하므로 메모리 창으로 충분하다(재시작 시 초기화).
 */
@Component
public class MailRequestLimitFilter extends OncePerRequestFilter {
    static final Set<String> PATHS = Set.of(
            "/webApi/user/regUser",
            "/webApi/certify/resend-account-verification",
            "/webApi/certify/find-password-request-certify");

    private final int shortLimit; private final Duration shortWindow; private final int dailyLimit;
    private final Clock clock;
    private final Cache<String, Deque<Long>> hits = CacheBuilder.newBuilder()
            .maximumSize(100_000).expireAfterAccess(1, TimeUnit.DAYS).build();

    @org.springframework.beans.factory.annotation.Autowired
    public MailRequestLimitFilter(@Value("${toady.security.mail-requests-per-10min:5}") int shortLimit,
                                  @Value("${toady.security.mail-requests-per-day:20}") int dailyLimit) {
        this(shortLimit, Duration.ofMinutes(10), dailyLimit, Clock.systemUTC());
    }
    MailRequestLimitFilter(int shortLimit, Duration shortWindow, int dailyLimit, Clock clock) {
        this.shortLimit = shortLimit; this.shortWindow = shortWindow; this.dailyLimit = dailyLimit; this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allow(clientIp(request))) {
            response.setStatus(429);
            response.setHeader("Retry-After", "600");
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(("{\"resultCode\":\"ERR-429\",\"resultMessage\":\"요청이 너무 많습니다. 10분 뒤에 다시 시도해 주세요.\"}")
                    .getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    /** 10분·하루 창 안의 요청 수를 확인하고, 허용할 때만 이번 요청을 기록한다. */
    boolean allow(String ip) {
        long now = clock.millis(), shortFrom = now - shortWindow.toMillis(), dayFrom = now - Duration.ofDays(1).toMillis();
        Deque<Long> times;
        try { times = hits.get(ip, ArrayDeque::new); } catch (java.util.concurrent.ExecutionException impossible) { return true; }
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= dayFrom) times.pollFirst();
            long recent = times.stream().filter(time -> time > shortFrom).count();
            if (recent >= shortLimit || times.size() >= dailyLimit) return false;
            times.addLast(now);
            return true;
        }
    }

    /**
     * 앞단 nginx가 넣은 X-Real-IP는 요청이 사설·루프백 주소(프록시)에서 왔을 때만 믿는다.
     * 외부에서 직접 들어온 요청이 헤더를 꾸며 제한을 피하지 못하게 하기 위해서다.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && !forwarded.isBlank() && fromProxy(remote)) return forwarded.trim();
        return remote;
    }
    private static boolean fromProxy(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress();
        } catch (Exception invalid) { return false; }
    }
}
