package com.toady.web.app.planner.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.Regions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 공급 주소를 카카오 로컬 REST API로 확인해 법정동 코드를 얻는다. 화면(resolveLocation.js)과 같은 규칙만 쓴다.
 * - 지번(동·리·로·길 + 번지)이 있는 후보만 조회하고, 응답의 지번이 요청과 같고 시·도·시·군·구가 공고 주소와 같을 때만 확정한다.
 * - 지구·블록 이름만 있는 주소는 확정하지 않는다(엉뚱한 위치에 확정하지 않는다). 단지별 예외 규칙은 두지 않는다.
 * - 운영자가 관리자에서 확인한 주소(TB_ANNOUNCEMENT_OVERRIDE.LOCATION_ADDRESS)가 있으면 먼저 같은 검증으로 확인한다(ConfirmedAddresses가 넘긴다).
 * 카카오 주소 검색 API(dapi.kakao.com/v2/local/search/address.json)를 부른다. SaleMapService(분양 지도 위치 확인)와 PriceGapSnapshots(주변 시세 미리 계산)가 쓴다.
 */
// @Component: 스프링 빈으로 등록한다.
@Component
public class AddressLocator {
    /** lat·lng: 확인한 지번의 좌표(지도 표시용). 좌표가 없으면 NaN. */
    public record Location(String bcode, String dong, String address, double lat, double lng) { }
    private final ResearchTransport transport;
    private final String key;
    private final ObjectMapper json = new ObjectMapper();

    // @Value: 카카오 REST 키를 설정에서 읽는다. 비어 있으면 enabled()가 false라 조회하지 않는다.
    public AddressLocator(ResearchTransport transport, @Value("${toady.research.kakao-rest-key:}") String key) {
        this.transport = transport; this.key = key == null ? "" : key.trim();
    }
    public boolean enabled() { return !key.isBlank(); }

    /** 정확한 지번 위치를 찾으면 돌려준다. 찾지 못하면 empty(후보 위치로 확정하지 않는다). 제공처 오류는 예외. */
    public Optional<Location> locate(String rawAddress) throws Exception { return locate(rawAddress, null); }

    /**
     * 화면 resolveLocation과 같은 순서로 위치를 확정한다: ① 운영자 확인 주소(관리자 '위치 확인', 후보 2개까지) → ② 공급 주소의 지번(후보 3개까지).
     * 운영자 주소도 공급 주소와 같은 검증(지번 일치)을 거치고, 찾은 위치가 공급 주소의 시·도·시·군·구와 같을 때만 쓴다(공급 주소가 비면 운영자 주소 기준).
     * 통과하지 못하면 공급 주소 판정으로 그대로 이어진다. 둘 다 못 찾으면 empty(미확인)다.
     */
    public Optional<Location> locate(String supplyAddress, String confirmedAddress) throws Exception {
        if (!enabled()) return Optional.empty();
        String supply = normalize(supplyAddress), confirmed = normalize(confirmedAddress);
        if (!confirmed.isEmpty()) {
            var found = find(confirmed, supply.isEmpty() ? null : supply, 2);
            if (found.isPresent()) return found;
        }
        return find(supply, null, 3);
    }
    /** address의 지번 후보를 limit개까지 조회해 지번이 같은 첫 결과. region이 있으면 그 주소와 같은 시·도·시·군·구여야 한다. */
    private Optional<Location> find(String address, String region, int limit) throws Exception {
        for (String candidate : parcelCandidates(address).stream().limit(limit).toList()) {
            URI uri = URI.create("https://dapi.kakao.com/v2/local/search/address.json?size=10&query=" + URLEncoder.encode(candidate, StandardCharsets.UTF_8));
            JsonNode body = json.readTree(transport.get(uri, Map.of("Authorization", "KakaoAK " + key)));
            for (JsonNode row : body.path("documents")) {
                if (!precise(row, address, candidate)) continue;
                String found = row.path("address").path("address_name").asText("");
                if (region != null && !sameRegion(region, found.isBlank() ? row.path("address_name").asText("") : found)) continue;
                String bcode = row.path("address").path("b_code").asText(""), dong = row.path("address").path("region_3depth_name").asText("");
                if (bcode.matches("\\d{10}") && !dong.isBlank()) return Optional.of(new Location(bcode, dong, row.path("address").path("address_name").asText(candidate),
                    row.path("y").asDouble(Double.NaN), row.path("x").asDouble(Double.NaN)));
            }
        }
        return Optional.empty();
    }

