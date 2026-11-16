package com.toady.admin.members

import com.toady.admin.Kst
import com.toady.admin.reports.ReportService
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 단계별 회원 제재(database.sql 26번 TB_USER_SANCTION).
 * - WARNING: 경고. 회원에게 안내만 보인다.
 * - STORY_BAN: 이야기(댓글) 쓰기 정지. API가 글·답글 작성과 수정을 막는다. 기간 또는 무기한.
 * - NICKNAME_RESET: 닉네임을 '회원번호' 기본값으로 바꾸고, 회원에게 새 닉네임을 정하라고 안내한다.
 * 쪽지 정지(TB_DM_SETTING)와 계정 잠금(LOCK_YN)은 기존 기능을 그대로 쓴다.
 * 회원에게는 종류·기간·사유 항목만 보이고 관리자 메모는 보이지 않는다.
 */
@Service
class SanctionService(private val jdbc: JdbcTemplate) {
	data class Sanction(val seq: Long, val kind: String, val reason: String, val memo: String?, val start: LocalDateTime?, val end: LocalDateTime?,
	                    val canceledAt: LocalDateTime?, val seenAt: LocalDateTime?, val admin: String) {
		val kindLabel get() = KINDS[kind] ?: kind
		val reasonLabel get() = REASONS[reason] ?: reason
		/** 지금 효력이 있는 이야기 정지인지(경고·닉네임 초기화는 효력 기간이 없다). */
		val active get() = canceledAt == null && kind == "STORY_BAN" && (end == null || end.isAfter(Kst.now()))
		val stateLabel get() = when {
			canceledAt != null -> "해제됨"
			kind != "STORY_BAN" -> if (seenAt != null) "회원 확인" else "안내 전"
			active -> if (end == null) "무기한" else "진행 중"
			else -> "끝남"
		}
	}
	enum class Outcome { DONE, INVALID, NOT_ACTIVE_MEMBER, UNAVAILABLE }
	data class Added(val outcome: Outcome, val nickname: String? = null, val previousNickname: String? = null)

	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun list(userSeq: Long): List<Sanction> = try {
		jdbc.query("SELECT * FROM TB_USER_SANCTION WHERE USER_SEQ=? ORDER BY SANCTION_SEQ DESC LIMIT 50", { r, _ ->
			Sanction(r.getLong("SANCTION_SEQ"), r.getString("KIND"), r.getString("REASON"), r.getString("MEMO"), r.getTimestamp("START_DT")?.toLocalDateTime(),
				r.getTimestamp("END_DT")?.toLocalDateTime(), r.getTimestamp("CANCELED_DT")?.toLocalDateTime(), r.getTimestamp("SEEN_DT")?.toLocalDateTime(), r.getString("ADMIN_NAME"))
		}, userSeq)
	} catch (missing: DataAccessException) { emptyList() }

	/** 지금 이야기 쓰기가 정지된 회원인지(목록·상세 표시용). */
	fun storyBannedUntil(userSeq: Long): Pair<Boolean, LocalDateTime?> = list(userSeq).firstOrNull { it.active }?.let { true to it.end } ?: (false to null)

	/**
	 * 제재를 더한다. days는 STORY_BAN에만 쓰며 1·3·7·30·-1(무기한). 이미 진행 중인 이야기 정지가 있으면 새 기간으로 바꾼다(겹쳐 쌓지 않는다).
	 * NICKNAME_RESET은 닉네임을 '회원{번호}'로 바꾼다(이미 누가 쓰고 있으면 뒤에 글자를 붙인다).
	 */
	@Transactional
	fun add(userSeq: Long, kind: String, reason: String, days: Int, memo: String, admin: String): Added {
		if (kind !in KINDS || reason !in REASONS || (kind == "STORY_BAN" && days !in setOf(1, 3, 7, 30, -1))) return Added(Outcome.INVALID)
		if (!available()) return Added(Outcome.UNAVAILABLE)
		val nickname = jdbc.query("SELECT USER_NIC_NAME FROM TB_USER WHERE USER_SEQ=? AND WITHDRAWAL_YN='N' FOR UPDATE", { r, _ -> r.getString(1) }, userSeq).firstOrNull()
			?: return Added(Outcome.NOT_ACTIVE_MEMBER)
		val now = Kst.now()
		val end = if (kind == "STORY_BAN" && days > 0) now.plusDays(days.toLong()) else null
		if (kind == "STORY_BAN") {
			jdbc.update("UPDATE TB_USER_SANCTION SET CANCELED_DT=? WHERE USER_SEQ=? AND KIND='STORY_BAN' AND CANCELED_DT IS NULL AND (END_DT IS NULL OR END_DT>?)",
				Timestamp.valueOf(now), userSeq, Timestamp.valueOf(now))
		}
		var renamed: String? = null
		if (kind == "NICKNAME_RESET") {
			renamed = freeNickname(userSeq)
			jdbc.update("UPDATE TB_USER SET USER_NIC_NAME=?, UPDATE_DT=? WHERE USER_SEQ=?", renamed, Timestamp.valueOf(now), userSeq)
		}
		jdbc.update("INSERT INTO TB_USER_SANCTION (USER_SEQ, KIND, REASON, MEMO, START_DT, END_DT, ADMIN_NAME, REG_DT) VALUES (?,?,?,?,?,?,?,?)",
			userSeq, kind, reason, memo.trim().take(300).ifEmpty { null }, Timestamp.valueOf(now), end?.let(Timestamp::valueOf), admin.take(100), Timestamp.valueOf(now))
		log.info("회원 제재: userSeq={}, kind={}, reason={}, days={}, admin={}", userSeq, kind, reason, days, admin)
		return Added(Outcome.DONE, renamed, nickname)
	}

	/** 진행 중인 제재를 해제한다(이야기 정지를 일찍 풀 때). 바뀐 행이 없으면 false. */
	fun cancel(userSeq: Long, sanctionSeq: Long, admin: String): Boolean {
		val changed = try {
			jdbc.update("UPDATE TB_USER_SANCTION SET CANCELED_DT=? WHERE SANCTION_SEQ=? AND USER_SEQ=? AND CANCELED_DT IS NULL", Timestamp.valueOf(Kst.now()), sanctionSeq, userSeq)
		} catch (missing: DataAccessException) { 0 }
		if (changed > 0) log.info("회원 제재 해제: userSeq={}, sanctionSeq={}, admin={}", userSeq, sanctionSeq, admin)
		return changed > 0
	}

	private fun freeNickname(userSeq: Long): String {
		val base = "회원$userSeq"
		for (suffix in listOf("") + ('a'..'z').map { it.toString() }) {
			val candidate = (base + suffix).take(15)
			val taken = jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER WHERE USER_NIC_NAME=? AND USER_SEQ<>?", Long::class.java, candidate, userSeq) ?: 0
			if (taken == 0L) return candidate
		}
		return "회원" + System.currentTimeMillis().toString().takeLast(10)
	}

	companion object {
		val KINDS = linkedMapOf("WARNING" to "경고", "STORY_BAN" to "이야기 쓰기 정지", "NICKNAME_RESET" to "닉네임 초기화")
		val REASONS = LinkedHashMap(ReportService.REASONS).apply { put("NICKNAME", "부적절한 닉네임") }
		val DAYS = linkedMapOf(1 to "1일", 3 to "3일", 7 to "7일", 30 to "30일", -1 to "무기한")
		private val log = LoggerFactory.getLogger(SanctionService::class.java)
	}
}
