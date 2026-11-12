package com.toady.web.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API가 쌓는 익명 로그·발송 이력 중 보관 기간이 지난 행을 매일 나눠 지운다.
 * 한 번에 지우는 행 수를 제한해 긴 잠금과 대용량 undo 로그를 피한다(MariaDB·H2 모두 DELETE ... LIMIT 지원).
 * 테이블이 아직 없거나 스케줄러 계정에 DELETE 권한이 없으면 경고만 남기고 다음 테이블을 정리한다.
 * TB_MAIL_ATTEMPT는 API가 발송량 확인 시 직접 정리하므로 대상에서 제외한다.
 */
@Component
public class DataRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionJob.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final int BATCH_SIZE = 10_000;
    // 한 테이블을 한 번 실행에서 최대 100만 행까지만 지운다. 남은 행은 다음 날 이어서 정리한다.
    static final int MAX_BATCHES = 100;
    // API의 생활권 새 공고 판정(PushDigest.BASELINE)이 최초 기록에 쓰는 기준 시각. 이 행은 지우지 않는다.
    public static final LocalDateTime PUSH_AREA_BASELINE = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** 정리 대상 하나. where의 ?에는 params가 순서대로 들어간다. */
    record Target(String table, String where, List<Object> params) { }

    private final JdbcTemplate jdbc;
    private final int batchSize;

    @Autowired
    public DataRetentionJob(JdbcTemplate jdbc) { this(jdbc, BATCH_SIZE); }
    DataRetentionJob(JdbcTemplate jdbc, int batchSize) { this.jdbc = jdbc; this.batchSize = batchSize; }

    /** 기본 04:40(한국시간). 수집(03:15·04:15)과 겹치지 않는 시각이며 DATA_RETENTION_CRON=- 이면 끈다. */
    @Scheduled(cron = "${data.retention.cron:0 40 4 * * *}", zone = "Asia/Seoul")
    public void runDaily() {
        run(LocalDateTime.now(SEOUL));
    }

    static List<Target> targets(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        return List.of(
                // 지표 화면은 최근 9주만 읽는다. 90일이 지나면 방문 로그를 지운다.
                new Target("TB_PAGE_VIEW", "VISIT_DATE < ?", List.of(Date.valueOf(today.minusDays(90)))),
                new Target("TB_USAGE_EVENT", "VISIT_DATE < ?", List.of(Date.valueOf(today.minusDays(180)))),
                // 일정 알림 중복 방지는 당일 기준이라 오래된 발송 이력은 필요 없다.
                new Target("TB_REMINDER_DELIVERY", "SEND_DATE < ?", List.of(Date.valueOf(today.minusDays(90)))),
                // 생활권 새 공고 판정: 1년 넘게 보이지 않은(마지막 확인 기준) 공고만 지운다. 아직 접수 중인 장기 공고를 지웠다가
                // '새 공고'로 다시 알리는 일을 막는다. 생활권별 기록 시작 표시('*')는 남긴다. LAST_SEEN_DT는 database.sql 15번이며,
                // 없으면 이 테이블만 건너뛴다(경고 로그).
                // LAST_SEEN_DT가 아직 없는 행(컬럼 추가 직후)은 기준 시각(2000-01-01) 행을 지우지 않는다. 기준 시각 행은 생활권 기록을
                // 시작할 때 이미 접수 중이던 공고라, 지우면 다음 묶음 알림에서 '새 공고'로 대량 발송된다.
                new Target("TB_PUSH_AREA_SEEN", "ANNOUNCEMENT_ID <> '*' AND ((LAST_SEEN_DT IS NOT NULL AND LAST_SEEN_DT < ?)"
                        + " OR (LAST_SEEN_DT IS NULL AND FIRST_SEEN_DT > ? AND FIRST_SEEN_DT < ?))",
                        List.of(Timestamp.valueOf(now.minusDays(365)), Timestamp.valueOf(PUSH_AREA_BASELINE), Timestamp.valueOf(now.minusDays(365)))));
    }

    /** 테이블별 삭제 행 수를 돌려준다. 실패한 테이블은 -1이다. */
    public Map<String, Long> run(LocalDateTime now) {
        return run(targets(now));
    }

    Map<String, Long> run(List<Target> targets) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Target target : targets) {
            long total = 0;
            try {
                String sql = "DELETE FROM " + target.table() + " WHERE " + target.where() + " LIMIT " + batchSize;
                for (int batch = 0; batch < MAX_BATCHES; batch++) {
                    int deleted = jdbc.update(sql, target.params().toArray());
                    total += deleted;
                    if (deleted < batchSize) break;
                }
                result.put(target.table(), total);
                if (total > 0) log.info("Data retention deleted {} row(s) from {}", total, target.table());
            } catch (DataAccessException failure) {
                // 테이블 미생성·권한 부족은 서비스에 영향이 없으므로 경고만 남긴다. 이미 지운 배치는 유지된다.
                result.put(target.table(), -1L);
                log.warn("Data retention skipped {} after {} row(s): {}", target.table(), total,
                        failure.getMostSpecificCause().getMessage());
            }
        }
        return result;
    }
}
