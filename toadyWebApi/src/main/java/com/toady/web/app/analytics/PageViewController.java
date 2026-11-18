package com.toady.web.app.analytics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * 익명 페이지 조회만 수집한다. 쿼리 문자열·IP·회원 정보·요청 헤더는 저장하지 않는다.
 * 화면이 페이지를 열 때 POST로 보내고, 서버는 TB_PAGE_VIEW에 한 줄 INSERT한다(관리자 사이트 방문 지표의 원본).
 * 서비스·리포지토리 없이 컨트롤러가 JdbcTemplate으로 바로 저장하는 단순한 구조다.
 */
// @RestController: JSON 응답 컨트롤러
@RestController
// @RequestMapping: 클래스 공통 주소
@RequestMapping("/api/v1/analytics/page-views")
public class PageViewController {
    private static final Logger log = LoggerFactory.getLogger(PageViewController.class);
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    // @Value: toady.analytics.enabled가 false면 받기만 하고 저장하지 않는다(기본 true)
    public PageViewController(JdbcTemplate jdbc, @Value("${toady.analytics.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    /**
     * 요청 본문. eventId는 중복 저장 방지용, visitorId·sessionId는 브라우저가 만든 무작위 번호다.
     * @NotNull: 값이 꼭 있어야 한다. @Pattern: 정해진 경로만 받아 임의 값이 지표에 섞이지 않게 한다.
     */
    public record PageView(@NotNull UUID eventId, @NotNull UUID visitorId, @NotNull UUID sessionId,
            @NotNull @Pattern(regexp = "^(/|/my|/score|/match|/match/zupzup|/faq|/this-week|/regions|/announcements|/login|/certify|/showSubscriptDetail)$") String path,
            // 세션 첫 조회의 외부 유입 도메인만 받는다. 전체 URL·검색어는 받지 않는다.
            @Pattern(regexp = "^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$") @Size(max = 100) String referrerHost) { }

    /** 운영 DB에 유입 컬럼을 추가하기 전에 배포돼도 조회 기록이 끊기지 않게 한다. 컬럼이 생기면 재시작 없이 다시 시도한다. */
    private volatile long legacyUntil = 0;

    /** 조회 한 번을 저장한다. 성공·중복·로봇 모두 204(본문 없음), DB 장애면 503. */
    // @PostMapping: POST /api/v1/analytics/page-views. @Valid @RequestBody: JSON 본문을 PageView로 바꾸고 검증한다
    @PostMapping
    public ResponseEntity<Void> record(@Valid @RequestBody PageView event, HttpServletRequest request) {
        if (!enabled || BotFilter.isBot(request)) return ResponseEntity.noContent().build();
        // 시각과 집계 날짜는 클라이언트 시계가 아닌 서버의 한국시간을 사용한다.
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        try {
            if (System.currentTimeMillis() >= legacyUntil) {
                try {
                    jdbc.update("INSERT INTO TB_PAGE_VIEW (EVENT_ID,VISITOR_ID,SESSION_ID,PAGE_PATH,VISIT_DATE,REG_DT,REFERRER_HOST) VALUES (?,?,?,?,?,?,?)",
                            event.eventId().toString(), event.visitorId().toString(), event.sessionId().toString(),
                            event.path(), Date.valueOf(now.toLocalDate()), Timestamp.valueOf(now), event.referrerHost());
                    return ResponseEntity.noContent().build();
                } catch (BadSqlGrammarException missingColumn) {
                    legacyUntil = System.currentTimeMillis() + 600_000;
                    log.warn("TB_PAGE_VIEW.REFERRER_HOST 컬럼이 없어 유입 경로 없이 저장합니다. database.sql의 컬럼 추가 SQL을 적용하세요.");
                }
            }
            jdbc.update("INSERT INTO TB_PAGE_VIEW (EVENT_ID,VISITOR_ID,SESSION_ID,PAGE_PATH,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",
                    event.eventId().toString(), event.visitorId().toString(), event.sessionId().toString(),
                    event.path(), Date.valueOf(now.toLocalDate()), Timestamp.valueOf(now));
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
