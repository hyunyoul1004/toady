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

    @Autowired
    public PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper) {
        this(store, sender, jdbc, planner, summaries, areas, users, mapper, Clock.system(PushSubscriptions.KST));
    }
    PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper, Clock clock) {
        this.store = store; this.sender = sender; this.jdbc = jdbc; this.planner = planner; this.summaries = summaries;
        this.areas = areas; this.users = users; this.mapper = mapper; this.clock = clock;
    }

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
        List<PushSubscriptions.Row> pending = store.pending(today);
        if (pending.isEmpty()) return new Report(0, 0, 0, 0);
        Map<String, List<Seen>> news = new HashMap<>();
        Map<Long, List<PushMessage.Line>> members = new HashMap<>();
        Map<String, Optional<PlannerSummary.Summary>> guestSummaries = new HashMap<>();
        int sent = 0, removed = 0, retry = 0, empty = 0;
        for (PushSubscriptions.Row row : pending) {
            try {
                List<PushMessage.Line> lines = new ArrayList<>();
                if (row.favorites()) {
                    if (row.userSeq() != null) lines.addAll(members.computeIfAbsent(row.userSeq(), user -> memberLines(user, tomorrow)));
                    else for (String id : row.favoriteIds()) guestSummary(id, guestSummaries).ifPresent(summary -> lines.addAll(favoriteLines(summary, tomorrow)));
                }
                LocalDateTime since = row.lastDigestAt() != null ? row.lastDigestAt() : row.registeredAt();
                Set<String> already = new HashSet<>();
                lines.forEach(line -> already.add(line.announcementId()));
                for (String slug : row.areas()) {
                    var area = LivingAreas.bySlug(slug);
                    if (area.isEmpty()) continue;
                    for (Seen seen : news.computeIfAbsent(slug, key -> refreshArea(area.get(), today, now)))
                        if (seen.firstSeen().isAfter(since) && already.add(seen.id()))
                            lines.add(new PushMessage.Line("AREA", seen.id(), seen.item().houseNm(), "새 공고", slug, area.get().label()));
                }
                var message = PushMessage.digest(lines);
                if (message.isEmpty()) { empty++; continue; }
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

    private List<PushMessage.Line> memberLines(long user, LocalDate tomorrow) {
        var account = users.findById(user).orElse(null);
        if (account == null || !"N".equals(account.getWithdrawalYn()) || !"N".equals(account.getLockYn())) return List.of();
        List<PushMessage.Line> lines = new ArrayList<>();
        for (var favorite : planner.list(user)) if (!favorite.summary().unavailable()) lines.addAll(favoriteLines(favorite.summary(), tomorrow));
        return lines;
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
        List<AnnouncementListItem> active = areas.active(area, today);
        Map<String, LocalDateTime> known = new HashMap<>();
        jdbc.query("SELECT ANNOUNCEMENT_ID,FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=?", (org.springframework.jdbc.core.RowCallbackHandler) r ->
            known.put(r.getString(1), r.getTimestamp(2).toLocalDateTime()), area.slug());
        LocalDateTime firstSeen = known.isEmpty() ? BASELINE : now;
        // 접수 중 공고가 없는 생활권도 기록 시작 시점을 남겨, 나중에 처음 올라온 공고를 새 공고로 판정한다.
        if (known.isEmpty()) {
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,'*',?)", area.slug(), Timestamp.valueOf(BASELINE)); }
            catch (org.springframework.dao.DuplicateKeyException raced) { firstSeen = now; }
        }
        List<Seen> result = new ArrayList<>();
        for (AnnouncementListItem item : active) {
            String id = item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo();
            LocalDateTime at = known.get(id);
            if (at == null) {
                try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,?,?)", area.slug(), id, Timestamp.valueOf(firstSeen)); at = firstSeen; }
                catch (org.springframework.dao.DuplicateKeyException raced) { at = BASELINE; }
                known.put(id, at);
            }
            result.add(new Seen(item, at));
        }
        return result;
    }
}
