package com.toady.web.app.planner;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공급 주소에서 시·도와 시·군·구를 정하는 공통 규칙. 지역별 공개 페이지(/regions/...)·공고 페이지 빵부스러기·당첨가점 지역 묶음이 함께 쓴다.
 * 주소 첫 낱말이 정확한 시·도 이름(또는 줄임말)일 때만 인정하고, '광주시 오포읍'처럼 시·도 없이 시·군으로 시작하면 공급지역명(청약홈 분류)으로 시·도를 정한다.
 * 시·군·구는 시·도 다음 낱말이 그 시·도의 실제 시·군·구 이름(DISTRICTS)일 때만 쓴다(수원시 영통구는 수원시).
 * '검단신도시', '진주혁신도시', '평택고덕국제화지구'처럼 '시·구'로 끝나는 지구·신도시 이름은 시·군·구로 보지 않고 시·도에만 둔다.
 * '화성특례시'처럼 특례시 표기는 행정구역 이름(화성시)으로 바꿔 본다(수원·고양·용인·창원·화성 모두 같은 규칙).
 * 웹(toadyReactWeb features/explore/regionRules.js regionOf·districts.js)도 같은 규칙이고, 같은 주소 샘플(test/resources/region-samples.tsv)로 양쪽 결과를 대조한다.
 * '전남광주통합특별시 북구'처럼 통합특별시 표기는 다음 낱말(시·군·구)로 옛 시·도(광주광역시/전라남도)를 정한다.
 * 첫 낱말이 지구·사업 이름이고 괄호 안에 정식 주소가 있으면 괄호 안 주소로 다시 판정한다.
 * 단지별 예외 규칙은 두지 않는다.
 * 스프링 빈이 아닌 정적 도우미 클래스다. PublicAnnouncementIndex, PublicRegionPages, PublicAnnouncementPage, PublicAreaPage, ScoreBenchmarks 등이 부른다.
 */
public final class Regions {
    private Regions() { }

    /** name: 정식 이름(캘린더 검색 지역 목록과 같은 이름, 공개 주소에 쓴다), shortName: 줄임말. */
    public record Sido(String name, String shortName) {
        public String path() { return "/regions/" + segment(name); }
    }
    /** district: 시·군·구(없으면 null. 세종은 시·군·구가 없다). */
    public record Region(Sido sido, String district) {
        public String label() { return district == null ? sido.name() : sido.name() + " " + district; }
        public String shortLabel() { return district == null ? sido.shortName() : sido.shortName() + " " + district; }
        public String path() { return district == null ? sido.path() : sido.path() + "/" + segment(district); }
    }

    /** 웹 지역 판정 목록(districts.js SIDOS)과 같은 순서·이름. */
    public static final List<Sido> ALL = List.of(
        new Sido("서울특별시", "서울"), new Sido("부산광역시", "부산"), new Sido("대구광역시", "대구"), new Sido("인천광역시", "인천"),
        new Sido("광주광역시", "광주"), new Sido("대전광역시", "대전"), new Sido("울산광역시", "울산"), new Sido("세종특별자치시", "세종"),
        new Sido("경기도", "경기"), new Sido("강원특별자치도", "강원"), new Sido("충청북도", "충북"), new Sido("충청남도", "충남"),
        new Sido("전북특별자치도", "전북"), new Sido("전라남도", "전남"), new Sido("경상북도", "경북"), new Sido("경상남도", "경남"),
        new Sido("제주특별자치도", "제주"));
    /** 예전 이름·흔한 표기. '광주시'는 경기 광주시와 헷갈리므로 넣지 않는다. */
    private static final Map<String, String> ALIASES = Map.ofEntries(
        Map.entry("서울시", "서울"), Map.entry("부산시", "부산"), Map.entry("대구시", "대구"), Map.entry("인천시", "인천"), Map.entry("대전시", "대전"),
        Map.entry("울산시", "울산"), Map.entry("세종시", "세종"), Map.entry("강원도", "강원"), Map.entry("전라북도", "전북"), Map.entry("제주도", "제주"));

