package com.toady.admin.operations

import com.toady.admin.Kst
import org.springframework.beans.factory.annotation.Value
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
 * 한도 값은 API와 같은 환경변수(GMAIL_ROLLING_LIMIT 등)를 읽는다. 관리자 컨테이너에 없으면 API 기본값을 쓴다.
 */
@Service
class MailStatusService(
	private val jdbc: JdbcTemplate,
	@Value("\${toady.mail.rolling-limit:400}") private val limit: Int,
	@Value("\${toady.mail.auth-reserve:100}") private val reserve: Int,
	@Value("\${toady.mail.signup-limit:0}") signupLimit: Int,
) {
	private val signupCap = if (signupLimit > 0) signupLimit else maxOf(1, limit / 2)

	data class Usage(val auth: Long, val signup: Long, val reminder: Long) { val total get() = auth + signup + reminder }
	data class Delivery(val date: LocalDate, val sent: Long, val failed: Long, val unknown: Long, val sending: Long)
	data class Report(val usage: Usage?, val limit: Int, val reserve: Int, val signupCap: Int, val deliveries: List<Delivery>?, val alertsOn: Long?) {
		/** 알림·가입 메일이 쓸 수 있는 남은 양(인증 예비분 제외). */
		val generalLeft get() = usage?.let { maxOf(0L, limit - reserve - it.total) }
		val authLeft get() = usage?.let { maxOf(0L, limit - it.total) }
		val usedPercent get() = usage?.let { minOf(100, (it.total * 100 / maxOf(1, limit)).toInt()) } ?: 0
		val warning get() = usage != null && usage.total >= limit - reserve
	}

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
		return Report(usage, limit, reserve, signupCap, deliveries, alertsOn)
	}
}
