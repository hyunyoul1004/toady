package com.toady.web.app.analytics;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/**
 * 로그인한 회원이 서비스를 쓴 날짜만 하루 한 줄 남긴다(database.sql 29번, TB_MEMBER_VISIT_DAY).
 * 익명 방문자 번호는 브라우저 저장소에 있어 아이폰 사파리(7일 미방문 시 삭제)·인앱 브라우저에서 같은 사람이 새 방문자로 잡힌다.
 * 회원 기준 재방문은 이 왜곡이 없어 관리자 지표의 30일 재방문을 회원·비회원으로 나눠 보는 데 쓴다.
 * 무엇을 봤는지는 남기지 않고 날짜만 남긴다. 탈퇴하면 지우고 400일이 지나면 스케줄러가 지운다.
 */
@Slf4j
@Component
public class MemberVisits extends OncePerRequestFilter {
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;
    /** 이미 기록한 (회원, 날짜). 같은 날 두 번째 요청부터는 DB에 가지 않는다. */
    private final Cache<String, Boolean> recorded = CacheBuilder.newBuilder().maximumSize(100_000).expireAfterWrite(2, TimeUnit.DAYS).build();
    private volatile boolean missingLogged;
    /** 기록이 실패하면(29번 미적용, DB 일시 장애) 이 시각까지 시도를 쉰다. 회원 요청마다 실패하는 INSERT를 보내지 않게. */
    private volatile long pausedUntil;

    @org.springframework.beans.factory.annotation.Autowired
    public MemberVisits(JdbcTemplate jdbc) { this(jdbc, Clock.system(KST)); }
    MemberVisits(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/") || path.startsWith("/webApi/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails user && user.getUserSeq() != null) {
            record(user.getUserSeq());
        }
        chain.doFilter(request, response);
    }

    /** 오늘 처음 온 회원이면 한 줄 넣는다. 테이블이 아직 없거나 DB가 잠시 실패해도 요청은 그대로 처리한다. */
    void record(long userSeq) {
        LocalDate today = LocalDate.now(clock);
        String key = userSeq + "|" + today;
        if (recorded.getIfPresent(key) != null || clock.millis() < pausedUntil) return;
        try {
            jdbc.update("INSERT INTO TB_MEMBER_VISIT_DAY (USER_SEQ, VISIT_DATE) VALUES (?, ?)", userSeq, Date.valueOf(today));
            recorded.put(key, Boolean.TRUE);
        } catch (DuplicateKeyException already) {
            recorded.put(key, Boolean.TRUE);
        } catch (DataAccessException unavailable) {
            pausedUntil = clock.millis() + TimeUnit.MINUTES.toMillis(10);
            if (!missingLogged) {
                missingLogged = true;
                log.warn("회원 방문 일자를 기록하지 못했습니다(database.sql 29번 적용 여부 확인): {}", unavailable.getClass().getSimpleName());
            }
        }
    }
}
