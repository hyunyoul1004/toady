package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.toady.web.app.announcement.dto.AnnouncementListItem;
import com.toady.web.app.announcement.dto.AnnouncementSearch;
import com.toady.web.app.announcement.service.AnnouncementSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * '내게 맞는 청약'·'줍줍' 화면용 공개 목록. 전국의 접수 중·예정 APT·무순위·임의공급 공고에
 * 자격 판정에 필요한 공고 조건(국민·민영, 투기과열·조정대상, 공급 지역, 특별공급 유형별 물량, 주택형별 최고 공급금액)을 붙인다.
 * 판정 자체는 브라우저에서 한다(입력한 개인 조건을 서버로 보내지 않는다).
 */
@RestController
public class EligibilityFeed {
    static final int PAST_DAYS = 60, FUTURE_DAYS = 60, MAX_ITEMS = 500;
    static final List<String> TYPES = List.of("apt", "remaining", "optional");
    private final AnnouncementSearchService search;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PriceGapStore gapStore;
    private final Cache<String, Map<String, Object>> cache = CacheBuilder.newBuilder().expireAfterWrite(Duration.ofMinutes(10)).build();

    @Autowired
    public EligibilityFeed(AnnouncementSearchService search, JdbcTemplate jdbc, PriceGapStore gapStore) { this(search, jdbc, gapStore, Clock.system(ZoneId.of("Asia/Seoul"))); }
    EligibilityFeed(AnnouncementSearchService search, JdbcTemplate jdbc, PriceGapStore gapStore, Clock clock) { this.search = search; this.jdbc = jdbc; this.gapStore = gapStore; this.clock = clock; }

    public record Unit(String type, BigDecimal area, Integer general, Integer special, Long amountManwon) { }
    /** 특별공급 유형별 세대수 합계(주택형 합산). */
    public record Special(int newlywed, int firstLife, int multiChild, int oldParents, int youth, int newborn, int institution) {
        static final Special NONE = new Special(0, 0, 0, 0, 0, 0, 0);
        Special plus(Special other) {
            return new Special(newlywed + other.newlywed, firstLife + other.firstLife, multiChild + other.multiChild, oldParents + other.oldParents,
                youth + other.youth, newborn + other.newborn, institution + other.institution);
        }
    }
    public record Item(String id, String type, String houseNm, String address, String region, String status,
                       String start, String end, String specialStart, String rank1, String rank1Gg, String rank1Etc, String rank2,
                       String winner, String contract, String moveIn, Integer totalSupply,
                       boolean national, boolean speculative, boolean adjusted, boolean priceCap,
                       Special special, List<Unit> units, PriceGapStore.Gap gap) {
        Item withGap(PriceGapStore.Gap value) {
            return new Item(id, type, houseNm, address, region, status, start, end, specialStart, rank1, rank1Gg, rank1Etc, rank2, winner, contract, moveIn, totalSupply,
                national, speculative, adjusted, priceCap, special, units, value);
        }
    }

    @GetMapping("/api/v1/public/eligibility/announcements")
    public ResponseEntity<Map<String, Object>> announcements() {
        LocalDate today = LocalDate.now(clock);
        Map<String, Object> body = cache.asMap().computeIfAbsent(today.toString(), key -> load(today));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(body);
    }

    /** 테스트에서 데이터를 바꾼 뒤 다시 읽게 한다. */
    void invalidate() { cache.invalidateAll(); }

