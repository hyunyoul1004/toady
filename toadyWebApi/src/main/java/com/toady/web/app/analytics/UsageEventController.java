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

/**
 * 집계용 행동 이벤트. 회원 ID·메모·검색어·쿼리 문자열은 수집하지 않는다(검색어는 방문자와 연결하지 않은 일별 건수만 SiteContentController가 센다).
 * 상세 보기·관심 추가·공유 같은 행동을 TB_USAGE_EVENT에 한 줄씩 남긴다. 관리자 사이트가 이 테이블로 지표를 만든다.
 */
// @RestController: JSON 응답 컨트롤러
@RestController
public class UsageEventController {
    private final JdbcTemplate jdbc; private final boolean enabled;
    public UsageEventController(JdbcTemplate jdbc,@Value("${toady.analytics.enabled:true}") boolean enabled) { this.jdbc=jdbc;this.enabled=enabled; }
    /** 요청 본문. action은 정해진 행동 이름, subject는 공고 주소(유형/주택관리번호/공고번호) 또는 tool/기능이름. */
    public record Event(@NotNull UUID eventId,@NotNull UUID visitorId,
        @NotNull @Pattern(regexp="detail_view|favorite_add|alert_enable|official_click|research_click|compare|share|score_save|poll_vote|push_enable|match_save|map_tool|push_open|push_denied|push_disable|app_install|signup_prompt_view|signup_prompt_click|guest_import") String action,
        @NotNull @Pattern(regexp="(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}|tool/[a-z0-9-]{1,40}") String subject) { }
    /** 공고 행동은 공고 주소만, 지도 기능은 정해진 기능 이름만 받는다(임의 값으로 관리자 집계가 채워지지 않게). */
    static final java.util.Set<String> ANNOUNCEMENT_ACTIONS=java.util.Set.of("detail_view","favorite_add","alert_enable","official_click","research_click","compare","poll_vote");
    static final java.util.Set<String> MAP_TOOLS=java.util.Set.of("tool/map-fullscreen","tool/map-measure","tool/map-radius","tool/map-search","tool/map-suggest");
    /** 알림 열기(어떤 알림인지)·권한 거절·알림 끄기는 정해진 이름만 받는다. 열기는 웹 서비스 워커가 붙인 src(push.digest 등)에서 온다. */
    static final java.util.Set<String> PUSH_OPEN=java.util.Set.of("tool/push-digest","tool/push-result","tool/push-message","tool/push-notice","tool/push-other");
    /** 홈 화면 추가: 설치 창 수락·닫기, 설치 완료, 방법 안내 열기. */
    static final java.util.Set<String> INSTALL=java.util.Set.of("tool/install-accepted","tool/install-dismissed","tool/install-done","tool/install-guide");
    /** 가입 권유를 보인 순간(관심 공고 3번째 저장·발표 임박·캘린더 연동·이야기 쓰기). 웹 signupPrompt.js의 SIGNUP_MOMENTS와 같다. */
    static final java.util.Set<String> SIGNUP_MOMENTS=java.util.Set.of("tool/signup-favorite","tool/signup-result","tool/signup-calendar","tool/signup-story");
    /** 가입·로그인 뒤 기기 관심 공고를 계정으로 옮긴 개수 구간. 회원 번호는 싣지 않는다. */
    static final java.util.Set<String> GUEST_IMPORT=java.util.Set.of("tool/import-1-2","tool/import-3-5","tool/import-6");
    /** 행동과 대상의 조합이 맞는지 본다. 정규식만으로는 막지 못하는 '이 행동엔 이 대상만' 규칙이다. */
    static boolean validPair(String action,String subject) {
        if("signup_prompt_view".equals(action)||"signup_prompt_click".equals(action)) return SIGNUP_MOMENTS.contains(subject);
        if("guest_import".equals(action)) return GUEST_IMPORT.contains(subject);
        if("app_install".equals(action)) return INSTALL.contains(subject);
        if("map_tool".equals(action)) return MAP_TOOLS.contains(subject);
        if("push_open".equals(action)) return PUSH_OPEN.contains(subject);
        if("push_denied".equals(action)||"push_disable".equals(action)) return "tool/push".equals(subject);
        if(ANNOUNCEMENT_ACTIONS.contains(action)) return !subject.startsWith("tool/");
        return true;
    }
    /** 이벤트 한 건 저장. 같은 eventId가 다시 오면(재전송) 무시한다. 시각은 서버의 한국 시간. */
    @PostMapping("/api/v1/analytics/events") public ResponseEntity<Void> record(@Valid @RequestBody Event event, HttpServletRequest request) {
        if(!enabled || BotFilter.isBot(request)) return ResponseEntity.noContent().build();
        if(!validPair(event.action(),event.subject())) return ResponseEntity.badRequest().build();
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        try { jdbc.update("INSERT INTO TB_USAGE_EVENT (EVENT_ID,VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE,REG_DT) VALUES (?,?,?,?,?,?)",event.eventId().toString(),event.visitorId().toString(),event.action(),event.subject(),Date.valueOf(now.toLocalDate()),Timestamp.valueOf(now)); }
        catch(DuplicateKeyException ignored) { }
        catch(DataAccessException error) { return ResponseEntity.status(503).build(); }
        return ResponseEntity.noContent().build();
    }
}