    static String normalize(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim(); }
    private static final String[][] PROVINCES = {{"서울","서울"},{"부산","부산"},{"대구","대구"},{"인천","인천"},{"광주","광주"},{"대전","대전"},{"울산","울산"},{"세종","세종"},{"경기","경기"},{"강원","강원"},
        {"충청북","충북"},{"충북","충북"},{"충청남","충남"},{"충남","충남"},{"전북","전북"},{"전라북","전북"},{"전라남","전남"},{"전남","전남"},{"경상북","경북"},{"경북","경북"},{"경상남","경남"},{"경남","경남"},{"제주","제주"}};
    /** 주소 첫 단어의 시·도를 짧은 이름으로('경기도' → '경기', '전남광주통합특별시 북구' → '광주'). 하나로 정하지 못하면 null. */
    public static String province(String address) {
        Set<String> found = provinces(address);
        return found.size() == 1 ? found.iterator().next() : null;
    }
    /**
     * 주소 첫 단어의 시·도 후보(짧은 이름). 보통은 하나이고, 모르면 빈 집합.
     * '전남광주통합특별시'처럼 통합특별시 표기는 Regions(지역 판정 기준)와 같게 다음 낱말로 옛 시·도를 정하고('… 북구' → 광주, '… 목포시' → 전남),
     * 정하지 못하면 통합 대상 시·도 모두(전남, 광주)를 후보로 둔다. 다른 시·도로 확정하지 않는다.
     */
    public static Set<String> provinces(String address) {
        String text = normalize(address), first = text.split(" ")[0];
        List<Regions.Sido> merged = Regions.merged(first);
        if (!merged.isEmpty()) {
            Optional<Regions.Region> region = Regions.of(text).filter(found -> merged.contains(found.sido()));
            if (region.isPresent()) return Set.of(region.get().sido().shortName());
            Set<String> group = new LinkedHashSet<>();
            merged.forEach(sido -> group.add(sido.shortName()));
            return Collections.unmodifiableSet(group);
        }
        for (String[] pair : PROVINCES) if (first.startsWith(pair[0])) return Set.of(pair[1]);
        return Set.of();
    }
    /** 두 주소의 시·도 후보가 겹치는지(통합특별시를 옛 시·도로 풀어 비교한다). 어느 한쪽이라도 모르면 false. */
    public static boolean sameProvince(String address, String other) {
        Set<String> left = provinces(address), right = provinces(other);
        return !left.isEmpty() && !right.isEmpty() && !Collections.disjoint(left, right);
    }
    /** 주소에서 시·군·구 낱말을 최대 2개('수원시 영통구'). '검단신도시'처럼 지구·도시로 끝나는 이름은 뺀다. */
    static List<String> municipality(String address) {
        List<String> tokens = new ArrayList<>(Arrays.asList(normalize(address).split(" ")));
        if (!provinces(address).isEmpty() && !tokens.isEmpty()) tokens.remove(0);
        return tokens.stream().filter(token -> token.matches("^[가-힣]+[시군구]$") && !token.matches(".*(지구|특구|도시)$")).limit(2).toList();
    }
    /** 카카오가 찾은 주소가 공고 주소와 같은 시·도·시·군·구인지. 이름이 같은 다른 지역 동을 확정하지 않게 한다. */
    static boolean sameRegion(String address, String found) {
        boolean knownProvince = !provinces(address).isEmpty(); List<String> cities = municipality(address);
        if (!knownProvince && cities.isEmpty()) return false;
        if (knownProvince && !sameProvince(address, found)) return false;
        List<String> foundTokens = Arrays.asList(normalize(found).split(" "));
        return foundTokens.containsAll(cities);
    }
    /** researchUtils.addressCandidates와 같다: 괄호 안 지번 우선, 번지·일원·외 N필지 제거, 최대 3개. */
    static List<String> addressCandidates(String address) {
        String raw = address.replace('　', ' ').trim();
        Matcher prefixMatch = Pattern.compile("^([가-힣]+(?:시|도)\\s+[가-힣]+(?:시|군|구))").matcher(raw);
        String prefix = prefixMatch.find() ? prefixMatch.group(1) : "";
        Matcher insideMatch = Pattern.compile("\\(([^)]+)\\)").matcher(raw);
        String inside = insideMatch.find() ? insideMatch.group(1) : null;
        UnaryOperator<String> clean = value -> value.replace("번지", "").replaceAll("\\s*(일원|일대|외\\s*\\d+필지).*$", "").trim();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (inside != null) {
            values.add(clean.apply(inside));
            if (!prefix.isEmpty() && !inside.startsWith(prefix.split(" ")[0])) values.add(prefix + " " + clean.apply(inside));
        }
        values.add(clean.apply(raw.replaceAll("\\([^)]*\\)", "")));
        return values.stream().filter(value -> !value.isBlank()).limit(3).toList();
    }
    /** resolveLocation.parcelCandidates와 같다. */
    static List<String> parcelCandidates(String address) {
        String raw = normalize(address);
        Matcher prefixMatch = Pattern.compile("^(.+?[시군구])\\s").matcher(raw);
        String prefix = prefixMatch.find() ? prefixMatch.group(1) : "";
        Matcher insideMatch = Pattern.compile("\\(([^)]+)\\)").matcher(raw);
        String inside = insideMatch.find() ? insideMatch.group(1) : null;
        List<String> values = new ArrayList<>(addressCandidates(raw));
        if (inside != null && !prefix.isEmpty()) values.add(0, (prefix + " " + inside).replaceAll("번지|\\s*일원.*$", ""));
        Pattern parcel = Pattern.compile("(?:동|리|로|길)\\s+(?:산\\s*)?\\d+(?:-\\d+)?(?:\\s|$)");
        return new LinkedHashSet<>(values).stream().filter(value -> parcel.matcher(value).find() && sameRegion(raw, value)).toList();
    }
    private static final Pattern PARCEL = Pattern.compile("([가-힣\\d]+(?:동|리|로|길))\\s+(산\\s*)?(\\d+(?:-\\d+)?)");
    /** 주소에서 '동·리·로·길 + 번지' 부분만 뽑아 비교용 문자열로 만든다(예: '야당동 123-4'). */
    static String parcel(String value) {
        Matcher m = PARCEL.matcher(normalize(value));
        return m.find() ? m.group(1) + (m.group(2) == null ? "" : m.group(2)) + m.group(3) : null;
    }
    /** 응답 한 줄이 확정 조건(한국 좌표 범위, 주소 유형, 요청 지번과 같음, 같은 시·군·구)을 모두 만족하는지. */
    static boolean precise(JsonNode row, String address, String candidate) {
        double x = row.path("x").asDouble(Double.NaN), y = row.path("y").asDouble(Double.NaN);
        boolean coord = x >= 124 && x <= 132 && y >= 32 && y <= 40;
        String type = row.path("address_type").asText("");
        String actual = !row.path("address").path("address_name").asText("").isBlank() ? row.path("address").path("address_name").asText()
            : !row.path("road_address").path("address_name").asText("").isBlank() ? row.path("road_address").path("address_name").asText() : row.path("address_name").asText("");
        String requested = parcel(candidate);
        boolean matches = requested != null && Stream.of(row.path("address").path("address_name").asText(""), row.path("road_address").path("address_name").asText(""), row.path("address_name").asText(""))
            .anyMatch(value -> requested.equals(parcel(value)));
        return coord && (type.equals("REGION_ADDR") || type.equals("ROAD_ADDR")) && matches && sameRegion(address, actual);
    }
}
