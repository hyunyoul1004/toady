package com.toady.admin.comments

import com.toady.admin.Kst
import com.toady.admin.Page
import com.toady.admin.announcements.AnnouncementService
import com.toady.admin.likeContains
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URLEncoder
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 공고 댓글 모니터링과 숨김/복구. 서비스와 같은 규칙으로 원댓글을 숨기면 답글도 함께 숨긴다(DELETE_YN).
 * 숨긴 댓글은 지우지 않으므로 복구할 수 있다.
 */
@Service
class CommentService(private val jdbc: JdbcTemplate, private val announcements: AnnouncementService) {
	data class Comment(val seq: Long, val announcementId: String, val parentSeq: Long?, val content: String, val deleted: Boolean,
	                   val authorSeq: Long, val author: String?, val createdAt: LocalDateTime?, val updatedAt: LocalDateTime?, val houseNm: String? = null,
	                   val adminHidden: Boolean = false) {
		/** 숨김 사유. 작성자가 직접 지운 댓글은 관리자가 되살리지 않는다. */
		val hiddenLabel get() = if (adminHidden) "관리자 숨김" else "작성자 삭제"
		/** 앱 공고 상세 주소(새 창). */
		val appPath get() = "/?subscriptSeq=" + URLEncoder.encode(announcementId, Charsets.UTF_8)
		/** 관리자 공고 상세 주소. */
		val adminPath get() = "/admin/announcements/$announcementId"
	}

	/**
	 * state: visible · hidden · all. 검색어는 내용·작성자 닉네임 부분 일치.
	 * announcementId·authorSeq를 주면 그 공고·회원의 댓글만(상세 화면용).
	 */
	fun search(query: String?, state: String?, page: Int, size: Int = PAGE_SIZE, announcementId: String? = null, authorSeq: Long? = null): Page<Comment> {
		val where = StringBuilder(" WHERE 1=1"); val args = ArrayList<Any>()
		announcementId?.split('/')?.takeIf { it.size == 3 }?.let { (type, house, notice) ->
			where.append(" AND C.ANNOUNCEMENT_TYPE=? AND C.HOUSE_MANAGE_NO=? AND C.PBLANC_NO=?")
			args += type.uppercase().replace('-', '_'); args += house; args += notice
		}
		authorSeq?.let { where.append(" AND C.CREATE_USER_SEQ=?"); args += it }
		when (state) {
			"hidden" -> where.append(" AND C.DELETE_YN='Y'")
			"all" -> {}
			else -> where.append(" AND C.DELETE_YN='N'")
		}
		query?.trim()?.takeIf { it.isNotEmpty() }?.take(100)?.let {
			val like = likeContains(it)
			where.append(" AND (C.CONTENT LIKE ? OR U.USER_NIC_NAME LIKE ?)"); args += like; args += like
		}
		val from = " FROM TB_LTTOT_PBLANC_COMMENT C LEFT JOIN TB_USER U ON U.USER_SEQ=C.CREATE_USER_SEQ"
		val total = jdbc.queryForObject("SELECT COUNT(*)$from$where", Long::class.java, *args.toTypedArray()) ?: 0
		val safePage = page.coerceIn(0, 10_000)
		val items = jdbc.query("SELECT C.*,U.USER_NIC_NAME$from$where ORDER BY C.COMMENT_SEQ DESC LIMIT ? OFFSET ?", { r, _ ->
			val parent = r.getLong("PARENT_COMMENT_SEQ").takeUnless { r.wasNull() }
			Comment(r.getLong("COMMENT_SEQ"), "${r.getString("ANNOUNCEMENT_TYPE").lowercase().replace('_', '-')}/${r.getString("HOUSE_MANAGE_NO")}/${r.getString("PBLANC_NO")}",
				parent, r.getString("CONTENT"), r.getString("DELETE_YN") == "Y", r.getLong("CREATE_USER_SEQ"), r.getString("USER_NIC_NAME"),
				r.getTimestamp("REG_DT")?.toLocalDateTime(), r.getTimestamp("UPDATE_DT")?.toLocalDateTime())
		}, *(args + size + safePage * size).toTypedArray())
		// 공고 ID 대신 단지명을 함께 보여 준다.
		val names = announcements.names(items.map { it.announcementId }.distinct())
		val hiddenByAdmin = adminHidden(items.filter { it.deleted }.map { it.seq })
		return Page(items.map { it.copy(houseNm = names[it.announcementId], adminHidden = it.seq in hiddenByAdmin) }, total, safePage, size)
	}

	/** 관리자가 숨긴 댓글 번호. 기록 테이블(database.sql 21번)이 없으면 비어 있다(되살리기도 막힌다). */
	private fun adminHidden(seqs: List<Long>): Set<Long> {
		if (seqs.isEmpty()) return emptySet()
		return try {
			jdbc.queryForList("SELECT COMMENT_SEQ FROM TB_COMMENT_ADMIN_HIDE WHERE COMMENT_SEQ IN (${seqs.joinToString(",") { "?" }})", Long::class.java, *seqs.toTypedArray()).toSet()
		} catch (missing: DataAccessException) { emptySet() }
	}

