package com.toady.web.app.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.planner.LivingAreas;
import com.toady.web.app.planner.PlannerService;
import com.toady.web.app.planner.PlannerSummary;
import com.toady.web.app.planner.PublicAreaPage;
import com.toady.web.app.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 하루 한 번, 구독마다 '관심 공고의 내일 일정'과 '구독 생활권의 새 공고'를 하나의 알림으로 묶어 보낸다.
 * 메일과 달리 발송 한도가 없지만, 알림 피로를 줄이려고 하루 1회·내용이 있을 때만 보낸다.
 * 푸시 서비스가 404·410으로 응답하면(브라우저에서 해지·만료) 구독을 바로 삭제한다.
 * 실제 발송은 PUSH_DIGEST_ENABLED=true일 때만 시작한다.
 */
@Service
@ConditionalOnProperty(name = "toady.push.digest-enabled", havingValue = "true")
public class PushDigest {
    @Configuration @EnableScheduling static class Scheduling { }
    private static final Logger log = LoggerFactory.getLogger(PushDigest.class);
    /** 생활권 새 공고 기록을 처음 만들 때 기존 공고를 '새 공고'로 보내지 않기 위한 기준 시각. */
    static final LocalDateTime BASELINE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PushSubscriptions store; private final WebPushSender sender; private final JdbcTemplate jdbc;
    private final PlannerService planner; private final PlannerSummary summaries; private final PublicAreaPage areas;
    private final UserRepository users; private final ObjectMapper mapper; private final Clock clock;
    private final com.toady.web.app.planner.AnnouncementResults results;

