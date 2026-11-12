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
    /** 묶음에 들어가는 한 줄. kind는 FAVORITE(내일 일정) 또는 AREA(생활권 새 공고). */
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

    /** 보낼 내용이 없으면 비어 있다. 같은 tag라 전날 알림이 남아 있어도 새 묶음으로 교체된다. */
    public static Optional<PushMessage> digest(List<Line> lines) {
        List<Line> favorites = lines.stream().filter(line -> line.kind().equals("FAVORITE")).toList();
        List<Line> news = lines.stream().filter(line -> line.kind().equals("AREA")).toList();
        if (favorites.isEmpty() && news.isEmpty()) return Optional.empty();
        String tag = "toady-digest";
        if (favorites.size() == 1 && news.isEmpty()) {
            Line only = favorites.get(0);
            return Optional.of(new PushMessage("내일 " + only.label() + " · " + name(only.houseNm()),
                "‘" + name(only.houseNm()) + "’ " + only.label() + " 일정이 내일이에요. 눌러서 공고와 준비 상황을 확인하세요.", detailUrl(only.announcementId()), tag, List.of()));
        }
        if (favorites.isEmpty() && news.size() == 1) {
            Line only = news.get(0);
            return Optional.of(new PushMessage(only.areaLabel() + " 새 청약 공고",
                "‘" + name(only.houseNm()) + "’ 공고가 올라왔어요. 눌러서 일정과 분양가를 확인하세요.", detailUrl(only.announcementId()), tag, List.of()));
        }
        List<String> parts = new ArrayList<>();
        if (!favorites.isEmpty()) parts.add("내일 일정 " + favorites.size() + "건 · " + name(favorites.get(0).houseNm()) + " " + favorites.get(0).label() + (favorites.size() > 1 ? " 외 " + (favorites.size() - 1) + "건" : ""));
        Map<String, List<Line>> byArea = new LinkedHashMap<>();
        news.forEach(line -> byArea.computeIfAbsent(line.areaSlug(), key -> new ArrayList<>()).add(line));
        byArea.values().stream().limit(3).forEach(group -> parts.add(group.get(0).areaLabel() + " 새 공고 " + group.size() + "건 · " + name(group.get(0).houseNm()) + (group.size() > 1 ? " 외 " + (group.size() - 1) + "건" : "")));
        List<Action> actions = new ArrayList<>();
        String url;
        if (!favorites.isEmpty()) {
            url = detailUrl(favorites.get(0).announcementId());
            actions.add(new Action("my", "나의 청약", "/my"));
        } else url = detailUrl(news.get(0).announcementId());
        if (!byArea.isEmpty()) {
            Line first = byArea.values().iterator().next().get(0);
            actions.add(new Action("area", first.areaLabel() + " 새 공고", PushTopics.url(first.areaSlug())));
        }
        String title = favorites.isEmpty() ? "새 청약 공고 " + news.size() + "건" : "TOADY 청약 알림 · " + (favorites.size() + news.size()) + "건";
        return Optional.of(new PushMessage(title, String.join("\n", parts), url, tag, actions.stream().limit(2).toList()));
    }

    public String json(ObjectMapper mapper) {
        try {
            String json = mapper.writeValueAsString(this);
            if (json.getBytes(StandardCharsets.UTF_8).length <= WebPushCrypto.MAX_PLAINTEXT) return json;
            return mapper.writeValueAsString(new PushMessage(title, body.length() > 300 ? body.substring(0, 300) + "…" : body, url, tag, List.of()));
        } catch (JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
