package com.toady.web.app.planner;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.*;

/** TB_PRICE_GAP 조회. 계산 작업(PriceGapSnapshots)과 목록·공개 페이지·알림이 함께 쓴다. */
@Component
public class PriceGapStore {
    private final JdbcTemplate jdbc;
    public PriceGapStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    static final int SHOW_DAYS = 7;

    /** 화면·알림에 쓰는 요약. text는 "약 1.0억~1.4억 낮음" 또는 "비슷". */
    public record Gap(String id, int area, long supply, String scope, String basis, int basisCount, String confidence,
                      long q1, long q3, long diffLow, long diffHigh, int months, String dong, String tone, String text, String computedDate) {
        /** 저장할 때의 분양가가 지금 공고의 같은 면적 최고 공급금액과 같은지(정정 공고로 금액이 바뀌면 다시 계산할 때까지 숨긴다). */
        public boolean matches(Map<Integer, Long> supplies) { return Objects.equals(supplies.get(area), supply); }
    }
    /** READY인 요약만 돌려준다. 테이블이 없거나 조회에 실패하면 빈 결과(화면은 뱃지를 숨긴다). */
    public Map<String, Gap> forIds(Collection<String> ids) {
        List<String> list = ids.stream().filter(Objects::nonNull).distinct().limit(500).toList();
        if (list.isEmpty()) return Map.of();
        try {
            Map<String, Gap> out = new LinkedHashMap<>();
            for (int from = 0; from < list.size(); from += 200) {
                List<String> chunk = list.subList(from, Math.min(list.size(), from + 200));
                String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
                // 계산한 지 7일이 지난 값은 보여주지 않는다(마감 뒤 다시 계산되지 않는 공고의 옛 값을 '최근' 값처럼 보이지 않게).
                jdbc.query("SELECT * FROM TB_PRICE_GAP WHERE STATUS='READY' AND COMPUTED_DT >= ? AND ANNOUNCEMENT_ID IN (" + marks + ")", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    long low = r.getLong("Q1_MANWON") - r.getLong("SUPPLY_MANWON"), high = r.getLong("Q3_MANWON") - r.getLong("SUPPLY_MANWON");
                    String id = r.getString("ANNOUNCEMENT_ID");
                    out.put(id, new Gap(id, r.getInt("AREA"), r.getLong("SUPPLY_MANWON"), r.getString("SCOPE"), r.getString("BASIS"), r.getInt("BASIS_COUNT"),
                        r.getString("CONFIDENCE"), r.getLong("Q1_MANWON"), r.getLong("Q3_MANWON"), low, high, r.getInt("MONTHS"), r.getString("DONG"),
                        PriceGapRules.tone(low, high), PriceGapRules.text(low, high), r.getTimestamp("COMPUTED_DT").toLocalDateTime().toLocalDate().toString()));
                }, java.util.stream.Stream.concat(java.util.stream.Stream.of((Object) java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(SHOW_DAYS))), chunk.stream()).toArray());
            }
            // 정정 공고로 분양가가 바뀌었으면 다시 계산할 때까지 숨긴다. 목록·공개 페이지·나의 청약·알림이 모두 이 경로를 쓴다.
            Map<String, Map<Integer, Long>> current = currentSupplies(out.keySet());
            out.values().removeIf(gap -> !gap.matches(current.getOrDefault(gap.id(), Map.of())));
            return out;
        } catch (DataAccessException unavailable) { return Map.of(); }
    }

    static final Map<String, String> MODEL_TABLES = Map.of("apt", "TB_APT_LTTOT_PBLANC_MDL", "remaining", "TB_REMNDR_LTTOT_PBLANC_MDL", "optional", "TB_OPT_LTTOT_PBLANC_MDL");
    /** 공고별 지금의 '전용면적(반올림) → 최고 공급금액'. 계산할 때와 같은 규칙(PriceGapSnapshots.supplies, 10㎡ 미만 제외)이다. */
    Map<String, Map<Integer, Long>> currentSupplies(Collection<String> ids) {
        Map<String, List<String[]>> byType = new HashMap<>();
        for (String id : ids) {
            String[] parts = id.split("/");
            if (parts.length == 3 && MODEL_TABLES.containsKey(parts[0])) byType.computeIfAbsent(parts[0], key -> new ArrayList<>()).add(parts);
        }
        Map<String, Map<Integer, Long>> out = new HashMap<>();
        byType.forEach((type, keys) -> {
            for (int from = 0; from < keys.size(); from += 200) {
                List<String[]> chunk = keys.subList(from, Math.min(keys.size(), from + 200));
                String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
                jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_TY,LTTOT_TOP_AMOUNT FROM " + MODEL_TABLES.get(type) + " WHERE HOUSE_MANAGE_NO IN (" + marks + ")",
                    (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                        var area = EligibilityFeed.area(r.getString(3));
                        Integer amount = EligibilityFeed.number(r.getString(4));
                        if (area == null || area.doubleValue() < 10 || amount == null || amount <= 0) return;
                        out.computeIfAbsent(type + "/" + r.getString(1) + "/" + r.getString(2), key -> new HashMap<>())
                            .merge((int) Math.round(area.doubleValue()), amount.longValue(), Math::max);
                    }, chunk.stream().map(key -> (Object) key[1]).toArray());
            }
        });
        return out;
    }

}
