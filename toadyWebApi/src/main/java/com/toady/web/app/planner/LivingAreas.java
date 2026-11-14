package com.toady.web.app.planner;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 생활권은 행정 경계와 다르다. 아래 주소·단지명 키워드는 검색 보조 기준으로만 사용한다. */
@RestController
public class LivingAreas {
    @Value("${toady.planner.reminders-enabled:false}") private boolean remindersEnabled;
    @GetMapping("/api/v1/planner/settings") public Map<String,Object> settings() { return Map.of("remindersEnabled",remindersEnabled,"maxFavorites",50); }
    @Autowired private PlannerSummary summaries;
    public record SummaryKeys(@NotNull @Size(max=50)
        List<@NotNull @Pattern(regexp="(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }
    @PostMapping("/api/v1/planner/summaries")
    public List<PlannerSummary.Summary> summaries(@Valid @RequestBody SummaryKeys request) {
        return request.ids().stream().distinct().map(id -> {
            String[] parts=id.split("/");
            try { return summaries.get(parts[0],parts[1],parts[2]); }
            catch(EntityNotFoundException missing) { return PlannerSummary.Summary.missing(id); }
        }).toList();
    }
    /**
     * slug는 생활권 공개 페이지 주소(/areas/{slug})로 쓰며 바꾸면 기존 검색 결과 링크가 끊긴다.
     * scope는 생활권이 속한 시·군 이름이다. '검단'처럼 시·군이 없는 짧은 이름(공백 없는 term)은 scope도 함께 있어야 인정한다.
     * 예: 대구 북구 검단동, 경북 고령군 다산면이 인천 검단·남양주 다산으로 잘못 분류되지 않게 한다.
     */
    public record Area(String slug, String label, List<String> aliases, List<String> terms, String description, List<String> scope) { }
    public static final List<Area> ALL = List.of(
        new Area("unjeong", "운정", List.of("운정", "운정신도시", "운정역"), List.of("운정", "파주시 동패동", "파주시 목동동", "파주시 야당동", "파주시 와동동", "파주시 다율동", "파주시 당하동"), "파주 운정 생활권 · 주소/단지명 기준", List.of("파주")),
        new Area("dongtan", "동탄", List.of("동탄", "동탄신도시", "동탄역"), List.of("동탄", "화성시 청계동", "화성시 영천동", "화성시 오산동", "화성시 산척동", "화성시 목동", "화성시 송동"), "화성 동탄 생활권 · 주소/단지명 기준", List.of("화성")),
        new Area("dasan", "다산", List.of("다산", "다산신도시", "다산역"), List.of("다산", "남양주시 다산동"), "남양주 다산 생활권", List.of("남양주")),
        new Area("geomdan", "검단", List.of("검단", "검단신도시"), List.of("검단", "서구 원당동", "서구 당하동", "서구 마전동", "서구 불로동"), "인천 검단 생활권 · 주소/단지명 기준", List.of("인천")),
        new Area("wirye", "위례", List.of("위례", "위례신도시"), List.of("위례", "송파구 장지동", "수정구 창곡동", "하남시 학암동"), "서울·성남·하남 위례 생활권", List.of("송파", "성남", "하남")),
        new Area("gwanggyo", "광교", List.of("광교", "광교신도시", "광교역"), List.of("광교", "영통구 이의동", "영통구 하동", "수지구 상현동"), "수원·용인 광교 생활권", List.of("수원", "용인"))
    );
    /** 검색 조건 한 개: term이 주소·단지명에 있고, scope가 있으면 그중 하나도 함께 있어야 한다. */
    public record Matcher(String term, List<String> scope) {
        boolean matches(String text) {
            return text.contains(term.toLowerCase(Locale.ROOT)) && (scope.isEmpty() || scope.stream().anyMatch(value -> text.contains(value.toLowerCase(Locale.ROOT))));
        }
    }
    /** 짧은 생활권 이름(공백 없음)에만 시·군 조건을 붙인다. '서구 원당동'처럼 구·동이 붙은 term은 그대로 쓴다. */
    static List<Matcher> matchers(Area area) {
        return area.terms().stream().map(term -> new Matcher(term, term.contains(" ") ? List.of() : area.scope())).toList();
    }
    @GetMapping("/api/v1/living-areas") public List<Area> areas() { return ALL; }
    public static Optional<Area> bySlug(String slug) { return ALL.stream().filter(area -> area.slug().equals(slug)).findFirst(); }
    /** 검색과 같은 기준(주소·단지명에 생활권 보조 키워드 포함)으로 공고의 생활권을 판정한다. */
    public static Optional<Area> match(String... texts) {
        String text = String.join(" ", Arrays.stream(texts).map(value -> value == null ? "" : value).toList()).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return ALL.stream().filter(area -> matchers(area).stream().anyMatch(matcher -> matcher.matches(text))).findFirst();
    }
    /** 검색어가 생활권 별칭이면 그 생활권의 조건들, 아니면 검색어 하나(시·군 조건 없음). */
    public static List<Matcher> matchers(String keyword) {
        String text = keyword.trim().toLowerCase(Locale.ROOT);
        return ALL.stream().filter(a -> a.aliases().contains(text)).findFirst().map(LivingAreas::matchers).orElse(List.of(new Matcher(text, List.of())));
    }
}
