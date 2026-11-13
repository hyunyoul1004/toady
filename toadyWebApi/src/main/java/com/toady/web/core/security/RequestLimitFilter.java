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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 비용이 큰 공개 요청을 요청자 IP별로 제한한다(규칙마다 10분·하루 창).
 * - 메일: 가입·인증 재발송·비밀번호 찾기. 무작위 이메일 대량 요청으로 하루 메일 한도(Gmail)를 소진시키는 공격을 막는다.
 * - 외부 API·대량 조회: 동네 실거래(국토부 일일 쿼터), 관심 공고 요약(요청당 최대 50건 조회), 자동완성.
 * - 익명 쓰기: 웹 푸시 구독, 경쟁률 투표.
 * API는 단일 인스턴스로 운영하므로 메모리 창으로 충분하다(재시작 시 초기화).
 */
@Component
public class RequestLimitFilter extends OncePerRequestFilter {
    /** pattern: 경로 중간에 변수가 있는 규칙(예: 공고별 댓글)용. 없으면 null. */
    record Rule(String name, Set<String> methods, List<String> prefixes, Set<String> exact, java.util.regex.Pattern pattern, int shortLimit, int dailyLimit) {
        Rule(String name, Set<String> methods, List<String> prefixes, Set<String> exact, int shortLimit, int dailyLimit) {
            this(name, methods, prefixes, exact, null, shortLimit, dailyLimit);
        }
        boolean matches(String method, String path) {
            return methods.contains(method) && (exact.contains(path) || prefixes.stream().anyMatch(path::startsWith)
                || (pattern != null && pattern.matcher(path).matches()));
        }
    }
    static List<Rule> rules(int mailShort, int mailDaily) {
        return List.of(
            new Rule("mail", Set.of("POST"), List.of(), Set.of("/webApi/user/regUser", "/webApi/certify/resend-account-verification", "/webApi/certify/find-password-request-certify"), mailShort, mailDaily),
            // 인증 코드 확인(가입 인증·비밀번호 재설정)은 메일을 보내지 않지만 코드·가입 여부를 반복해 시험할 수 있으므로 따로 제한한다.
            new Rule("certify", Set.of("POST"), List.of(), Set.of("/webApi/certify/certify-check", "/webApi/certify/certify-confrim-and-password-change"), 30, 200),
            new Rule("trade", Set.of("GET"), List.of("/api/v1/research/transactions"), Set.of(), 60, 500),
            // 지도 단지 시세·분양 공고: 좌표 확인이 끝날 때까지 화면이 시·군·구(최대 7곳)마다 몇 번 다시 부르므로 따로 두고 여유를 둔다
            // (7곳 × 13회 = 91회가 한 사람의 정상 사용 상한).
            new Rule("complex", Set.of("GET"), List.of("/api/v1/research/complexes"), Set.of(), 150, 1500),
            new Rule("sales", Set.of("GET"), List.of("/api/v1/research/sales"), Set.of(), 150, 1500),
            // 분양가 비교는 요청 한 번이 시·군·구 6개월(외부 호출 6회 이상)을 읽으므로 실거래 목록보다 낮게 둔다.
            new Rule("gap", Set.of("GET"), List.of("/api/v1/research/price-gap"), Set.of(), 20, 150),
            // 가까운 시설: 요청 한 번이 카카오 카테고리 검색 3회. 같은 지점은 서버가 하루 기억한다.
            new Rule("nearby", Set.of("GET"), List.of("/api/v1/research/nearby"), Set.of(), 30, 300),
            new Rule("summary", Set.of("POST"), List.of(), Set.of("/api/v1/planner/summaries", "/api/v1/planner/results"), 60, 1000),
            new Rule("analytics", Set.of("POST"), List.of("/api/v1/analytics/"), Set.of(), 300, 3000),
            // 댓글 작성·수정: 도배와 저장소 증가를 막는다(회원만 쓸 수 있어 넉넉하지 않게).
            new Rule("comment", Set.of("POST", "PATCH"), List.of(), Set.of(),
                java.util.regex.Pattern.compile("^/api/v1/[a-z-]+/announcements/[^/]+/[^/]+/comments(/[^/]+)?$"), 20, 200),
            // 공유 이미지(PNG 렌더링)와 통합 목록 조회는 요청 한 번의 서버 비용이 커서 반복 호출을 제한한다.
            new Rule("image", Set.of("GET"), List.of("/api/v1/public/announcement-images/"), Set.of(), 120, 1500),
            new Rule("list", Set.of("GET"), List.of(), Set.of("/api/v1/announcements"), 300, 5000),
            new Rule("suggest", Set.of("GET"), List.of(), Set.of("/api/v1/announcements/suggestions"), 300, 5000),
            // 유형별 목록(이전 API)도 통합 목록과 같은 한도를 둔다.
            new Rule("type-list", Set.of("GET"), List.of(), Set.of(),
                java.util.regex.Pattern.compile("^/api/v1/(apt|urban|remaining|public-rental|optional)/announcements$"), 300, 5000),
            new Rule("push", Set.of("POST", "PUT"), List.of("/api/v1/push/"), Set.of(), 30, 200),
            new Rule("poll", Set.of("POST"), List.of("/api/v1/polls/"), Set.of(), 20, 100));
    }

