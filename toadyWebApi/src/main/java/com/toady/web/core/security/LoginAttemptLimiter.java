package com.toady.web.core.security;

import com.toady.web.core.redis.SlidingWindows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

/**
 * 로그인 실패를 세어 무차별 대입을 늦춘다(15분 창).
 * - IP(IPv6는 /64) 20회, 같은 IP에서 같은 이메일 5회 실패하면 그 조합을 막는다.
 * - 이메일 전체 실패는 30회까지 허용한다. 공격자가 남의 이메일로 일부러 몇 번 틀려 주인을 잠그는 일을 막되,
 *   여러 IP로 나눠 한 계정을 노리는 분산 공격은 늦춘다.
 * 성공하면 그 이메일의 기록을 지운다. 기록은 Redis에 두어 배포·재시작 뒤에도 유지한다(없으면 메모리).
 * 확인과 기록을 묶는 synchronized는 이 API 안에서만 원자적이다. API를 여러 대 띄우면 동시 시도가 한도를 조금 넘을 수 있어,
 * 그때는 확인·기록을 Lua 스크립트 하나로 묶어야 한다(지금은 API 한 대라 synchronized로 충분하다). 이메일은 길이를 제한해 키에 넣는다.
 * UserService의 로그인 처리에서 부른다. 실제 저장·계산은 SlidingWindows(Redis 또는 메모리)가 한다.
 */
// @Component: 빈 하나(싱글턴)로 등록되므로 synchronized가 앱 전체의 로그인 시도를 한 줄로 세운다
@Component
public class LoginAttemptLimiter {
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final int PER_IP = 20, PER_EMAIL_IP = 5, PER_EMAIL = 30;
    /** 로그인에 성공한 (이메일, 네트워크)를 기억하는 기간. */
    static final Duration TRUSTED = Duration.ofDays(30);
    private final SlidingWindows windows;

    // @Autowired: 생성자가 둘이라 스프링이 쓸 생성자를 지정한다. 아래 Clock 생성자는 테스트용(메모리 저장)
    @Autowired
    public LoginAttemptLimiter(SlidingWindows windows) { this.windows = windows; }
    LoginAttemptLimiter(Clock clock) { this(SlidingWindows.inMemory(clock)); }

    /** 이메일 최대 길이(RFC 5321). 이보다 긴 입력은 잘라서 세므로 아주 긴 문자열로 큰 키를 만들 수 없다. */
    static final int MAX_EMAIL = 254;
    private static String email(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return "email|" + (normalized.length() > MAX_EMAIL ? normalized.substring(0, MAX_EMAIL) : normalized);
    }
    private static String ip(String value) { return "ip|" + RequestLimitFilter.limitKey(value); }
    private static String pair(String ip, String email) { return email(email) + "|" + RequestLimitFilter.limitKey(ip); }

    /** 지금 이 IP·이메일 조합으로 로그인을 시도해도 되는지 본다(기록하지 않는다). */
    public boolean blocked(String ip, String email) {
        // 이메일 전체 한도는 이 이메일로 로그인에 성공한 적 있는 네트워크에는 적용하지 않는다.
        // 여러 IP로 남의 계정을 일부러 잠가도 평소 쓰던 기기·네트워크의 주인은 로그인할 수 있다.
        return count(ip(ip)) >= PER_IP || count(pair(ip, email)) >= PER_EMAIL_IP
            || (count(email(email)) >= PER_EMAIL && !trusted(ip, email));
    }
    /** 실패 한 번을 IP, IP+이메일, 이메일 세 키에 모두 기록한다. */
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
    /** 로그인 성공: 이 이메일의 실패 기록을 지우고, 이 네트워크를 30일 동안 신뢰 목록에 올린다. */
    public synchronized void succeeded(String ip, String email) {
        windows.clear(login(email(email))); windows.clear(login(pair(ip, email)));
        // 미리 센 IP 시도도 되돌린다. 같은 사무실(NAT)의 정상 로그인이 IP 한도를 채우지 않게 한다.
        removeLast(ip(ip));
        windows.mark(login(pair(ip, email)), TRUSTED);
    }
    private static String login(String key) { return "login|" + key; }
    private void removeLast(String key) { windows.removeLast(login(key)); }
    private boolean trusted(String ip, String email) { return windows.marked(login(pair(ip, email))); }

    private int count(String key) { return windows.count(login(key), WINDOW); }
    private void add(String key) { windows.add(login(key), WINDOW); }
}
