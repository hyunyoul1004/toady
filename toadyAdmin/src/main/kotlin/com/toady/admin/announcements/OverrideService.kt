package com.toady.admin.announcements

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 공고 숨김·정정 안내(database.sql 26번 TB_ANNOUNCEMENT_OVERRIDE). 공고 원본은 바꾸지 않는다.
 * 숨기면 API가 목록·검색·캘린더·사이트맵·RSS에서 빼고 새 이야기를 막는다(1분 안에 반영). 안내 문구는 공고 상세 위에 보인다.
 */
@Service
class OverrideService(private val jdbc: JdbcTemplate) {
	data class Override(val hidden: Boolean, val notice: String?, val admin: String?, val updatedAt: LocalDateTime?) {
		val empty get() = !hidden && notice.isNullOrBlank()
		/** 작업 이력에 남길 요약. */
		val summary get() = (if (hidden) "숨김" else "공개") + (notice?.takeIf { it.isNotBlank() }?.let { " · 안내: $it" } ?: "")
	}

	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_ANNOUNCEMENT_OVERRIDE WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** id: apt/주택관리번호/공고번호 (관리자 화면 주소와 같은 모양). */
	fun get(id: String): Override? {
		val key = key(id) ?: return null
		return try {
			jdbc.query("SELECT HIDDEN_YN, NOTICE, ADMIN_NAME, UPDATE_DT FROM TB_ANNOUNCEMENT_OVERRIDE WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?", { r, _ ->
				Override(r.getString(1) == "Y", r.getString(2), r.getString(3), r.getTimestamp(4)?.toLocalDateTime())
			}, key.first, key.second, key.third).firstOrNull()
		} catch (missing: DataAccessException) { null }
	}

	/** 숨김 여부와 안내 문구를 저장한다. 둘 다 비면 행을 지워 원래대로 돌린다. 저장 전 값을 돌려준다. */
	fun save(id: String, hidden: Boolean, notice: String, admin: String): Pair<Boolean, Override?> {
		val key = key(id) ?: return false to null
		if (!available()) return false to null
		val before = get(id)
		val text = notice.trim().replace(Regex("\\s+"), " ").take(500).ifEmpty { null }
		if (!hidden && text == null) {
			jdbc.update("DELETE FROM TB_ANNOUNCEMENT_OVERRIDE WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?", key.first, key.second, key.third)
			return true to before
		}
		val now = Timestamp.valueOf(Kst.now())
		val updated = jdbc.update("UPDATE TB_ANNOUNCEMENT_OVERRIDE SET HIDDEN_YN=?, NOTICE=?, ADMIN_NAME=?, UPDATE_DT=? WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",
			if (hidden) "Y" else "N", text, admin.take(100), now, key.first, key.second, key.third)
		if (updated == 0) jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, HIDDEN_YN, NOTICE, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,?)",
			key.first, key.second, key.third, if (hidden) "Y" else "N", text, admin.take(100), now)
		return true to before
	}

	/** 숨긴 공고 수(목록 안내용). */
	fun hiddenCount(): Long? = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_ANNOUNCEMENT_OVERRIDE WHERE HIDDEN_YN='Y'", Long::class.java) } catch (missing: DataAccessException) { null }

	companion object {
		private val PART = Regex("[A-Za-z0-9_-]{1,40}")
		/** apt/H/P → (APT, H, P). public-rental → PUBLIC_RENTAL. */
		fun key(id: String): Triple<String, String, String>? {
			val parts = id.split('/')
			if (parts.size != 3 || parts[0] !in AnnouncementService.TYPES.keys || !PART.matches(parts[1]) || !PART.matches(parts[2])) return null
			return Triple(parts[0].uppercase().replace('-', '_'), parts[1], parts[2])
		}
	}
}
