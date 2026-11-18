package com.toady.admin.reports

import com.toady.admin.Kst
import com.toady.admin.Page
import com.toady.admin.announcements.AnnouncementService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 회원·이야기·쪽지 신고 처리와 쪽지 보내기 정지(database.sql 25번).
 * 쪽지 내용은 신고된 쪽지와 같은 대화의 앞뒤 두 통씩만 읽는다. 평소에는 쪽지를 열람하지 않는다.
 * 테이블: TB_USER_REPORT(신고), TB_DM_MESSAGE(쪽지 맥락), TB_DM_SETTING(쪽지 정지), TB_LTTOT_PBLANC_COMMENT(신고된 이야기).
 * 호출: AdminController의 신고 화면(/admin/reports)과 회원 상세.
 */
// @Service: 업무 로직 빈.
@Service
class ReportService(private val jdbc: JdbcTemplate, private val announcements: AnnouncementService) {
	/** 신고 대상의 내용. 쪽지는 신고된 쪽지(reported=true)와 앞뒤 맥락. */
	data class Line(val author: String?, val content: String, val at: LocalDateTime?, val reported: Boolean = false)

	/** 신고 한 건과 화면에 함께 보일 대상 내용·대상 회원의 대기 신고 수·쪽지 정지 상태. */
	data class Report(
		val seq: Long, val type: String, val targetSeq: Long, val reason: String, val detail: String?, val status: String,
		val reporterSeq: Long, val reporter: String?, val targetUserSeq: Long, val targetUser: String?, val createdAt: LocalDateTime?,
		val actionNote: String?, val handledBy: String?, val handledAt: LocalDateTime?,
		val lines: List<Line> = emptyList(), val commentDeleted: Boolean = false, val announcementId: String? = null, val houseNm: String? = null,
		val openAgainstTarget: Long = 0, val suspendedUntil: LocalDateTime? = null,
	) {
		val typeLabel get() = when (type) { "COMMENT" -> "이야기"; "MESSAGE" -> "쪽지"; else -> "회원" }
		val reasonLabel get() = REASONS[reason] ?: reason
		val statusLabel get() = when (status) { "DONE" -> "조치 완료"; "DISMISSED" -> "문제 없음"; else -> "대기" }
		val open get() = status == "OPEN"
		val suspended get() = suspendedUntil != null && suspendedUntil.isAfter(Kst.now())
		val appPath get() = announcementId?.let { "/?subscriptSeq=" + URLEncoder.encode(it, Charsets.UTF_8) + "&tabType=comment&comment=$targetSeq" }
	}

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_REPORT WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** 처리를 기다리는 신고 수(메뉴 배지·대시보드용). */
	fun openCount(): Long = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_REPORT WHERE STATUS='OPEN'", Long::class.java) ?: 0 } catch (missing: DataAccessException) { 0 }

	/** status: open · closed · all. 오래 기다린 신고부터(대기) 또는 최근 처리부터(처리됨). */
	fun search(status: String?, page: Int, size: Int = PAGE_SIZE): Page<Report> {
		if (!available()) return Page(emptyList(), 0, 0, size)
		val where = when (status) { "closed" -> " WHERE R.STATUS<>'OPEN'"; "all" -> ""; else -> " WHERE R.STATUS='OPEN'" }
		val order = if (status == "closed" || status == "all") " ORDER BY R.REPORT_SEQ DESC" else " ORDER BY R.REPORT_SEQ ASC"
		val total = jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_REPORT R$where", Long::class.java) ?: 0
		val safePage = page.coerceIn(0, 10_000)
		val rows = jdbc.query("""SELECT R.*, A.USER_NIC_NAME AS REPORTER_NAME, T.USER_NIC_NAME AS TARGET_NAME FROM TB_USER_REPORT R
			LEFT JOIN TB_USER A ON A.USER_SEQ=R.REPORTER_SEQ LEFT JOIN TB_USER T ON T.USER_SEQ=R.TARGET_USER_SEQ$where$order LIMIT ? OFFSET ?""".trimIndent(), { r, _ ->
			Report(r.getLong("REPORT_SEQ"), r.getString("TARGET_TYPE"), r.getLong("TARGET_SEQ"), r.getString("REASON"), r.getString("DETAIL"), r.getString("STATUS"),
				r.getLong("REPORTER_SEQ"), r.getString("REPORTER_NAME"), r.getLong("TARGET_USER_SEQ"), r.getString("TARGET_NAME"), r.getTimestamp("REG_DT")?.toLocalDateTime(),
				r.getString("ACTION_NOTE"), r.getString("HANDLED_BY"), r.getTimestamp("HANDLED_DT")?.toLocalDateTime())
		}, size, safePage * size)
		val names = HashMap<String, String?>()
		val items = rows.map { report ->
			val withTarget = when (report.type) {
				"COMMENT" -> comment(report)
				"MESSAGE" -> report.copy(lines = messageContext(report.targetSeq))
				else -> report
			}
			withTarget.copy(openAgainstTarget = count("SELECT COUNT(*) FROM TB_USER_REPORT WHERE TARGET_USER_SEQ=? AND STATUS='OPEN'", report.targetUserSeq),
				suspendedUntil = suspendedUntil(report.targetUserSeq))
		}
		items.mapNotNull { it.announcementId }.distinct().takeIf { it.isNotEmpty() }?.let { names.putAll(announcements.names(it)) }
		return Page(items.map { it.copy(houseNm = it.announcementId?.let(names::get)) }, total, safePage, size)
	}

