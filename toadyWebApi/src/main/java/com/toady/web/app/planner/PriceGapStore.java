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

    /** 화면·알림에 쓰는 요약. text는 "약 1.0억~1.4억 낮음" 또는 "비슷". */
    public record Gap(String id, int area, long supply, String scope, String basis, int basisCount, String confidence,
                      long q1, long q3, long diffLow, long diffHigh, int months, String dong, String tone, String text) { }
    /** READY인 요약만 돌려준다. 테이블이 없거나 조회에 실패하면 빈 결과(화면은 뱃지를 숨긴다). */
    public Map<String, Gap> forIds(Collection<String> ids) {
        List<String> list = ids.stream().filter(Objects::nonNull).distinct().limit(500).toList();
        if (list.isEmpty()) return Map.of();
        try {
            Map<String, Gap> out = new LinkedHashMap<>();
            for (int from = 0; from < list.size(); from += 200) {
                List<String> chunk = list.subList(from, Math.min(list.size(), from + 200));
                String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
                jdbc.query("SELECT * FROM TB_PRICE_GAP WHERE STATUS='READY' AND ANNOUNCEMENT_ID IN (" + marks + ")", (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    long low = r.getLong("Q1_MANWON") - r.getLong("SUPPLY_MANWON"), high = r.getLong("Q3_MANWON") - r.getLong("SUPPLY_MANWON");
                    String id = r.getString("ANNOUNCEMENT_ID");
                    out.put(id, new Gap(id, r.getInt("AREA"), r.getLong("SUPPLY_MANWON"), r.getString("SCOPE"), r.getString("BASIS"), r.getInt("BASIS_COUNT"),
                        r.getString("CONFIDENCE"), r.getLong("Q1_MANWON"), r.getLong("Q3_MANWON"), low, high, r.getInt("MONTHS"), r.getString("DONG"),
                        PriceGapRules.tone(low, high), PriceGapRules.text(low, high)));
                }, chunk.toArray());
            }
            return out;
        } catch (DataAccessException unavailable) { return Map.of(); }
    }

}