    @Autowired
    public PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper, com.toady.web.app.planner.AnnouncementResults results) {
        this(store, sender, jdbc, planner, summaries, areas, users, mapper, results, Clock.system(PushSubscriptions.KST));
    }
    PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper, com.toady.web.app.planner.AnnouncementResults results, Clock clock) {
        this.store = store; this.sender = sender; this.jdbc = jdbc; this.planner = planner; this.summaries = summaries;
        this.areas = areas; this.users = users; this.mapper = mapper; this.results = results; this.clock = clock;
    }

    static final int STALE_DAYS = 180;
    public record Report(int sent, int removed, int retry, int empty) { }
    record Seen(AnnouncementListItem item, LocalDateTime firstSeen) {
        String id() { return item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo(); }
    }

    /** 내일 일정을 전날 저녁에 알린다. 서버가 18시에 멈춰 있어도 20시 30분까지 재기동 후 보낼 수 있게 30분마다 확인한다. */
    @Scheduled(cron = "0 0,30 18-20 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        if (!sender.enabled() || !store.ready()) return;
        Report report = run();
        if (report.sent() + report.removed() + report.retry() > 0)
            log.info("웹 푸시 묶음 알림: sent={}, removed={}, retry={}, empty={}", report.sent(), report.removed(), report.retry(), report.empty());
    }

    public Report run() {
        LocalDateTime now = PushSubscriptions.now(clock);
        LocalDate today = now.toLocalDate(), tomorrow = today.plusDays(1);
        // 오래 쓰지 않은 구독 정리: 6개월 넘게 전달 성공도 없고 브라우저의 재등록(앱 방문 시 범위 저장)도 없는 행.
        // 보낼 내용이 없던 구독은 푸시 서비스의 404·410 응답을 받을 일이 없어 이렇게 따로 정리한다.
        store.removeStale(now.minusDays(STALE_DAYS));
        List<PushSubscriptions.Row> pending = store.pending(today);
        if (pending.isEmpty()) return new Report(0, 0, 0, 0);
        Map<String, List<Seen>> news = new HashMap<>();
        Map<Long, List<PlannerSummary.Summary>> members = new HashMap<>();
        Map<String, LocalDateTime> resultSeen = new HashMap<>();
        Map<String, com.toady.web.app.planner.AnnouncementResults.Result> resultCache = new HashMap<>();
        Set<String> startedResultKinds = new HashSet<>();
        PublicAreaPage.WeekCounts[] week = new PublicAreaPage.WeekCounts[1];
        Map<String, Optional<PlannerSummary.Summary>> guestSummaries = new HashMap<>();
        List<List<AnnouncementListItem>> unranked = new ArrayList<>();
        int sent = 0, removed = 0, retry = 0, empty = 0;
        for (PushSubscriptions.Row row : pending) {
            try {
                List<PushMessage.Line> lines = new ArrayList<>();
                LocalDateTime since = row.lastDigestAt() != null ? row.lastDigestAt() : row.registeredAt();
                if (row.favorites()) {
                    List<PlannerSummary.Summary> favorites = new ArrayList<>();
                    if (row.userSeq() != null) favorites.addAll(members.computeIfAbsent(row.userSeq(), this::memberSummaries));
                    else for (String id : row.favoriteIds()) guestSummary(id, guestSummaries).ifPresent(favorites::add);
                    for (PlannerSummary.Summary summary : favorites) lines.addAll(favoriteLines(summary, tomorrow));
                    lines.addAll(resultLines(favorites, today, since, now, resultCache, resultSeen, startedResultKinds));
                }
                Set<String> already = new HashSet<>();
                lines.forEach(line -> already.add(line.announcementId()));
                for (String slug : row.areas()) {
                    // 주간 브리핑: 일요일 저녁 묶음에만 다음 주(월~일) 접수 시작 공고 수를 넣는다.
                    if (PushTopics.WEEKLY.equals(slug)) {
                        if (today.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) continue;
                        if (week[0] == null) week[0] = areas.startingCounts(today.plusDays(1), today.plusDays(7));
                        if (week[0].total() > 0) lines.add(new PushMessage.Line("WEEKLY", null, weeklyText(week[0]), "이번 주 청약", slug, "이번 주 청약"));
                        continue;
                    }
                    var area = LivingAreas.bySlug(slug);
                    var topic = PushTopics.bySlug(slug);
                    if (area.isEmpty() && topic.isEmpty()) continue;
                    String label = area.map(LivingAreas.Area::label).orElseGet(() -> topic.get().label());
                    List<Seen> seenList = news.computeIfAbsent(slug, key -> area.isPresent() ? refreshArea(area.get(), today, now)
                        : refresh(slug, unranked(today, unranked).stream().filter(topic.get()::includes).toList(), now));
                    for (Seen seen : seenList)
                        if (seen.firstSeen().isAfter(since) && already.add(seen.id()))
                            lines.add(new PushMessage.Line("AREA", seen.id(), seen.item().houseNm(), "새 공고", slug, label));
                }
                var message = PushMessage.digest(lines);
                // 보낼 내용이 없어도 오늘 확인을 마친 것으로 기록해, 저녁 30분마다 같은 구독을 다시 계산하지 않는다.
                if (message.isEmpty()) { store.claim(row.hash(), today); empty++; continue; }
                if (!store.claim(row.hash(), today)) continue;
                switch (sender.send(row.target(), message.get().json(mapper), "digest")) {
                    case ACCEPTED -> { store.delivered(row.hash(), now, true); sent++; }
                    case GONE -> { store.deleteHash(row.hash()); removed++; }
                    // 일시 장애는 다음 30분 확인 때 다시 보낸다. 같은 tag·Topic이라 중복 표시되지 않는다.
                    case RETRY -> { store.release(row.hash(), row.lastAttemptDate()); retry++; }
                    case REJECTED -> { }
                }
            } catch (Exception failure) {
                log.warn("웹 푸시 묶음 알림 처리 실패: error={}", failure.getClass().getSimpleName());
            }
        }
        return new Report(sent, removed, retry, empty);
    }

    private List<PlannerSummary.Summary> memberSummaries(long user) {
        var account = users.findById(user).orElse(null);
        if (account == null || !"N".equals(account.getWithdrawalYn()) || !"N".equals(account.getLockYn())) return List.of();
        return planner.list(user).stream().map(PlannerService.Favorite::summary).filter(summary -> !summary.unavailable()).toList();
    }
    static String weeklyText(PublicAreaPage.WeekCounts counts) {
        List<String> parts = new ArrayList<>();
        if (counts.apt() > 0) parts.add("아파트 " + counts.apt() + "건");
        if (counts.unranked() > 0) parts.add("무순위·줍줍 " + counts.unranked() + "건");
        if (counts.others() > 0) parts.add("기타 " + counts.others() + "건");
        return "접수 시작 " + counts.total() + "건 · " + String.join(" · ", parts);
    }

    /** 결과 알림은 접수 마감 뒤 이 기간 안의 공고만 보낸다(오래된 공고를 새로 저장했을 때 지난 결과를 새 소식처럼 보내지 않는다). */
    static final int RESULT_FRESH_DAYS = 30;
    static final String RATE_SLUG = "result-rate", SCORE_SLUG = "result-score";
    /** 관심 공고의 1순위 경쟁률·당첨가점이 마지막 알림 뒤 처음 공개됐으면 한 줄씩 만든다. 공개 시점은 TB_PUSH_AREA_SEEN에 결과 종류별로 기록한다. */
    List<PushMessage.Line> resultLines(List<PlannerSummary.Summary> favorites, LocalDate today, LocalDateTime since, LocalDateTime now,
            Map<String, com.toady.web.app.planner.AnnouncementResults.Result> cache, Map<String, LocalDateTime> seen, Set<String> started) {
        List<PlannerSummary.Summary> fresh = favorites.stream().filter(summary -> summary.id().startsWith("apt/") && summary.endDate() != null
            && !summary.endDate().isBefore(today.minusDays(RESULT_FRESH_DAYS)) && !summary.endDate().isAfter(today)).toList();
        if (fresh.isEmpty()) return List.of();
        // 두 결과 종류의 기록을 함께 시작한다. 시작한 실행에서 보이는 결과는 기준 시각으로 두고, 그 뒤 처음 보이는 결과만 새 소식이다.
        for (String slug : List.of(RATE_SLUG, SCORE_SLUG)) startResults(slug, started);
        List<String> missing = fresh.stream().map(PlannerSummary.Summary::id).filter(id -> !cache.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<String, com.toady.web.app.planner.AnnouncementResults.Result> found = results.forIds(missing);
            for (String id : missing) cache.put(id, found.get(id));
        }
        List<PushMessage.Line> lines = new ArrayList<>();
        for (PlannerSummary.Summary summary : fresh) {
            var result = cache.get(summary.id());
            if (result == null) continue;
            if (result.ratePublished() && resultSeen(RATE_SLUG, summary.id(), now, seen, started).isAfter(since))
                lines.add(new PushMessage.Line("RESULT", summary.id(), summary.houseNm(), "경쟁률 발표", null, null));
            if (result.scorePublished() && resultSeen(SCORE_SLUG, summary.id(), now, seen, started).isAfter(since))
                lines.add(new PushMessage.Line("RESULT", summary.id(), summary.houseNm(), "당첨가점 공개", null, null));
        }
        return lines;
    }
    /** 결과 종류의 기록 시작 표시('*')를 확인하고, 이번 실행에서 처음 시작했으면 slug+"*"로 기억한다. */
    private void startResults(String slug, Set<String> started) {
        if (started.contains(slug) || started.contains(slug + "*")) return;
        boolean before = jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=? AND ANNOUNCEMENT_ID='*'", Integer.class, slug) > 0;
        if (!before) {
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,'*',?)", slug, Timestamp.valueOf(BASELINE)); }
            catch (org.springframework.dao.DuplicateKeyException raced) { /* 다른 실행이 먼저 시작 */ }
        }
        started.add(before ? slug : slug + "*");
    }
    /** 결과 종류(slug)별 최초 확인 시각. 그 종류를 처음 기록하는 실행에서는 이미 공개된 결과를 새 소식으로 보지 않는다(기준 시각). */
    private LocalDateTime resultSeen(String slug, String id, LocalDateTime now, Map<String, LocalDateTime> seen, Set<String> started) {
        return seen.computeIfAbsent(slug + "|" + id, key -> {
            List<Timestamp> found = jdbc.queryForList("SELECT FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=? AND ANNOUNCEMENT_ID=?", Timestamp.class, slug, id);
            if (!found.isEmpty()) { markSeen(slug, List.of(id), now); return found.get(0).toLocalDateTime(); }
            // slug+"*": 이번 실행에서 기록을 시작했다 → 지금 공개돼 있는 결과는 모두 기준 시각으로 둔다.
            LocalDateTime at = started.contains(slug + "*") ? BASELINE : now;
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,?,?)", slug, id, Timestamp.valueOf(at)); }
            catch (org.springframework.dao.DuplicateKeyException raced) { at = BASELINE; }
            markSeen(slug, List.of(id), now);
            return at;
        });
    }
    private Optional<PlannerSummary.Summary> guestSummary(String id, Map<String, Optional<PlannerSummary.Summary>> cache) {
        return cache.computeIfAbsent(id, key -> {
            String[] parts = key.split("/");
            if (parts.length != 3) return Optional.empty();
            try { return Optional.of(summaries.get(parts[0], parts[1], parts[2])); }
            catch (EntityNotFoundException | IllegalArgumentException missing) { return Optional.empty(); }
        });
    }
    static List<PushMessage.Line> favoriteLines(PlannerSummary.Summary summary, LocalDate tomorrow) {
        return summary.events().stream().filter(event -> event.date().equals(tomorrow))
            .map(event -> new PushMessage.Line("FAVORITE", summary.id(), summary.houseNm(), event.label(), null, null)).toList();
    }

    /** 생활권의 접수 중·예정 공고를 기록하고, 공고별 최초 확인 시각을 돌려준다. 첫 기록은 기존 공고를 새 공고로 보지 않는다. */
    List<Seen> refreshArea(LivingAreas.Area area, LocalDate today, LocalDateTime now) {
        return refresh(area.slug(), areas.active(area, today), now);
    }
    /** 이번 실행에서 무순위·임의공급 목록은 한 번만 읽어 줍줍 주제들이 함께 쓴다. */
    private List<AnnouncementListItem> unranked(LocalDate today, List<List<AnnouncementListItem>> memo) {
        if (memo.isEmpty()) memo.add(areas.activeUnranked(today));
        return memo.get(0);
    }

    /** 생활권·알림 주제(slug)의 접수 중·예정 공고를 기록하고, 공고별 최초 확인 시각을 돌려준다. 첫 기록은 기존 공고를 새 공고로 보지 않는다. */
    List<Seen> refresh(String slug, List<AnnouncementListItem> active, LocalDateTime now) {
        Map<String, LocalDateTime> known = new HashMap<>();
        jdbc.query("SELECT ANNOUNCEMENT_ID,FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=?", (org.springframework.jdbc.core.RowCallbackHandler) r ->
            known.put(r.getString(1), r.getTimestamp(2).toLocalDateTime()), slug);
        LocalDateTime firstSeen = known.isEmpty() ? BASELINE : now;
        // 접수 중 공고가 없는 생활권도 기록 시작 시점을 남겨, 나중에 처음 올라온 공고를 새 공고로 판정한다.
        if (known.isEmpty()) {
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,'*',?)", slug, Timestamp.valueOf(BASELINE)); }
            catch (org.springframework.dao.DuplicateKeyException raced) { firstSeen = now; }
        }
        List<Seen> result = new ArrayList<>();
        for (AnnouncementListItem item : active) {
            String id = item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo();
            LocalDateTime at = known.get(id);
            if (at == null) {
                try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,?,?)", slug, id, Timestamp.valueOf(firstSeen)); at = firstSeen; }
                catch (org.springframework.dao.DuplicateKeyException raced) { at = BASELINE; }
                known.put(id, at);
            }
            result.add(new Seen(item, at));
        }
        markSeen(slug, result.stream().map(Seen::id).toList(), now);
        return result;
    }

    /** 0이면 아직 확인 전, 1이면 LAST_SEEN_DT 컬럼 있음, -1이면 없음(database.sql 15번 미적용). */
    private volatile int lastSeenColumn = 0;
    /**
     * 지금도 접수 중·예정인 공고의 마지막 확인 시각을 남긴다. 보관 정리는 이 값 기준으로 지워,
     * 1년 넘게 열려 있는 공고의 최초 확인 기록이 지워졌다가 '새 공고'로 다시 알림되는 일을 막는다.
     */
    void markSeen(String slug, List<String> ids, LocalDateTime now) {
        if (ids.isEmpty() || lastSeenColumn < 0) return;
        try {
            for (String id : ids) jdbc.update("UPDATE TB_PUSH_AREA_SEEN SET LAST_SEEN_DT=? WHERE AREA_SLUG=? AND ANNOUNCEMENT_ID=?", Timestamp.valueOf(now), slug, id);
            lastSeenColumn = 1;
        } catch (org.springframework.jdbc.BadSqlGrammarException missingColumn) {
            lastSeenColumn = -1;
            log.warn("TB_PUSH_AREA_SEEN.LAST_SEEN_DT 컬럼이 없어 마지막 확인 시각을 기록하지 않습니다. database.sql 15번을 적용하세요.");
        }
    }
}
