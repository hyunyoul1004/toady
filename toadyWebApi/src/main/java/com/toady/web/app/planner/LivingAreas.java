package com.toady.web.app.planner;

import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 생활권은 행정 경계와 다르다. 아래 주소·단지명 키워드는 검색 보조 기준으로만 사용한다. */
@RestController
public class LivingAreas {
    @org.springframework.beans.factory.annotation.Value("${toady.planner.reminders-enabled:false}") private boolean remindersEnabled;
    @GetMapping("/api/v1/planner/settings") public java.util.Map<String,Object> settings() { return java.util.Map.of("remindersEnabled",remindersEnabled,"maxFavorites",50); }
    @org.springframework.beans.factory.annotation.Autowired private PlannerSummary summaries;
    public record SummaryKeys(@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Size(max=50)
        List<@jakarta.validation.constraints.NotNull @jakarta.validation.constraints.Pattern(regexp="(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }
    @org.springframework.web.bind.annotation.PostMapping("/api/v1/planner/summaries")
    public List<PlannerSummary.Summary> summaries(@jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody SummaryKeys request) {
        return request.ids().stream().distinct().map(id -> {
            String[] parts=id.split("/");
            try { return summaries.get(parts[0],parts[1],parts[2]); }
            catch(jakarta.persistence.EntityNotFoundException missing) { return new PlannerSummary.Summary(id,"원본을 찾을 수 없는 공고","","","",null,null,null,"",List.of(),true); }
        }).toList();
    }
    public record Area(String label, List<String> aliases, List<String> terms, String description) { }
    public static final List<Area> ALL = List.of(
        new Area("운정", List.of("운정", "운정신도시", "운정역"), List.of("운정", "파주시 동패동", "파주시 목동동", "파주시 야당동", "파주시 와동동", "파주시 다율동", "파주시 당하동"), "파주 운정 생활권 · 주소/단지명 기준"),
        new Area("동탄", List.of("동탄", "동탄신도시", "동탄역"), List.of("동탄", "화성시 청계동", "화성시 영천동", "화성시 오산동", "화성시 산척동", "화성시 목동", "화성시 송동"), "화성 동탄 생활권 · 주소/단지명 기준"),
        new Area("다산", List.of("다산", "다산신도시", "다산역"), List.of("다산", "남양주시 다산동"), "남양주 다산 생활권"),
        new Area("검단", List.of("검단", "검단신도시"), List.of("검단", "서구 원당동", "서구 당하동", "서구 마전동", "서구 불로동"), "인천 검단 생활권 · 주소/단지명 기준"),
        new Area("위례", List.of("위례", "위례신도시"), List.of("위례", "송파구 장지동", "수정구 창곡동", "하남시 학암동"), "서울·성남·하남 위례 생활권"),
        new Area("광교", List.of("광교", "광교신도시", "광교역"), List.of("광교", "영통구 이의동", "영통구 하동", "수지구 상현동"), "수원·용인 광교 생활권")
    );
    @GetMapping("/api/v1/living-areas") public List<Area> areas() { return ALL; }
    public static List<String> terms(String keyword) {
        String text = keyword.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(a -> a.aliases().contains(text)).findFirst().map(Area::terms).orElse(List.of(text));
    }
}