	/** 이야기 신고에 원문·숨김 여부·공고 ID를 붙인다. 댓글이 없어졌으면 '(지워진 이야기)'로 보인다. */
	private fun comment(report: Report): Report {
		val found = jdbc.query("SELECT ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, CONTENT, DELETE_YN, REG_DT FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=?", { r, _ ->
			report.copy(lines = listOf(Line(report.targetUser, r.getString("CONTENT"), r.getTimestamp("REG_DT")?.toLocalDateTime(), true)),
				commentDeleted = r.getString("DELETE_YN") == "Y",
				announcementId = "${r.getString(1).lowercase().replace('_', '-')}/${r.getString(2)}/${r.getString(3)}")
		}, report.targetSeq)
		return found.firstOrNull() ?: report.copy(lines = listOf(Line(null, "(지워진 이야기)", null, true)))
	}

	/** 신고된 쪽지와 같은 대화의 앞뒤 두 통. 보관 기간이 지나 지워졌으면 그렇게 알린다. */
	private fun messageContext(messageSeq: Long): List<Line> = try {
		val thread = jdbc.query("SELECT THREAD_SEQ FROM TB_DM_MESSAGE WHERE MESSAGE_SEQ=?", { r, _ -> r.getLong(1) }, messageSeq).firstOrNull()
		if (thread == null) listOf(Line(null, "(보관 기간이 지나 지워진 쪽지)", null, true))
		else {
			// 같은 SQL 틀에 비교 기호·정렬 방향만 바꿔 '신고 쪽지 포함 이전 3통'과 '이후 2통'을 읽는다.
			val sql = """SELECT M.MESSAGE_SEQ, M.CONTENT, M.REG_DT, U.USER_NIC_NAME FROM TB_DM_MESSAGE M LEFT JOIN TB_USER U ON U.USER_SEQ=M.SENDER_SEQ
				WHERE M.THREAD_SEQ=? AND M.MESSAGE_SEQ %s ? ORDER BY M.MESSAGE_SEQ %s LIMIT ?""".trimIndent()
			val map = { r: java.sql.ResultSet, _: Int -> Line(r.getString(4), r.getString(2), r.getTimestamp(3)?.toLocalDateTime(), r.getLong(1) == messageSeq) }
			val before = jdbc.query(sql.format("<=", "DESC"), map, thread, messageSeq, 3).reversed()
			val after = jdbc.query(sql.format(">", "ASC"), map, thread, messageSeq, 2)
			before + after
		}
	} catch (missing: DataAccessException) { listOf(Line(null, "(쪽지를 읽지 못했습니다. database.sql 25번 권한 확인)", null, true)) }

