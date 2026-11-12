package com.toady.web.app.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/** 익명 페이지 조회만 수집한다. 쿼리 문자열·IP·회원 정보·요청 헤더는 저장하지 않는다. */
@RestController
@RequestMapping("/api/v1/analytics/page-views")
public class PageViewController {
    private static final Logger log = LoggerFactory.getLogger(PageViewController.class);
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public PageViewController(JdbcTemplate jdbc, @Value("${toady.analytics.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public record PageView(@NotNull UUID eventId, @NotNull UUID visitorId, @NotNull UUID sessionId,
            @NotNull @Pattern(regexp = "^(/|/my|/announcements|/login|/certify|/showSubscriptDetail)$") String path) { }

    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody PageView event) {
        if (!enabled) return ResponseEntity.noContent().build();
        // 시각과 집계 날짜는 클라이언트 시계가 아닌 서버의 한국시간을 사용한다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        try {
            jdbc.update("INSERT INTO TB_PAGE_VIEW (EVENT_ID,VISITOR_ID,SESSION_ID,PAGE_PATH,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",
                    event.eventId().toString(), event.visitorId().toString(), event.sessionId().toString(),
                    event.path(), java.sql.Date.valueOf(now.toLocalDate()), Timestamp.valueOf(now));
        } catch (DuplicateKeyException duplicate) {
            // 응답 유실 후 같은 이벤트를 다시 보내도 한 번만 집계한다.
        } catch (DataAccessException failure) {
            // 장애 중 요청마다 스택을 남겨 로그를 폭증시키지 않는다. 상세 원인은 DEBUG에서 확인한다.
            log.debug("페이지 조회 저장 실패", failure);
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.noContent().build();
    }
}
