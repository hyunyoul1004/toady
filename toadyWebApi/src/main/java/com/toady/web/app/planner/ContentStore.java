package com.toady.web.app.planner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 관리자 화면에서 고치는 생활권(TB_LIVING_AREA)·자주 묻는 질문(TB_FAQ_ITEM, database.sql 27번)을 1분마다 읽어 서비스에 반영한다.
 * 테이블이 비어 있으면 처음 한 번 코드의 기본 값으로 채워, 관리자가 기존 내용을 그대로 이어서 고칠 수 있게 한다.
 * 테이블이 없거나 읽지 못하면 지금 쓰던 값을 그대로 둔다(기본은 코드의 값).
 * 생활권은 LivingAreas.replace(), 자주 묻는 질문은 PublicFaqPage.replace()로 바꿔 넣고, 바뀌면 공개 페이지 캐시도 비운다.
 */
// @Component: 스프링 빈으로 등록한다. 그래야 아래 @EventListener·@Scheduled가 동작한다.
@Component
public class ContentStore {
    private static final Logger log = LoggerFactory.getLogger(ContentStore.class);
    private final JdbcTemplate jdbc;
    public ContentStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // @EventListener(ApplicationReadyEvent): 서버가 다 뜬 직후 한 번 실행한다. 빈 테이블을 채우고 바로 읽어 온다.
    @EventListener(ApplicationReadyEvent.class)
    public void start() { seedIfEmpty(); refresh(); }

    // @Scheduled(fixedDelay=60초): 이전 실행이 끝나고 1분 뒤에 다시 실행한다. initialDelay로 첫 실행도 1분 뒤부터.
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void refresh() { refreshAreas(); refreshFaq(); }

    /** 테이블이 비어 있으면 코드의 기본 생활권·FAQ를 넣는다. 정렬 순서는 10 간격이라 관리자가 사이에 끼워 넣기 쉽다. */
    void seedIfEmpty() {
        Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneId.of("Asia/Seoul")));
        try {
            if (count("TB_LIVING_AREA") == 0) {
                int order = 0;
                for (var area : LivingAreas.BUILT_IN) {
                    jdbc.update("INSERT INTO TB_LIVING_AREA (SLUG, LABEL, ALIASES, TERMS, SCOPE, DESCRIPTION, SORT_ORDER, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,?,'Y','system',?)",
                            area.slug(), area.label(), String.join(",", area.aliases()), String.join(",", area.terms()), String.join(",", area.scope()), area.description(), order += 10, now);
                }
                log.info("생활권 기본값을 DB에 채웠습니다: {}개", LivingAreas.BUILT_IN.size());
            }
        } catch (DataAccessException missing) { log.debug("생활권 테이블 없음(database.sql 27번 미적용)"); }
        try {
            if (count("TB_FAQ_ITEM") == 0) {
                int order = 0;
                for (var group : PublicFaqPage.BUILT_IN) for (var faq : group.items()) {
                    jdbc.update("INSERT INTO TB_FAQ_ITEM (ITEM_ID, GROUP_ID, GROUP_TITLE, QUESTION, ANSWER, SORT_ORDER, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,'Y','system',?)",
                            faq.id(), group.id(), group.title(), faq.question(), faq.answer(), order += 10, now);
                }
                log.info("자주 묻는 질문 기본값을 DB에 채웠습니다");
            }
        } catch (DataAccessException missing) { log.debug("자주 묻는 질문 테이블 없음(database.sql 27번 미적용)"); }
    }

    void refreshAreas() {
        try {
            List<LivingAreas.Area> areas = jdbc.query("SELECT SLUG, LABEL, ALIASES, TERMS, SCOPE, DESCRIPTION FROM TB_LIVING_AREA WHERE USE_YN='Y' ORDER BY SORT_ORDER, SLUG", (r, i) -> {
                List<String> aliases = list(r.getString(3)), terms = list(r.getString(4));
                if (aliases.isEmpty() || terms.isEmpty()) return null;
                return new LivingAreas.Area(r.getString(1), r.getString(2), aliases, terms, r.getString(6), list(r.getString(5)));
            }).stream().filter(Objects::nonNull).toList();
            if (!areas.equals(LivingAreas.all())) {
                LivingAreas.replace(areas);
                PublicHtml.clearCache();
            }
        } catch (DataAccessException missing) { /* 테이블 없음: 코드 기본값 유지 */ }
    }

    void refreshFaq() {
        try {
            Map<String, String> titles = new LinkedHashMap<>();
            Map<String, List<PublicFaqPage.Faq>> items = new LinkedHashMap<>();
            jdbc.query("SELECT ITEM_ID, GROUP_ID, GROUP_TITLE, QUESTION, ANSWER FROM TB_FAQ_ITEM WHERE USE_YN='Y' ORDER BY SORT_ORDER, ITEM_ID", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                titles.putIfAbsent(r.getString(2), r.getString(3));
                items.computeIfAbsent(r.getString(2), key -> new ArrayList<>()).add(new PublicFaqPage.Faq(r.getString(1), r.getString(4), r.getString(5)));
            });
            List<PublicFaqPage.Group> groups = new ArrayList<>();
            titles.forEach((id, title) -> groups.add(new PublicFaqPage.Group(id, title, List.copyOf(items.get(id)))));
            PublicFaqPage.replace(groups);
        } catch (DataAccessException missing) { /* 테이블 없음: 코드 기본값 유지 */ }
    }

    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    /** 쉼표로 이은 DB 값('운정,운정역')을 목록으로. 앞뒤 공백·빈 값·중복은 뺀다. */
    static List<String> list(String csv) {
        return csv == null ? List.of() : Arrays.stream(csv.split(",")).map(String::strip).filter(value -> !value.isEmpty()).distinct().toList();
    }
}
