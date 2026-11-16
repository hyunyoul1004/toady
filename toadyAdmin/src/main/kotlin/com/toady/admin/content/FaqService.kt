package com.toady.admin.content

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 자주 묻는 질문(database.sql 27번 TB_FAQ_ITEM). 처음에는 API가 코드의 기본 질문으로 채운다.
 * 앱 화면(/faq)과 검색용 서버 페이지(/help/faq)가 1분 안에 같은 내용으로 바뀐다. 답의 [문구](/경로)는 사이트 안 링크가 된다.
 */
@Service
class FaqService(private val jdbc: JdbcTemplate) {
	data class Item(val id: String, val groupId: String, val groupTitle: String, val question: String, val answer: String, val order: Int,
	                val enabled: Boolean, val admin: String, val updatedAt: LocalDateTime?)
	data class Group(val id: String, val title: String, val items: List<Item>)

	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAQ_ITEM WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun groups(): List<Group> = try {
		jdbc.query("SELECT * FROM TB_FAQ_ITEM ORDER BY SORT_ORDER, ITEM_ID") { r, _ ->
			Item(r.getString("ITEM_ID"), r.getString("GROUP_ID"), r.getString("GROUP_TITLE"), r.getString("QUESTION"), r.getString("ANSWER"), r.getInt("SORT_ORDER"),
				r.getString("USE_YN") == "Y", r.getString("ADMIN_NAME"), r.getTimestamp("UPDATE_DT")?.toLocalDateTime())
		}.groupBy { it.groupId }.map { (id, items) -> Group(id, items.first().groupTitle, items) }
	} catch (missing: DataAccessException) { emptyList() }

	fun find(id: String): Item? = groups().flatMap { it.items }.firstOrNull { it.id == id }

	/** 추가·수정 공통 검사. 문제 없으면 null. */
	private fun invalid(id: String, groupId: String, groupTitle: String, question: String, answer: String): String? = when {
		!ID.matches(id) || !ID.matches(groupId) -> "질문·묶음 ID는 영문 소문자·숫자·하이픈 2~40자입니다."
		groupTitle.trim().length !in 2..50 -> "묶음 이름은 2~50자입니다."
		question.trim().length !in 5..200 -> "질문은 5~200자입니다."
		answer.trim().length !in 10..4000 -> "답은 10~4000자입니다."
		else -> null
	}

	fun add(id: String, groupId: String, groupTitle: String, question: String, answer: String, order: Int, admin: String): String? {
		invalid(id, groupId, groupTitle, question, answer)?.let { return it }
		if (!available()) return "자주 묻는 질문 테이블이 없습니다. database.sql 27번과 관리자 계정 권한을 적용하세요."
		return try {
			jdbc.update("INSERT INTO TB_FAQ_ITEM (ITEM_ID, GROUP_ID, GROUP_TITLE, QUESTION, ANSWER, SORT_ORDER, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,'Y',?,?)",
				id, groupId, groupTitle.trim(), question.trim(), answer.trim(), order, admin.take(100), Timestamp.valueOf(Kst.now()))
			null
		} catch (duplicate: DuplicateKeyException) { "이미 있는 질문 ID입니다." }
	}

	fun update(id: String, groupId: String, groupTitle: String, question: String, answer: String, order: Int, enabled: Boolean, admin: String): String? {
		invalid(id, groupId, groupTitle, question, answer)?.let { return it }
		val changed = try {
			jdbc.update("UPDATE TB_FAQ_ITEM SET GROUP_ID=?, GROUP_TITLE=?, QUESTION=?, ANSWER=?, SORT_ORDER=?, USE_YN=?, ADMIN_NAME=?, UPDATE_DT=? WHERE ITEM_ID=?",
				groupId, groupTitle.trim(), question.trim(), answer.trim(), order, if (enabled) "Y" else "N", admin.take(100), Timestamp.valueOf(Kst.now()), id)
		} catch (missing: DataAccessException) { 0 }
		return if (changed > 0) null else "없는 질문입니다."
	}

	fun delete(id: String): Boolean = try { jdbc.update("DELETE FROM TB_FAQ_ITEM WHERE ITEM_ID=?", id) > 0 } catch (missing: DataAccessException) { false }

	companion object { val ID = Regex("[a-z0-9-]{2,40}") }
}
