package com.toady.admin.operations

import com.toady.admin.Kst
import com.toady.admin.content.SettingService
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Gmail 발송 현황. API의 메일 한도(MailBudget)는 최근 24시간(UTC 시각 기준) 발송 시도 수로 판단하므로 같은 기준으로 센다.
 * 한도 값은 API MailBudget과 같은 규칙으로 정한다: 서비스 설정(TB_APP_SETTING의 mail.*)에 저장한 값이 있으면 그 값, 없으면
 * API와 같은 환경변수(GMAIL_ROLLING_LIMIT 등). 계산은 SettingService.mailLimits 한 곳에서 한다.
 * 읽는 테이블: TB_MAIL_ATTEMPT(발송 시도), TB_REMINDER_DELIVERY(일정 알림 발송 결과), TB_FAVORITE(메일 알림 켠 관심 공고). 읽기 전용.
 * 호출: AdminController의 메일 현황 화면(/admin/mail).
 */
// @Service: 업무 로직 빈.
@Service
class MailStatusService(private val jdbc: JdbcTemplate, private val settings: SettingService) {

	/** 최근 24시간 종류별 발송 시도 수(인증·가입·알림). */
	data class Usage(val auth: Long, val signup: Long, val reminder: Long) { val total get() = auth + signup + reminder }
	/** 일정 알림 메일의 날짜별 결과 수. */
	data class Delivery(val date: LocalDate, val sent: Long, val failed: Long, val unknown: Long, val sending: Long)
	/** fromSettings: 한도 중 하나라도 서비스 설정 화면에서 바꾼 값이면 true(화면에 출처를 알린다). */
	data class Report(val usage: Usage?, val limit: Int, val reserve: Int, val signupCap: Int, val deliveries: List<Delivery>?, val alertsOn: Long?, val fromSettings: Boolean = false) {
		/** 알림·가입 메일이 쓸 수 있는 남은 양(인증 예비분 제외). */
		val generalLeft get() = usage?.let { maxOf(0L, limit - reserve - it.total) }
		val authLeft get() = usage?.let { maxOf(0L, limit - it.total) }
		val usedPercent get() = usage?.let { minOf(100, (it.total * 100 / maxOf(1, limit)).toInt()) } ?: 0
		val warning get() = usage != null && usage.total >= limit - reserve
	}

	/** 화면 값을 모은다. 항목별로 실패를 따로 처리해 테이블이 없는 항목만 비워 둔다. */
	fun report(): Report {
		val since = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC).minusHours(24))
		val usage = try {
			val counts = HashMap<String, Long>()
			jdbc.query("SELECT MAIL_KIND, COUNT(*) FROM TB_MAIL_ATTEMPT WHERE REG_DT>=? GROUP BY MAIL_KIND", { r -> counts[r.getString(1)] = r.getLong(2) }, since)
			Usage(counts["AUTH"] ?: 0, counts["SIGNUP"] ?: 0, counts["REMINDER"] ?: 0)
		} catch (missing: DataAccessException) { null }
		val deliveries = try {
			val from = Date.valueOf(Kst.today().minusDays(6))
			val byDate = sortedMapOf<LocalDate, MutableMap<String, Long>>(compareByDescending { it })
			jdbc.query("SELECT SEND_DATE, STATUS, COUNT(*) FROM TB_REMINDER_DELIVERY WHERE SEND_DATE>=? GROUP BY SEND_DATE, STATUS", { r ->
				byDate.getOrPut(r.getDate(1).toLocalDate()) { HashMap() }[r.getString(2)] = r.getLong(3)
			}, from)
			byDate.map { (date, s) -> Delivery(date, s["SENT"] ?: 0, s["FAILED"] ?: 0, s["UNKNOWN"] ?: 0, s["SENDING"] ?: 0) }
		} catch (missing: DataAccessException) { null }
		val alertsOn = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE EMAIL_ENABLED=TRUE", Long::class.java) } catch (missing: DataAccessException) { null }
		val limits = settings.mailLimits()
		return Report(usage, limits.limit, limits.reserve, limits.signupCap, deliveries, alertsOn, limits.fromSettings)
	}
}
