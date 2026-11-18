package com.toady.web.app.operation;

import com.toady.web.app.analytics.BotFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 관리자 화면에서 정한 사이트 공지(TB_SITE_NOTICE)와 검색어 통계 수집(TB_SEARCH_STAT, database.sql 27번).
 * 검색어는 방문자·회원과 연결하지 않고 날짜별 건수만 더한다.
 * 공지는 관리자 사이트가 쓰고, 이 컨트롤러는 화면에 내보내기만 한다. 서비스 계층 없이 JdbcTemplate을 바로 쓴다.
 */
// @RestController: JSON 응답 컨트롤러
@RestController
public class SiteContentController {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 링크는 사이트 안 경로 또는 https 주소만 내보낸다(관리자 입력이라도 javascript: 등은 화면에 넘기지 않는다). */
    static final Pattern SAFE_LINK = Pattern.compile("^(/[A-Za-z0-9/_?=&%.\\-]*|https://[A-Za-z0-9.\\-]+(/[A-Za-z0-9/_?=&%.\\-#]*)?)$");
    private final JdbcTemplate jdbc;
    private volatile List<Map<String, Object>> notices = List.of();
    private volatile long loadedAt;

    public SiteContentController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 지금 보여 줄 공지. 1분 동안 같은 결과를 쓴다. */
    // @GetMapping: GET 요청 처리. 응답에 Cache-Control: public, max-age=60을 붙여 브라우저도 1분 동안 다시 묻지 않게 한다
    @GetMapping("/api/v1/public/notices")
    public ResponseEntity<List<Map<String, Object>>> notices() {
        long now = System.currentTimeMillis();
        if (now - loadedAt > 60_000) {
            synchronized (this) {
                if (now - loadedAt > 60_000) { notices = load(); loadedAt = now; }
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic()).body(notices);
    }

    // 사용 중이고 기간 안인 공지 최신 3개를 읽는다. 기간 비교는 한국 시간 기준
    List<Map<String, Object>> load() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(KST));
        try {
            return jdbc.query("SELECT NOTICE_SEQ, MESSAGE, LINK_URL, LINK_LABEL, PLACEMENT, LEVEL, END_DT FROM TB_SITE_NOTICE WHERE USE_YN='Y' AND START_DT<=? AND END_DT>? ORDER BY NOTICE_SEQ DESC LIMIT 3", (r, i) -> {
                Map<String, Object> notice = new LinkedHashMap<>();
                notice.put("seq", r.getLong(1));
                notice.put("message", r.getString(2));
                String link = r.getString(3);
                if (link != null && SAFE_LINK.matcher(link.trim()).matches()) {
                    notice.put("link", link.trim());
                    notice.put("linkLabel", r.getString(4) == null || r.getString(4).isBlank() ? "자세히 보기" : r.getString(4));
                }
                notice.put("placement", "HOME".equals(r.getString(5)) ? "HOME" : "ALL");
                notice.put("level", "WARN".equals(r.getString(6)) ? "WARN" : "INFO");
                return notice;
            }, now, now);
        } catch (DataAccessException missing) { return List.of(); }
    }

    /** 요청 본문: 검색어와 결과 수. @NotBlank·@Size·@Min·@Max로 값의 범위를 검사한다. */
    public record SearchStat(@NotBlank @Size(max = 60) String term, @Min(0) @Max(100000) int results) { }

    /** 검색 한 번을 센다. 공백을 줄이고 소문자로 바꿔 30자까지만 저장한다. 봇 요청은 세지 않는다. */
    @PostMapping("/api/v1/analytics/search")
    public ResponseEntity<Void> search(@Valid @RequestBody SearchStat stat, HttpServletRequest request) {
        if (BotFilter.isBot(request)) return ResponseEntity.noContent().build();
        String term = stat.term().strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        if (term.length() < 2) return ResponseEntity.noContent().build();
        if (term.length() > 30) term = term.substring(0, 30);
        // 숫자만 6자리 이상(전화번호·계좌 등 개인정보일 수 있는 값)은 세지 않는다.
        if (term.replaceAll("[\\s-]", "").matches("\\d{6,}")) return ResponseEntity.noContent().build();
        Date today = Date.valueOf(LocalDateTime.now(KST).toLocalDate());
        String empty = stat.results() == 0 ? "Y" : "N";
        try {
            // UPDATE 먼저, 행이 없으면 INSERT, 그 사이 다른 요청이 먼저 넣었으면 다시 UPDATE(간단한 upsert)
            if (jdbc.update("UPDATE TB_SEARCH_STAT SET HITS=HITS+1 WHERE STAT_DATE=? AND TERM=? AND EMPTY_YN=?", today, term, empty) == 0) {
                try { jdbc.update("INSERT INTO TB_SEARCH_STAT (STAT_DATE, TERM, EMPTY_YN, HITS) VALUES (?,?,?,1)", today, term, empty); }
                catch (DuplicateKeyException raced) { jdbc.update("UPDATE TB_SEARCH_STAT SET HITS=HITS+1 WHERE STAT_DATE=? AND TERM=? AND EMPTY_YN=?", today, term, empty); }
            }
        } catch (DataAccessException missing) { /* 27번 미적용: 세지 않는다 */ }
        return ResponseEntity.noContent().build();
    }
}
