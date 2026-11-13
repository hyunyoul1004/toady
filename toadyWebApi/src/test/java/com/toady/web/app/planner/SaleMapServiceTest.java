package com.toady.web.app.planner;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SaleMapServiceTest {
    @Test void regionNamesNarrowAnnouncementsBeforeLocating() {
        assertEquals(List.of("파주시"), SaleMapService.municipalityNames("경기 파주시"));
        assertEquals(List.of("수원시", "영통구"), SaleMapService.municipalityNames("경기 수원시 영통구"));
        assertEquals(List.of("제주시"), SaleMapService.municipalityNames("제주특별자치도 제주시"));
        assertEquals(List.of("세종"), SaleMapService.municipalityNames("세종특별자치시"));
        assertEquals(List.of(), SaleMapService.municipalityNames(null));
    }
}
