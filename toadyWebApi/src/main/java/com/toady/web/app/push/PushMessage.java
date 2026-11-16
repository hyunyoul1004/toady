package com.toady.web.app.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 서비스 워커(public/sw.js)가 그대로 표시하는 알림 내용. 주소는 같은 출처의 상대 경로만 쓴다.
 * 관심 공고 '내일 일정'과 구독 생활권 '새 공고'를 하루 한 번 하나의 알림으로 묶는다.
 */
public record PushMessage(String title, String body, String url, String tag, List<Action> actions) {
    public record Action(String action, String title, String url) { }
    /** 묶음에 들어가는 한 줄. kind는 FAVORITE·RESULT·AREA·WEEKLY(digest 설명 참고). */
    public record Line(String kind, String announcementId, String houseNm, String label, String areaSlug, String areaLabel) { }

    static final int MAX_NAME = 40;

    public static String detailUrl(String announcementId) {
        return "/?subscriptSeq=" + URLEncoder.encode(announcementId, StandardCharsets.UTF_8);
    }
    static String name(String value) {
        String text = value == null || value.isBlank() ? "공고" : value.strip();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME - 1) + "…" : text;
    }

    public static PushMessage test(String announcementId, String houseNm) {
        if (announcementId == null) return new PushMessage("TOADY 알림이 켜졌어요", "관심 공고의 전날 일정과 구독한 생활권의 새 공고를 하루 한 번 모아서 알려 드릴게요.", "/my", "toady-test", List.of());
        return new PushMessage("TOADY 알림이 켜졌어요", "이렇게 알려 드려요. 눌러서 ‘" + name(houseNm) + "’ 상세를 열어 보세요.", detailUrl(announcementId), "toady-test", List.of());
    }

    /**
     * 결과 즉시 알림(관심 공고의 1순위 경쟁률·당첨가점이 처음 공개된 직후). 숫자는 청약홈 수집값 그대로, 반영 시각을 함께 적는다.
     * lines의 label은 "경쟁률 발표"·"당첨가점 공개", details는 공고 id → 결과, seenAt은 "RATE|id"·"SCORE|id" → 처음 확인 시각.
     */
    /** 확인 시각 표기: 오늘이면 'HH:mm', 어제면 '어제 HH:mm', 그보다 전이면 'M월 d일'. */
    static String checkedAt(java.time.LocalDateTime at, java.time.LocalDate today) {
        String time = at.toLocalTime().withSecond(0).withNano(0).toString();
        if (at.toLocalDate().equals(today)) return time;
        if (at.toLocalDate().equals(today.minusDays(1))) return "어제 " + time;
        return at.getMonthValue() + "월 " + at.getDayOfMonth() + "일";
    }
    public static Optional<PushMessage> results(List<Line> lines, java.util.Map<String, com.toady.web.app.planner.AnnouncementResults.Result> details,
            java.util.Map<String, java.time.LocalDateTime> seenAt, java.time.LocalDateTime now) {
        if (lines.isEmpty()) return Optional.empty();
        String tag = "toady-result";
        if (lines.size() == 1) {
            Line only = lines.get(0);
            var result = details.get(only.announcementId());
            boolean rate = "경쟁률 발표".equals(only.label());
            var at = seenAt.get((rate ? "RATE|" : "SCORE|") + only.announcementId());
            // TOADY가 청약홈 수집값을 처음 확인한 시각(밤사이 공개분은 아침 확인 시각이 된다). 공개 시각처럼 쓰지 않는다.
            String when = at == null ? "" : " · 청약홈 집계, " + checkedAt(at, now.toLocalDate()) + " 확인";
            String body;
            if (rate) body = result != null && result.topRate() != null
                ? "1순위 최고 " + formatRate(result.topRate()) + " 대 1" + when + ". 눌러서 주택형별 경쟁률을 확인하세요."
                : "1순위 경쟁률이 공개됐어요" + when + ". 눌러서 주택형별 경쟁률을 확인하세요.";
            else body = result != null && !result.scoreLow().isEmpty()
                ? "주택형별 당첨가점 " + scoreRange(result.scoreLow(), result.scoreHigh()) + when + ". 눌러서 내 가점과 비교해 보세요."
                : "당첨가점이 공개됐어요" + when + ". 눌러서 내 가점과 비교해 보세요.";
            return Optional.of(new PushMessage(only.label() + " · " + name(only.houseNm()), body, resultUrl(only.announcementId()), tag, List.of()));
        }
        Line first = lines.get(0);
        String body = "‘" + name(first.houseNm()) + "’ " + first.label() + " 외 " + (lines.size() - 1) + "건이 공개됐어요. 눌러서 확인하세요.";
        return Optional.of(new PushMessage("관심 공고 결과 " + lines.size() + "건", body, resultUrl(first.announcementId()), tag,
            List.of(new Action("my", "나의 청약", "/my"))));
    }
    /** 경쟁률 숫자 표기(100 이상은 정수, 그 아래는 소수 둘째 자리까지). 공유 미리보기에서도 같은 표기를 쓴다. */
    public static String rate(double rate) { return formatRate(rate); }
    static String formatRate(double rate) {
        return rate >= 100 ? String.valueOf(Math.round(rate)) : java.math.BigDecimal.valueOf(rate).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    static String scoreRange(String low, String high) {
        if (high == null || high.isEmpty() || high.equals(low)) return low + "점";
        return low + "~" + high + "점";
    }

    /** 결과 알림은 경쟁률 탭으로 바로 연다. */
    public static String resultUrl(String announcementId) { return detailUrl(announcementId) + "&tabType=rate"; }

    /**
     * 보낼 내용이 없으면 비어 있다. 같은 tag라 전날 알림이 남아 있어도 새 묶음으로 교체된다.
     * 줄 종류: FAVORITE(관심 공고 내일 일정) · RESULT(관심 공고 경쟁률 발표·당첨가점 공개) · AREA(생활권·줍줍 새 공고) · WEEKLY(일요일 주간 브리핑, houseNm에 요약 문장).
     */
    public static Optional<PushMessage> digest(List<Line> lines) {
        List<Line> favorites = kind(lines, "FAVORITE"), results = kind(lines, "RESULT"), news = kind(lines, "AREA"), weekly = kind(lines, "WEEKLY");
        int total = favorites.size() + results.size() + news.size() + weekly.size();
        if (total == 0) return Optional.empty();
        String tag = "toady-digest";
        if (total == 1) {
            if (!favorites.isEmpty()) {
                Line only = favorites.get(0);
                return Optional.of(new PushMessage("내일 " + only.label() + " · " + name(only.houseNm()),
                    "‘" + name(only.houseNm()) + "’ " + only.label() + " 일정이 내일이에요. 눌러서 공고와 준비 상황을 확인하세요.", detailUrl(only.announcementId()), tag, List.of()));
            }
            if (!results.isEmpty()) {
                Line only = results.get(0);
                String body = only.label().equals("경쟁률 발표") ? "1순위 경쟁률이 공개됐어요. 눌러서 내 예상·가점과 비교해 보세요." : "당첨가점이 공개됐어요. 눌러서 내 가점과 비교해 보세요.";
                return Optional.of(new PushMessage(only.label() + " · " + name(only.houseNm()), "‘" + name(only.houseNm()) + "’ " + body, resultUrl(only.announcementId()), tag, List.of()));
            }
            if (!news.isEmpty()) {
                Line only = news.get(0);
                // 주변 시세 비교가 있으면 본문에 함께 적는다(라벨: '새 공고 · 주변 시세 대비 약 1.2억 낮음').
                String gap = only.label() != null && only.label().startsWith("새 공고 · ") ? " " + only.label().substring("새 공고 · ".length()) + "." : "";
                return Optional.of(new PushMessage(only.areaLabel() + " 새 청약 공고",
                    "‘" + name(only.houseNm()) + "’ 공고가 올라왔어요." + gap + " 눌러서 일정과 분양가를 확인하세요.", detailUrl(only.announcementId()), tag, List.of()));
            }
            return Optional.of(new PushMessage("이번 주 청약 브리핑", weekly.get(0).houseNm() + ". 눌러서 이번 주 일정을 확인하세요.", PushTopics.WEEKLY_URL, tag, List.of()));
        }
        List<String> parts = new ArrayList<>();
        if (!favorites.isEmpty()) parts.add("내일 일정 " + favorites.size() + "건 · " + name(favorites.get(0).houseNm()) + " " + favorites.get(0).label() + (favorites.size() > 1 ? " 외 " + (favorites.size() - 1) + "건" : ""));
        if (!results.isEmpty()) parts.add("결과 발표 " + results.size() + "건 · " + name(results.get(0).houseNm()) + " " + results.get(0).label() + (results.size() > 1 ? " 외 " + (results.size() - 1) + "건" : ""));
        Map<String, List<Line>> byArea = new LinkedHashMap<>();
        news.forEach(line -> byArea.computeIfAbsent(line.areaSlug(), key -> new ArrayList<>()).add(line));
        byArea.values().stream().limit(3).forEach(group -> parts.add(group.get(0).areaLabel() + " 새 공고 " + group.size() + "건 · " + name(group.get(0).houseNm()) + (group.size() > 1 ? " 외 " + (group.size() - 1) + "건" : "")));
        if (!weekly.isEmpty()) parts.add("이번 주 청약 · " + weekly.get(0).houseNm());
        List<Action> actions = new ArrayList<>();
        String url;
        if (!favorites.isEmpty()) url = detailUrl(favorites.get(0).announcementId());
        else if (!results.isEmpty()) url = resultUrl(results.get(0).announcementId());
        else if (!news.isEmpty()) url = detailUrl(news.get(0).announcementId());
        else url = PushTopics.WEEKLY_URL;
        if (!favorites.isEmpty() || !results.isEmpty()) actions.add(new Action("my", "나의 청약", "/my"));
        if (!byArea.isEmpty()) {
            Line first = byArea.values().iterator().next().get(0);
            actions.add(new Action("area", first.areaLabel() + " 새 공고", PushTopics.url(first.areaSlug())));
        }
        if (!weekly.isEmpty()) actions.add(new Action("weekly", "이번 주 청약", PushTopics.WEEKLY_URL));
        int personal = favorites.size() + results.size();
        String title = personal > 0 ? "TOADY 청약 알림 · " + (personal + news.size()) + "건" : !news.isEmpty() ? "새 청약 공고 " + news.size() + "건" : "이번 주 청약 브리핑";
        return Optional.of(new PushMessage(title, String.join("\n", parts), url, tag, actions.stream().limit(2).toList()));
    }
    private static List<Line> kind(List<Line> lines, String kind) { return lines.stream().filter(line -> line.kind().equals(kind)).toList(); }

    public String json(ObjectMapper mapper) {
        try {
            String json = mapper.writeValueAsString(this);
            if (json.getBytes(StandardCharsets.UTF_8).length <= WebPushCrypto.MAX_PLAINTEXT) return json;
            return mapper.writeValueAsString(new PushMessage(title, body.length() > 300 ? body.substring(0, 300) + "…" : body, url, tag, List.of()));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
