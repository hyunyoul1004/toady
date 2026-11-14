package com.toady.admin.metrics

import com.toady.admin.Kst
import com.toady.admin.announcements.AnnouncementService
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.NavigableSet
import java.util.TreeSet
import kotlin.math.roundToLong

/**
 * API가 쌓는 익명 조회·행동 로그를 집계한다(한국시간). 개인 식별 정보는 다루지 않는다.
 * 운영자가 등록한 브라우저(ExclusionService)는 모든 방문 지표에서 뺀다.
 * 재방문은 그 주 시작 전 RETURN_WEEKS주 안에 다른 날 방문이 있는 방문자다.
 */
@Service
class MetricsService(private val jdbc: JdbcTemplate, private val exclusions: ExclusionService, private val announcements: AnnouncementService) {
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
	data class Today(val visitors: Long?, val pageViews: Long?, val newMembers: Long?, val members: Long?, val comments: Long?, val favorites: Long?, val pushSubscriptions: Long?)
	/** 일별 추이 한 칸. percent는 기간 최대값 대비 막대 길이. */
	data class Day(val date: LocalDate, val visitors: Long, val pageViews: Long, val percent: Long) {
		val label get() = "%d.%d".format(date.monthValue, date.dayOfMonth)
		val weekend get() = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
	}
	data class Ranked(val key: String, val label: String, val count: Long, val visitors: Long, val percent: Long)
	data class Signup(val seq: Long, val nickname: String, val email: String, val certified: Boolean, val createdAt: LocalDateTime?)

