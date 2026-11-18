package com.toady.web.app.planner;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 검색엔진에 알리는 공고 목록(사이트맵·지역별 공개 페이지·공고 페이지의 '같은 지역 다른 공고')을 한 기준으로 만든다.
 * 접수 마감(없으면 모집공고일)이 최근 recent-days 이내이거나 아직 오지 않은 공고만 넣고, 운영자가 숨긴 공고는 뺀다.
 * updated는 공고를 처음 수집한 시각과 경쟁률·당첨가점이 처음 들어온 시각 중 가장 늦은 날짜다(사이트맵 lastmod).
 * 테이블: 유형별 공고 상세(TB_*_LTTOT_PBLANC_DETAIL)와 결과 테이블(경쟁률·가점). 운영자 숨김은 AnnouncementOverrides로 거른다.
 * AnnouncementSitemap, PublicRegionPages, PublicHomePages, PublicCollectionPages, PublicAnnouncementPage 등이 쓴다.
 */
// @Component: 스프링 빈으로 등록한다.
@Component
public class PublicAnnouncementIndex {
    private static final Logger log = LoggerFactory.getLogger(PublicAnnouncementIndex.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    /** 유형별 원본 테이블과 컬럼. 오피스텔·도시형은 주소가 HSSPLY_HSHLDCO에 들어 있다. 결과 테이블은 갱신일 계산에만 쓴다. */
    record Source(String table, String start, String end, String address, List<String> results) { }
    static final Map<String, Source> SOURCES = new LinkedHashMap<>();
    static {
        SOURCES.put("apt", new Source("TB_APT_LTTOT_PBLANC_DETAIL", "RCEPT_BGNDE", "RCEPT_ENDDE", "HSSPLY_ADRES", List.of("TB_APT_LTTOT_PBLANC_CMPET", "TB_APT_LTTOT_PBLANC_SCORE")));
        SOURCES.put("urban", new Source("TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_HSHLDCO", List.of("TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET")));
        SOURCES.put("remaining", new Source("TB_REMNDR_LTTOT_PBLANC_DETAIL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", List.of("TB_REMNDR_LTTOT_PBLANC_CMPET")));
        SOURCES.put("public-rental", new Source("TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", List.of("TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET")));
        SOURCES.put("optional", new Source("TB_OPT_LTTOT_PBLANC_DETAIL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", List.of("TB_OPT_LTTOT_PBLANC_CMPET")));
    }
    static final Map<String, String> LABELS = Map.of("apt", "아파트", "urban", "오피스텔·도시형", "remaining", "무순위·잔여세대",
        "public-rental", "공공지원 민간임대", "optional", "임의공급");

    /** 오늘 기준 공고 상태: 접수 중(OPEN), 접수 예정(UPCOMING), 마감(CLOSED). */
    public enum Status { OPEN, UPCOMING, CLOSED }
    /** 공개 페이지·사이트맵에 쓰는 공고 한 건. region은 Regions로 정한 시·도/시·군·구(모르면 null). */
    public record Entry(String type, String house, String notice, String houseNm, String houseKind, String address,
                        LocalDate start, LocalDate end, LocalDate announced, LocalDate winner, LocalDate updated, Regions.Region region) {
        public String id() { return type + "/" + house + "/" + notice; }
        public String path() { return "/announcements/" + id(); }
        /** 최신순 정렬 기준: 접수 시작일, 없으면 모집공고일. */
        public LocalDate sortDate() { return start != null ? start : announced; }
        // 접수 시작일이 없으면(일부 임대 공고) 모집공고일로만 예정/마감을 나눈다.
        public Status status(LocalDate today) {
            if (start == null) return announced != null && announced.isAfter(today) ? Status.UPCOMING : Status.CLOSED;
            if (start.isAfter(today)) return Status.UPCOMING;
            return (end == null ? start : end).isBefore(today) ? Status.CLOSED : Status.OPEN;
        }
        public String typeLabel() { return LABELS.getOrDefault(type, ""); }
    }

    private final JdbcTemplate jdbc; private final int recentDays; private final Clock clock;
    // @Autowired(required=false): 운영자 숨김 기능(AnnouncementOverrides) 빈이 있으면 넣고, 없으면 null로 두고 숨김 없이 동작한다.
    @Autowired(required = false)
    com.toady.web.app.operation.AnnouncementOverrides overrides;
    private final Cache<String, List<Entry>> cache = CacheBuilder.newBuilder().maximumSize(2).expireAfterWrite(Duration.ofMinutes(30)).build();

    // @Autowired: 스프링이 쓸 생성자. @Value로 사이트맵에 넣을 최근 일수(기본 365일, 최소 30일)를 읽는다.
    @Autowired
    public PublicAnnouncementIndex(JdbcTemplate jdbc, @Value("${toady.sitemap.recent-days:365}") int recentDays) {
        this(jdbc, recentDays, Clock.system(KST));
    }
    PublicAnnouncementIndex(JdbcTemplate jdbc, int recentDays, Clock clock) {
        this.jdbc = jdbc; this.recentDays = Math.max(30, recentDays); this.clock = clock;
    }
    LocalDate today() { return LocalDate.now(clock.withZone(KST)); }
    /** 테스트에서 데이터를 바꾼 뒤 다시 읽게 한다. */
    void invalidate() { cache.invalidateAll(); }

    /** 최근·앞으로의 공개 공고 전체(유형 순서, 같은 유형은 주택관리번호·공고번호 순). 30분 캐시. */
    public List<Entry> recent() {
        LocalDate today = today();
        return PublicHtml.load(cache, today.toString(), () -> load(today));
    }
    public Optional<Entry> find(String id) { return recent().stream().filter(entry -> entry.id().equals(id)).findFirst(); }

    // 유형마다 SQL을 한 번씩 실행해 합친다. 결과 테이블이 없는 환경이면 갱신일 없는 단순 SQL로 다시 읽는다.
    private List<Entry> load(LocalDate today) {
        String since = today.minusDays(recentDays).format(DAY);
        List<Entry> out = new ArrayList<>();
        for (var source : SOURCES.entrySet()) {
            String type = source.getKey();
            List<Entry> rows;
            try { rows = jdbc.query(sql(source.getValue(), true), (r, n) -> entry(type, r, true, today), since); }
            catch (DataAccessException missingColumns) {
                // 결과 테이블·등록일 컬럼이 없는 환경(일부 테스트·이전 스키마)에서는 갱신일 없이 목록만 만든다.
                log.debug("공고 갱신일 없이 목록을 만듭니다: type={}, reason={}", type, missingColumns.getMostSpecificCause().getMessage());
                rows = jdbc.query(sql(source.getValue(), false), (r, n) -> entry(type, r, false, today), since);
            }
            rows.stream().filter(Objects::nonNull)
                .filter(e -> overrides == null || !overrides.hidden(type.toUpperCase(Locale.ROOT).replace('-', '_'), e.house(), e.notice()))
                .forEach(out::add);
        }
        return List.copyOf(out);
    }
    /** 테이블·컬럼명은 고정 목록에서만 온다. 날짜 형식(YYYY-MM-DD·YYYYMMDD)은 '-'를 빼 맞춘다. */
    static String sql(Source source, boolean withUpdated) {
        StringBuilder sql = new StringBuilder("SELECT d.HOUSE_MANAGE_NO,d.PBLANC_NO,d.HOUSE_NM,d.HOUSE_SECD_NM,d.").append(source.address())
            .append(",d.SUBSCRPT_AREA_CODE_NM,d.").append(source.start()).append(",d.").append(source.end()).append(",d.RCRIT_PBLANC_DE,d.PRZWNER_PRESNATN_DE");
        if (withUpdated) {
            sql.append(",d.REG_DT");
            for (String result : source.results())
                sql.append(",(SELECT MAX(r.REG_DT) FROM ").append(result).append(" r WHERE r.HOUSE_MANAGE_NO=d.HOUSE_MANAGE_NO AND r.PBLANC_NO=d.PBLANC_NO)");
        }
        return sql.append(" FROM ").append(source.table()).append(" d WHERE REPLACE(COALESCE(NULLIF(d.").append(source.end())
            .append(",''),d.RCRIT_PBLANC_DE),'-','') >= ? ORDER BY d.HOUSE_MANAGE_NO,d.PBLANC_NO").toString();
    }
    // SQL 한 줄 → Entry. 번호에 이상한 문자가 있으면 공개 주소로 쓸 수 없으니 null로 버린다.
    // 11번째 열부터는 갱신일 후보(수집일·결과 등록일)라 그중 가장 늦은 날을 updated로 쓴다.
    private static Entry entry(String type, ResultSet r, boolean withUpdated, LocalDate today) throws SQLException {
        String house = r.getString(1), notice = r.getString(2);
        if (house == null || notice == null || !house.matches("[a-zA-Z0-9_-]{1,40}") || !notice.matches("[a-zA-Z0-9_-]{1,40}")) return null;
        LocalDate announced = date(r.getString(9));
        LocalDate updated = null;
        if (withUpdated) {
            int columns = r.getMetaData().getColumnCount();
            for (int column = 11; column <= columns; column++) updated = latest(updated, day(r.getTimestamp(column)));
        }
        // 수집 시각이 없으면 모집공고일로 둔다. 오늘보다 뒤인 날짜는 알리지 않는다.
        if (updated == null) updated = announced;
        if (updated != null && updated.isAfter(today)) updated = today;
        String address = r.getString(5);
        return new Entry(type, house, notice, nz(r.getString(3)).strip(), nz(r.getString(4)), nz(address).strip(), date(r.getString(7)), date(r.getString(8)),
            announced, date(r.getString(10)), updated, Regions.of(address, r.getString(6)).orElse(null));
    }
    private static LocalDate day(Timestamp value) { return value == null ? null : value.toLocalDateTime().toLocalDate(); }
    static LocalDate latest(LocalDate a, LocalDate b) { return a == null ? b : b == null ? a : a.isAfter(b) ? a : b; }
    private static LocalDate date(String value) { return value == null ? null : PlannerSummary.date(value.trim()); }
    private static String nz(String value) { return value == null ? "" : value; }

    /** 같은 지역 공고: 접수 중·예정(시작일 순) 다음 지난 공고(최신순). */
    public static List<Entry> ordered(List<Entry> entries, LocalDate today) {
        Comparator<Entry> newest = Comparator.comparing(Entry::sortDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(Entry::id);
        List<Entry> active = new ArrayList<>(), past = new ArrayList<>();
        for (Entry entry : entries) (entry.status(today) == Status.CLOSED ? past : active).add(entry);
        active.sort(Comparator.comparing(Entry::sortDate, Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Entry::id));
        past.sort(newest);
        active.addAll(past);
        return active;
    }
}