    /**
     * 시·도(줄임말) → 실제 시·군·구 이름(행정안전부 행정구역 기준, 일반구는 넣지 않는다). 시·군·구 판정은 이 목록 한 곳만 본다.
     * 편입·개편 전 주소로 나온 지난 공고도 묶이도록 예전 이름을 함께 둔다(대구·경북 군위군, 인천 중구·동구·서구와 2026년 개편 이름).
     */
    static final Map<String, Set<String>> DISTRICTS = Map.ofEntries(
        Map.entry("서울", names("종로구 중구 용산구 성동구 광진구 동대문구 중랑구 성북구 강북구 도봉구 노원구 은평구 서대문구 마포구 양천구 강서구 구로구 금천구 영등포구 동작구 관악구 서초구 강남구 송파구 강동구")),
        Map.entry("부산", names("중구 서구 동구 영도구 부산진구 동래구 남구 북구 해운대구 사하구 금정구 강서구 연제구 수영구 사상구 기장군")),
        Map.entry("대구", names("중구 동구 서구 남구 북구 수성구 달서구 달성군 군위군")),
        Map.entry("인천", names("중구 동구 미추홀구 연수구 남동구 부평구 계양구 서구 강화군 옹진군 영종구 제물포구 검단구 서해구")),
        Map.entry("광주", names("동구 서구 남구 북구 광산구")),
        Map.entry("대전", names("동구 중구 서구 유성구 대덕구")),
        Map.entry("울산", names("중구 남구 동구 북구 울주군")),
        Map.entry("세종", Set.of()),
        Map.entry("경기", names("수원시 성남시 의정부시 안양시 부천시 광명시 평택시 동두천시 안산시 고양시 과천시 구리시 남양주시 오산시 시흥시 군포시 의왕시 하남시 용인시 파주시 이천시 안성시 김포시 화성시 광주시 양주시 포천시 여주시 연천군 가평군 양평군")),
        Map.entry("강원", names("춘천시 원주시 강릉시 동해시 태백시 속초시 삼척시 홍천군 횡성군 영월군 평창군 정선군 철원군 화천군 양구군 인제군 고성군 양양군")),
        Map.entry("충북", names("청주시 충주시 제천시 보은군 옥천군 영동군 증평군 진천군 괴산군 음성군 단양군")),
        Map.entry("충남", names("천안시 공주시 보령시 아산시 서산시 논산시 계룡시 당진시 금산군 부여군 서천군 청양군 홍성군 예산군 태안군")),
        Map.entry("전북", names("전주시 군산시 익산시 정읍시 남원시 김제시 완주군 진안군 무주군 장수군 임실군 순창군 고창군 부안군")),
        Map.entry("전남", names("목포시 여수시 순천시 나주시 광양시 담양군 곡성군 구례군 고흥군 보성군 화순군 장흥군 강진군 해남군 영암군 무안군 함평군 영광군 장성군 완도군 진도군 신안군")),
        Map.entry("경북", names("포항시 경주시 김천시 안동시 구미시 영주시 영천시 상주시 문경시 경산시 군위군 의성군 청송군 영양군 영덕군 청도군 고령군 성주군 칠곡군 예천군 봉화군 울진군 울릉군")),
        Map.entry("경남", names("창원시 진주시 통영시 사천시 김해시 밀양시 거제시 양산시 의령군 함안군 창녕군 고성군 남해군 하동군 산청군 함양군 거창군 합천군")),
        Map.entry("제주", names("제주시 서귀포시")));
    private static Set<String> names(String spaced) { return Set.of(spaced.split(" ")); }

    /** '수원특례시' → '수원시'. 특례시는 행정구역상 시라서 시·군·구 이름은 '시'로 맞춘다. 다른 낱말은 그대로. */
    static String canonical(String token) {
        String text = token == null ? "" : token.trim();
        return text.length() > 3 && text.endsWith("특례시") ? text.substring(0, text.length() - 3) + "시" : text;
    }

    /** 그 시·도의 실제 시·군·구 이름(특례시 표기 포함)이면 정식 이름, 아니면 빈 값. 공개 주소의 시·군·구 부분 확인에도 쓴다. */
    public static Optional<String> district(Sido sido, String token) {
        if (sido == null || token == null) return Optional.empty();
        String text = canonical(token);
        return DISTRICTS.getOrDefault(sido.shortName(), Set.of()).contains(text) ? Optional.of(text) : Optional.empty();
    }

    /** 정확한 시·도 이름·줄임말·예전 이름이면 그 시·도. 주소 첫 낱말 판정에 쓴다. */
    public static Optional<Sido> strict(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String text = token.trim();
        String shortName = ALIASES.getOrDefault(text, text);
        return ALL.stream().filter(sido -> sido.name().equals(text) || sido.shortName().equals(shortName)).findFirst();
    }
    /** 공급지역명('경기', '서울특별시')처럼 시·도 분류 값은 앞부분으로도 찾는다. */
    // '충청북도'·'경상남도'처럼 줄임말로 시작하지 않는 이름은 앞부분으로 따로 맞춘다.
    static Optional<Sido> loose(String token) {
        Optional<Sido> exact = strict(token);
        if (exact.isPresent() || token == null) return exact;
        String text = token.trim();
        if (text.startsWith("충청북")) return strict("충북");
        if (text.startsWith("충청남")) return strict("충남");
        if (text.startsWith("전라북")) return strict("전북");
        if (text.startsWith("전라남")) return strict("전남");
        if (text.startsWith("경상북")) return strict("경북");
        if (text.startsWith("경상남")) return strict("경남");
        return text.length() < 2 ? Optional.empty() : ALL.stream().filter(sido -> text.startsWith(sido.shortName())).findFirst();
    }
    /** 공개 주소의 시·도 부분(정식 이름이나 줄임말). */
    public static Optional<Sido> bySegment(String segment) { return strict(segment); }

