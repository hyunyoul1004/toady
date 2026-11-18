package com.toady.admin.audit

import com.toady.admin.Kst
import com.toady.admin.Page
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 관리자 작업 이력(database.sql 26번 TB_ADMIN_AUDIT). 관리자 화면에서 바꾼 모든 것과 회원 상세 열람을 남긴다.
 * 기록에 실패해도(테이블 미적용 등) 원래 조치는 그대로 두고 로그로만 남긴다. 이력은 고치거나 지우지 않는다.
 * (1년이 지난 이력은 스케줄러의 DataRetentionJob이 지운다.) 호출: 거의 모든 관리자 조치 서비스·컨트롤러.
 */
// @Service: 업무 로직 빈. 생성자의 JdbcTemplate은 스프링이 넣어 준다.
@Service
class AuditService(private val jdbc: JdbcTemplate) {
	/** 이력 한 줄. actionLabel·targetLabel은 화면에 보일 한국어 이름이다. */
	data class Entry(val seq: Long, val admin: String, val action: String, val targetType: String, val targetId: String,
	                 val before: String?, val after: String?, val note: String?, val at: LocalDateTime?) {
		val actionLabel get() = ACTIONS[action] ?: action
		val targetLabel get() = TARGETS[targetType] ?: targetType
		/** 대상으로 바로 가는 관리자 주소(있으면). */
		val targetPath get() = when (targetType) {
			"MEMBER" -> targetId.toLongOrNull()?.let { "/admin/members/$it" }
			"ANNOUNCEMENT" -> "/admin/announcements/$targetId"
			"RULE" -> "/admin/moderation"
			"REPORT" -> "/admin/reports?status=all"
			"JOB", "SCHEDULE" -> "/admin/jobs"
			"SETTING" -> "/admin/settings"; "NOTICE" -> "/admin/notices"; "FAQ" -> "/admin/faq"; "AREA" -> "/admin/areas"; "PUSH" -> "/admin/push"
			else -> null
		}
	}

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_ADMIN_AUDIT WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** 기록한다. 긴 값은 잘라 저장한다. */
	fun record(admin: String, action: String, targetType: String, targetId: Any, before: String? = null, after: String? = null, note: String? = null) {
		try {
			jdbc.update("INSERT INTO TB_ADMIN_AUDIT (ADMIN_NAME, ACTION, TARGET_TYPE, TARGET_ID, BEFORE_VALUE, AFTER_VALUE, NOTE, REG_DT) VALUES (?,?,?,?,?,?,?,?)",
				admin.take(100), action.take(40), targetType.take(20), targetId.toString().take(120), before?.take(500), after?.take(500),
				note?.trim()?.take(300)?.ifEmpty { null }, Timestamp.valueOf(Kst.now()))
		} catch (failure: DataAccessException) {
			log.warn("관리자 작업 이력을 남기지 못했습니다(database.sql 26번 확인): action={}, target={}:{}, admin={}", action, targetType, targetId, admin)
		}
	}

	/** 목록. targetType·action·검색어(대상 ID·메모·관리자) 조건, 최신순. */
	fun search(targetType: String?, action: String?, query: String?, page: Int, size: Int = PAGE_SIZE): Page<Entry> {
		if (!available()) return Page(emptyList(), 0, 0, size)
		val where = StringBuilder(" WHERE 1=1"); val args = ArrayList<Any>()
		targetType?.takeIf { it in TARGETS }?.let { where.append(" AND TARGET_TYPE=?"); args += it }
		action?.takeIf { it in ACTIONS }?.let { where.append(" AND ACTION=?"); args += it }
		query?.trim()?.takeIf { it.isNotEmpty() }?.take(100)?.let {
			val like = com.toady.admin.likeContains(it)
			where.append(" AND (TARGET_ID LIKE ? OR NOTE LIKE ? OR ADMIN_NAME LIKE ?)"); args += like; args += like; args += like
		}
		val total = jdbc.queryForObject("SELECT COUNT(*) FROM TB_ADMIN_AUDIT$where", Long::class.java, *args.toTypedArray()) ?: 0
		val safePage = page.coerceIn(0, 10_000)
		val items = jdbc.query("SELECT * FROM TB_ADMIN_AUDIT$where ORDER BY AUDIT_SEQ DESC LIMIT ? OFFSET ?", { r, _ -> row(r) }, *(args + size + safePage * size).toTypedArray())
		return Page(items, total, safePage, size)
	}

