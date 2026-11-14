package com.toady.web.app.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;

/** 집계용 행동 이벤트. 회원 ID·메모·검색어·쿼리 문자열은 수집하지 않는다. */
@RestController
public class UsageEventController {
    private final JdbcTemplate jdbc; private final boolean enabled;
    public UsageEventController(JdbcTemplate jdbc,@Value("${toady.analytics.enabled:true}") boolean enabled) { this.jdbc=jdbc;this.enabled=enabled; }
    public record Event(@NotNull UUID eventId,@NotNull UUID visitorId,
        @NotNull @Pattern(regexp="detail_view|favorite_add|alert_enable|official_click|research_click|compare|share|score_save|poll_vote|push_enable|match_save") String action,
        @NotNull @Pattern(regexp="(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}|tool/[a-z0-9-]{1,40}") String subject) { }
    @PostMapping("/api/v1/analytics/events") public ResponseEntity<Void> record(@Valid @RequestBody Event event, HttpServletRequest request) {
        if(!enabled || BotFilter.isBot(request)) return ResponseEntity.noContent().build();
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        try { jdbc.update("INSERT INTO TB_USAGE_EVENT (EVENT_ID,VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",event.eventId().toString(),event.visitorId().toString(),event.action(),event.subject(),Date.valueOf(now.toLocalDate()),Timestamp.valueOf(now)); }
        catch(DuplicateKeyException ignored) { }
        catch(DataAccessException error) { return ResponseEntity.status(503).build(); }
        return ResponseEntity.noContent().build();
    }
}
