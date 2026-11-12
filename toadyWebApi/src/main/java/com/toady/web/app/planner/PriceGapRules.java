package com.toady.web.app.planner;

import com.toady.web.app.planner.research.NeighborhoodResearchService;
import java.util.*;

/**
 * 분양가 vs 주변 시세 비교 규칙(화면 priceGapRules.js와 같은 기준). 금액 단위는 만원.
 * 준공 10년 이내 거래가 3건 이상이면 신축 기준, 1~3사분위 범위와 분양 최고가의 차이, 신뢰도(높음·보통·참고).
 */
public final class PriceGapRules {
    private PriceGapRules() { }
    static final int MIN_NEW_TRADES = 3;
    public record Row(int area, long supply, String scope, String basis, int basisCount, String confidence, long q1, long q3, long diffLow, long diffHigh) { }

    static String confidence(String basis, String scope, int basisCount) {
        if ("new".equals(basis) && "DONG".equals(scope) && basisCount >= 5) return "high";
        return basisCount >= 3 ? "medium" : "low";
    }
    /** units: 전용면적(반올림) → 그 면적 주택형의 최고 공급금액. */
    static List<Row> rows(Map<Integer, Long> supplies, List<NeighborhoodResearchService.AreaStat> stats) {
        List<Row> rows = new ArrayList<>();
        for (var stat : stats) {
            if (stat.count() <= 0) continue;
            int area = (int) Math.round(stat.area().doubleValue());
            Long supply = supplies.get(area);
            if (supply == null || supply <= 0) continue;
            boolean useNew = stat.newCount() >= MIN_NEW_TRADES;
            long median = useNew ? stat.newMedian() : stat.median();
            long q1 = useNew ? stat.newQ1() : stat.q1(), q3 = useNew ? stat.newQ3() : stat.q3();
            if (q1 <= 0) q1 = median; if (q3 <= 0) q3 = median;
            String basis = useNew ? "new" : "all";
            int basisCount = useNew ? stat.newCount() : stat.count();
            rows.add(new Row(area, supply, stat.scope(), basis, basisCount, confidence(basis, stat.scope(), basisCount), q1, q3, q1 - supply, q3 - supply));
        }
        return rows;
    }
    /** 신뢰도 높은 순 → 거래 많은 순 → 84㎡에 가까운 순. '참고'만 있으면 empty. */
    static Optional<Row> representative(List<Row> rows) {
        Map<String, Integer> rank = Map.of("high", 2, "medium", 1, "low", 0);
        return rows.stream().filter(row -> !"low".equals(row.confidence()))
            .min(Comparator.<Row>comparingInt(row -> -rank.get(row.confidence())).thenComparingInt(row -> -row.basisCount()).thenComparingInt(row -> Math.abs(row.area() - 84)));
    }
    /** 1천만원 단위 반올림: 12,345 → 1.2억, 4,800 → 5,000만원. */
    public static String roughEok(long manwon) {
        long value = Math.abs(Math.round(manwon / 1000.0) * 1000);
        if (value < 10000) return value == 0 ? "0원" : String.format("%,d만원", value);
        String eok = String.format("%.1f", value / 10000.0);
        return (eok.endsWith(".0") ? eok.substring(0, eok.length() - 2) : eok) + "억";
    }
    /** below(분양가가 낮음)·above(높음)·same(범위가 분양가를 가로지름). */
    public static String tone(long diffLow, long diffHigh) {
        long low = Math.min(diffLow, diffHigh), high = Math.max(diffLow, diffHigh);
        if (low < 0 && high > 0) return "same";
        return high > 0 ? "below" : "above";
    }
    /** "약 1.0억~1.4억 낮음" / "비슷". 화면 gapText와 같은 반올림. */
    public static String text(long diffLow, long diffHigh) {
        long low = Math.min(diffLow, diffHigh), high = Math.max(diffLow, diffHigh);
        String tone = tone(diffLow, diffHigh);
        if ("same".equals(tone)) return "비슷";
        boolean below = "below".equals(tone);
        String a = roughEok(Math.abs(below ? low : high)), b = roughEok(Math.abs(below ? high : low));
        return (a.equals(b) ? "약 " + a : "약 " + a + "~" + b) + (below ? " 낮음" : " 높음");
    }
}
