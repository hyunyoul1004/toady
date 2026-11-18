package com.toady.web.app.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.planner.AnnouncementResults;
import com.toady.web.app.planner.HiddenAnnouncements;
import com.toady.web.app.planner.LivingAreas;
import com.toady.web.app.planner.PlannerService;
import com.toady.web.app.planner.PlannerSummary;
import com.toady.web.app.planner.PriceGapStore;
import com.toady.web.app.planner.PublicAreaPage;
import com.toady.web.app.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/**
 * 하루 한 번, 구독마다 '관심 공고의 내일 일정'과 '구독 생활권의 새 공고'를 하나의 알림으로 묶어 보낸다.
 * 메일과 달리 발송 한도가 없지만, 알림 피로를 줄이려고 하루 1회·내용이 있을 때만 보낸다.
 * 푸시 서비스가 404·410으로 응답하면(브라우저에서 해지·만료) 구독을 바로 삭제한다.
 * 실제 발송은 PUSH_DIGEST_ENABLED=true일 때만 시작한다.
 * 스케줄러 프로젝트가 아니라 이 API 안에서 @Scheduled로 돈다. 공고 데이터는 planner 쪽 서비스(PlannerSummary·PublicAreaPage 등)로 읽고,
 * 구독은 PushSubscriptions, 발송은 WebPushSender, 기록은 TB_PUSH_AREA_SEEN·TB_PUSH_RESULT_SENT에 JdbcTemplate으로 남긴다.
 * 시각은 모두 한국 시간(KST)이다.
 */
// @Service: 서비스 빈
@Service
// @ConditionalOnProperty: 설정 값 toady.push.digest-enabled가 "true"일 때만 이 빈을 만든다. 아니면 클래스 전체가 없는 것과 같다
@ConditionalOnProperty(name = "toady.push.digest-enabled", havingValue = "true")
public class PushDigest {
    private static final Logger log = LoggerFactory.getLogger(PushDigest.class);
    /** 생활권 새 공고 기록을 처음 만들 때 기존 공고를 '새 공고'로 보내지 않기 위한 기준 시각. */
    static final LocalDateTime BASELINE = LocalDateTime.of(2000, 1, 1, 0, 0);

    private final PushSubscriptions store; private final WebPushSender sender; private final JdbcTemplate jdbc;
    private final PlannerService planner; private final PlannerSummary summaries; private final PublicAreaPage areas;
    private final UserRepository users; private final ObjectMapper mapper; private final Clock clock;
    private final AnnouncementResults results;
    private final PriceGapStore gapStore;

