package com.toady.web.apt.service;

import com.fasterxml.jackson.databind.JsonNode;
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

	private final JdbcTemplate jdbcTemplate;

	public ApplyhomeCompetitionPersistenceService(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
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
