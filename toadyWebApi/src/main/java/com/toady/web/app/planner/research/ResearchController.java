package com.toady.web.app.planner.research;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@Validated
@RequestMapping("/api/v1/research")
public class ResearchController {
    private final NeighborhoodResearchService research;
    private final ComplexPriceService complexes;
    public ResearchController(NeighborhoodResearchService research,ComplexPriceService complexes) {this.research=research;this.complexes=complexes;}
    @GetMapping("/capabilities") public Map<String,Boolean> capabilities() {return Map.of("tradesEnabled",research.tradesEnabled());}
    @GetMapping("/transactions") public NeighborhoodResearchService.TradeResult trades(
            @RequestParam @Pattern(regexp="[0-9]{10}") String bcode,
            @RequestParam @NotBlank @Size(max=40) String dong,
            @RequestParam(required=false) @DecimalMin("10") @DecimalMax("400") BigDecimal area) {
        return research.trades(bcode,dong.trim(),area);
    }
    /**
     * 위치 탭 지도: 같은 시·군·구의 최근 매매 실거래를 단지별로 묶어 좌표와 함께 준다. months: 3·6·12.
     * region: 카카오 주소 검색 정확도를 높이는 시·도 + 시·군·구 이름(예: "경기 파주시", "경기 수원시 영통구").
     */
    @GetMapping("/complexes") public ComplexPriceService.Result complexes(
            @RequestParam @Pattern(regexp="[0-9]{10}") String bcode,
            @RequestParam(required=false) @Pattern(regexp="[가-힣]{2,10}( [가-힣]{1,10}){0,2}") String region,
            @RequestParam(defaultValue="6") @Min(3) @Max(12) int months) {
        return complexes.complexes(bcode,region,months);
    }
    /** 분양가와 비교할 면적별 실거래 중간값(최근 6개월). areas: 전용면적(㎡) 최대 8개. */
    @GetMapping("/price-gap") public NeighborhoodResearchService.GapResult priceGap(
            @RequestParam @Pattern(regexp="[0-9]{10}") String bcode,
            @RequestParam @NotBlank @Size(max=40) String dong,
            @RequestParam @Size(min=1,max=8) List<@DecimalMin("10") @DecimalMax("400") BigDecimal> areas) {
        return research.priceGap(bcode,dong.trim(),areas);
    }
}
