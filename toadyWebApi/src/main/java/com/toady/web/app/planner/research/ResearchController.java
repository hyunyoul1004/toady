package com.toady.web.app.planner.research;

import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
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
}
