package com.toady.web.app.planner.research;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 공급 주소를 카카오 로컬 REST API로 확인해 법정동 코드를 얻는다. 화면(resolveLocation.js)과 같은 규칙만 쓴다.
 * - 지번(동·리·로·길 + 번지)이 있는 후보만 조회하고, 응답의 지번이 요청과 같고 시·도·시·군·구가 공고 주소와 같을 때만 확정한다.
 * - 지구·블록 이름만 있는 주소는 확정하지 않는다(엉뚱한 위치에 확정하지 않는다). 단지별 예외 규칙은 두지 않는다.
 */
@Component
public class AddressLocator {
    public record Location(String bcode, String dong, String address) { }
    private final ResearchTransport transport;
    private final String key;
    private final ObjectMapper json = new ObjectMapper();

    public AddressLocator(ResearchTransport transport, @Value("${toady.research.kakao-rest-key:}") String key) {
        this.transport = transport; this.key = key == null ? "" : key.trim();
    }
    public boolean enabled() { return !key.isBlank(); }

    /** 정확한 지번 위치를 찾으면 돌려준다. 찾지 못하면 empty(후보 위치로 확정하지 않는다). 제공처 오류는 예외. */
    public Optional<Location> locate(String rawAddress) throws Exception {
        if (!enabled()) return Optional.empty();
        String address = normalize(rawAddress);
        for (String candidate : parcelCandidates(address).stream().limit(3).toList()) {
            URI uri = URI.create("https://dapi.kakao.com/v2/local/search/address.json?size=10&query=" + URLEncoder.encode(candidate, StandardCharsets.UTF_8));
            JsonNode body = json.readTree(transport.get(uri, Map.of("Authorization", "KakaoAK " + key)));
            for (JsonNode row : body.path("documents")) {
                if (!precise(row, address, candidate)) continue;
                String bcode = row.path("address").path("b_code").asText(""), dong = row.path("address").path("region_3depth_name").asText("");
                if (bcode.matches("\\d{10}") && !dong.isBlank()) return Optional.of(new Location(bcode, dong, row.path("address").path("address_name").asText(candidate)));
            }
        }
        return Optional.empty();
    }

    static String normalize(String value) { return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC).replaceAll("\\s+", " ").trim(); }
    private static final String[][] PROVINCES = {{"서울","서울"},{"부산","부산"},{"대구","대구"},{"인천","인천"},{"광주","광주"},{"대전","대전"},{"울산","울산"},{"세종","세종"},{"경기","경기"},{"강원","강원"},
        {"충청북","충북"},{"충북","충북"},{"충청남","충남"},{"충남","충남"},{"전북","전북"},{"전라북","전북"},{"전라남","전남"},{"전남","전남"},{"경상북","경북"},{"경북","경북"},{"경상남","경남"},{"경남","경남"},{"제주","제주"}};
    static String province(String address) {
        String first = normalize(address).split(" ")[0];
        for (String[] pair : PROVINCES) if (first.startsWith(pair[0])) return pair[1];
        return null;
    }
    static List<String> municipality(String address) {
        List<String> tokens = new ArrayList<>(Arrays.asList(normalize(address).split(" ")));
        if (province(address) != null && !tokens.isEmpty()) tokens.remove(0);
        return tokens.stream().filter(token -> token.matches("^[가-힣]+[시군구]$") && !token.matches(".*(지구|특구|도시)$")).limit(2).toList();
    }
    static boolean sameRegion(String address, String found) {
        String expected = province(address); List<String> cities = municipality(address);
        if (expected == null && cities.isEmpty()) return false;
        if (expected != null && !expected.equals(province(found))) return false;
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
        java.util.function.UnaryOperator<String> clean = value -> value.replace("번지", "").replaceAll("\\s*(일원|일대|외\\s*\\d+필지).*$", "").trim();
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
    static String parcel(String value) {
        Matcher m = PARCEL.matcher(normalize(value));
        return m.find() ? m.group(1) + (m.group(2) == null ? "" : m.group(2)) + m.group(3) : null;
    }
    static boolean precise(JsonNode row, String address, String candidate) {
        double x = row.path("x").asDouble(Double.NaN), y = row.path("y").asDouble(Double.NaN);
        boolean coord = x >= 124 && x <= 132 && y >= 32 && y <= 40;
        String type = row.path("address_type").asText("");
        String actual = !row.path("address").path("address_name").asText("").isBlank() ? row.path("address").path("address_name").asText()
            : !row.path("road_address").path("address_name").asText("").isBlank() ? row.path("road_address").path("address_name").asText() : row.path("address_name").asText("");
        String requested = parcel(candidate);
        boolean matches = requested != null && java.util.stream.Stream.of(row.path("address").path("address_name").asText(""), row.path("road_address").path("address_name").asText(""), row.path("address_name").asText(""))
            .anyMatch(value -> requested.equals(parcel(value)));
        return coord && (type.equals("REGION_ADDR") || type.equals("ROAD_ADDR")) && matches && sameRegion(address, actual);
    }
}