    Map<String, Object> load(LocalDate today) {
        AnnouncementSearch query = new AnnouncementSearch();
        query.setStartDate(today.minusDays(PAST_DAYS));
        query.setEndDate(today.plusDays(FUTURE_DAYS));
        query.setSize(MAX_ITEMS);
        var page = search.searchAll(query, TYPES);
        if (page.getTotalElements() > MAX_ITEMS) org.slf4j.LoggerFactory.getLogger(EligibilityFeed.class)
            .warn("자격 판정 목록이 {}건을 넘어 일부 공고가 빠졌습니다: total={}", MAX_ITEMS, page.getTotalElements());
        var groups = PublicAreaPage.classify(page.getContent(), today, 0);
        List<PublicAreaPage.Item> active = new ArrayList<>(groups.open());
        active.addAll(groups.upcoming());

        Map<String, List<PublicAreaPage.Item>> byType = active.stream().collect(Collectors.groupingBy(item -> item.source().type()));
        Map<String, Map<String, Object>> details = new HashMap<>();
        Map<String, List<Map<String, Object>>> models = new HashMap<>();
        byType.forEach((type, items) -> {
            String detailTable = switch (type) { case "apt" -> "TB_APT_LTTOT_PBLANC_DETAIL"; case "remaining" -> "TB_REMNDR_LTTOT_PBLANC_DETAIL"; default -> "TB_OPT_LTTOT_PBLANC_DETAIL"; };
            String modelTable = switch (type) { case "apt" -> "TB_APT_LTTOT_PBLANC_MDL"; case "remaining" -> "TB_REMNDR_LTTOT_PBLANC_MDL"; default -> "TB_OPT_LTTOT_PBLANC_MDL"; };
            List<String> houses = items.stream().map(item -> item.source().houseManageNo()).distinct().toList();
            for (int from = 0; from < houses.size(); from += 200) {
                List<String> chunk = houses.subList(from, Math.min(houses.size(), from + 200));
                String marks = String.join(",", Collections.nCopies(chunk.size(), "?"));
                for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + detailTable + " WHERE HOUSE_MANAGE_NO IN (" + marks + ")", chunk.toArray()))
                    details.put(type + "/" + row.get("HOUSE_MANAGE_NO") + "/" + row.get("PBLANC_NO"), row);
                for (Map<String, Object> row : jdbc.queryForList("SELECT * FROM " + modelTable + " WHERE HOUSE_MANAGE_NO IN (" + marks + ") ORDER BY MODEL_NO", chunk.toArray()))
                    models.computeIfAbsent(type + "/" + row.get("HOUSE_MANAGE_NO") + "/" + row.get("PBLANC_NO"), k -> new ArrayList<>()).add(row);
            }
        });

        List<Item> built = active.stream().map(entry -> {
            AnnouncementListItem source = entry.source();
            String id = source.type() + "/" + source.houseManageNo() + "/" + source.pblancNo();
            return item(id, source, entry.status().name(), details.getOrDefault(id, Map.of()), models.getOrDefault(id, List.of()));
        }).toList();
        // 미리 계산한 주변 시세 비교(database.sql 18번)를 붙인다. 없으면 null이고 화면은 뱃지를 숨긴다.
        Map<String, PriceGapStore.Gap> gaps = gapStore.forIds(built.stream().map(Item::id).toList());
        List<Item> items = built.stream().map(item -> {
            // 분양가가 바뀐 공고는 gapStore.forIds가 이미 빼고 돌려준다.
            return item.withGap(gaps.get(item.id()));
        }).toList();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("checkedAt", clock.instant().toString());
        body.put("today", today.toString());
        body.put("items", items);
        return body;
    }

    static Item item(String id, AnnouncementListItem source, String status, Map<String, Object> detail, List<Map<String, Object>> modelRows) {
        Function<String, String> text = key -> { Object value = detail.get(key); return value == null ? null : String.valueOf(value).trim(); };
        String type = source.type();
        Special special = Special.NONE;
        List<Unit> units = new ArrayList<>();
        for (Map<String, Object> row : modelRows) {
            if ("apt".equals(type)) special = special.plus(new Special(count(row, "NWWDS_HSHLDCO"), count(row, "LFE_FRST_HSHLDCO"), count(row, "MNYCH_HSHLDCO"),
                count(row, "OLD_PARNTS_SUPORT_HSHLDCO"), count(row, "YGMN_HSHLDCO"), count(row, "NWBB_HSHLDCO"), count(row, "INSTT_RECOMEND_HSHLDCO")));
            String houseTy = row.get("HOUSE_TY") == null ? "" : String.valueOf(row.get("HOUSE_TY")).trim();
            units.add(new Unit(houseTy, area(houseTy), number(row.get("SUPLY_HSHLDCO")), number(row.get("SPSPLY_HSHLDCO")),
                Optional.ofNullable(number(row.get("LTTOT_TOP_AMOUNT"))).map(Integer::longValue).orElse(null)));
        }
        String dtl = Optional.ofNullable(text.apply("HOUSE_DTL_SECD_NM")).orElse("");
        return new Item(id, type, source.houseNm(), source.hssplyAdres(), Optional.ofNullable(text.apply("SUBSCRPT_AREA_CODE_NM")).orElse(source.regionName()), status,
            date(source.rceptBgnde()), date(source.rceptEndde()), date(text.apply("SPSPLY_RCEPT_BGNDE")),
            date(source.gnrlRnk1CrspareaRcptde()), date(source.gnrlRnk1EtcGgRcptde()), date(source.gnrlRnk1EtcAreaRcptde()), date(source.gnrlRnk2CrspareaRcptde()),
            date(text.apply("PRZWNER_PRESNATN_DE")), date(text.apply("CNTRCT_CNCLS_BGNDE")), month(text.apply("MVN_PREARNGE_YM")),
            number(source.totalSupplyHouseholds()),
            dtl.contains("국민"), "Y".equals(text.apply("SPECLT_RDN_EARTH_AT")), "Y".equals(text.apply("MDAT_TRGET_AREA_SECD")), "Y".equals(text.apply("PARCPRC_ULS_AT")),
            special, units, null);
    }

    private static int count(Map<String, Object> row, String key) { Integer value = number(row.get(key)); return value == null ? 0 : value; }
    static Integer number(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).replace(",", "").trim();
        if (!text.matches("\\d{1,9}")) return null;
        return Integer.parseInt(text);
    }
    /** 주택형 '084.9500A' → 전용 84.95㎡. 숫자로 시작하지 않으면 면적을 모르는 것으로 둔다. */
    static BigDecimal area(String houseTy) {
        var match = java.util.regex.Pattern.compile("^(\\d{1,3}(?:\\.\\d+)?)").matcher(houseTy == null ? "" : houseTy);
        if (!match.find()) return null;
        BigDecimal value = new BigDecimal(match.group(1)).stripTrailingZeros();
        return value.signum() > 0 && value.compareTo(BigDecimal.valueOf(400)) <= 0 ? value : null;
    }
    static String date(String value) {
        if (value == null || value.isBlank()) return null;
        LocalDate parsed = PlannerSummary.date(value.trim().length() > 10 ? value.trim().substring(0, 10) : value.trim());
        return parsed == null ? null : parsed.toString();
    }
    static String month(String value) {
        if (value == null) return null;
        String digits = value.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 4) + "-" + digits.substring(4, 6) : null;
    }

}
