package com.toady.web.admin;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 관리자 로그인 실패를 접속 IP별로 세고, 짧은 기간 안에 반복 실패하면 일정 시간 로그인 시도를 막는다.
 * 단일 스케줄러 인스턴스의 메모리 상태이므로 재시작하면 초기화된다(관리자 계정 하나를 지키는 용도로 충분하다).
 */
public class AdminLoginThrottle {
    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    // 다수 IP로 메모리를 채우는 요청에 대비한 상한. 넘으면 만료 항목부터 정리한다.
    private static final int MAX_TRACKED = 10_000;
    // X-Real-IP는 IP 리터럴만 받는다. 호스트 이름을 넘겨 DNS 조회가 일어나지 않게 한다.
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9A-Fa-f:.]{2,45}");

    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil) { }
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AdminLoginThrottle() { this(Clock.systemUTC()); }
    AdminLoginThrottle(Clock clock) { this.clock = clock; }

    public boolean isLocked(String ip) {
        Attempts current = attempts.get(ip);
        return current != null && current.lockedUntil() != null && clock.instant().isBefore(current.lockedUntil());
    }

    /** 실패를 기록한다. 기간 안의 실패가 상한에 닿으면 잠금 시각을 설정한다. */
    public void failed(String ip) {
        Instant now = clock.instant();
        if (attempts.size() >= MAX_TRACKED) purge(now);
        if (attempts.size() >= MAX_TRACKED) attempts.clear();
        attempts.compute(ip, (key, current) -> {
            if (current == null || expired(current, now)) return new Attempts(1, now, null);
            int failures = current.failures() + 1;
            return new Attempts(failures, current.firstFailure(), failures >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
    }

    public void succeeded(String ip) { attempts.remove(ip); }

    private static boolean expired(Attempts value, Instant now) {
        if (value.lockedUntil() != null) return !now.isBefore(value.lockedUntil());
        return !now.isBefore(value.firstFailure().plus(WINDOW));
    }
    private void purge(Instant now) { attempts.values().removeIf(value -> expired(value, now)); }

    /**
     * 실제 접속 IP. 앞단 nginx(같은 서버·도커 네트워크)를 거친 요청에서만 X-Real-IP를 믿는다.
     * 외부에서 직접 들어온 요청이 헤더를 꾸며 다른 IP로 위장하지 못하게 하기 위해서다.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && IP_LITERAL.matcher(forwarded.trim()).matches() && isInternal(remote)) {
            return forwarded.trim();
        }
        return remote;
    }

    static boolean isInternal(String address) {
        if (address == null || !IP_LITERAL.matcher(address).matches()) return false;
        try {
            InetAddress parsed = InetAddress.getByName(address);
            if (parsed.isLoopbackAddress() || parsed.isSiteLocalAddress()) return true;
            // IPv6 고유 로컬 주소(fc00::/7)는 isSiteLocalAddress가 판별하지 않는다.
            byte[] bytes = parsed.getAddress();
            return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        } catch (UnknownHostException invalid) {
            return false;
        }
    }
}
