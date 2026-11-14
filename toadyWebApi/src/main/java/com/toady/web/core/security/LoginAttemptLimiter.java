package com.toady.web.core.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 로그인 실패를 세어 무차별 대입을 늦춘다(15분 창).
 * - IP(IPv6는 /64) 20회, 같은 IP에서 같은 이메일 5회 실패하면 그 조합을 막는다.
 * - 이메일 전체 실패는 30회까지 허용한다. 공격자가 남의 이메일로 일부러 몇 번 틀려 주인을 잠그는 일을 막되,
 *   여러 IP로 나눠 한 계정을 노리는 분산 공격은 늦춘다.
 * 성공하면 그 이메일의 기록을 지운다. 단일 API 인스턴스 기준 메모리 창이다.
 */
@Component
public class LoginAttemptLimiter {
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int PER_IP = 20, PER_EMAIL_IP = 5, PER_EMAIL = 30;
    private final Clock clock;
    /** 로그인에 성공한 (이메일, 네트워크). 30일간 기억한다. */
    private final Cache<String, Boolean> trustedNetworks = CacheBuilder.newBuilder().maximumSize(200_000).expireAfterWrite(30, TimeUnit.DAYS).build();
    private final Cache<String, Deque<Long>> failures = CacheBuilder.newBuilder()
            .maximumSize(200_000).expireAfterAccess(WINDOW.toMinutes(), TimeUnit.MINUTES).build();

    public LoginAttemptLimiter() { this(Clock.systemUTC()); }
    LoginAttemptLimiter(Clock clock) { this.clock = clock; }

    private static String email(String value) { return "email|" + (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)); }
    private static String ip(String value) { return "ip|" + RequestLimitFilter.limitKey(value); }
    private static String pair(String ip, String email) { return email(email) + "|" + RequestLimitFilter.limitKey(ip); }

    public boolean blocked(String ip, String email) {
        // 이메일 전체 한도는 이 이메일로 로그인에 성공한 적 있는 네트워크에는 적용하지 않는다.
        // 여러 IP로 남의 계정을 일부러 잠가도 평소 쓰던 기기·네트워크의 주인은 로그인할 수 있다.
        return count(ip(ip)) >= PER_IP || count(pair(ip, email)) >= PER_EMAIL_IP
            || (count(email(email)) >= PER_EMAIL && !trusted(ip, email));
    }
    public void failed(String ip, String email) { add(ip(ip)); add(pair(ip, email)); add(email(email)); }

    /**
     * 비밀번호를 확인하기 전에 시도 한 번을 먼저 기록한다. 확인(BCrypt)과 기록 사이에 동시 요청이 끼어
     * 한도를 넘는 일을 막기 위해 확인과 기록을 한 번에 한다. 막혀 있으면 false.
     * 비밀번호가 맞으면 succeeded, 비밀번호와 무관한 결과(잠금·탈퇴·서버 오류)면 release를 부른다.
     */
    public synchronized boolean tryAcquire(String ip, String email) {
        if (blocked(ip, email)) return false;
        failed(ip, email);
        return true;
    }
    /** tryAcquire로 미리 센 시도를 되돌린다(비밀번호 확인과 무관한 결과). */
    public synchronized void release(String ip, String email) { removeLast(ip(ip)); removeLast(pair(ip, email)); removeLast(email(email)); }
    public synchronized void succeeded(String ip, String email) {
        failures.invalidate(email(email)); failures.invalidate(pair(ip, email));
        // 미리 센 IP 시도도 되돌린다. 같은 사무실(NAT)의 정상 로그인이 IP 한도를 채우지 않게 한다.
        removeLast(ip(ip));
        trustedNetworks.put(pair(ip, email), Boolean.TRUE);
    }
    private void removeLast(String key) {
        Deque<Long> times = failures.getIfPresent(key);
        if (times != null) synchronized (times) { times.pollLast(); }
    }
    private boolean trusted(String ip, String email) { return trustedNetworks.getIfPresent(pair(ip, email)) != null; }

    private int count(String key) {
        Deque<Long> times = failures.getIfPresent(key);
        if (times == null) return 0;
        long from = clock.millis() - WINDOW.toMillis();
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= from) times.pollFirst();
            return times.size();
        }
    }
    private void add(String key) {
        try {
            Deque<Long> times = failures.get(key, ArrayDeque::new);
            synchronized (times) { times.addLast(clock.millis()); }
        } catch (ExecutionException impossible) { /* 기록 실패는 로그인 흐름에 영향 없음 */ }
    }
}
