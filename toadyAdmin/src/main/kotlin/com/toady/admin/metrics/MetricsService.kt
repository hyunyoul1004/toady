package com.toady.admin.metrics

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.NavigableSet
import java.util.TreeSet
import kotlin.math.roundToLong

/**
 * API가 쌓는 익명 조회·행동 로그를 주 단위(월~일, 한국시간)로 집계하고, 오늘의 운영 숫자를 모은다.
 * 개인 식별 정보는 다루지 않는다. 재방문은 그 주 시작 전 RETURN_WEEKS주 안에 다른 날 방문이 있는 방문자다.
 */
@Service
class MetricsService(private val jdbc: JdbcTemplate) {
	data class Week(val start: LocalDate, val end: LocalDate, val visitors: Long, val sessions: Long, val pageViews: Long, val returning: Long,
	                val activeExplorers: Long, val detailViews: Long, val favorites: Long, val shares: Long) {
		val returningRate get() = if (visitors == 0L) 0 else (returning * 100.0 / visitors).roundToLong()
		val label get() = "%d.%02d ~ %d.%02d".format(start.monthValue, start.dayOfMonth, end.monthValue, end.dayOfMonth)
	}
	data class Source(val label: String, val host: String, val sessions: Long, val percent: Long)
	data class Report(val weeks: List<Week>, val sources: List<Source>, val sourceSessions: Long, val sourceNotice: String?, val error: String?) {
		val current get() = weeks.firstOrNull()
		val previous get() = weeks.getOrNull(1)
		val searchPercent get() = sources.filter { it.label.startsWith("검색") }.sumOf { it.sessions } * 100 / maxOf(1, sourceSessions)
	}
	/** 오늘의 운영 숫자. 테이블이 없으면 그 항목만 null(화면에 '—'). */
	data class Today(val visitors: Long?, val newMembers: Long?, val members: Long?, val comments: Long?, val favorites: Long?, val pushSubscriptions: Long?)

