package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 경쟁률 API 응답을 테이블별 복합키 기준으로 저장한다.
 * 엔티티 없이 JdbcTemplate으로 SQL을 직접 만든다. 테이블·컬럼 목록은 ApplyhomeCompetitionSyncService가 넘겨준다
 * (경쟁률, 특별공급 신청현황, 당첨 가점 테이블 등).
 */
// @Service: 업무 로직 빈. @Transactional 메서드는 트랜잭션 안에서 실행된다.
@Service
public class ApplyhomeCompetitionPersistenceService {

	private static final int BATCH_SIZE = 500;
	private static final Logger LOG = LoggerFactory.getLogger(ApplyhomeCompetitionPersistenceService.class);

	private final JdbcTemplate jdbcTemplate;

	public ApplyhomeCompetitionPersistenceService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

    /**
     * 완전하게 조회한 공고별 스냅샷을 교체한다. 검증 실패 시 기존 데이터는 유지한다.
     * 제공처가 일시적으로 0건을 돌려주면 이미 저장한 경쟁률·가점이 모두 사라지므로, 응답이 비어 있으면 교체하지 않는다.
     * @return 교체했으면 true, 빈 응답이라 기존 행을 유지했으면 false
     */
    // @Transactional: DELETE와 INSERT를 한 트랜잭션으로 묶어, 중간에 실패하면 지운 행도 되살린다.
    @Transactional
    public boolean replace(String table, List<String> columns, List<String> keyColumns,
            String houseManageNo, String pblancNo, List<JsonNode> rows) {
        if (rows.isEmpty()) {
            Integer existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE HOUSE_MANAGE_NO = ? AND PBLANC_NO = ?",
                    Integer.class, houseManageNo, pblancNo);
            if (existing != null && existing > 0) LOG.warn("빈 응답이라 기존 행을 유지: table={}, house={}, notice={}, rows={}", table, houseManageNo, pblancNo, existing);
            return false;
        }
        for (JsonNode row : rows) {
            for (String key : keyColumns) {
                if (field(row, key) == null || field(row, key).isNull()) {
                    throw new IllegalStateException("Missing snapshot key: " + key);
                }
            }
            if (!houseManageNo.equals(field(row, "HOUSE_MANAGE_NO").asText())
                    || !pblancNo.equals(field(row, "PBLANC_NO").asText())) {
                throw new IllegalStateException("Snapshot contains another announcement");
            }
        }
        jdbcTemplate.update("DELETE FROM " + table + " WHERE HOUSE_MANAGE_NO = ? AND PBLANC_NO = ?",
                houseManageNo, pblancNo);
        upsert(table, columns, keyColumns, rows);
        return true;
    }

	/**
	 * 여러 행을 한 번에 넣는 배치 upsert. INSERT ... ON DUPLICATE KEY UPDATE는 MariaDB 문법으로,
	 * 기본키·유니크키가 겹치면 INSERT 대신 키가 아닌 컬럼만 새 값(VALUES(컬럼))으로 UPDATE한다.
	 * batchUpdate는 500행씩 묶어 DB 왕복 횟수를 줄인다. 값은 모두 문자열로 넣는다.
	 */
	@Transactional
	public void upsert(String table, List<String> columns, List<String> keyColumns, List<JsonNode> rows) {
		if (rows.isEmpty()) {
			return;
		}

		Set<String> keys = Set.copyOf(keyColumns);
		String columnSql = String.join(", ", columns);
		String placeholders = columns.stream().map(ignored -> "?").collect(Collectors.joining(", "));
		String updates = columns.stream()
				.filter(column -> !keys.contains(column))
				.map(column -> column + " = VALUES(" + column + ")")
				.collect(Collectors.joining(", "));
		// 모든 컬럼이 키인 표는 고칠 값이 없어 UPDATE 절이 비면 문법 오류가 난다.
		// 키 컬럼 하나를 자기 값으로 두어 겹치는 행은 그대로 두고 넘어간다(INSERT IGNORE와 달리 다른 오류는 숨기지 않는다).
		if (updates.isEmpty()) {
			String firstKey = keyColumns.get(0);
			updates = firstKey + " = " + firstKey;
		}
		String sql = "INSERT INTO " + table + " (" + columnSql + ") VALUES (" + placeholders
				+ ") ON DUPLICATE KEY UPDATE " + updates;

		for (JsonNode row : rows) {
			for (String key : keyColumns) {
				if (field(row, key) == null || field(row, key).isNull()) {
					throw new IllegalStateException("Applyhome competition API row is missing primary-key field: " + key);
				}
			}
		}

		jdbcTemplate.batchUpdate(sql, rows, BATCH_SIZE, (statement, row) -> {
			for (int index = 0; index < columns.size(); index++) {
				JsonNode value = field(row, columns.get(index));
				statement.setString(index + 1, value == null || value.isNull() ? null : value.asText());
			}
		});
	}
    /**
     * DB 컬럼 이름과 청약홈 응답 필드 이름이 다른 곳. 오피스텔·도시형 경쟁률의 공급구분명은 DB가 RESIDENT_PRIOR_SENM,
     * 청약홈이 RESIDNT_PRIOR_SENM이라 그대로 찾으면 늘 비어 저장됐다(API·웹이 이 컬럼 이름을 써서 DB 이름은 그대로 둔다).
     */
    static final java.util.Map<String, String> SOURCE_FIELDS = java.util.Map.of("RESIDENT_PRIOR_SENM", "RESIDNT_PRIOR_SENM");

    /** 대문자 키를 먼저 찾고, 없으면 소문자 키로, 그래도 없으면 청약홈 쪽 이름(SOURCE_FIELDS)으로 다시 찾는다. */
    private JsonNode field(JsonNode row, String name) {
        JsonNode value = row.get(name);
        if (value == null) value = row.get(name.toLowerCase(Locale.ROOT));
        String source = SOURCE_FIELDS.get(name);
        return value == null && source != null ? row.get(source) : value;
    }

}
