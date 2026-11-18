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
 * 읽는 테이블: TB_APT_LTTOT_PBLANC_CMPET(경쟁률), TB_APT_LTTOT_PBLANC_SCORE(당첨가점). 둘 다 스케줄러가 청약홈에서 모아 둔다.
 * 나의 청약 화면, 공고 공개 페이지(주택형별 결과 표), 푸시 결과 알림이 쓴다.
 */
// @RestController: JSON 응답 컨트롤러. @Validated: 검증 애너테이션을 켠다.
@RestController
@Validated
public class AnnouncementResults {
    static final java.util.regex.Pattern APT_ID = java.util.regex.Pattern.compile("apt/([a-zA-Z0-9_-]{1,40})/([a-zA-Z0-9_-]{1,40})");
    private final JdbcTemplate jdbc;
    public AnnouncementResults(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    /** 운영자가 숨긴 공고. 공개 API가 없는 것처럼 뺀다. 빈이 없으면(단위 테스트) 아무것도 숨기지 않는다. */
    private HiddenAnnouncements hidden;
    // @Autowired(required=false) 메서드 주입: 생성자를 늘리지 않고 숨김 확인 빈이 있을 때만 넣는다.
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void useHidden(HiddenAnnouncements hidden) { this.hidden = hidden; }

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

    // @PostMapping + @RequestBody: 본문 JSON의 ids(최대 50개)를 받는다. @Valid로 형식을 검사한다.
    @PostMapping("/api/v1/planner/results")
    // 숨긴 공고는 결과 목록에서 뺀다(없는 공고와 같은 모양). 푸시 등 내부 호출(forIds)은 각자 숨김을 거른다.
    public List<Result> results(@Valid @RequestBody Request request) {
        List<String> ids = hidden == null ? request.ids() : request.ids().stream().filter(id -> !hidden.hiddenId(id)).toList();
        return List.copyOf(forIds(ids).values());
    }

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

    /**
     * 주택형별 1순위 결과 한 줄. supply: 공급세대수(공개 값 그대로), requests: 1순위 거주지역별 접수건수 합,
     * topRate: 1순위 거주지역 중 숫자로 공개된 최고 경쟁률(전부 미달 표기면 null), scoreLow/scoreHigh: 공개 당첨가점(없으면 빈 문자열).
     */
    public record HouseTypeResult(String houseType, String supply, Long requests, Double topRate, boolean rated, String scoreLow, String scoreHigh) { }

    /** APT 공고의 주택형별 1순위 경쟁률·당첨가점(모델번호 순). 공개 전이면 빈 목록. */
    public List<HouseTypeResult> houseTypes(String house, String notice) {
        Map<String, String> names = new LinkedHashMap<>(), supplies = new HashMap<>();
        Map<String, List<String>> rates = new HashMap<>();
        Map<String, Long> requests = new HashMap<>();
        jdbc.query("SELECT MODEL_NO,HOUSE_TY,SUPLY_HSHLDCO,REQ_CNT,CMPET_RATE FROM TB_APT_LTTOT_PBLANC_CMPET WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? AND SUBSCRPT_RANK_CODE='1' ORDER BY MODEL_NO,RESIDE_SECD",
            (RowCallbackHandler) r -> {
                String model = r.getString(1);
                names.putIfAbsent(model, r.getString(2) == null ? model : r.getString(2).trim());
                if (r.getString(3) != null && !r.getString(3).isBlank()) supplies.putIfAbsent(model, r.getString(3).trim());
                rates.computeIfAbsent(model, key -> new ArrayList<>()).add(r.getString(5));
                Long count = count(r.getString(4));
                if (count != null) requests.merge(model, count, Long::sum);
            }, house, notice);
        Map<String, List<String>> lows = new HashMap<>(), highs = new HashMap<>();
        jdbc.query("SELECT MODEL_NO,HOUSE_TY,LWET_SCORE,TOP_SCORE FROM TB_APT_LTTOT_PBLANC_SCORE WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? ORDER BY MODEL_NO,RESIDE_SECD",
            (RowCallbackHandler) r -> {
                String model = r.getString(1);
                names.putIfAbsent(model, r.getString(2) == null ? model : r.getString(2).trim());
                lows.computeIfAbsent(model, key -> new ArrayList<>()).add(r.getString(3));
                highs.computeIfAbsent(model, key -> new ArrayList<>()).add(r.getString(4));
            }, house, notice);
        List<HouseTypeResult> out = new ArrayList<>();
        names.keySet().stream().sorted().forEach(model -> {
            var range = ShareScores.range(lows.getOrDefault(model, List.of()), highs.getOrDefault(model, List.of()));
            List<String> rate = rates.getOrDefault(model, List.of());
            out.add(new HouseTypeResult(names.get(model), supplies.getOrDefault(model, ""), requests.get(model), rate.isEmpty() ? null : RatePoll.topRate(rate), !rate.isEmpty(), range.minimum(), range.maximum()));
        });
        return out;
    }
    // '1,234' 같은 접수 건수를 숫자로. 숫자가 아니면 null.
    private static Long count(String value) {
        String text = value == null ? "" : value.replace(",", "").trim();
        return text.matches("[0-9]{1,9}") ? Long.parseLong(text) : null;
    }
}