	fun today(today: LocalDate = Kst.today()): Today = Today(
		count("SELECT COUNT(DISTINCT VISITOR_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE = ?", today),
		count("SELECT COUNT(*) FROM TB_USER WHERE CREATE_DT >= ?", today.atStartOfDay()),
		count("SELECT COUNT(*) FROM TB_USER WHERE EMAIL_CERTIFY_YN = 'Y' AND WITHDRAWAL_YN = 'N'"),
		count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN = 'N' AND REG_DT >= ?", today.atStartOfDay()),
		count("SELECT COUNT(*) FROM TB_FAVORITE"),
		count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION"))

	private fun count(sql: String, vararg args: Any): Long? =
		try { jdbc.queryForObject(sql, Long::class.java, *args) } catch (missing: DataAccessException) { null }

	fun report(today: LocalDate = Kst.today()): Report {
		val first = weekStart(today).minusWeeks((WEEKS - 1).toLong())
		val windowStart = first.minusWeeks(RETURN_WEEKS.toLong())
		return try {
			val visitorDays = HashMap<String, NavigableSet<LocalDate>>()
			jdbc.query("SELECT DISTINCT VISITOR_ID,VISIT_DATE FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?", RowCallbackHandler { r ->
				visitorDays.getOrPut(r.getString(1)) { TreeSet() }.add(r.getDate(2).toLocalDate())
			}, windowStart, today)
			val traffic = HashMap<LocalDate, LongArray>()
			jdbc.query("SELECT VISIT_DATE,COUNT(*),COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ? GROUP BY VISIT_DATE", RowCallbackHandler { r ->
				traffic[r.getDate(1).toLocalDate()] = longArrayOf(r.getLong(2), r.getLong(3))
			}, first, today)
			val actions = HashMap<String, HashMap<String, HashSet<String>>>() // 주 시작일 → 행동 → 방문자
			val subjects = HashMap<String, HashMap<String, HashSet<String>>>() // 주 시작일 → 방문자 → 본 공고
			val counts = HashMap<String, HashMap<String, Long>>()
			jdbc.query("SELECT VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE FROM TB_USAGE_EVENT WHERE VISIT_DATE BETWEEN ? AND ? AND ACTION_NAME IN ('detail_view','favorite_add','share')", RowCallbackHandler { r ->
				val week = weekStart(r.getDate(4).toLocalDate()).toString()
				val visitor = r.getString(1); val action = r.getString(2)
				actions.getOrPut(week) { HashMap() }.getOrPut(action) { HashSet() }.add(visitor)
				counts.getOrPut(week) { HashMap() }.merge(action, 1L, Long::plus)
				if (action == "detail_view") subjects.getOrPut(week) { HashMap() }.getOrPut(visitor) { HashSet() }.add(r.getString(3))
			}, first, today)
			val weeks = (0 until WEEKS).map { i ->
				val start = weekStart(today).minusWeeks(i.toLong()); val end = start.plusDays(6)
				var views = 0L; var sessions = 0L
				generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.forEach { day ->
					traffic[day]?.let { views += it[0]; sessions += it[1] }
				}
				var visitors = 0L; var returning = 0L
				for (days in visitorDays.values) {
					val last = days.floor(end) ?: continue
					if (last.isBefore(start)) continue
					visitors++
					val earlier = days.lower(last)
					if (earlier != null && !earlier.isBefore(start.minusWeeks(RETURN_WEEKS.toLong()))) returning++
				}
				val key = start.toString()
				val active = HashSet(actions[key]?.get("favorite_add") ?: emptySet())
				subjects[key]?.forEach { (visitor, seen) -> if (seen.size >= 2) active.add(visitor) }
				val c = counts[key] ?: emptyMap()
				Week(start, end, visitors, sessions, views, returning, active.size.toLong(), c["detail_view"] ?: 0, c["favorite_add"] ?: 0, c["share"] ?: 0)
			}
			val (sources, total, notice) = sources(today)
			Report(weeks, sources, total, notice, null)
		} catch (failure: DataAccessException) {
			Report(emptyList(), emptyList(), 0, null, "지표 테이블을 읽지 못했습니다. TB_PAGE_VIEW·TB_USAGE_EVENT 존재 여부와 관리자 DB 계정의 SELECT 권한을 확인하세요.")
		}
	}

	private fun sources(today: LocalDate): Triple<List<Source>, Long, String?> {
		val start = today.minusDays(27)
		return try {
			val total = jdbc.queryForObject("SELECT COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?", Long::class.java, start, today) ?: 0L
			val byLabel = LinkedHashMap<String, Long>(); val hosts = HashMap<String, String>()
			for (row in jdbc.queryForList("SELECT REFERRER_HOST,COUNT(DISTINCT SESSION_ID) AS SESSIONS FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ? AND REFERRER_HOST IS NOT NULL GROUP BY REFERRER_HOST", start, today)) {
				val host = row["REFERRER_HOST"].toString(); val label = sourceLabel(host)
				byLabel.merge(label, (row["SESSIONS"] as Number).toLong(), Long::plus)
				hosts.merge(label, host) { a, b -> if (a.length <= b.length) a else b }
			}
			byLabel["직접 방문·알 수 없음"] = maxOf(0, total - byLabel.values.sum())
			val denominator = maxOf(1, total)
			Triple(byLabel.entries.sortedByDescending { it.value }.map { Source(it.key, hosts[it.key] ?: "", it.value, (it.value * 100.0 / denominator).roundToLong()) }, total, null)
		} catch (missing: BadSqlGrammarException) {
			Triple(emptyList(), 0L, "유입 경로 컬럼(REFERRER_HOST)이 아직 없습니다. database.sql 10번을 적용하면 이후 방문부터 집계됩니다.")
		}
	}

	companion object {
		const val WEEKS = 8
		/** 재방문 판정에 보는 이전 기간(주). TB_PAGE_VIEW 보관 90일 안에서 모든 주가 같은 기간을 보게 한다. */
		const val RETURN_WEEKS = 4
		fun weekStart(day: LocalDate): LocalDate = day.with(DayOfWeek.MONDAY)

		/** 검색·메신저처럼 의미 있는 묶음으로 보이게 도메인을 분류한다. 부분 문자열이 아니라 도메인·하위 도메인으로만 판별한다. */
		fun sourceLabel(host: String): String {
			val h = host.lowercase(Locale.ROOT)
			return when {
				GOOGLE.matches(h) -> "검색 · 구글"
				isHost(h, "naver.com") -> if (isHost(h, "cafe.naver.com")) "네이버 카페" else if (isHost(h, "blog.naver.com")) "네이버 블로그" else "검색 · 네이버"
				isHost(h, "daum.net") -> if (isHost(h, "cafe.daum.net")) "다음 카페" else "검색 · 다음"
				isHost(h, "bing.com") -> "검색 · 빙"
				isHost(h, "kakao.com") || isHost(h, "kakaocorp.com") -> "카카오톡·카카오"
				SNS.any { isHost(h, it) } -> "SNS"
				else -> h
			}
		}
		private val GOOGLE = Regex("(?:.+\\.)?google\\.(?:com|co\\.[a-z]{2}|com\\.[a-z]{2}|[a-z]{2})")
		private val SNS = listOf("instagram.com", "threads.net", "threads.com", "facebook.com", "t.co", "x.com", "twitter.com")
		fun isHost(host: String, domain: String) = host == domain || host.endsWith(".$domain")
	}
}