	/**
	 * 숨긴다. 원댓글이면 답글도 함께 숨긴다. 바뀐 댓글 수를 돌려준다.
	 * 관리자가 숨긴 댓글만 기록해 두어, 작성자가 직접 지운 댓글(같은 DELETE_YN='Y')을 되살리지 않게 한다.
	 */
	@Transactional
	fun hide(seq: Long, admin: String): Int {
		val now = Timestamp.valueOf(Kst.now())
		val targets = jdbc.queryForList("SELECT COMMENT_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE (COMMENT_SEQ=? OR PARENT_COMMENT_SEQ=?) AND DELETE_YN='N'", Long::class.java, seq, seq)
		if (targets.isEmpty()) return 0
		val changed = jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y', UPDATE_DT=? WHERE COMMENT_SEQ IN (${targets.joinToString(",") { "?" }}) AND DELETE_YN='N'",
			now, *targets.toTypedArray())
		// 건별로 남긴다(이미 기록이 있으면 그대로 두고 나머지 답글도 계속 기록한다).
		val unrecorded = targets.count {
			try {
				jdbc.update("INSERT INTO TB_COMMENT_ADMIN_HIDE (COMMENT_SEQ, ADMIN_NAME, REG_DT) VALUES (?,?,?)", it, admin.take(100), now); false
			} catch (duplicate: DuplicateKeyException) { false
			} catch (failure: DataAccessException) { true }
		}
		if (unrecorded > 0) log.warn("관리자 숨김 기록 {}건을 남기지 못했습니다(TB_COMMENT_ADMIN_HIDE, database.sql 21번 적용 여부 확인).", unrecorded)
		if (changed > 0) log.info("관리자 댓글 숨김: commentSeq={}, total={}, admin={}", seq, changed, admin)
		return changed
	}

	/** 관리자가 숨긴 댓글을 되살린다. 작성자가 지운 댓글과 원댓글이 숨겨진 답글은 되살리지 않는다. */
	@Transactional
	fun restore(seq: Long, admin: String): RestoreResult {
		val parent = jdbc.query("SELECT PARENT_COMMENT_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=? AND DELETE_YN='Y'", { r, _ -> r.getLong(1).takeUnless { r.wasNull() } }, seq)
		if (parent.isEmpty()) return RestoreResult.NOT_FOUND
		if (seq !in adminHidden(listOf(seq))) return RestoreResult.NOT_ADMIN_HIDDEN
		parent.first()?.let { parentSeq ->
			val parentHidden = jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=? AND DELETE_YN='Y'", Long::class.java, parentSeq) ?: 0
			if (parentHidden > 0) return RestoreResult.PARENT_HIDDEN
		}
		val now = Timestamp.valueOf(Kst.now())
		// 원댓글을 숨긴 시각. hide()는 원댓글과 함께 숨긴 답글에 같은 시각을 남기므로, 그 시각의 답글만 함께 되살린다.
		val hiddenAt = jdbc.query("SELECT REG_DT FROM TB_COMMENT_ADMIN_HIDE WHERE COMMENT_SEQ=?", { r, _ -> r.getTimestamp(1) }, seq).firstOrNull()
		val changed = jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='N', UPDATE_DT=? WHERE COMMENT_SEQ=? AND DELETE_YN='Y'", now, seq)
		jdbc.update("DELETE FROM TB_COMMENT_ADMIN_HIDE WHERE COMMENT_SEQ=?", seq)
		// 원댓글을 숨길 때 함께 숨긴 답글만 되살린다. 그보다 먼저 따로 숨긴 답글(예: 욕설)과 작성자가 지운 답글은 그대로 둔다.
		if (changed == 1 && parent.first() == null && hiddenAt != null) {
			val hiddenByAdmin = jdbc.queryForList("""SELECT c.COMMENT_SEQ FROM TB_LTTOT_PBLANC_COMMENT c JOIN TB_COMMENT_ADMIN_HIDE h ON h.COMMENT_SEQ=c.COMMENT_SEQ
				WHERE c.PARENT_COMMENT_SEQ=? AND c.DELETE_YN='Y' AND h.REG_DT=?""".trimIndent(), Long::class.java, seq, hiddenAt)
			if (hiddenByAdmin.isNotEmpty()) {
				val marks = hiddenByAdmin.joinToString(",") { "?" }
				jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='N', UPDATE_DT=? WHERE COMMENT_SEQ IN ($marks) AND DELETE_YN='Y'", now, *hiddenByAdmin.toTypedArray())
				jdbc.update("DELETE FROM TB_COMMENT_ADMIN_HIDE WHERE COMMENT_SEQ IN ($marks)", *hiddenByAdmin.toTypedArray())
			}
		}
		if (changed == 1) log.info("관리자 댓글 복구: commentSeq={}, admin={}", seq, admin)
		return if (changed == 1) RestoreResult.RESTORED else RestoreResult.NOT_FOUND
	}
	enum class RestoreResult { RESTORED, PARENT_HIDDEN, NOT_ADMIN_HIDDEN, NOT_FOUND }

	companion object {
		const val PAGE_SIZE = 30
		private val log = LoggerFactory.getLogger(CommentService::class.java)
	}
}