	/** 한 대상의 최근 이력(회원·공고 상세 화면용). 열람 기록은 빼고 보여 준다. */
	fun forTarget(targetType: String, targetId: Any, limit: Int = 20): List<Entry> = try {
		jdbc.query("SELECT * FROM TB_ADMIN_AUDIT WHERE TARGET_TYPE=? AND TARGET_ID=? AND ACTION<>'MEMBER_VIEW' ORDER BY AUDIT_SEQ DESC LIMIT ?",
			{ r, _ -> row(r) }, targetType, targetId.toString(), limit)
	} catch (missing: DataAccessException) { emptyList() }

	/** DB 한 행을 Entry로 바꾼다. */
	private fun row(r: java.sql.ResultSet) = Entry(r.getLong("AUDIT_SEQ"), r.getString("ADMIN_NAME"), r.getString("ACTION"), r.getString("TARGET_TYPE"),
		r.getString("TARGET_ID"), r.getString("BEFORE_VALUE"), r.getString("AFTER_VALUE"), r.getString("NOTE"), r.getTimestamp("REG_DT")?.toLocalDateTime())

	// ACTIONS·TARGETS: 저장 코드 → 화면 이름. 검색 조건도 이 목록에 있는 값만 받아 SQL에 넣는다.
	companion object {
		const val PAGE_SIZE = 50
		val ACTIONS = linkedMapOf(
			"MEMBER_VIEW" to "회원 상세 열람", "MEMBER_LOCK" to "회원 잠금", "MEMBER_UNLOCK" to "회원 잠금 해제",
			"SANCTION_ADD" to "제재", "SANCTION_CANCEL" to "제재 해제", "NICKNAME_RESET" to "닉네임 초기화", "DM_SUSPEND" to "쪽지 정지 변경",
			"COMMENT_HIDE" to "이야기 숨김", "COMMENT_RESTORE" to "이야기 되살림", "REPORT_CLOSE" to "신고 처리",
			"RULE_ADD" to "금칙어 추가", "RULE_TOGGLE" to "금칙어 사용 변경", "RULE_DELETE" to "금칙어 삭제",
			"ANNOUNCEMENT_OVERRIDE" to "공고 숨김·안내 변경", "VISITOR_EXCLUDE" to "지표 제외 브라우저", "VISITOR_INCLUDE" to "지표 제외 해제",
			"JOB_START" to "수집 시작", "JOB_CANCEL" to "수집 중지", "JOB_RETRY" to "수집 다시 실행", "SCHEDULE_SAVE" to "자동 수집 일정",
			"SETTING_SAVE" to "서비스 설정 변경", "NOTICE_ADD" to "공지 추가", "NOTICE_TOGGLE" to "공지 노출 변경", "NOTICE_DELETE" to "공지 삭제",
			"FAQ_ADD" to "질문 추가", "FAQ_UPDATE" to "질문 수정", "FAQ_DELETE" to "질문 삭제", "AREA_ADD" to "생활권 추가", "AREA_UPDATE" to "생활권 수정",
			"LOCATION_SAVE" to "위치 주소 확인", "RECOLLECT" to "공고 다시 수집", "OFFICIAL_WRITE" to "운영팀 이야기", "OFFICIAL_PIN" to "운영팀 글 고정 변경",
			"COMMENT_HIDE_ALL" to "회원 이야기 모두 숨김", "PUSH_TEST" to "푸시 시험 발송", "PUSH_BROADCAST" to "푸시 전체 발송",
			"MEMBER_MEMO" to "회원 메모", "MEMBER_RESEND" to "인증 메일 다시 보내기", "MEMBER_WITHDRAW" to "탈퇴 처리 대행")
		val TARGETS = linkedMapOf("MEMBER" to "회원", "COMMENT" to "이야기", "REPORT" to "신고", "RULE" to "금칙어", "ANNOUNCEMENT" to "공고",
			"VISITOR" to "지표 제외", "JOB" to "수집 작업", "SCHEDULE" to "수집 일정", "SETTING" to "서비스 설정", "NOTICE" to "사이트 공지",
			"FAQ" to "자주 묻는 질문", "AREA" to "생활권", "PUSH" to "푸시")
		private val log = LoggerFactory.getLogger(AuditService::class.java)
	}
}
