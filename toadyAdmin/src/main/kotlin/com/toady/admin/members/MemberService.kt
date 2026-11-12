package com.toady.admin.members

import com.toady.admin.Kst
import org.slf4j.LoggerFactory
import com.toady.admin.announcements.AnnouncementService
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 회원 조회와 잠금/해제. 잠긴 계정은 API 로그인에서 거절된다(LOCK_YN).
 * 비밀번호·인증 코드는 다루지 않으며, 조치는 관리자 이름과 함께 로그로 남긴다.
 */
@Service
class MemberService(private val jdbc: JdbcTemplate, private val announcements: AnnouncementService) {
	/** 관심 공고 한 줄. 회원의 비공개 메모는 관리자 화면에도 보여 주지 않는다. */
	data class Favorite(val id: String, val houseNm: String?, val stage: Int, val savedAt: LocalDateTime?) {
		val stageLabel get() = STAGES.getOrElse(stage) { "관심" }
		val adminPath get() = "/admin/announcements/$id"
	}
	data class Detail(val member: Member, val updatedAt: LocalDateTime?, val favorites: List<Favorite>?, val pushSubscriptions: Long?)

	fun detail(seq: Long): Detail? {
		val member = search(null, null, 0, 1, seq).items.firstOrNull() ?: return null
		val updated = jdbc.queryForObject("SELECT UPDATE_DT FROM TB_USER WHERE USER_SEQ=?", java.sql.Timestamp::class.java, seq)?.toLocalDateTime()
		val favorites = try {
			val rows = jdbc.query("SELECT ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, STAGE, REG_DT FROM TB_FAVORITE WHERE USER_SEQ=? ORDER BY REG_DT DESC", { r, _ ->
				Favorite("${r.getString(1)}/${r.getString(2)}/${r.getString(3)}", null, r.getInt(4), r.getTimestamp(5)?.toLocalDateTime())
			}, seq)
			val names = announcements.names(rows.map { it.id })
			rows.map { it.copy(houseNm = names[it.id]) }
		} catch (missing: DataAccessException) { null }
		val push = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE USER_SEQ=?", Long::class.java, seq) } catch (missing: DataAccessException) { null }
		return Detail(member, updated, favorites, push)
	}

	data class Member(val seq: Long, val email: String, val nickname: String, val certified: Boolean, val locked: Boolean, val withdrawn: Boolean,
	                  val createdAt: LocalDateTime?, val lockedAt: LocalDateTime?, val favorites: Long, val comments: Long) {
		val status get() = when {
			withdrawn -> "탈퇴"
			locked -> "잠김"
			!certified -> "미인증"
			else -> "정상"
		}
	}
	data class Page(val items: List<Member>, val total: Long, val page: Int, val size: Int) {
		val pages get() = maxOf(1, ((total + size - 1) / size).toInt())
	}

	/** filter: all · certified · unverified · locked · withdrawn. 검색어는 이메일·닉네임 부분 일치. */
	fun search(query: String?, filter: String?, page: Int, size: Int = PAGE_SIZE, seq: Long? = null): Page {
		val where = StringBuilder(" WHERE 1=1"); val args = ArrayList<Any>()
		seq?.let { where.append(" AND U.USER_SEQ=?"); args += it }
		query?.trim()?.takeIf { it.isNotEmpty() }?.take(100)?.let {
			// LIKE 특수문자는 글자 그대로 찾도록 이스케이프한다.
			val like = "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
			where.append(" AND (U.USER_EMAIL LIKE ? OR U.USER_NIC_NAME LIKE ?)"); args += like; args += like
		}
		when (filter) {
			"certified" -> where.append(" AND U.EMAIL_CERTIFY_YN='Y' AND U.WITHDRAWAL_YN='N' AND U.LOCK_YN='N'")
			"unverified" -> where.append(" AND U.EMAIL_CERTIFY_YN<>'Y' AND U.WITHDRAWAL_YN='N'")
			"locked" -> where.append(" AND U.LOCK_YN='Y'")
			"withdrawn" -> where.append(" AND U.WITHDRAWAL_YN='Y'")
		}
		val total = jdbc.queryForObject("SELECT COUNT(*) FROM TB_USER U$where", Long::class.java, *args.toTypedArray()) ?: 0
		val safePage = page.coerceIn(0, 10_000)
		val items = jdbc.query("""
			SELECT U.USER_SEQ,U.USER_EMAIL,U.USER_NIC_NAME,U.EMAIL_CERTIFY_YN,U.LOCK_YN,U.WITHDRAWAL_YN,U.CREATE_DT,U.LOCK_DT,
			  (SELECT COUNT(*) FROM TB_FAVORITE F WHERE F.USER_SEQ=U.USER_SEQ) AS FAVORITES,
			  (SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT C WHERE C.CREATE_USER_SEQ=U.USER_SEQ AND C.DELETE_YN='N') AS COMMENTS
			FROM TB_USER U$where ORDER BY U.USER_SEQ DESC LIMIT ? OFFSET ?""".trimIndent(), { r, _ ->
			Member(r.getLong("USER_SEQ"), r.getString("USER_EMAIL"), r.getString("USER_NIC_NAME"), r.getString("EMAIL_CERTIFY_YN") == "Y",
				r.getString("LOCK_YN") == "Y", r.getString("WITHDRAWAL_YN") == "Y", r.getTimestamp("CREATE_DT")?.toLocalDateTime(),
				r.getTimestamp("LOCK_DT")?.toLocalDateTime(), r.getLong("FAVORITES"), r.getLong("COMMENTS"))
		}, *(args + size + safePage * size).toTypedArray())
		return Page(items, total, safePage, size)
	}

	/** 잠그거나 푼다. 탈퇴 회원은 바꾸지 않는다. 바뀐 행이 없으면 false. */
	fun setLocked(seq: Long, locked: Boolean, admin: String): Boolean {
		val changed = jdbc.update("UPDATE TB_USER SET LOCK_YN=?, LOCK_DT=?, UPDATE_DT=? WHERE USER_SEQ=? AND WITHDRAWAL_YN='N' AND LOCK_YN<>?",
			if (locked) "Y" else "N", if (locked) Timestamp.valueOf(Kst.now()) else null, Timestamp.valueOf(Kst.now()), seq, if (locked) "Y" else "N")
		if (changed == 1) log.info("관리자 회원 {}: userSeq={}, admin={}", if (locked) "잠금" else "잠금 해제", seq, admin)
		return changed == 1
	}

	companion object {
		const val PAGE_SIZE = 30
		/** 서비스 나의 청약 진행 단계(stageRules.js와 같은 순서). */
		val STAGES = listOf("관심", "신청 예정", "신청 완료", "당첨", "미당첨", "계약 완료")
		private val log = LoggerFactory.getLogger(MemberService::class.java)
	}
}
