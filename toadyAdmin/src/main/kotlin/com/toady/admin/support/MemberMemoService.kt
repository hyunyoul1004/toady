package com.toady.admin.support

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/** 회원 메모(문의 대응 기록, database.sql 27번 TB_MEMBER_MEMO). 관리자만 보며 탈퇴하면 지운다. */
// @Service: 업무 로직 빈. 생성자 매개변수(JdbcTemplate)는 스프링이 넣어 준다.
@Service
class MemberMemoService(private val jdbc: JdbcTemplate) {
	data class Memo(val seq: Long, val memo: String, val admin: String, val at: LocalDateTime?)

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). 없으면 화면에서 메모 칸을 숨긴다. */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_MEMBER_MEMO WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** 회원 한 명의 최근 메모 50개. 테이블이 없으면 빈 목록. */
	fun list(userSeq: Long): List<Memo> = try {
		jdbc.query("SELECT MEMO_SEQ, MEMO, ADMIN_NAME, REG_DT FROM TB_MEMBER_MEMO WHERE USER_SEQ=? ORDER BY MEMO_SEQ DESC LIMIT 50",
			{ r, _ -> Memo(r.getLong(1), r.getString(2), r.getString(3), r.getTimestamp(4)?.toLocalDateTime()) }, userSeq)
	} catch (missing: DataAccessException) { emptyList() }

	/** 메모 추가. 500자로 자르고 2자 미만이면 저장하지 않는다. */
	fun add(userSeq: Long, memo: String, admin: String): Boolean {
		val text = memo.trim().take(500)
		if (text.length < 2) return false
		return try { jdbc.update("INSERT INTO TB_MEMBER_MEMO (USER_SEQ, MEMO, ADMIN_NAME, REG_DT) VALUES (?,?,?,?)", userSeq, text, admin.take(100), Timestamp.valueOf(Kst.now())) > 0 }
		catch (missing: DataAccessException) { false }
	}
}
