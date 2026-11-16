package com.toady.web.app.operation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 관리자 화면에서 바꾸는 서비스 설정(database.sql 27번 TB_APP_SETTING). 1분마다 다시 읽는다.
 * 값이 없거나 형식·범위가 맞지 않으면 호출한 쪽이 넘긴 기본값(환경변수 값)을 쓴다. 테이블이 없어도 기본값으로 동작한다.
 */
@Component
public class AppSettings {
    private static final Logger log = LoggerFactory.getLogger(AppSettings.class);
    private static final long REFRESH_MS = 60_000;
    public static final String DM_MIN_ACCOUNT_HOURS = "dm.minAccountHours", DM_REQUIRE_STORY = "dm.requireStory",
            DM_DAILY_NEW_THREADS = "dm.dailyNewThreads", DM_DAILY_MESSAGES = "dm.dailyMessages",
            MAIL_LIMIT = "mail.rollingLimit", MAIL_AUTH_RESERVE = "mail.authReserve", MAIL_SIGNUP_LIMIT = "mail.signupLimit",
            MOLIT_DAILY_LIMIT = "molit.dailyLimit", OFFICIAL_USER_SEQ = "official.userSeq";

    private final JdbcTemplate jdbc;
    private volatile Map<String, String> values = Map.of();
    private volatile long loadedAt;

    public AppSettings(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 정수 설정. 범위 밖이면 기본값. */
    public long number(String key, long fallback, long min, long max) {
        String raw = current().get(key);
        if (raw == null) return fallback;
        try { long value = Long.parseLong(raw.trim()); return value < min || value > max ? fallback : value; }
        catch (NumberFormatException invalid) { return fallback; }
    }

    public boolean flag(String key, boolean fallback) {
        String raw = current().get(key);
        return raw == null ? fallback : "true".equalsIgnoreCase(raw.trim());
    }

    public void refresh() { loadedAt = 0; }

    private Map<String, String> current() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < REFRESH_MS) return values;
        synchronized (this) {
            if (now - loadedAt < REFRESH_MS) return values;
            try {
                Map<String, String> next = new HashMap<>();
                jdbc.query("SELECT SETTING_KEY, SETTING_VALUE FROM TB_APP_SETTING", (RowCallbackHandler) r -> next.put(r.getString(1), r.getString(2)));
                values = Map.copyOf(next);
            } catch (DataAccessException missing) {
                if (!values.isEmpty()) log.warn("서비스 설정을 다시 읽지 못해 이전 값을 씁니다: {}", missing.getMostSpecificCause().getMessage());
            }
            loadedAt = now;
            return values;
        }
    }
}