	fun today(today: LocalDate = Kst.today()): Today {
		val ex = exclusions.clause()
		return Today(
			count("SELECT COUNT(DISTINCT VISITOR_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE = ?$ex", today),
			count("SELECT COUNT(*) FROM TB_PAGE_VIEW WHERE VISIT_DATE = ?$ex", today),
			count("SELECT COUNT(*) FROM TB_USER WHERE CREATE_DT >= ?", today.atStartOfDay()),
			count("SELECT COUNT(*) FROM TB_USER WHERE EMAIL_CERTIFY_YN = 'Y' AND WITHDRAWAL_YN = 'N'"),
			count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN = 'N' AND REG_DT >= ?", today.atStartOfDay()),
			count("SELECT COUNT(*) FROM TB_FAVORITE"),
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION"))
	}

	/** 최근 days일 일별 방문자·페이지뷰(오늘 포함, 오래된 날부터). */
	fun daily(days: Int = 14, today: LocalDate = Kst.today()): List<Day> {
		val start = today.minusDays((days - 1).toLong())
		val rows = HashMap<LocalDate, LongArray>()
		try {
			jdbc.query("SELECT VISIT_DATE, COUNT(DISTINCT VISITOR_ID), COUNT(*) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?${exclusions.clause()} GROUP BY VISIT_DATE",
				RowCallbackHandler { r -> rows[r.getDate(1).toLocalDate()] = longArrayOf(r.getLong(2), r.getLong(3)) }, start, today)
		} catch (missing: DataAccessException) { return emptyList() }
		val max = maxOf(1L, rows.values.maxOfOrNull { it[0] } ?: 1L)
		return (0 until days).map { i ->
			val day = start.plusDays(i.toLong()); val v = rows[day] ?: longArrayOf(0, 0)
			Day(day, v[0], v[1], v[0] * 100 / max)
		}
	}

	/** 최근 days일 가장 많이 연 공고 상세(사람 수 기준). */
	fun topAnnouncements(days: Int = 7, limit: Int = 10, today: LocalDate = Kst.today()): List<Ranked> = try {
		val rows = jdbc.query("""SELECT SUBJECT_ID, COUNT(*) AS VIEWS, COUNT(DISTINCT VISITOR_ID) AS PEOPLE FROM TB_USAGE_EVENT
			WHERE ACTION_NAME='detail_view' AND SUBJECT_ID NOT LIKE 'tool/%' AND VISIT_DATE >= ?${exclusions.clause()} GROUP BY SUBJECT_ID ORDER BY PEOPLE DESC, VIEWS DESC LIMIT ?""".trimIndent(),
			{ r, _ -> Triple(r.getString(1), r.getLong(2), r.getLong(3)) }, today.minusDays((days - 1).toLong()), limit)
		val names = announcements.names(rows.map { it.first })
		val max = maxOf(1L, rows.maxOfOrNull { it.third } ?: 1L)
		rows.map { (id, views, people) -> Ranked(id, names[id]?.takeIf { it.isNotBlank() } ?: id, views, people, people * 100 / max) }
	} catch (missing: DataAccessException) { emptyList() }

	/** 전환 흐름 한 단계: 이름, 방문자 수, 첫 단계 대비 %. */
	data class Step(val label: String, val visitors: Long, val percent: Long)
	/**
	 * 최근 days일 방문자의 흐름과 신규 방문자의 재방문.
	 * steps: 방문 → 공고 상세 → 관심 저장 → 알림 켬(이메일·푸시). 같은 방문자를 한 번만 센다.
	 * 재방문: 첫 방문(그 전 28일 동안 방문 없음)이 7~27일 전인 신규 방문자 중, 첫 방문 7~13일 뒤에 다시 온 비율.
	 * 알림 비교: 첫 방문 7일 안에 알림을 켠 신규 방문자와 켜지 않은 신규 방문자의 재방문율.
	 *   (재방문하면서 켠 경우를 빼야 알림 쪽이 부풀지 않는다. 관심이 많은 사람이 알림을 켤 수도 있어 인과는 아니다.)
	 */
	data class Funnel(val days: Int, val steps: List<Step>, val returnBase: Long, val returnRate: Long?,
	                  val alertBase: Long, val alertReturnRate: Long?, val otherBase: Long, val otherReturnRate: Long?, val error: String?)
	fun funnel(days: Int = 28, today: LocalDate = Kst.today()): Funnel = try {
		val start = today.minusDays((days - 1).toLong())
		// 신규 판단용으로 28일을 더 읽는다(첫 방문 전 28일 동안 방문이 없어야 신규).
		val loadFrom = start.minusDays(28)
		val ex = exclusions.clause()
		val visits = HashMap<String, NavigableSet<LocalDate>>()
		jdbc.query("SELECT DISTINCT VISITOR_ID,VISIT_DATE FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?$ex", RowCallbackHandler { r ->
			visits.getOrPut(r.getString(1)) { TreeSet() }.add(r.getDate(2).toLocalDate())
		}, loadFrom, today)
		val recent = visits.filterValues { it.ceiling(start) != null }.keys
		val acted = HashMap<String, HashSet<String>>()
		jdbc.query("SELECT DISTINCT VISITOR_ID,ACTION_NAME FROM TB_USAGE_EVENT WHERE VISIT_DATE BETWEEN ? AND ? AND ACTION_NAME IN ('detail_view','favorite_add','alert_enable','push_enable')$ex",
			RowCallbackHandler { r -> acted.getOrPut(r.getString(2)) { HashSet() }.add(r.getString(1)) }, start, today)
		val viewed = (acted["detail_view"] ?: emptySet<String>()).filter { it in recent }.toSet()
		val saved = (acted["favorite_add"] ?: emptySet<String>()).filter { it in recent }.toSet()
		val alerted = ((acted["alert_enable"] ?: emptySet()) + (acted["push_enable"] ?: emptySet())).filter { it in recent }.toSet()
		val total = recent.size.toLong()
		fun pct(n: Int) = if (total == 0L) 0L else n * 100L / total
		val steps = listOf(Step("방문", total, if (total == 0L) 0 else 100), Step("공고 상세", viewed.size.toLong(), pct(viewed.size)),
			Step("관심 저장", saved.size.toLong(), pct(saved.size)), Step("알림 켬", alerted.size.toLong(), pct(alerted.size)))
		// 신규 방문자: 읽은 범위의 첫 방문이 [start, today-7] 안. 그 전 28일은 읽은 범위 안이라 방문이 없었다는 뜻이다.
		val cohort = visits.filterValues { val first = it.first(); !first.isBefore(start) && !first.isAfter(today.minusDays(7)) }
		fun returned(dates: NavigableSet<LocalDate>): Boolean { val first = dates.first(); val again = dates.ceiling(first.plusDays(7)); return again != null && !again.isAfter(first.plusDays(13)) }
		val alertOn = HashMap<String, LocalDate>()
		jdbc.query("SELECT VISITOR_ID,MIN(VISIT_DATE) FROM TB_USAGE_EVENT WHERE VISIT_DATE BETWEEN ? AND ? AND ACTION_NAME IN ('alert_enable','push_enable')$ex GROUP BY VISITOR_ID",
			RowCallbackHandler { r -> alertOn[r.getString(1)] = r.getDate(2).toLocalDate() }, start, today)
		val early = cohort.filter { (visitor, dates) -> alertOn[visitor]?.let { !it.isAfter(dates.first().plusDays(6)) } == true }
		val others = cohort.filterKeys { it !in early }
		fun rate(group: Map<String, NavigableSet<LocalDate>>) = if (group.isEmpty()) null else group.count { returned(it.value) } * 100L / group.size
		Funnel(days, steps, cohort.size.toLong(), rate(cohort), early.size.toLong(), rate(early), others.size.toLong(), rate(others), null)
	} catch (missing: DataAccessException) {
		Funnel(days, emptyList(), 0, null, 0, null, 0, null, "흐름 지표를 읽지 못했습니다. TB_PAGE_VIEW·TB_USAGE_EVENT와 관리자 DB 계정의 SELECT 권한을 확인하세요.")
	}

	/** 최근 days일 지도 기능 사용(위치 탭): 기능별 횟수와 사람 수. 측정 이름은 웹 trackAction('map_tool', 'tool/…')과 같다. */
	fun mapTools(days: Int = 7, today: LocalDate = Kst.today()): List<Ranked> = try {
		val rows = jdbc.query("""SELECT SUBJECT_ID, COUNT(*) AS USES, COUNT(DISTINCT VISITOR_ID) AS PEOPLE FROM TB_USAGE_EVENT
			WHERE ACTION_NAME='map_tool' AND SUBJECT_ID IN (${MAP_TOOL_NAMES.keys.joinToString(",") { "?" }}) AND VISIT_DATE >= ?${exclusions.clause()}
			GROUP BY SUBJECT_ID ORDER BY USES DESC""".trimIndent(),
			{ r, _ -> Triple(r.getString(1), r.getLong(2), r.getLong(3)) }, *MAP_TOOL_NAMES.keys.toTypedArray(), today.minusDays((days - 1).toLong()))
		val max = maxOf(1L, rows.maxOfOrNull { it.second } ?: 1L)
		rows.map { (subject, uses, people) -> Ranked(subject, MAP_TOOL_NAMES[subject] ?: subject, uses, people, uses * 100 / max) }
	} catch (missing: DataAccessException) { emptyList() }

	/** 최근 days일 화면별 페이지뷰. */
	fun topPages(days: Int = 7, today: LocalDate = Kst.today()): List<Ranked> = try {
		val rows = jdbc.query("""SELECT PAGE_PATH, COUNT(*) AS VIEWS, COUNT(DISTINCT VISITOR_ID) AS PEOPLE FROM TB_PAGE_VIEW
			WHERE VISIT_DATE >= ?${exclusions.clause()} GROUP BY PAGE_PATH ORDER BY VIEWS DESC LIMIT 12""".trimIndent(),
			{ r, _ -> Triple(r.getString(1), r.getLong(2), r.getLong(3)) }, today.minusDays((days - 1).toLong()))
		val max = maxOf(1L, rows.maxOfOrNull { it.second } ?: 1L)
		rows.map { (path, views, people) -> Ranked(path, PAGE_NAMES[path] ?: path, views, people, views * 100 / max) }
	} catch (missing: DataAccessException) { emptyList() }

	fun recentSignups(limit: Int = 6): List<Signup> = try {
		jdbc.query("SELECT USER_SEQ, USER_NIC_NAME, USER_EMAIL, EMAIL_CERTIFY_YN, CREATE_DT FROM TB_USER ORDER BY USER_SEQ DESC LIMIT ?", { r, _ ->
			Signup(r.getLong(1), r.getString(2), r.getString(3), r.getString(4) == "Y", r.getTimestamp(5)?.toLocalDateTime())
		}, limit)
	} catch (missing: DataAccessException) { emptyList() }

	private fun count(sql: String, vararg args: Any): Long? =
		try { jdbc.queryForObject(sql, Long::class.java, *args) } catch (missing: DataAccessException) { null }

	fun report(today: LocalDate = Kst.today()): Report {
		val first = weekStart(today).minusWeeks((WEEKS - 1).toLong())
		val windowStart = first.minusWeeks(RETURN_WEEKS.toLong())
		val ex = exclusions.clause()
		return try {
			val visitorDays = HashMap<String, NavigableSet<LocalDate>>()
			jdbc.query("SELECT DISTINCT VISITOR_ID,VISIT_DATE FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?$ex", RowCallbackHandler { r ->
				visitorDays.getOrPut(r.getString(1)) { TreeSet() }.add(r.getDate(2).toLocalDate())
			}, windowStart, today)
			val traffic = HashMap<LocalDate, LongArray>()
			jdbc.query("SELECT VISIT_DATE,COUNT(*),COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?$ex GROUP BY VISIT_DATE", RowCallbackHandler { r ->
				traffic[r.getDate(1).toLocalDate()] = longArrayOf(r.getLong(2), r.getLong(3))
			}, first, today)
			val actions = HashMap<String, HashMap<String, HashSet<String>>>() // 주 시작일 → 행동 → 방문자
			val subjects = HashMap<String, HashMap<String, HashSet<String>>>() // 주 시작일 → 방문자 → 본 공고
			val counts = HashMap<String, HashMap<String, Long>>()
			jdbc.query("SELECT VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE FROM TB_USAGE_EVENT WHERE VISIT_DATE BETWEEN ? AND ? AND ACTION_NAME IN ('detail_view','favorite_add','share')$ex", RowCallbackHandler { r ->
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
			val (sources, total, notice) = sources(today, ex)
			Report(weeks, sources, total, notice, null)
		} catch (failure: DataAccessException) {
			Report(emptyList(), emptyList(), 0, null, "지표 테이블을 읽지 못했습니다. TB_PAGE_VIEW·TB_USAGE_EVENT 존재 여부와 관리자 DB 계정의 SELECT 권한을 확인하세요.")
		}
	}

	private fun sources(today: LocalDate, ex: String): Triple<List<Source>, Long, String?> {
		val start = today.minusDays(27)
		return try {
			val total = jdbc.queryForObject("SELECT COUNT(DISTINCT SESSION_ID) FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ?$ex", Long::class.java, start, today) ?: 0L
			val byLabel = LinkedHashMap<String, Long>(); val hosts = HashMap<String, String>()
			for (row in jdbc.queryForList("SELECT REFERRER_HOST,COUNT(DISTINCT SESSION_ID) AS SESSIONS FROM TB_PAGE_VIEW WHERE VISIT_DATE BETWEEN ? AND ? AND REFERRER_HOST IS NOT NULL$ex GROUP BY REFERRER_HOST", start, today)) {
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
		val MAP_TOOL_NAMES = mapOf("tool/map-fullscreen" to "지도 전체 화면", "tool/map-measure" to "거리 재기", "tool/map-radius" to "반경 그리기",
			"tool/map-search" to "지도 검색", "tool/map-suggest" to "검색 자동완성 선택")
		val PAGE_NAMES = mapOf("/" to "청약 캘린더", "/announcements" to "공고 상세", "/match" to "맞춤 청약", "/match/zupzup" to "무순위·줍줍", "/score" to "가점 계산기",
			"/my" to "나의 청약", "/faq" to "자주 묻는 질문", "/this-week" to "이번 주 청약", "/regions" to "지역별 청약", "/login" to "로그인", "/certify" to "이메일 인증",
			"/showSubscriptDetail" to "공고 상세(이전 주소)")

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
