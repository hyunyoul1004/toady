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

/** 경쟁률 API 응답을 테이블별 복합키 기준으로 저장한다. */
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
    private JsonNode field(JsonNode row, String name) {
        JsonNode value = row.get(name);
        return value != null ? value : row.get(name.toLowerCase(Locale.ROOT));
    }

}
