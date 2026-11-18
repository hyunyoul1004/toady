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

/**
 * 생활권(운정·동탄 같은 신도시 이름) 목록과 판정 규칙, 그리고 나의 청약 화면이 쓰는 작은 API 몇 개를 함께 둔 컨트롤러.
 * 생활권은 행정 경계와 다르다. 아래 주소·단지명 키워드는 검색 보조 기준으로만 사용한다.
 * 공고 검색(생활권 검색), PublicAreaPage, AnnouncementSitemap, ContentStore(TB_LIVING_AREA 반영)가 쓴다.
 */
// @RestController: 메서드 반환값을 JSON으로 응답한다.
@RestController
public class LivingAreas {
    // @Value: 설정값 toady.planner.reminders-enabled(없으면 false)를 필드에 넣는다. 메일 알림 기능을 켰는지 화면에 알려 줄 때 쓴다.
    @Value("${toady.planner.reminders-enabled:false}") private boolean remindersEnabled;
    // @GetMapping: GET 요청을 이 메서드에 연결한다. 나의 청약 화면 설정(알림 사용 여부, 최대 저장 수).
    @GetMapping("/api/v1/planner/settings") public Map<String,Object> settings() { return Map.of("remindersEnabled",remindersEnabled,"maxFavorites",50); }
    // @Autowired: 스프링이 PlannerSummary 빈을 필드에 직접 넣는다(필드 주입).
    @Autowired private PlannerSummary summaries;
    /** 요약을 받을 공고 키 목록(최대 50개). 로그인 없이 브라우저에 저장한 관심 공고도 같은 요약을 받을 수 있다. */
    public record SummaryKeys(@NotNull @Size(max=50)
        List<@NotNull @Pattern(regexp="(apt|urban|remaining|public-rental|optional)/[a-zA-Z0-9_-]{1,40}/[a-zA-Z0-9_-]{1,40}") String> ids) { }
    // @PostMapping: POST 본문(JSON)으로 키 목록을 받는다. @Valid로 SummaryKeys의 @Size·@Pattern 규칙을 검사한다.
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
    /** 코드에 있는 기본 생활권. DB(TB_LIVING_AREA, database.sql 27번)가 비어 있거나 없으면 이 목록을 쓴다. */
    public static final List<Area> BUILT_IN = List.of(
        new Area("unjeong", "운정", List.of("운정", "운정신도시", "운정역"), List.of("운정", "파주시 동패동", "파주시 목동동", "파주시 야당동", "파주시 와동동", "파주시 다율동", "파주시 당하동"), "파주 운정 생활권 · 주소/단지명 기준", List.of("파주")),
        new Area("dongtan", "동탄", List.of("동탄", "동탄신도시", "동탄역"), List.of("동탄", "화성시 청계동", "화성시 영천동", "화성시 오산동", "화성시 산척동", "화성시 목동", "화성시 송동"), "화성 동탄 생활권 · 주소/단지명 기준", List.of("화성")),
        new Area("dasan", "다산", List.of("다산", "다산신도시", "다산역"), List.of("다산", "남양주시 다산동"), "남양주 다산 생활권", List.of("남양주")),
        new Area("geomdan", "검단", List.of("검단", "검단신도시"), List.of("검단", "서구 원당동", "서구 당하동", "서구 마전동", "서구 불로동"), "인천 검단 생활권 · 주소/단지명 기준", List.of("인천")),
        new Area("wirye", "위례", List.of("위례", "위례신도시"), List.of("위례", "송파구 장지동", "수정구 창곡동", "하남시 학암동"), "서울·성남·하남 위례 생활권", List.of("송파", "성남", "하남")),
        new Area("gwanggyo", "광교", List.of("광교", "광교신도시", "광교역"), List.of("광교", "영통구 이의동", "영통구 하동", "수지구 상현동"), "수원·용인 광교 생활권", List.of("수원", "용인"))
    );
    /** 지금 쓰는 생활권 목록. ContentStore가 1분마다 DB 값으로 바꾼다(관리자 화면에서 추가·수정). */
    private static volatile List<Area> current = BUILT_IN;
    public static List<Area> all() { return current; }
    /** DB에서 읽은 목록으로 바꾼다. 비어 있으면 기본 목록으로 되돌린다. */
    public static void replace(List<Area> areas) { current = areas == null || areas.isEmpty() ? BUILT_IN : List.copyOf(areas); }
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
    // 화면 검색창의 생활권 칩 목록.
    @GetMapping("/api/v1/living-areas") public List<Area> areas() { return all(); }
    /** 공개 페이지 주소의 slug로 생활권을 찾는다. */
    public static Optional<Area> bySlug(String slug) { return all().stream().filter(area -> area.slug().equals(slug)).findFirst(); }
    /** 검색과 같은 기준(주소·단지명에 생활권 보조 키워드 포함)으로 공고의 생활권을 판정한다. */
    public static Optional<Area> match(String... texts) {
        String text = String.join(" ", Arrays.stream(texts).map(value -> value == null ? "" : value).toList()).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return all().stream().filter(area -> matchers(area).stream().anyMatch(matcher -> matcher.matches(text))).findFirst();
    }
    /** 검색어가 생활권 별칭이면 그 생활권의 조건들, 아니면 검색어 하나(시·군 조건 없음). */
    public static List<Matcher> matchers(String keyword) {
        String text = keyword.trim().toLowerCase(Locale.ROOT);
        return all().stream().filter(a -> a.aliases().contains(text)).findFirst().map(LivingAreas::matchers).orElse(List.of(new Matcher(text, List.of())));
    }
}
