package com.toady.web.app.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 관리자가 정한 공고 숨김·운영 안내(database.sql 26번 TB_ANNOUNCEMENT_OVERRIDE). 1분마다 다시 읽는다.
 * 키는 유형 이름(APT·PUBLIC_RENTAL 등)/주택관리번호/공고번호. 테이블이 없으면 숨김·안내가 없는 것으로 본다.
 * 공고 목록·상세를 만드는 쪽(공고 서비스)이 불러 숨긴 공고를 빼고 운영 안내 문구를 붙인다.
 * 표 전체를 메모리 맵으로 들고 있다가 1분마다 통째로 바꾼다(AppSettings와 같은 이중 확인 방식).
 */
// @Component: 빈 하나로 등록된다
@Component
public class AnnouncementOverrides {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementOverrides.class);
    private static final long REFRESH_MS = 60_000;
    /** locationAddress: 운영자가 확인한 지번·도로명 주소(27번). 앱이 지도 검색으로 다시 검증한 뒤에만 쓴다. */
    public record Override(boolean hidden, String notice, String locationAddress) {
        public Override(boolean hidden, String notice) { this(hidden, notice, null); }
    }

    private final JdbcTemplate jdbc;
    private volatile Map<String, Override> overrides = Map.of();
    private volatile long loadedAt;

    public AnnouncementOverrides(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 맵 키 "유형/주택관리번호/공고번호"를 만든다. */
    public static String key(String typeName, String houseManageNo, String pblancNo) { return typeName + "/" + houseManageNo + "/" + pblancNo; }

    public Override get(String typeName, String houseManageNo, String pblancNo) { return current().get(key(typeName, houseManageNo, pblancNo)); }

    public boolean hidden(String typeName, String houseManageNo, String pblancNo) {
        Override value = get(typeName, houseManageNo, pblancNo);
        return value != null && value.hidden();
    }

    /** 한 유형의 숨긴 공고 (주택관리번호, 공고번호) 목록. 검색 조건에 쓴다. */
    public Set<String[]> hiddenOf(String typeName) {
        String prefix = typeName + "/";
        return current().entrySet().stream().filter(e -> e.getValue().hidden() && e.getKey().startsWith(prefix))
                .map(e -> e.getKey().substring(prefix.length()).split("/", 2)).filter(p -> p.length == 2).collect(Collectors.toSet());
    }

    void refresh() { loadedAt = 0; }

    private Map<String, Override> current() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < REFRESH_MS) return overrides;
        synchronized (this) {
            if (now - loadedAt < REFRESH_MS) return overrides;
            try {
                Map<String, Override> next = new HashMap<>();
                boolean withLocation = true;
                String sql = "SELECT ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, HIDDEN_YN, NOTICE, LOCATION_ADDRESS FROM TB_ANNOUNCEMENT_OVERRIDE";
                // WHERE 1=0으로 행 없이 실행만 해 보고, LOCATION_ADDRESS 컬럼이 없는 예전 DB면 그 컬럼 없이 읽는다
                try { jdbc.queryForList(sql + " WHERE 1=0"); }
                catch (DataAccessException noColumn) { withLocation = false; sql = "SELECT ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, HIDDEN_YN, NOTICE FROM TB_ANNOUNCEMENT_OVERRIDE"; }
                final boolean location = withLocation;
                jdbc.query(sql, (org.springframework.jdbc.core.RowCallbackHandler) r -> {
                    String notice = r.getString(5), address = location ? r.getString(6) : null;
                    next.put(key(r.getString(1), r.getString(2), r.getString(3)), new Override("Y".equals(r.getString(4)),
                            notice == null || notice.isBlank() ? null : notice.strip(), address == null || address.isBlank() ? null : address.strip()));
                });
                overrides = Map.copyOf(next);
            } catch (DataAccessException missing) {
                if (!overrides.isEmpty()) log.warn("공고 숨김·안내를 다시 읽지 못해 이전 값을 씁니다: {}", missing.getMostSpecificCause().getMessage());
            }
            loadedAt = now;
            return overrides;
        }
    }
}
