package com.toady.admin.moderation

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.Locale

/**
 * 금칙어(database.sql 26번 TB_MODERATION_RULE). API가 1분마다 다시 읽어 이야기·쪽지에 적용한다.
 * 정규식은 받지 않고 '띄어쓰기·대소문자를 무시한 부분 일치'만 쓴다(API ModerationRules와 같은 규칙).
 * 호출: AdminController의 금칙어 화면(/admin/moderation).
 */
// @Service: 업무 로직 빈.
@Service
class RuleService(private val jdbc: JdbcTemplate) {
	data class Rule(val seq: Long, val pattern: String, val scope: String, val action: String, val enabled: Boolean, val note: String?,
	                val admin: String, val updatedAt: LocalDateTime?) {
		val scopeLabel get() = SCOPES[scope] ?: scope
		val actionLabel get() = ACTIONS[action] ?: action
	}
	/** 추가 결과. 컨트롤러가 결과마다 다른 안내를 보여 준다. */
	enum class Outcome { ADDED, INVALID, DUPLICATE, UNAVAILABLE }

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_MODERATION_RULE WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun list(): List<Rule> = try {
		jdbc.query("SELECT * FROM TB_MODERATION_RULE ORDER BY USE_YN DESC, RULE_SEQ DESC", { r, _ ->
			Rule(r.getLong("RULE_SEQ"), r.getString("PATTERN"), r.getString("SCOPE"), r.getString("ACTION"), r.getString("USE_YN") == "Y", r.getString("NOTE"),
				r.getString("ADMIN_NAME"), r.getTimestamp("UPDATE_DT")?.toLocalDateTime())
		})
	} catch (missing: DataAccessException) { emptyList() }

	fun find(seq: Long): Rule? = list().firstOrNull { it.seq == seq }

	/** 금칙어 추가. 띄어쓰기를 뺀 길이가 2~30자여야 한다(한 글자는 일상어를 너무 많이 막는다). 쪽지 표시(FLAG)는 쪽지에만 쓴다. */
	fun add(pattern: String, scope: String, action: String, note: String, admin: String): Pair<Outcome, Rule?> {
		val text = pattern.trim().replace(Regex("\\s+"), " ")
		val compact = normalize(text)
		if (compact.length !in 2..30 || scope !in SCOPES || action !in ACTIONS || (action == "FLAG" && scope != "DM")) return Outcome.INVALID to null
		if (!available()) return Outcome.UNAVAILABLE to null
		if (list().any { normalize(it.pattern) == compact && it.scope == scope }) return Outcome.DUPLICATE to null
		val now = Timestamp.valueOf(Kst.now())
		return try {
			jdbc.update("INSERT INTO TB_MODERATION_RULE (PATTERN, SCOPE, ACTION, USE_YN, NOTE, ADMIN_NAME, REG_DT, UPDATE_DT) VALUES (?,?,?,'Y',?,?,?,?)",
				text, scope, action, note.trim().take(200).ifEmpty { null }, admin.take(100), now, now)
			Outcome.ADDED to list().firstOrNull { it.pattern == text && it.scope == scope }
		} catch (duplicate: DuplicateKeyException) { Outcome.DUPLICATE to null }
	}

	/** 사용 켬·끔. 이미 같은 상태면 바꾸지 않고 false. */
	fun setEnabled(seq: Long, enabled: Boolean, admin: String): Boolean = try {
		jdbc.update("UPDATE TB_MODERATION_RULE SET USE_YN=?, ADMIN_NAME=?, UPDATE_DT=? WHERE RULE_SEQ=? AND USE_YN<>?",
			if (enabled) "Y" else "N", admin.take(100), Timestamp.valueOf(Kst.now()), seq, if (enabled) "Y" else "N") > 0
	} catch (missing: DataAccessException) { false }

	fun delete(seq: Long): Boolean = try { jdbc.update("DELETE FROM TB_MODERATION_RULE WHERE RULE_SEQ=?", seq) > 0 } catch (missing: DataAccessException) { false }

	/** 시험 문장에 걸리는 금칙어(사용 중인 것만). */
	fun test(text: String): List<Rule> {
		val compact = normalize(text)
		if (compact.isEmpty()) return emptyList()
		return list().filter { it.enabled && compact.contains(normalize(it.pattern)) }
	}

	companion object {
		val SCOPES = linkedMapOf("ALL" to "이야기·쪽지", "STORY" to "이야기만", "DM" to "쪽지만")
		val ACTIONS = linkedMapOf("BLOCK" to "올리지 못함", "FLAG" to "거래 의심 표시(쪽지)")
		/** 띄어쓰기·줄바꿈·대소문자를 무시한다(API ModerationRules.normalize와 같아야 한다). */
		fun normalize(text: String?): String = (text ?: "").lowercase(Locale.ROOT).replace(Regex("\\s+"), "")
	}
}