    private static final java.util.regex.Pattern IP_LITERAL = java.util.regex.Pattern.compile("[0-9A-Fa-f:.]{2,45}");
    private static final org.springframework.web.util.UrlPathHelper PATHS = new org.springframework.web.util.UrlPathHelper();
    private final List<Rule> rules;
    private final Duration shortWindow;
    private final Clock clock;
    private final Cache<String, Deque<Long>> hits = CacheBuilder.newBuilder()
            .maximumSize(200_000).expireAfterAccess(1, TimeUnit.DAYS).build();

    @org.springframework.beans.factory.annotation.Autowired
    public RequestLimitFilter(@Value("${toady.security.mail-requests-per-10min:5}") int mailShort,
                              @Value("${toady.security.mail-requests-per-day:20}") int mailDaily) {
        this(rules(mailShort, mailDaily), Duration.ofMinutes(10), Clock.systemUTC());
    }
    RequestLimitFilter(List<Rule> rules, Duration shortWindow, Clock clock) {
        this.rules = rules; this.shortWindow = shortWindow; this.clock = clock;
    }

    private Optional<Rule> rule(HttpServletRequest request) {
        // 원본 URI가 아니라 Spring이 컨트롤러를 찾을 때 쓰는 디코딩·정규화된 경로로 비교한다.
        // 예: /webApi/user/reg%55ser 는 컨트롤러에서 /webApi/user/regUser 로 매칭되므로 같은 규칙을 적용해야 한다.
        String method = request.getMethod(), path = PATHS.getLookupPathForRequest(request);
        return rules.stream().filter(rule -> rule.matches(method, path)).findFirst();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) { return rule(request).isEmpty(); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Rule rule = rule(request).orElseThrow();
        if (!allow(rule, limitKey(clientIp(request)))) {
            tooMany(response, "요청이 너무 많습니다. 10분 뒤에 다시 시도해 주세요.");
            return;
        }
        chain.doFilter(request, response);
    }

    static void tooMany(HttpServletResponse response, String message) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", "600");
        response.setContentType("application/json;charset=UTF-8");
        response.getOutputStream().write(("{\"resultCode\":\"ERR-429\",\"resultMessage\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8));
    }

    /** 규칙별 10분·하루 창 안의 요청 수를 확인하고, 허용할 때만 이번 요청을 기록한다. */
    boolean allow(Rule rule, String ip) {
        long now = clock.millis(), shortFrom = now - shortWindow.toMillis(), dayFrom = now - Duration.ofDays(1).toMillis();
        Deque<Long> times;
        try { times = hits.get(rule.name() + "|" + ip, ArrayDeque::new); } catch (java.util.concurrent.ExecutionException impossible) { return true; }
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= dayFrom) times.pollFirst();
            long recent = times.stream().filter(time -> time > shortFrom).count();
            if (recent >= rule.shortLimit() || times.size() >= rule.dailyLimit()) return false;
            times.addLast(now);
            return true;
        }
    }

    /**
     * 앞단 nginx가 넣은 X-Real-IP는 요청이 사설·루프백 주소(프록시)에서 왔을 때만 믿는다.
     * 외부에서 직접 들어온 요청이 헤더를 꾸며 제한을 피하지 못하게 하기 위해서다.
     */
    public static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Real-IP");
        // 프록시가 넣은 값이라도 IP 형식일 때만 쓴다(호스트 이름이면 DNS 조회가 일어나고, 임의 문자열로 키를 바꿀 수 있다).
        if (forwarded != null && IP_LITERAL.matcher(forwarded.trim()).matches() && fromProxy(remote)) return forwarded.trim();
        return remote;
    }
    /**
     * 제한에 쓰는 주소 키. IPv6는 한 가입자가 /64 대역 안의 주소를 마음대로 바꿀 수 있어 /64 접두사로 묶는다.
     */
    public static String limitKey(String ip) {
        try {
            InetAddress parsed = InetAddress.getByName(ip);
            if (parsed instanceof java.net.Inet6Address) {
                byte[] bytes = parsed.getAddress();
                StringBuilder prefix = new StringBuilder("v6:");
                for (int i = 0; i < 8; i += 2) prefix.append(String.format("%02x%02x", bytes[i], bytes[i + 1])).append(i < 6 ? ":" : "");
                return prefix + "::/64";
            }
        } catch (Exception invalid) { /* 주소 형식이 아니면 그대로 쓴다 */ }
        return ip;
    }
    private static boolean fromProxy(String address) {
        try {
            InetAddress parsed = InetAddress.getByName(address);
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress();
        } catch (Exception invalid) { return false; }
    }
}
