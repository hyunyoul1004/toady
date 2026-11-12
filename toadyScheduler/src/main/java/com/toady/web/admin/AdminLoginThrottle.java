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

    // inFlight는 비밀번호 확인(BCrypt)이 끝나지 않은 시도 수다. 동시에 보낸 요청이 실패 기록 전에 모두 통과하지 못하게 함께 센다.
    private record Attempts(int failures, Instant firstFailure, Instant lockedUntil, int inFlight) {
        boolean empty() { return failures == 0 && inFlight == 0; }
    }
    private static final Attempts NONE = new Attempts(0, null, null, 0);
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public AdminLoginThrottle() { this(Clock.systemUTC()); }
    AdminLoginThrottle(Clock clock) { this.clock = clock; }

    public boolean isLocked(String ip) {
        Attempts current = attempts.get(ip);
        return current != null && current.lockedUntil() != null && clock.instant().isBefore(current.lockedUntil());
    }

    /**
     * 비밀번호 확인 전에 호출한다. 잠겼거나 (기간 안 실패 + 진행 중 시도)가 상한에 닿으면 false를 반환한다.
     * true를 받은 호출은 반드시 failed·succeeded·abandoned 중 하나로 끝내야 한다.
     */
    public boolean tryBegin(String ip) {
        Instant now = clock.instant();
        makeRoom(now);
        boolean[] allowed = {false};
        attempts.compute(ip, (key, stored) -> {
            Attempts current = fresh(stored, now);
            if (current.lockedUntil() != null || current.failures() + current.inFlight() >= MAX_FAILURES) {
                return current.empty() ? null : current;
            }
            allowed[0] = true;
            return new Attempts(current.failures(), current.firstFailure(), null, current.inFlight() + 1);
        });
        return allowed[0];
    }

    /** 실패를 기록한다. 기간 안의 실패가 상한에 닿으면 잠금 시각을 설정한다. */
    public void failed(String ip) {
        Instant now = clock.instant();
        makeRoom(now);
        attempts.compute(ip, (key, stored) -> {
            Attempts current = fresh(stored, now);
            int failures = current.failures() + 1;
            Instant first = current.failures() == 0 ? now : current.firstFailure();
            return new Attempts(failures, first, failures >= MAX_FAILURES ? now.plus(LOCK) : null,
                    Math.max(0, current.inFlight() - 1));
        });
    }

    /** 성공하면 실패 기록을 지운다. 같은 IP의 다른 진행 중 시도 수는 유지한다. */
    public void succeeded(String ip) {
        attempts.computeIfPresent(ip, (key, current) -> {
            int inFlight = Math.max(0, current.inFlight() - 1);
            return inFlight == 0 ? null : new Attempts(0, null, null, inFlight);
        });
    }

    /** 인증 결과 없이 끝난 시도(예외 등)는 실패로 세지 않고 진행 중 수만 줄인다. */
    public void abandoned(String ip) {
        Instant now = clock.instant();
        attempts.computeIfPresent(ip, (key, stored) -> {
            Attempts current = fresh(stored, now);
            Attempts next = new Attempts(current.failures(), current.firstFailure(), current.lockedUntil(),
                    Math.max(0, current.inFlight() - 1));
            return next.empty() ? null : next;
        });
    }

    // 잠금이 끝났거나 실패 집계 기간이 지났으면 실패 수를 새로 센다. 진행 중 시도 수는 유지한다.
    private static Attempts fresh(Attempts value, Instant now) {
        if (value == null) return NONE;
        boolean reset = value.lockedUntil() != null ? !now.isBefore(value.lockedUntil())
                : value.firstFailure() != null && !now.isBefore(value.firstFailure().plus(WINDOW));
        return reset ? new Attempts(0, null, null, value.inFlight()) : value;
    }
    private void makeRoom(Instant now) {
        if (attempts.size() < MAX_TRACKED) return;
        attempts.replaceAll((key, value) -> fresh(value, now));
        attempts.values().removeIf(Attempts::empty);
        // 그래도 넘치면 진행 중이 아닌 기록부터 버린다. 진행 중 수를 잃으면 동시 시도 차단이 약해지기 때문이다.
        if (attempts.size() >= MAX_TRACKED) attempts.values().removeIf(value -> value.inFlight() == 0);
    }

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
