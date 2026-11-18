package com.toady.web.app.operation;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.LocalDate;

/**
 * 날짜별 사용량(database.sql 27번 TB_DAILY_COUNTER). 재시작해도 이어서 세고, 여러 요청이 동시에 와도 한도를 넘지 않게
 * '한도 미만일 때만 1 올리기'를 한 문장으로 처리한다. 테이블이 없으면 null을 돌려 호출한 쪽이 메모리 계수기를 쓰게 한다.
 * 키(COUNTER_KEY)와 날짜(COUNT_DATE)가 한 행이다. 예: 국토부 실거래 API의 하루 호출 수.
 */
// @Component: 빈으로 등록된다. 상태는 DB에만 두므로 여러 서버가 함께 세도 된다
@Component
public class DailyCounter {
    private final JdbcTemplate jdbc;
    public DailyCounter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 한도 안이면 1 올리고 true, 한도에 닿았으면 false. 테이블이 없으면 null. */
    public Boolean tryIncrement(String key, LocalDate day, long limit) {
        try {
            // 1) 행이 있고 한도 미만이면 UPDATE 한 번으로 끝난다. 조건(COUNT_VALUE<한도)과 증가가 한 문장이라 동시에 와도 넘지 않는다
            if (jdbc.update("UPDATE TB_DAILY_COUNTER SET COUNT_VALUE=COUNT_VALUE+1 WHERE COUNTER_KEY=? AND COUNT_DATE=? AND COUNT_VALUE<?", key, Date.valueOf(day), limit) > 0) return true;
            // 2) 행이 있는데 한도에 닿았으면 false. 3) 행이 없으면 1로 INSERT하고, 다른 요청이 먼저 넣었으면(중복 키) 다시 UPDATE한다
            if (count(key, day) != null && count(key, day) >= limit) return false;
            try { jdbc.update("INSERT INTO TB_DAILY_COUNTER (COUNTER_KEY, COUNT_DATE, COUNT_VALUE) VALUES (?,?,1)", key, Date.valueOf(day)); return limit > 0; }
            catch (DuplicateKeyException raced) {
                return jdbc.update("UPDATE TB_DAILY_COUNTER SET COUNT_VALUE=COUNT_VALUE+1 WHERE COUNTER_KEY=? AND COUNT_DATE=? AND COUNT_VALUE<?", key, Date.valueOf(day), limit) > 0;
            }
        } catch (DataAccessException missing) { return null; }
    }

    /** 그날 지금까지 센 값. 행이 없으면 0, 테이블이 없으면 null. */
    public Long count(String key, LocalDate day) {
        try {
            return jdbc.query("SELECT COUNT_VALUE FROM TB_DAILY_COUNTER WHERE COUNTER_KEY=? AND COUNT_DATE=?", (r, i) -> r.getLong(1), key, Date.valueOf(day))
                    .stream().findFirst().orElse(0L);
        } catch (DataAccessException missing) { return null; }
    }
}
