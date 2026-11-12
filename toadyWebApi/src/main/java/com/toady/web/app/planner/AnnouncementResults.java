package com.toady.web.app.planner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 관심 공고들의 청약 결과(1순위 경쟁률·공개 당첨가점)를 한 번에 요약한다. 나의 청약의 진행 단계와 결과 알림이 같은 기준을 쓴다.
 * 공개 자료만 다루므로 로그인 없이 조회한다. APT 공고만 경쟁률·당첨가점이 공개된다.
 */
@RestController
@Validated
public class AnnouncementResults {
    static final java.util.regex.Pattern APT_ID = java.util.regex.Pattern.compile("apt/([a-zA-Z0-9_-]{1,40})/([a-zA-Z0-9_-]{1,40})");
    private final JdbcTemplate jdbc;
    public AnnouncementResults(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * ratePublished: 1순위 경쟁률 행이 있음(전 주택형 미달이면 topRate는 null). topRate: 숫자로 공개된 1순위 최고 경쟁률.
     * scoreLow/scoreHigh: 공개 주택형·지역 전체의 최저·최고 당첨가점(없으면 빈 문자열).
     */
    public record Result(String id, boolean ratePublished, Double topRate, String scoreLow, String scoreHigh, boolean scorePublished) {
        static Result of(String id, boolean ratePublished, Double topRate, String scoreLow, String scoreHigh) {
            return new Result(id, ratePublished, topRate, scoreLow, scoreHigh, !scoreLow.isEmpty() || !scoreHigh.isEmpty());
        }
    }
    public record Request(@NotNull @Size(max = 50) List<@NotNull @Pattern(regexp = "(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }

    @PostMapping("/api/v1/planner/results")
    public List<Result> results(@Valid @RequestBody Request request) { return List.copyOf(forIds(request.ids()).values()); }

    /** APT 공고 ID만 골라 결과를 돌려준다. 결과가 하나도 공개되지 않은 공고도 빈 결과로 넣는다. */
    public Map<String, Result> forIds(Collection<String> ids) {
        Map<String, String[]> apt = new LinkedHashMap<>();
        for (String id : ids) {
            var matcher = APT_ID.matcher(id == null ? "" : id);
            if (matcher.matches()) apt.put(id, new String[]{matcher.group(1), matcher.group(2)});
        }
        if (apt.isEmpty()) return Map.of();
        List<String> houses = apt.values().stream().map(pair -> pair[0]).distinct().toList();
        String marks = String.join(",", Collections.nCopies(houses.size(), "?"));
        Map<String, List<String>> rates = new HashMap<>();
        jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO,CMPET_RATE FROM TB_APT_LTTOT_PBLANC_CMPET WHERE SUBSCRPT_RANK_CODE='1' AND HOUSE_MANAGE_NO IN (" + marks + ")",
            (RowCallbackHandler) r -> rates.computeIfAbsent("apt/" + r.getString(1) + "/" + r.getString(2), key -> new ArrayList<>()).add(r.getString(3)), houses.toArray());
        Map<String, List<String>> lows = new HashMap<>(), highs = new HashMap<>();
        jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO,LWET_SCORE,TOP_SCORE FROM TB_APT_LTTOT_PBLANC_SCORE WHERE HOUSE_MANAGE_NO IN (" + marks + ")", (RowCallbackHandler) r -> {
            String id = "apt/" + r.getString(1) + "/" + r.getString(2);
            lows.computeIfAbsent(id, key -> new ArrayList<>()).add(r.getString(3));
            highs.computeIfAbsent(id, key -> new ArrayList<>()).add(r.getString(4));
        }, houses.toArray());
        Map<String, Result> out = new LinkedHashMap<>();
        for (String id : apt.keySet()) {
            List<String> rate = rates.getOrDefault(id, List.of());
            var range = ShareScores.range(lows.getOrDefault(id, List.of()), highs.getOrDefault(id, List.of()));
            out.put(id, Result.of(id, !rate.isEmpty(), rate.isEmpty() ? null : RatePoll.topRate(rate), range.minimum(), range.maximum()));
        }
        return out;
    }
}
