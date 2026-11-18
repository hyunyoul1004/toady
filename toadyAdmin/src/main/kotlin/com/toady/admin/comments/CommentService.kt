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
 * 서비스 화면에서는 '이야기'라고 부른다. 테이블: TB_LTTOT_PBLANC_COMMENT(댓글), TB_COMMENT_ADMIN_HIDE(관리자 숨김 기록),
 * TB_COMMENT_OFFICIAL(운영팀 글 표시·고정). 호출: AdminController(이야기 목록·회원·공고 상세), ReportService(신고 처리).
 */
// @Service: 업무 로직 빈.
@Service
class CommentService(private val jdbc: JdbcTemplate, private val announcements: AnnouncementService) {
	/** 목록 한 줄. deleted는 DELETE_YN='Y'(숨김 또는 작성자 삭제)이고, adminHidden이 둘을 구분한다. */
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
	// @Transactional: 댓글 숨김과 숨김 기록을 한 트랜잭션으로 묶는다. 다른 빈에서 부를 때만 적용된다
	// (같은 클래스 안의 hideAllBy가 부르면 스프링 프록시를 거치지 않아 트랜잭션이 걸리지 않는다).
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

	/** 한 회원이 쓴 보이는 이야기를 모두 숨긴다(원글이면 답글도 함께). 숨긴 건수를 돌려준다. */
	fun hideAllBy(userSeq: Long, admin: String): Int {
		val roots = jdbc.queryForList("SELECT COMMENT_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N' ORDER BY COMMENT_SEQ LIMIT 1000", Long::class.java, userSeq)
		return roots.sumOf { hide(it, admin) }
	}

	/**
	 * 운영팀 계정으로 이야기를 쓴다(database.sql 27번 TB_COMMENT_OFFICIAL에 운영팀 글로 표시). parentSeq가 있으면 그 원글의 답글.
	 * pinned면 공고 이야기 맨 위에 고정한다(원글만). 새 이야기 번호를 돌려준다.
	 */
	// 댓글 INSERT와 운영팀 표시 INSERT를 한 트랜잭션으로 묶는다. 표시 저장이 실패하면 댓글도 되돌린다.
	@org.springframework.transaction.annotation.Transactional
	fun writeOfficial(announcementId: String, officialUserSeq: Long, content: String, parentSeq: Long?, pinned: Boolean, admin: String): Long {
		val (type, house, notice) = announcementId.split('/').also { require(it.size == 3) }
		val typeName = type.uppercase().replace('-', '_')
		if (parentSeq != null) {
			val ok = jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=? AND PARENT_COMMENT_SEQ IS NULL AND DELETE_YN='N' AND ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",
				Long::class.java, parentSeq, typeName, house, notice) ?: 0
			require(ok > 0) { "답글을 달 원글을 찾을 수 없습니다." }
		}
		val now = Timestamp.valueOf(Kst.now())
		// GeneratedKeyHolder: INSERT 후 DB가 매긴 COMMENT_SEQ(AUTO_INCREMENT)를 받아 오는 그릇.
		val key = org.springframework.jdbc.support.GeneratedKeyHolder()
		jdbc.update({ c ->
			c.prepareStatement("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, PARENT_COMMENT_SEQ, CONTENT, RECOMMEND, OPPOSE, DELETE_YN, CREATE_USER_SEQ, REG_DT) VALUES (?,?,?,?,?,0,0,'N',?,?)",
				arrayOf("COMMENT_SEQ")).apply {
				setString(1, typeName); setString(2, house); setString(3, notice)
				if (parentSeq == null) setNull(4, java.sql.Types.BIGINT) else setLong(4, parentSeq)
				setString(5, content.trim()); setLong(6, officialUserSeq); setTimestamp(7, now)
			}
		}, key)
		val seq = key.key!!.toLong()
		jdbc.update("INSERT INTO TB_COMMENT_OFFICIAL (COMMENT_SEQ, PINNED_YN, ADMIN_NAME, REG_DT) VALUES (?,?,?,?)", seq, if (pinned && parentSeq == null) "Y" else "N", admin.take(100), now)
		log.info("운영팀 이야기 작성: commentSeq={}, announcement={}, admin={}", seq, announcementId, admin)
		return seq
	}

	/** 운영팀 원글 고정·해제. 바뀐 행이 없으면 false. */
	fun setPinned(seq: Long, pinned: Boolean): Boolean = try {
		jdbc.update("UPDATE TB_COMMENT_OFFICIAL SET PINNED_YN=? WHERE COMMENT_SEQ=? AND PINNED_YN<>?", if (pinned) "Y" else "N", seq, if (pinned) "Y" else "N") > 0
	} catch (missing: DataAccessException) { false }

	/** 공고의 운영팀 글 번호와 고정 여부. */
	fun officialIn(announcementId: String): Map<Long, Boolean> {
		val parts = announcementId.split('/'); if (parts.size != 3) return emptyMap()
		return try {
			val result = LinkedHashMap<Long, Boolean>()
			jdbc.query("SELECT O.COMMENT_SEQ, O.PINNED_YN FROM TB_COMMENT_OFFICIAL O JOIN TB_LTTOT_PBLANC_COMMENT C ON C.COMMENT_SEQ=O.COMMENT_SEQ WHERE C.ANNOUNCEMENT_TYPE=? AND C.HOUSE_MANAGE_NO=? AND C.PBLANC_NO=?",
				{ r -> result[r.getLong(1)] = r.getString(2) == "Y" }, parts[0].uppercase().replace('-', '_'), parts[1], parts[2])
			result
		} catch (missing: DataAccessException) { emptyMap() }
	}

	/** 운영팀 글 테이블(database.sql 27번)이 있는지. 없으면 운영팀 글쓰기 칸을 숨긴다. */
	fun officialReady(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_COMMENT_OFFICIAL WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

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
	/** 되살리기 결과. 컨트롤러가 결과마다 다른 안내 문구를 보여 준다. */
	enum class RestoreResult { RESTORED, PARENT_HIDDEN, NOT_ADMIN_HIDDEN, NOT_FOUND }

	companion object {
		const val PAGE_SIZE = 30
		private val log = LoggerFactory.getLogger(CommentService::class.java)
	}
}
