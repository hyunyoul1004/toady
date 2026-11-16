package com.toady.admin.content

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 서비스 설정(database.sql 27번 TB_APP_SETTING). API(AppSettings)가 1분마다 다시 읽는다.
 * 값을 비워 저장하면 행을 지워 환경변수 기본값으로 돌아간다. 키와 허용 범위는 API AppSettings와 같아야 한다.
 */
@Service
class SettingService(private val jdbc: JdbcTemplate) {
	data class Definition(val key: String, val group: String, val label: String, val help: String, val bool: Boolean = false, val min: Long = 0, val max: Long = 0)
	data class Item(val def: Definition, val value: String?, val admin: String?, val updatedAt: LocalDateTime?)

	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_APP_SETTING WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun list(): List<Item> {
		val rows = HashMap<String, Triple<String, String, LocalDateTime?>>()
		try {
			jdbc.query("SELECT SETTING_KEY, SETTING_VALUE, ADMIN_NAME, UPDATE_DT FROM TB_APP_SETTING") { r -> rows[r.getString(1)] = Triple(r.getString(2), r.getString(3), r.getTimestamp(4)?.toLocalDateTime()) }
		} catch (missing: DataAccessException) { }
		return DEFINITIONS.map { def -> rows[def.key].let { Item(def, it?.first, it?.second, it?.third) } }
	}

	fun value(key: String): String? = list().firstOrNull { it.def.key == key }?.value

	/** 저장. 형식·범위가 맞지 않으면 null(오류 문구), 맞으면 이전 값과 함께 성공. */
	fun save(key: String, raw: String, admin: String): Result<String?> {
		val def = DEFINITIONS.firstOrNull { it.key == key } ?: return Result.failure(IllegalArgumentException("없는 설정입니다."))
		val text = raw.trim()
		val before = value(key)
		if (text.isEmpty()) {
			jdbc.update("DELETE FROM TB_APP_SETTING WHERE SETTING_KEY=?", key)
			return Result.success(before)
		}
		val normalized = if (def.bool) {
			if (text != "true" && text != "false") return Result.failure(IllegalArgumentException("켬·끔만 고를 수 있습니다."))
			text
		} else {
			val number = text.toLongOrNull() ?: return Result.failure(IllegalArgumentException("${def.label}: 숫자를 넣어 주세요."))
			if (number < def.min || number > def.max) return Result.failure(IllegalArgumentException("${def.label}: ${def.min}~${def.max} 사이로 넣어 주세요."))
			number.toString()
		}
		val now = Timestamp.valueOf(Kst.now())
		if (jdbc.update("UPDATE TB_APP_SETTING SET SETTING_VALUE=?, ADMIN_NAME=?, UPDATE_DT=? WHERE SETTING_KEY=?", normalized, admin.take(100), now, key) == 0)
			jdbc.update("INSERT INTO TB_APP_SETTING (SETTING_KEY, SETTING_VALUE, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?)", key, normalized, admin.take(100), now)
		return Result.success(before)
	}

	companion object {
		const val OFFICIAL_USER_SEQ = "official.userSeq"
		val DEFINITIONS = listOf(
			Definition("dm.minAccountHours", "쪽지", "새 대화 시작: 가입 후 시간", "가입하고 이 시간이 지나야 새 대화를 시작할 수 있어요. 0이면 바로.", min = 0, max = 720),
			Definition("dm.requireStory", "쪽지", "새 대화 시작: 이야기 1개 이상", "켜면 공고 이야기를 하나 이상 쓴 회원만 새 대화를 시작할 수 있어요.", bool = true),
			Definition("dm.dailyNewThreads", "쪽지", "하루 새 대화 상대 수", "한 회원이 하루에 새로 대화를 시작할 수 있는 상대 수", min = 1, max = 50),
			Definition("dm.dailyMessages", "쪽지", "하루 쪽지 수", "한 회원이 하루에 보낼 수 있는 쪽지 수", min = 1, max = 500),
			Definition("mail.rollingLimit", "메일", "24시간 메일 한도", "Gmail 한 계정의 최근 24시간 발송 한도(최대 400)", min = 1, max = 400),
			Definition("mail.authReserve", "메일", "인증 메일 예비분", "비밀번호 찾기 같은 인증 메일만 쓸 수 있는 몫. 한도보다 작아야 해요.", min = 0, max = 399),
			Definition("mail.signupLimit", "메일", "24시간 가입 메일 상한", "0이면 한도의 절반. 무작위 가입 반복이 다른 메일을 막지 않게 해요.", min = 0, max = 399),
			Definition("molit.dailyLimit", "실거래 API", "국토부 실거래 하루 호출 한도", "사용자 조회는 이 값의 80%까지, 저녁 미리 계산은 100%까지 써요.", min = 100, max = 1_000_000),
			Definition(OFFICIAL_USER_SEQ, "운영팀", "운영팀 계정 회원 번호", "공고 이야기에 '운영팀' 글을 쓸 회원 번호. 서비스에서 운영팀용 계정을 가입한 뒤 번호를 넣으세요.", min = 1, max = Long.MAX_VALUE))
	}
}