	/** 신고를 닫는다. done: 조치함, dismiss: 문제 없음. 같은 대상의 다른 대기 신고도 함께 닫을 수 있다. */
	fun close(seq: Long, outcome: String, note: String, admin: String, sameTarget: Boolean): Int {
		val status = when (outcome) { "done" -> "DONE"; "dismiss" -> "DISMISSED"; else -> return 0 }
		val now = Timestamp.valueOf(Kst.now())
		val memo = note.trim().take(200).ifEmpty { null }
		val changed = if (sameTarget) {
			val target = jdbc.query("SELECT TARGET_TYPE, TARGET_SEQ FROM TB_USER_REPORT WHERE REPORT_SEQ=?", { r, _ -> r.getString(1) to r.getLong(2) }, seq).firstOrNull() ?: return 0
			jdbc.update("UPDATE TB_USER_REPORT SET STATUS=?, ACTION_NOTE=?, HANDLED_BY=?, HANDLED_DT=? WHERE TARGET_TYPE=? AND TARGET_SEQ=? AND STATUS='OPEN'",
				status, memo, admin.take(100), now, target.first, target.second)
		} else jdbc.update("UPDATE TB_USER_REPORT SET STATUS=?, ACTION_NOTE=?, HANDLED_BY=?, HANDLED_DT=? WHERE REPORT_SEQ=? AND STATUS='OPEN'",
			status, memo, admin.take(100), now, seq)
		if (changed > 0) log.info("신고 처리: reportSeq={}, status={}, count={}, admin={}", seq, status, changed, admin)
		return changed
	}

	/** 쪽지 정지 끝 시각. 정지한 적이 없거나 테이블이 없으면 null. */
	fun suspendedUntil(userSeq: Long): LocalDateTime? = try {
		jdbc.query("SELECT SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=?", { r, _ -> r.getTimestamp(1)?.toLocalDateTime() }, userSeq).firstOrNull()
	} catch (missing: DataAccessException) { null }

	/** 쪽지 보내기 정지. days가 0이면 해제, 음수면 무기한(2099년). */
	fun suspend(userSeq: Long, days: Int, admin: String): Boolean {
		// 없는 회원·탈퇴 회원에게는 설정 행을 만들지 않는다.
		val active = jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_SEQ=? AND WITHDRAWAL_YN='N'", Long::class.java, userSeq) ?: 0
		if (active == 0L) return false
		val now = Kst.now()
		val until = when { days == 0 -> null; days < 0 -> LocalDateTime.of(2099, 12, 31, 23, 59); else -> now.plusDays(days.toLong()) }
		val untilTs = until?.let(Timestamp::valueOf)
		val nowTs = Timestamp.valueOf(now)
		return try {
			if (jdbc.update("UPDATE TB_DM_SETTING SET SUSPENDED_UNTIL=?, UPDATE_DT=? WHERE USER_SEQ=?", untilTs, nowTs, userSeq) == 0 && until != null) {
				try { jdbc.update("INSERT INTO TB_DM_SETTING (USER_SEQ, RECEIVE_POLICY, SUSPENDED_UNTIL, UPDATE_DT) VALUES (?, 'ALL', ?, ?)", userSeq, untilTs, nowTs) }
				// 그 사이 다른 요청이 행을 먼저 만들었으면 INSERT가 키 중복으로 실패하므로 다시 UPDATE한다.
				catch (raced: DuplicateKeyException) { jdbc.update("UPDATE TB_DM_SETTING SET SUSPENDED_UNTIL=?, UPDATE_DT=? WHERE USER_SEQ=?", untilTs, nowTs, userSeq) }
			}
			log.info("쪽지 정지 변경: userSeq={}, until={}, admin={}", userSeq, until, admin)
			true
		} catch (failure: DataAccessException) {
			log.warn("쪽지 정지를 저장하지 못했습니다(TB_DM_SETTING, database.sql 25번 권한 확인): {}", failure.mostSpecificCause.message)
			false
		}
	}

	private fun count(sql: String, vararg args: Any): Long = jdbc.queryForObject(sql, Long::class.java, *args) ?: 0

	// REASONS: 신고 사유 코드 → 화면 이름. 서비스(API) 신고 화면과 같은 코드를 쓴다.
	companion object {
		const val PAGE_SIZE = 20
		val REASONS = linkedMapOf("TRADE" to "통장·전매 거래", "SPAM" to "홍보·광고", "ABUSE" to "욕설·괴롭힘", "PRIVACY" to "개인정보 노출", "ETC" to "기타")
		private val log = LoggerFactory.getLogger(ReportService::class.java)
	}
}
