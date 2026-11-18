package com.toady.admin.content

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 사이트 공지·배너(database.sql 27번 TB_SITE_NOTICE). 서비스는 1분 안에 반영한다.
 * 시작·끝 시각 사이에만 보인다. 호출: ContentController의 공지 화면(/admin/notices).
 */
// @Service: 업무 로직 빈.
@Service
class NoticeService(private val jdbc: JdbcTemplate) {
	/** 공지 한 건. live·stateLabel은 지금 시각(한국시간)으로 노출 상태를 계산한다. */
	data class Notice(val seq: Long, val message: String, val link: String?, val linkLabel: String?, val placement: String, val level: String,
	                  val start: LocalDateTime?, val end: LocalDateTime?, val enabled: Boolean, val admin: String, val updatedAt: LocalDateTime?) {
		val placementLabel get() = if (placement == "HOME") "첫 화면" else "모든 화면"
		val live get() = enabled && start != null && end != null && !start.isAfter(Kst.now()) && end.isAfter(Kst.now())
		val stateLabel get() = when { !enabled -> "멈춤"; live -> "노출 중"; start != null && start.isAfter(Kst.now()) -> "예정"; else -> "끝남" }
	}

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). 없으면 화면에 적용 안내를 보여 준다. */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_SITE_NOTICE WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun list(): List<Notice> = try {
		jdbc.query("SELECT * FROM TB_SITE_NOTICE ORDER BY NOTICE_SEQ DESC LIMIT 100") { r, _ ->
			Notice(r.getLong("NOTICE_SEQ"), r.getString("MESSAGE"), r.getString("LINK_URL"), r.getString("LINK_LABEL"), r.getString("PLACEMENT"), r.getString("LEVEL"),
				r.getTimestamp("START_DT")?.toLocalDateTime(), r.getTimestamp("END_DT")?.toLocalDateTime(), r.getString("USE_YN") == "Y", r.getString("ADMIN_NAME"), r.getTimestamp("UPDATE_DT")?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { emptyList() }

	/** 공지를 추가한다. 링크는 사이트 안 경로(/…) 또는 https 주소만 받는다(API도 같은 규칙으로 한 번 더 거른다). */
	fun add(message: String, link: String, linkLabel: String, placement: String, level: String, start: LocalDateTime, end: LocalDateTime, admin: String): String? {
		val text = message.trim().replace(Regex("\\s+"), " ")
		if (text.length !in 2..300) return "공지 문구는 2~300자로 써 주세요."
		val url = link.trim().ifEmpty { null }
		if (url != null && !SAFE_LINK.matches(url)) return "링크는 /로 시작하는 사이트 안 경로나 https 주소만 쓸 수 있습니다."
		if (!end.isAfter(start)) return "끝나는 시각이 시작 시각보다 뒤여야 합니다."
		if (!available()) return "공지 테이블이 없습니다. database.sql 27번과 관리자 계정 권한을 적용하세요."
		jdbc.update("INSERT INTO TB_SITE_NOTICE (MESSAGE, LINK_URL, LINK_LABEL, PLACEMENT, LEVEL, START_DT, END_DT, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,?,'Y',?,?)",
			text, url, linkLabel.trim().take(30).ifEmpty { null }, if (placement == "HOME") "HOME" else "ALL", if (level == "WARN") "WARN" else "INFO",
			Timestamp.valueOf(start), Timestamp.valueOf(end), admin.take(100), Timestamp.valueOf(Kst.now()))
		return null
	}

	/** 노출 켬·끔. 바뀐 행이 없으면 false. */
	fun setEnabled(seq: Long, enabled: Boolean, admin: String): Boolean = try {
		jdbc.update("UPDATE TB_SITE_NOTICE SET USE_YN=?, ADMIN_NAME=?, UPDATE_DT=? WHERE NOTICE_SEQ=?", if (enabled) "Y" else "N", admin.take(100), Timestamp.valueOf(Kst.now()), seq) > 0
	} catch (missing: DataAccessException) { false }

	fun delete(seq: Long): Boolean = try { jdbc.update("DELETE FROM TB_SITE_NOTICE WHERE NOTICE_SEQ=?", seq) > 0 } catch (missing: DataAccessException) { false }

	// SAFE_LINK: '/'로 시작하는 사이트 안 경로 또는 https 주소만 허용한다(javascript: 같은 위험한 링크 차단).
	// '//evil.com'·'/\evil.com'은 브라우저가 다른 사이트 주소로 읽으므로 '/' 바로 뒤에 '/'·'\'가 오면 거부한다.
	companion object {
		val SAFE_LINK = Regex("^(/(?![/\\\\])[A-Za-z0-9/_?=&%.\\-]*|https://[A-Za-z0-9.\\-]+(/[A-Za-z0-9/_?=&%.\\-#]*)?)$")
	}
}
