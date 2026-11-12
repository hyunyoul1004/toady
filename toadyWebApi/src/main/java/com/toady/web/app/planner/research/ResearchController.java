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
    public ResearchController(NeighborhoodResearchService research) {this.research=research;}
    @GetMapping("/capabilities") public Map<String,Boolean> capabilities() {return Map.of("tradesEnabled",research.tradesEnabled());}
    @GetMapping("/transactions") public NeighborhoodResearchService.TradeResult trades(
            @RequestParam @Pattern(regexp="[0-9]{10}") String bcode,
            @RequestParam @NotBlank @Size(max=40) String dong,
            @RequestParam(required=false) @DecimalMin("10") @DecimalMax("400") java.math.BigDecimal area) {
        return research.trades(bcode,dong.trim(),area);
    }
    /** 분양가와 비교할 면적별 실거래 중간값(최근 6개월). areas: 전용면적(㎡) 최대 8개. */
    @GetMapping("/price-gap") public NeighborhoodResearchService.GapResult priceGap(
            @RequestParam @Pattern(regexp="[0-9]{10}") String bcode,
            @RequestParam @NotBlank @Size(max=40) String dong,
            @RequestParam @Size(min=1,max=8) List<@DecimalMin("10") @DecimalMax("400") BigDecimal> areas) {
        return research.priceGap(bcode,dong.trim(),areas);
    }
}
