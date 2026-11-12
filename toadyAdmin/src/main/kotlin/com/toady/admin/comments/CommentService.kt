package com.toady.admin.comments

import com.toady.admin.Kst
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 공고 댓글 모니터링과 숨김/복구. 서비스와 같은 규칙으로 원댓글을 숨기면 답글도 함께 숨긴다(DELETE_YN).
 * 숨긴 댓글은 지우지 않으므로 복구할 수 있다.
 */
@Service
class CommentService(private val jdbc: JdbcTemplate) {
	data class Comment(val seq: Long, val announcementId: String, val parentSeq: Long?, val content: String, val deleted: Boolean,
	                   val authorSeq: Long, val author: String?, val createdAt: LocalDateTime?, val updatedAt: LocalDateTime?) {
		/** 앱 공고 상세 주소(새 창). */
		val appPath get() = "/?subscriptSeq=" + java.net.URLEncoder.encode(announcementId, Charsets.UTF_8)
	}
	data class Page(val items: List<Comment>, val total: Long, val page: Int, val size: Int) {
		val pages get() = maxOf(1, ((total + size - 1) / size).toInt())
	}

	/** state: visible · hidden · all. 검색어는 내용·작성자 닉네임 부분 일치. */
	fun search(query: String?, state: String?, page: Int, size: Int = PAGE_SIZE): Page {
		val where = StringBuilder(" WHERE 1=1"); val args = ArrayList<Any>()
		when (state) {
			"hidden" -> where.append(" AND C.DELETE_YN='Y'")
			"all" -> {}
			else -> where.append(" AND C.DELETE_YN='N'")
		}
		query?.trim()?.takeIf { it.isNotEmpty() }?.take(100)?.let {
			val like = "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
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
		return Page(items, total, safePage, size)
	}

	/** 숨긴다. 원댓글이면 답글도 함께 숨긴다. 바뀐 댓글 수를 돌려준다. */
	@Transactional
	fun hide(seq: Long, admin: String): Int {
		val now = Timestamp.valueOf(Kst.now())
		val own = jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y', UPDATE_DT=? WHERE COMMENT_SEQ=? AND DELETE_YN='N'", now, seq)
		val replies = jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y', UPDATE_DT=? WHERE PARENT_COMMENT_SEQ=? AND DELETE_YN='N'", now, seq)
		if (own + replies > 0) log.info("관리자 댓글 숨김: commentSeq={}, replies={}, admin={}", seq, replies, admin)
		return own + replies
	}

	/** 숨긴 댓글을 되살린다. 원댓글이 숨겨진 답글은 맥락이 없으므로 원댓글부터 되살리게 한다. */
	@Transactional
	fun restore(seq: Long, admin: String): RestoreResult {
		val parent = jdbc.query("SELECT PARENT_COMMENT_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=?", { r, _ -> r.getLong(1).takeUnless { r.wasNull() } }, seq)
		if (parent.isEmpty()) return RestoreResult.NOT_FOUND
		parent.first()?.let { parentSeq ->
			val parentHidden = jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=? AND DELETE_YN='Y'", Long::class.java, parentSeq) ?: 0
			if (parentHidden > 0) return RestoreResult.PARENT_HIDDEN
		}
		val changed = jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='N', UPDATE_DT=? WHERE COMMENT_SEQ=? AND DELETE_YN='Y'", Timestamp.valueOf(Kst.now()), seq)
		if (changed == 1) log.info("관리자 댓글 복구: commentSeq={}, admin={}", seq, admin)
		return if (changed == 1) RestoreResult.RESTORED else RestoreResult.NOT_FOUND
	}
	enum class RestoreResult { RESTORED, PARENT_HIDDEN, NOT_FOUND }

	companion object {
		const val PAGE_SIZE = 30
		private val log = LoggerFactory.getLogger(CommentService::class.java)
	}
}