    /** 주소만으로 판정한다(공급지역명이 없을 때). */
    public static Optional<Region> of(String address) { return of(address, null); }
    /**
     * 주소와 공급지역명(청약홈 SUBSCRPT_AREA_CODE_NM)으로 시·도/시·군·구를 정한다. 확인할 수 없으면 비운다.
     * 순서: ① 주소 첫 낱말(시·도 또는 통합특별시) ② 첫 낱말이 지구·사업 이름이면 괄호 안 정식 주소
     * ('김포 풍무역세권 C5블록 (경기도 김포시 …)') ③ 공급지역명 + 첫 낱말을 시·군으로.
     */
    public static Optional<Region> of(String address, String regionName) {
        String text = address == null ? "" : address;
        String[] outside = words(text.replaceAll("\\([^)]*\\)", " "));
        Optional<Region> found = headed(outside, regionName);
        // 시·도까지만 정해졌으면('인천 검단신도시 AB13블록 (인천광역시 서구 …)') 괄호 안 정식 주소에서 같은 시·도의 시·군·구를 찾는다.
        if (found.isPresent() && found.get().district() != null) return found;
        Matcher inner = PARENTHESES.matcher(text);
        while (inner.find()) {
            Optional<Region> nested = headed(words(inner.group(1)), regionName);
            if (nested.isEmpty()) continue;
            if (found.isEmpty()) return nested;
            if (nested.get().sido().equals(found.get().sido()) && nested.get().district() != null) return nested;
        }
        if (found.isPresent()) return found;
        // 주소가 시·군으로 시작하면(예: '광주시 오포읍') 공급지역명으로 시·도를 정하고 첫 낱말을 시·군·구로 쓴다.
        String head = outside[0];
        // 통합특별시인데 옛 시·도를 못 정했으면 다른 시·도로 확정하지 않는다(공급지역명이 통합 대상이 아니면 미확인).
        if (!merged(head).isEmpty()) return Optional.empty();
        Optional<Sido> fromRegion = regionName == null ? Optional.empty() : loose(regionName.trim().split("\\s+")[0]);
        return fromRegion.map(value -> new Region(value, district(value, head).orElse(null)));
    }
    private static final Pattern PARENTHESES = Pattern.compile("\\(([^)]*)\\)");
    private static String[] words(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.isEmpty() ? new String[] { "" } : trimmed.split("\\s+");
    }
    /** 첫 낱말이 시·도(또는 통합특별시)인 주소만 판정한다. 아니면 빈 값. */
    private static Optional<Region> headed(String[] tokens, String regionName) {
        String head = tokens[0];
        String next = tokens.length > 1 ? tokens[1] : null;
        Optional<Sido> sido = strict(head);
        if (sido.isPresent()) return Optional.of(new Region(sido.get(), district(sido.get(), next).orElse(null)));
        List<Sido> merged = merged(head);
        if (merged.isEmpty()) return Optional.empty();
        // 통합특별시: 다음 낱말이 어느 옛 시·도의 시·군·구인지로 정한다(광주 자치구와 전남 시·군은 이름이 겹치지 않는다).
        for (Sido candidate : merged) {
            Optional<String> name = district(candidate, next);
            if (name.isPresent()) return Optional.of(new Region(candidate, name.get()));
        }
        Optional<Sido> fromRegion = regionName == null ? Optional.empty() : loose(regionName.trim().split("\\s+")[0]);
        return fromRegion.filter(merged::contains).map(value -> new Region(value, null));
    }
    /**
     * '전남광주통합특별시'처럼 시·도 줄임말을 이어 붙인 통합특별시 이름 → 옛 시·도 목록(전남, 광주). 아니면 빈 목록.
     * 지역별 청약·검색은 옛 시·도 단위 그대로 쓴다(공급지역명·지난 공고와 묶이도록).
     */
    public static List<Sido> merged(String token) {
        String text = token == null ? "" : token.trim();
        if (!text.endsWith(MERGED_SUFFIX)) return List.of();
        String rest = text.substring(0, text.length() - MERGED_SUFFIX.length());
        List<Sido> found = new ArrayList<>();
        while (!rest.isEmpty()) {
            String part = rest;
            Optional<Sido> next = ALL.stream().filter(sido -> part.startsWith(sido.shortName())).findFirst();
            if (next.isEmpty()) return List.of();
            found.add(next.get());
            rest = rest.substring(next.get().shortName().length());
        }
        return found.size() >= 2 ? List.copyOf(found) : List.of();
    }
    private static final String MERGED_SUFFIX = "통합특별시";
    /** 주소 경로 한 조각(UTF-8 퍼센트 인코딩, 공백은 %20). */
    static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
}