    // @Autowired: 스프링이 쓸 생성자. 아래 Clock을 받는 생성자는 테스트에서 시각을 고정할 때 쓴다
    @Autowired
    public PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper, AnnouncementResults results,
            PriceGapStore gapStore) {
        this(store, sender, jdbc, planner, summaries, areas, users, mapper, results, gapStore, Clock.system(PushSubscriptions.KST));
    }
    PushDigest(PushSubscriptions store, WebPushSender sender, JdbcTemplate jdbc, PlannerService planner, PlannerSummary summaries,
            PublicAreaPage areas, UserRepository users, ObjectMapper mapper, AnnouncementResults results,
            PriceGapStore gapStore, Clock clock) {
        this.store = store; this.sender = sender; this.jdbc = jdbc; this.planner = planner; this.summaries = summaries;
        this.areas = areas; this.users = users; this.mapper = mapper; this.results = results; this.gapStore = gapStore; this.clock = clock;
    }

    /** 운영자가 숨긴 공고. 빈이 없으면(일부 테스트) 아무것도 숨기지 않는다. */
    private HiddenAnnouncements hidden;
    // @Autowired(required=false) 메서드 주입: 생성자를 늘리지 않고 숨김 확인 빈이 있을 때만 넣는다.
    @Autowired(required = false)
    void useHidden(HiddenAnnouncements hidden) { this.hidden = hidden; }

    static final int STALE_DAYS = 180;
    /** 한 번 실행한 결과 수: 보냄 / 만료로 삭제 / 다시 시도 / 보낼 내용 없음. */
    public record Report(int sent, int removed, int retry, int empty) { }
    /** 생활권·주제에서 본 공고와 그 공고를 처음 확인한 시각. */
    record Seen(AnnouncementListItem item, LocalDateTime firstSeen) {
        String id() { return item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo(); }
    }

    /** 내일 일정을 전날 저녁에 알린다. 서버가 18시에 멈춰 있어도 20시 30분까지 재기동 후 보낼 수 있게 30분마다 확인한다. */
    // @Scheduled(cron): "초 분 시 일 월 요일". 여기서는 18~20시의 0분·30분마다, 한국 시간 기준으로 실행한다
    @Scheduled(cron = "0 0,30 18-20 * * *", zone = "Asia/Seoul")
    public void scheduled() {
        if (!sender.enabled() || !store.ready()) return;
        Report report = run();
        if (report.sent() + report.removed() + report.retry() > 0)
            log.info("웹 푸시 묶음 알림: sent={}, removed={}, retry={}, empty={}", report.sent(), report.removed(), report.retry(), report.empty());
    }

    /**
     * 결과 즉시 알림: 관심 공고의 1순위 경쟁률·당첨가점이 처음 공개되면 묶음 알림(18~20시)을 기다리지 않고 바로 보낸다.
     * 수집기가 결과 시간대에 10분마다 수집하므로 그 5분 뒤에 확인한다. 밤 10시~아침 8시에는 보내지 않고 아침 8시 이후로 미룬다.
     * 한 구독에 하루 한 번까지(여러 결과는 한 알림으로 묶음). 묶음 알림과 합쳐 하루 2건을 넘지 않는다.
     * 보낸 결과는 TB_PUSH_RESULT_SENT(database.sql 29번)에 남겨 묶음 알림이 다시 보내지 않는다. 테이블이 없으면 즉시 알림만 쉰다.
     */
    // 매시 5분부터 10분 간격(5, 15, 25 ...분)으로 실행
    @Scheduled(cron = "0 5/10 * * * *", zone = "Asia/Seoul")
    public void scheduledResults() {
        if (!sender.enabled() || !store.ready()) return;
        Report report = runResults();
        if (report.sent() + report.removed() + report.retry() > 0)
            log.info("웹 푸시 결과 즉시 알림: sent={}, removed={}, retry={}", report.sent(), report.removed(), report.retry());
    }

    static final int QUIET_FROM = 22, QUIET_UNTIL = 8;
    private volatile boolean resultTableMissingLogged;

    /** 결과 즉시 알림 한 번 실행. 조용한 시간이거나 새 결과가 없으면 바로 끝난다. */
    public Report runResults() {
        LocalDateTime now = PushSubscriptions.now(clock);
        if (now.getHour() >= QUIET_FROM || now.getHour() < QUIET_UNTIL) return new Report(0, 0, 0, 0);
        LocalDate today = now.toLocalDate();
        try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_RESULT_SENT WHERE 1=0", Integer.class); }
        catch (DataAccessException missing) {
            if (!resultTableMissingLogged) { resultTableMissingLogged = true; log.warn("결과 즉시 알림을 쉽니다: database.sql 29번(TB_PUSH_RESULT_SENT)을 적용하세요"); }
            return new Report(0, 0, 0, 0);
        }
        Map<String, LocalDateTime> resultSeen = new HashMap<>();
        Map<String, AnnouncementResults.Result> resultCache = new HashMap<>();
        Set<String> startedResultKinds = new HashSet<>();
        safely("결과 기록", () -> { trackResults(today, now, resultCache, resultSeen, startedResultKinds); return List.of(); });
        // 최근 하루 안에 처음 공개된 결과가 있는 공고만 본다. 없으면 구독을 훑지 않는다(평소 대부분의 실행).
        LocalDateTime dayAgo = now.minusDays(1);
        Set<String> freshIds = new HashSet<>();
        resultSeen.forEach((key, at) -> { if (at.isAfter(dayAgo)) freshIds.add(key.substring(key.indexOf('|') + 1)); });
        if (freshIds.isEmpty()) return new Report(0, 0, 0, 0);
        // 오늘 이미 받은 구독, 이 공고들의 발송 기록, 이 공고를 저장한 회원을 실행마다 한 번에 읽는다(구독마다 조회하지 않게).
        Set<String> sentTodayHashes = new HashSet<>(jdbc.queryForList("SELECT DISTINCT ENDPOINT_HASH FROM TB_PUSH_RESULT_SENT WHERE SENT_DT>=?",
            String.class, Timestamp.valueOf(today.atStartOfDay())));
        Map<String, Set<String>> sentByHash = sentResults(freshIds);
        Map<Long, List<String>> memberFresh = memberFavoritesAmong(freshIds);
        Map<Long, Boolean> activeMembers = new HashMap<>();
        Map<String, Optional<PlannerSummary.Summary>> guestSummaries = new HashMap<>();
        Map<String, LocalDateTime> seenAt = new HashMap<>();
        resultSeen.forEach((key, at) -> seenAt.put((key.startsWith(RATE_SLUG + "|") ? "RATE|" : "SCORE|") + key.substring(key.indexOf('|') + 1), at));
        int sent = 0, removed = 0, retry = 0;
        for (PushSubscriptions.Row row : store.withFavorites()) {
            List<PushMessage.Line> claimed = List.of();
            try {
                if (sentTodayHashes.contains(row.hash())) continue;
                List<String> ids = row.userSeq() != null
                    ? (activeMembers.computeIfAbsent(row.userSeq(), this::memberActive) ? memberFresh.getOrDefault(row.userSeq(), List.of()) : List.of())
                    : row.favoriteIds().stream().filter(freshIds::contains).toList();
                if (ids.isEmpty()) continue;
                List<PlannerSummary.Summary> favorites = new ArrayList<>();
                for (String id : ids) guestSummary(id, guestSummaries).ifPresent(favorites::add);
                // 마지막 묶음 알림 뒤에 처음 공개된 결과만(묶음이 이미 알린 결과를 다시 보내지 않게).
                LocalDateTime since = row.lastDigestAt() != null && row.lastDigestAt().isAfter(row.registeredAt()) ? row.lastDigestAt() : row.registeredAt();
                Set<String> already = sentByHash.getOrDefault(row.hash(), Set.of());
                List<PushMessage.Line> lines = safely("결과 즉시 알림", () -> resultLines(favorites, today, since, now, resultCache, resultSeen, startedResultKinds)).stream()
                    .filter(line -> !already.contains(resultKey(line))).toList();
                // 보내기 전에 발송 기록을 먼저 남겨 선점한다. 다른 실행(배포 중 겹친 API 등)이 먼저 기록한 결과는 빼서 두 번 울리지 않게 한다.
                claimed = claimResults(row.hash(), lines, now);
                if (claimed.isEmpty()) continue;
                var message = PushMessage.results(claimed, resultCache, seenAt, now);
                if (message.isEmpty()) { releaseResults(row.hash(), claimed); continue; }
                var status = sender.send(row.target(), message.get().json(mapper), "result");
                // 발송 응답을 받은 뒤의 실패(전달 기록 등)로는 선점을 풀지 않는다(이미 보낸 알림을 다시 보내지 않게).
                List<PushMessage.Line> attempted = claimed;
                claimed = List.of();
                switch (status) {
                    case ACCEPTED -> { store.delivered(row.hash(), now, false); sent++; }
                    case GONE -> { store.deleteHash(row.hash()); removed++; }
                    // 일시 장애는 기록을 지워 다음 확인(10분 뒤) 때 다시 보낸다. 같은 tag라 중복 표시되지 않는다.
                    case RETRY -> { releaseResults(row.hash(), attempted); retry++; }
                    case REJECTED -> { }
                }
            } catch (Exception failure) {
                log.warn("웹 푸시 결과 즉시 알림 처리 실패: error={}", failure.getClass().getSimpleName());
                // 선점한 뒤 실패하면 기록을 풀어 다음 확인이나 묶음 알림이 보내게 한다(선점만 남아 영영 빠지지 않게).
                try { releaseResults(row.hash(), claimed); } catch (RuntimeException ignored) { /* 다음 실행에서 정리되지 않으면 묶음 알림 대상에서만 빠진다 */ }
            }
        }
        return new Report(sent, removed, retry, 0);
    }
    /** 결과 줄의 중복 판정 키: 공고 id|RATE 또는 id|SCORE. */
    static String resultKey(PushMessage.Line line) { return line.announcementId() + "|" + ("경쟁률 발표".equals(line.label()) ? "RATE" : "SCORE"); }
    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    /** 이 구독에 즉시 알림으로 보낸 결과(키 모음). 테이블이 없으면 빈 모음. */
    Set<String> sentResults(String hash) {
        try {
            return new HashSet<>(jdbc.queryForList("SELECT CONCAT(ANNOUNCEMENT_ID,'|',RESULT_KIND) FROM TB_PUSH_RESULT_SENT WHERE ENDPOINT_HASH=?", String.class, hash));
        } catch (DataAccessException missing) { return Set.of(); }
    }
    /** 주어진 공고들에 대해 구독별로 보낸 결과(키 모음). */
    private Map<String, Set<String>> sentResults(Set<String> ids) {
        Map<String, Set<String>> out = new HashMap<>();
        jdbc.query("SELECT ENDPOINT_HASH,CONCAT(ANNOUNCEMENT_ID,'|',RESULT_KIND) FROM TB_PUSH_RESULT_SENT WHERE ANNOUNCEMENT_ID IN (" + placeholders(ids.size()) + ")",
            (RowCallbackHandler) r -> out.computeIfAbsent(r.getString(1), key -> new HashSet<>()).add(r.getString(2)), ids.toArray());
        return out;
    }
    /** 주어진 공고를 저장한 회원별 공고 id(저장 최신순). */
    private Map<Long, List<String>> memberFavoritesAmong(Set<String> ids) {
        Set<String> houses = new HashSet<>();
        for (String id : ids) { String[] parts = id.split("/"); if (parts.length == 3) houses.add(parts[1]); }
        Map<Long, List<String>> out = new HashMap<>();
        if (houses.isEmpty()) return out;
        jdbc.query("SELECT USER_SEQ,CONCAT(ANNOUNCEMENT_TYPE,'/',HOUSE_MANAGE_NO,'/',PBLANC_NO) FROM TB_FAVORITE WHERE HOUSE_MANAGE_NO IN (" + placeholders(houses.size()) + ") ORDER BY REG_DT DESC",
            (RowCallbackHandler) r -> { if (ids.contains(r.getString(2))) out.computeIfAbsent(r.getLong(1), key -> new ArrayList<>()).add(r.getString(2)); }, houses.toArray());
        return out;
    }
    /** 결과 발송 기록을 먼저 넣어 선점한다. 이미 기록된(다른 실행이 보낸) 결과는 돌려주지 않는다. */
    private List<PushMessage.Line> claimResults(String hash, List<PushMessage.Line> lines, LocalDateTime now) {
        List<PushMessage.Line> claimed = new ArrayList<>();
        for (PushMessage.Line line : lines) {
            String key = resultKey(line);
            try {
                jdbc.update("INSERT INTO TB_PUSH_RESULT_SENT (ENDPOINT_HASH,ANNOUNCEMENT_ID,RESULT_KIND,SENT_DT) VALUES (?,?,?,?)",
                    hash, line.announcementId(), key.substring(key.lastIndexOf('|') + 1), Timestamp.valueOf(now));
                claimed.add(line);
            } catch (DuplicateKeyException already) { /* 다른 실행이 먼저 선점 */ }
        }
        return claimed;
    }
    /** claimResults로 넣은 발송 기록을 지운다(보내지 못했을 때). */
    private void releaseResults(String hash, List<PushMessage.Line> lines) {
        for (PushMessage.Line line : lines) {
            String key = resultKey(line);
            jdbc.update("DELETE FROM TB_PUSH_RESULT_SENT WHERE ENDPOINT_HASH=? AND ANNOUNCEMENT_ID=? AND RESULT_KIND=?",
                hash, line.announcementId(), key.substring(key.lastIndexOf('|') + 1));
        }
    }

    /**
     * 저녁 묶음 알림 한 번 실행. 순서: 오래된 구독 정리 → 생활권·주제별 새 공고와 결과 공개 시각 기록 →
     * 오늘 아직 안 보낸 구독마다 줄을 모아 알림 하나로 묶어 보낸다. 구독 행을 claim으로 선점해 하루 한 번만 보낸다.
     */
    public Report run() {
        LocalDateTime now = PushSubscriptions.now(clock);
        LocalDate today = now.toLocalDate(), tomorrow = today.plusDays(1);
        // 오래 쓰지 않은 구독 정리: 6개월 넘게 전달 성공도 없고 브라우저의 재등록(앱 방문 시 범위 저장)도 없는 행.
        // 보낼 내용이 없던 구독은 푸시 서비스의 404·410 응답을 받을 일이 없어 이렇게 따로 정리한다.
        store.removeStale(now.minusDays(STALE_DAYS));
        Map<String, List<Seen>> news = new HashMap<>();
        Map<Long, List<PlannerSummary.Summary>> members = new HashMap<>();
        Map<String, LocalDateTime> resultSeen = new HashMap<>();
        Map<String, Optional<PriceGapStore.Gap>> gapMemo = new HashMap<>();
        Map<String, AnnouncementResults.Result> resultCache = new HashMap<>();
        Set<String> startedResultKinds = new HashSet<>();
        PublicAreaPage.WeekCounts[] week = new PublicAreaPage.WeekCounts[1];
        Map<String, Optional<PlannerSummary.Summary>> guestSummaries = new HashMap<>();
        List<List<AnnouncementListItem>> unranked = new ArrayList<>();
        // 새 공고·결과의 최초 확인 시각을 구독자 유무와 상관없이 매 실행 기록한다. 그래야 한동안 구독자가 없던 생활권이나
        // 결과가 공개된 뒤 저장한 공고도 '실제로 처음 보인 시각' 기준으로 판정해, 지난 공고·결과를 새 소식으로 보내지 않는다.
        for (var area : LivingAreas.all()) news.put(area.slug(), safely("생활권 " + area.slug(), () -> refreshArea(area, today, now)));
        for (var topic : PushTopics.ALL) news.put(topic.slug(), safely("주제 " + topic.slug(),
            () -> refresh(topic.slug(), unranked(today, unranked).stream().filter(topic::includes).toList(), now)));
        safely("결과 기록", () -> { trackResults(today, now, resultCache, resultSeen, startedResultKinds); return List.of(); });
        List<PushSubscriptions.Row> pending = store.pending(today);
        if (pending.isEmpty()) return new Report(0, 0, 0, 0);
        int sent = 0, removed = 0, retry = 0, empty = 0;
        for (PushSubscriptions.Row row : pending) {
            try {
                List<PushMessage.Line> lines = new ArrayList<>();
                LocalDateTime since = row.lastDigestAt() != null ? row.lastDigestAt() : row.registeredAt();
                if (row.favorites()) {
                    List<PlannerSummary.Summary> favorites = new ArrayList<>();
                    if (row.userSeq() != null) favorites.addAll(members.computeIfAbsent(row.userSeq(), user -> memberSummaries(user, guestSummaries)));
                    else for (String id : row.favoriteIds()) guestSummary(id, guestSummaries).ifPresent(favorites::add);
                    for (PlannerSummary.Summary summary : favorites) lines.addAll(favoriteLines(summary, tomorrow));
                    // 결과 조회가 실패해도 내일 일정 줄은 보낸다.
                    // 결과 즉시 알림으로 이미 보낸 결과는 묶음에서 뺀다(같은 소식을 두 번 보내지 않게).
                    Set<String> sentNow = sentResults(row.hash());
                    lines.addAll(safely("결과 알림", () -> resultLines(favorites, today, since, now, resultCache, resultSeen, startedResultKinds)).stream()
                        .filter(line -> !sentNow.contains(resultKey(line))).toList());
                }
                Set<String> already = new HashSet<>();
                lines.forEach(line -> already.add(line.announcementId()));
                for (String slug : row.areas()) {
                    // 주간 브리핑: 일요일 저녁 묶음에만 다음 주(월~일) 접수 시작 공고 수를 넣는다.
                    if (PushTopics.WEEKLY.equals(slug)) {
                        if (today.getDayOfWeek() != DayOfWeek.SUNDAY) continue;
                        if (week[0] == null) {
                            try { week[0] = areas.startingCounts(today.plusDays(1), today.plusDays(7)); }
                            catch (RuntimeException failure) { log.warn("주간 브리핑 집계 실패: error={}", failure.getClass().getSimpleName()); week[0] = new PublicAreaPage.WeekCounts(0, 0, 0); }
                        }
                        if (week[0].total() > 0) lines.add(new PushMessage.Line("WEEKLY", null, weeklyText(week[0]), "이번 주 청약", slug, "이번 주 청약"));
                        continue;
                    }
                    var area = LivingAreas.bySlug(slug);
                    var topic = PushTopics.bySlug(slug);
                    if (area.isEmpty() && topic.isEmpty()) continue;
                    String label = area.map(LivingAreas.Area::label).orElseGet(() -> topic.get().label());
                    List<Seen> seenList = news.getOrDefault(slug, List.of());
                    for (Seen seen : seenList)
                        if (seen.firstSeen().isAfter(since) && already.add(seen.id()))
                            lines.add(new PushMessage.Line("AREA", seen.id(), seen.item().houseNm(), newLabel(seen.id(), gapMemo), slug, label));
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

    /** 회원 관심 공고 요약. 공고 요약은 비회원과 같은 캐시로 공고 단위로 한 번만 만든다(회원마다 다시 만들지 않는다). */
    private List<PlannerSummary.Summary> memberSummaries(long user, Map<String, Optional<PlannerSummary.Summary>> cache) {
        if (!memberActive(user)) return List.of();
        List<PlannerSummary.Summary> out = new ArrayList<>();
        for (String id : jdbc.queryForList("SELECT CONCAT(ANNOUNCEMENT_TYPE,'/',HOUSE_MANAGE_NO,'/',PBLANC_NO) FROM TB_FAVORITE WHERE USER_SEQ=? ORDER BY REG_DT DESC", String.class, user))
            guestSummary(id, cache).ifPresent(out::add);
        return out;
    }
    /** 탈퇴·잠금되지 않은 회원인지. */
    private boolean memberActive(long user) {
        var account = users.findById(user).orElse(null);
        return account != null && "N".equals(account.getWithdrawalYn()) && "N".equals(account.getLockYn());
    }
    /** 실패한 계산은 빈 결과로 이번 실행 동안 기억해, 같은 실패를 구독마다 되풀이하지 않고 다른 알림 줄은 계속 보낸다. */
    private <T> List<T> safely(String what, Supplier<List<T>> work) {
        try { return work.get(); }
        catch (RuntimeException failure) { log.warn("웹 푸시 {} 계산 실패: error={}", what, failure.getClass().getSimpleName()); return List.of(); }
    }
    /** 접수 마감 30일 이내 APT 공고의 결과 공개 시각을 구독자와 무관하게 기록한다. */
    void trackResults(LocalDate today, LocalDateTime now, Map<String, AnnouncementResults.Result> cache,
            Map<String, LocalDateTime> seen, Set<String> started) {
        List<String> ids = areas.aptEndedWithin(today, RESULT_FRESH_DAYS);
        if (ids.isEmpty()) return;
        for (String slug : List.of(RATE_SLUG, SCORE_SLUG)) startResults(slug, started);
        Map<String, AnnouncementResults.Result> found = results.forIds(ids);
        // 이미 기록된 최초 확인 시각은 공고마다 묻지 않고 한 번에 읽는다(10분마다 도는 즉시 알림의 DB 부담을 줄인다).
        List<Object> args = new ArrayList<>(List.of(RATE_SLUG, SCORE_SLUG));
        args.addAll(ids);
        jdbc.query("SELECT AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG IN (?,?) AND ANNOUNCEMENT_ID IN (" + placeholders(ids.size()) + ")",
            (RowCallbackHandler) r -> {
                String slug = r.getString(1), id = r.getString(2);
                if (seen.putIfAbsent(slug + "|" + id, r.getTimestamp(3).toLocalDateTime()) == null) markResult(slug, id, now);
            }, args.toArray());
        for (String id : ids) {
            var result = found.get(id);
            cache.put(id, result);
            if (result == null) continue;
            if (result.ratePublished()) resultSeen(RATE_SLUG, id, now, seen, started);
            if (result.scorePublished()) resultSeen(SCORE_SLUG, id, now, seen, started);
        }
    }
    /** 새 공고 줄 이름. 미리 계산한 주변 시세 비교가 있으면 '시세 대비 약 1.2억 낮음'을 붙인다(없으면 '새 공고'). */
    private String newLabel(String id, Map<String, Optional<PriceGapStore.Gap>> memo) {
        var gap = memo.computeIfAbsent(id, key -> Optional.ofNullable(gapStore.forIds(List.of(key)).get(key)));
        // 화면 뱃지(gapBadge)와 같은 문구: 신축 기준이면 '신축'을 밝히고, 참고 비교임을 덧붙인다.
        return gap.map(value -> {
            String label = "주변 " + ("new".equals(value.basis()) ? "신축 " : "") + "시세";
            return "새 공고 · " + ("비슷".equals(value.text()) ? label + "와 비슷" : label + " 대비 " + value.text()) + "(참고)";
        }).orElse("새 공고");
    }
    /** 주간 브리핑 한 줄 요약(예: 접수 시작 5건 · 아파트 3건 · 무순위·줍줍 2건). */
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
            Map<String, AnnouncementResults.Result> cache, Map<String, LocalDateTime> seen, Set<String> started) {
        List<PlannerSummary.Summary> fresh = favorites.stream().filter(summary -> summary.id().startsWith("apt/") && summary.endDate() != null
            && !summary.endDate().isBefore(today.minusDays(RESULT_FRESH_DAYS)) && !summary.endDate().isAfter(today)).toList();
        if (fresh.isEmpty()) return List.of();
        // 두 결과 종류의 기록을 함께 시작한다. 시작한 실행에서 보이는 결과는 기준 시각으로 두고, 그 뒤 처음 보이는 결과만 새 소식이다.
        for (String slug : List.of(RATE_SLUG, SCORE_SLUG)) startResults(slug, started);
        List<String> missing = fresh.stream().map(PlannerSummary.Summary::id).filter(id -> !cache.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<String, AnnouncementResults.Result> found = results.forIds(missing);
            for (String id : missing) cache.put(id, found.get(id));
        }
        // 결과는 '최근 하루 안에 처음 공개된 것'만 새 소식으로 본다. 오래 알림을 받지 않아 since가 몇 주 전이어도
        // 그 사이 공개된 결과를 나중에 저장한 공고에서 한꺼번에 보내지 않는다.
        LocalDateTime cutoff = since.isAfter(now.minusDays(1)) ? since : now.minusDays(1);
        List<PushMessage.Line> lines = new ArrayList<>();
        for (PlannerSummary.Summary summary : fresh) {
            var result = cache.get(summary.id());
            if (result == null) continue;
            if (result.ratePublished() && resultSeen(RATE_SLUG, summary.id(), now, seen, started).isAfter(cutoff))
                lines.add(new PushMessage.Line("RESULT", summary.id(), summary.houseNm(), "경쟁률 발표", null, null));
            if (result.scorePublished() && resultSeen(SCORE_SLUG, summary.id(), now, seen, started).isAfter(cutoff))
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
            catch (DuplicateKeyException raced) { /* 다른 실행이 먼저 시작 */ }
        }
        started.add(before ? slug : slug + "*");
    }
    /** 결과 종류(slug)별 최초 확인 시각. 그 종류를 처음 기록하는 실행에서는 이미 공개된 결과를 새 소식으로 보지 않는다(기준 시각). */
    private LocalDateTime resultSeen(String slug, String id, LocalDateTime now, Map<String, LocalDateTime> seen, Set<String> started) {
        return seen.computeIfAbsent(slug + "|" + id, key -> {
            List<Timestamp> found = jdbc.queryForList("SELECT FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=? AND ANNOUNCEMENT_ID=?", Timestamp.class, slug, id);
            if (!found.isEmpty()) { markResult(slug, id, now); return found.get(0).toLocalDateTime(); }
            // slug+"*": 이번 실행에서 기록을 시작했다 → 지금 공개돼 있는 결과는 모두 기준 시각으로 둔다.
            LocalDateTime at = started.contains(slug + "*") ? BASELINE : now;
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,?,?)", slug, id, Timestamp.valueOf(at)); }
            catch (DuplicateKeyException raced) { at = BASELINE; }
            markResult(slug, id, now);
            return at;
        });
    }
    /** 결과의 마지막 확인 시각은 하루 한 번만 갱신한다(보관 정리 기준이라 날짜 단위면 충분하다). */
    private final Map<String, LocalDate> resultMarked = new java.util.concurrent.ConcurrentHashMap<>();
    private void markResult(String slug, String id, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if (today.equals(resultMarked.put(slug + "|" + id, today))) return;
        // 지난 날짜 항목은 비워 서버가 오래 떠 있어도 쌓이지 않게 한다.
        if (resultMarked.size() > 2_000) resultMarked.values().removeIf(day -> !day.equals(today));
        markSeen(slug, List.of(id), now);
    }
    /**
     * 공고 id(유형/주택관리번호/공고번호)의 요약을 읽는다. 이번 실행 안에서는 cache로 공고마다 한 번만 읽는다.
     * 운영자가 숨긴 공고는 빈 값으로 돌려준다. 회원·비회원의 내일 일정 줄과 결과 줄(묶음·즉시 알림)이 모두 여기서 요약을 얻으므로 함께 빠진다.
     */
    private Optional<PlannerSummary.Summary> guestSummary(String id, Map<String, Optional<PlannerSummary.Summary>> cache) {
        return cache.computeIfAbsent(id, key -> {
            String[] parts = key.split("/");
            if (parts.length != 3 || (hidden != null && hidden.hiddenId(key))) return Optional.empty();
            try { return Optional.of(summaries.get(parts[0], parts[1], parts[2])); }
            catch (EntityNotFoundException | IllegalArgumentException missing) { return Optional.empty(); }
        });
    }
    /** 관심 공고의 일정 중 내일 날짜인 것을 알림 줄로 만든다. */
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
        jdbc.query("SELECT ANNOUNCEMENT_ID,FIRST_SEEN_DT FROM TB_PUSH_AREA_SEEN WHERE AREA_SLUG=?", (RowCallbackHandler) r ->
            known.put(r.getString(1), r.getTimestamp(2).toLocalDateTime()), slug);
        LocalDateTime firstSeen = known.isEmpty() ? BASELINE : now;
        // 접수 중 공고가 없는 생활권도 기록 시작 시점을 남겨, 나중에 처음 올라온 공고를 새 공고로 판정한다.
        if (known.isEmpty()) {
            try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,'*',?)", slug, Timestamp.valueOf(BASELINE)); }
            catch (DuplicateKeyException raced) { firstSeen = now; }
        }
        List<Seen> result = new ArrayList<>();
        for (AnnouncementListItem item : active) {
            String id = item.type() + "/" + item.houseManageNo() + "/" + item.pblancNo();
            LocalDateTime at = known.get(id);
            if (at == null) {
                try { jdbc.update("INSERT INTO TB_PUSH_AREA_SEEN (AREA_SLUG,ANNOUNCEMENT_ID,FIRST_SEEN_DT) VALUES (?,?,?)", slug, id, Timestamp.valueOf(firstSeen)); at = firstSeen; }
                catch (DuplicateKeyException raced) { at = BASELINE; }
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
        } catch (BadSqlGrammarException missingColumn) {
            lastSeenColumn = -1;
            log.warn("TB_PUSH_AREA_SEEN.LAST_SEEN_DT 컬럼이 없어 마지막 확인 시각을 기록하지 않습니다. database.sql 15번을 적용하세요.");
        }
    }
}
