package com.toady.admin.content

import com.toady.admin.Kst
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 서비스 설정(database.sql 27번 TB_APP_SETTING). API(AppSettings)가 1분마다 다시 읽는다.
 * 값을 비워 저장하면 행을 지워 환경변수 기본값으로 돌아간다. 키와 허용 범위는 API AppSettings와 같아야 한다.
 * 메일 한도 기본값은 API와 같은 환경변수(GMAIL_ROLLING_LIMIT 등, application.yml toady.mail.*)를 읽는다.
 * 호출: ContentController의 설정 화면(/admin/settings), 운영팀 글쓰기(OFFICIAL_USER_SEQ 읽기), MailStatusService(메일 한도).
 */
// @Service: 업무 로직 빈. 생성자 매개변수의 @Value로 application.yml의 toady.mail.* 값을 받는다.
@Service
class SettingService(
	private val jdbc: JdbcTemplate,
	@Value("\${toady.mail.rolling-limit:400}") private val envMailLimit: Int,
	@Value("\${toady.mail.auth-reserve:100}") private val envAuthReserve: Int,
	@Value("\${toady.mail.signup-limit:0}") private val envSignupLimit: Int,
) {
	/** 설정 하나의 정의: 키, 화면 묶음·이름·설명, 켬/끔 여부, 숫자 범위. */
	data class Definition(val key: String, val group: String, val label: String, val help: String, val bool: Boolean = false, val min: Long = 0, val max: Long = 0)
	data class Item(val def: Definition, val value: String?, val admin: String?, val updatedAt: LocalDateTime?)
	/** 실제로 적용되는 메일 한도: 24시간 한도, 인증 예비분, 가입 메일 상한. fromSettings는 서비스 설정 값이 하나라도 반영됐는지. */
	data class MailLimits(val limit: Int, val reserve: Int, val signupCap: Int, val fromSettings: Boolean)

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). 없으면 화면에 적용 안내를 보여 준다. */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_APP_SETTING WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** 정의된 모든 설정과 DB에 저장된 값. 저장 값이 없으면 value가 null(환경변수 기본값 사용)이다. */
	fun list(): List<Item> {
		val rows = HashMap<String, Triple<String, String, LocalDateTime?>>()
		try {
			jdbc.query("SELECT SETTING_KEY, SETTING_VALUE, ADMIN_NAME, UPDATE_DT FROM TB_APP_SETTING") { r -> rows[r.getString(1)] = Triple(r.getString(2), r.getString(3), r.getTimestamp(4)?.toLocalDateTime()) }
		} catch (missing: DataAccessException) { }
		return DEFINITIONS.map { def -> rows[def.key].let { Item(def, it?.first, it?.second, it?.third) } }
	}

	fun value(key: String): String? = list().firstOrNull { it.def.key == key }?.value

	/**
	 * 지금 적용되는 메일 한도. API MailBudget과 같은 규칙으로 계산한다:
	 * 한도는 설정값(1~400) 또는 환경변수, 인증 예비분은 한도-1 이하, 가입 상한은 0이면 한도의 절반이고 1~한도-1로 맞춘다.
	 * values를 넘기면(저장 전 검증) 그 값으로 계산한다.
	 */
	fun mailLimits(values: Map<String, String?> = list().associate { it.def.key to it.value }): MailLimits {
		fun stored(key: String, min: Long, max: Long) = values[key]?.toLongOrNull()?.takeIf { it in min..max }
		val storedLimit = stored(MAIL_LIMIT, 1, 400); val storedReserve = stored(MAIL_AUTH_RESERVE, 0, 399); val storedSignup = stored(MAIL_SIGNUP_LIMIT, 0, 399)
		val limit = (storedLimit ?: envMailLimit.toLong()).coerceIn(1, 400).toInt()
		val reserve = minOf(storedReserve ?: envAuthReserve.toLong(), (limit - 1).toLong()).coerceAtLeast(0).toInt()
		val signupRaw = storedSignup ?: envSignupLimit.toLong()
		val signup = (if (signupRaw == 0L) maxOf(1, limit / 2).toLong() else signupRaw).coerceIn(1, maxOf(1, limit - 1).toLong()).toInt()
		return MailLimits(limit, reserve, signup, storedLimit != null || storedReserve != null || storedSignup != null)
	}

	/**
	 * 저장. 형식·범위가 맞지 않거나 DB에 쓰지 못하면 Result.failure(화면에 보여 줄 문구), 맞으면 이전 값을 담은 Result.success.
	 * 빈 값이면 행을 지워 기본값으로 되돌린다(관리자 계정에 DELETE 권한 필요, database.sql 34번).
	 */
	fun save(key: String, raw: String, admin: String): Result<String?> {
		val def = DEFINITIONS.firstOrNull { it.key == key } ?: return Result.failure(IllegalArgumentException("없는 설정입니다."))
		val text = raw.trim()
		val current = list().associate { it.def.key to it.value }
		val before = current[key]
		val normalized = when {
			text.isEmpty() -> null
			def.bool -> {
				if (text != "true" && text != "false") return Result.failure(IllegalArgumentException("켬·끔만 고를 수 있습니다."))
				text
			}
			else -> {
				val number = text.toLongOrNull() ?: return Result.failure(IllegalArgumentException("${def.label}: 숫자를 넣어 주세요."))
				if (number < def.min || number > def.max) return Result.failure(IllegalArgumentException("${def.label}: ${def.min}~${def.max} 사이로 넣어 주세요."))
				number.toString()
			}
		}
		mailConflict(key, current + (key to normalized))?.let { return Result.failure(IllegalArgumentException(it)) }
		return try {
			if (normalized == null) {
				jdbc.update("DELETE FROM TB_APP_SETTING WHERE SETTING_KEY=?", key)
			} else {
				val now = Timestamp.valueOf(Kst.now())
				// UPDATE가 0행이면 처음 저장하는 키라 INSERT한다.
				if (jdbc.update("UPDATE TB_APP_SETTING SET SETTING_VALUE=?, ADMIN_NAME=?, UPDATE_DT=? WHERE SETTING_KEY=?", normalized, admin.take(100), now, key) == 0)
					jdbc.update("INSERT INTO TB_APP_SETTING (SETTING_KEY, SETTING_VALUE, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?)", key, normalized, admin.take(100), now)
			}
			Result.success(before)
		} catch (failure: DataAccessException) {
			// 권한 부족(예: 34번 DELETE 미적용)·테이블 없음 등은 500 화면 대신 안내 문구로 돌려준다.
			log.warn("서비스 설정을 저장하지 못했습니다: key={}, reset={}, admin={}, cause={}", key, normalized == null, admin, failure.mostSpecificCause.javaClass.simpleName)
			Result.failure(IllegalStateException(
				if (normalized == null) "기본값으로 되돌리지 못했습니다. 관리자 DB 계정에 TB_APP_SETTING DELETE 권한이 있는지 확인하세요(database.sql 34번)."
				else "저장하지 못했습니다. database.sql 27번과 관리자 DB 권한을 확인하세요."))
		}
	}

	/**
	 * 메일 설정끼리 맞지 않는 조합을 막는다. 인증 예비분·가입 상한이 24시간 한도 이상이면 API가 설정을 무시하고 값을 깎으므로 저장 단계에서 거부한다.
	 * 다른 쪽이 비어 있으면 환경변수 기본값과 비교한다. 바꾸려는 키가 메일 설정이 아니면 검사하지 않는다.
	 */
	private fun mailConflict(key: String, values: Map<String, String?>): String? {
		if (key !in setOf(MAIL_LIMIT, MAIL_AUTH_RESERVE, MAIL_SIGNUP_LIMIT)) return null
		val limit = values[MAIL_LIMIT]?.toLongOrNull() ?: envMailLimit.toLong()
		val reserve = values[MAIL_AUTH_RESERVE]?.toLongOrNull() ?: envAuthReserve.toLong()
		val signup = values[MAIL_SIGNUP_LIMIT]?.toLongOrNull() ?: envSignupLimit.toLong()
		if (reserve >= limit) return "인증 메일 예비분(${reserve}통)은 24시간 메일 한도(${limit}통)보다 작아야 합니다."
		if (signup != 0L && signup >= limit) return "24시간 가입 메일 상한(${signup}통)은 24시간 메일 한도(${limit}통)보다 작아야 합니다."
		return null
	}

	companion object {
		private val log = LoggerFactory.getLogger(SettingService::class.java)
		const val OFFICIAL_USER_SEQ = "official.userSeq"
		const val MAIL_LIMIT = "mail.rollingLimit"
		const val MAIL_AUTH_RESERVE = "mail.authReserve"
		const val MAIL_SIGNUP_LIMIT = "mail.signupLimit"
		val DEFINITIONS = listOf(
			Definition("dm.minAccountHours", "쪽지", "새 대화 시작: 가입 후 시간", "가입하고 이 시간이 지나야 새 대화를 시작할 수 있어요. 0이면 바로.", min = 0, max = 720),
			Definition("dm.requireStory", "쪽지", "새 대화 시작: 이야기 1개 이상", "켜면 공고 이야기를 하나 이상 쓴 회원만 새 대화를 시작할 수 있어요.", bool = true),
			Definition("dm.dailyNewThreads", "쪽지", "하루 새 대화 상대 수", "한 회원이 하루에 새로 대화를 시작할 수 있는 상대 수", min = 1, max = 50),
			Definition("dm.dailyMessages", "쪽지", "하루 쪽지 수", "한 회원이 하루에 보낼 수 있는 쪽지 수", min = 1, max = 500),
			Definition(MAIL_LIMIT, "메일", "24시간 메일 한도", "Gmail 한 계정의 최근 24시간 발송 한도(최대 400)", min = 1, max = 400),
			Definition(MAIL_AUTH_RESERVE, "메일", "인증 메일 예비분", "비밀번호 찾기 같은 인증 메일만 쓸 수 있는 몫. 한도보다 작아야 해요.", min = 0, max = 399),
			Definition(MAIL_SIGNUP_LIMIT, "메일", "24시간 가입 메일 상한", "0이면 한도의 절반. 무작위 가입 반복이 다른 메일을 막지 않게 해요.", min = 0, max = 399),
			Definition("molit.dailyLimit", "실거래 API", "국토부 실거래 하루 호출 한도", "사용자 조회는 이 값의 80%까지, 저녁 미리 계산은 100%까지 써요.", min = 100, max = 1_000_000),
			Definition(OFFICIAL_USER_SEQ, "운영팀", "운영팀 계정 회원 번호", "공고 이야기에 '운영팀' 글을 쓸 회원 번호. 서비스에서 운영팀용 계정을 가입한 뒤 번호를 넣으세요.", min = 1, max = Long.MAX_VALUE))
	}
}
