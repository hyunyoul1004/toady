package com.toady.admin.metrics

import com.toady.admin.Kst
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 지표에서 뺄 운영자 브라우저(database.sql 20번). 방문자는 브라우저 저장소의 무작위 ID로 세므로(IP 미저장)
 * 운영자가 쓰는 브라우저마다 관리자 화면에서 한 번 등록하면 과거 기록까지 지표에서 빠진다.
 * 테이블이 없으면 제외 없이 모든 방문을 센다.
 */
@Service
class ExclusionService(private val jdbc: JdbcTemplate) {
	data class Excluded(val visitorId: String, val label: String, val registeredAt: LocalDateTime?)

	/** 지표 쿼리에 붙일 조건. 테이블이 없으면 빈 문자열. column은 VISITOR_ID 컬럼 이름(별칭 포함). */
	fun clause(column: String = "VISITOR_ID"): String =
		if (available()) " AND $column NOT IN (SELECT VISITOR_ID FROM TB_ANALYTICS_EXCLUDED_VISITOR)" else ""

	fun available(): Boolean = try {
		jdbc.queryForObject("SELECT COUNT(*) FROM TB_ANALYTICS_EXCLUDED_VISITOR WHERE 1=0", Long::class.java); true
	} catch (missing: DataAccessException) { false }

	fun list(): List<Excluded> = try {
		jdbc.query("SELECT VISITOR_ID, LABEL, REG_DT FROM TB_ANALYTICS_EXCLUDED_VISITOR ORDER BY REG_DT DESC") { r, _ ->
			Excluded(r.getString(1), r.getString(2), r.getTimestamp(3)?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { emptyList() }

	/** 등록 결과: ADDED · EXISTS · INVALID · UNAVAILABLE(테이블 없음) */
	fun add(visitorId: String, label: String, admin: String): String {
		if (!UUID.matches(visitorId)) return "INVALID"
		val name = label.trim().ifEmpty { "운영자 브라우저" }.take(50)
		return try {
			jdbc.update("INSERT INTO TB_ANALYTICS_EXCLUDED_VISITOR (VISITOR_ID, LABEL, REG_DT) VALUES (?,?,?)", visitorId.lowercase(), name, Timestamp.valueOf(Kst.now()))
			log.info("지표 제외 브라우저 등록: label={}, admin={}", name, admin)
			"ADDED"
		} catch (duplicate: DuplicateKeyException) { "EXISTS" }
		catch (missing: DataAccessException) { "UNAVAILABLE" }
	}

	fun remove(visitorId: String, admin: String): Boolean = try {
		(jdbc.update("DELETE FROM TB_ANALYTICS_EXCLUDED_VISITOR WHERE VISITOR_ID=?", visitorId) == 1).also {
			if (it) log.info("지표 제외 브라우저 해제: admin={}", admin)
		}
	} catch (missing: DataAccessException) { false }

	companion object {
		private val UUID = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
		private val log = LoggerFactory.getLogger(ExclusionService::class.java)
	}
}
