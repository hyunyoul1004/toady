package com.toady.admin.insights

import com.toady.admin.Kst
import com.toady.admin.announcements.AnnouncementService
import com.toady.admin.metrics.ExclusionService
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Date
import java.sql.Timestamp

/**
 * 유입·커뮤니티 지표(3단계): 검색어(결과 0건 포함), 공유와 메신저 유입, 단지별 이야기 활성도.
 * 검색어는 방문자와 연결하지 않은 일별 건수(database.sql 27번 TB_SEARCH_STAT)만 읽는다.
 * 공유·메신저 유입은 대시보드 지표처럼 운영자 브라우저(ExclusionService)를 뺀다.
 * 읽는 테이블: TB_SEARCH_STAT, TB_USAGE_EVENT(share), TB_PAGE_VIEW(유입 사이트), TB_LTTOT_PBLANC_COMMENT, TB_USER_REPORT. 읽기 전용.
 * 호출: AdminController의 유입·커뮤니티 화면(/admin/insights).
 */
// @Service: 업무 로직 빈.
@Service
class InsightService(private val jdbc: JdbcTemplate, private val announcements: AnnouncementService, private val exclusions: ExclusionService) {
	data class Term(val term: String, val hits: Long)
	data class Shared(val id: String, val name: String?, val shares: Long) { val adminPath get() = if (id.startsWith("tool/")) null else "/admin/announcements/$id" }
	data class Inflow(val host: String, val sessions: Long)
	data class Story(val id: String, val name: String?, val roots: Long, val replies: Long, val writers: Long, val firstReplyMinutes: Long?, val reports: Long) {
		val adminPath get() = "/admin/announcements/$id"
	}
	/** 화면 전체 값. 테이블이 없는 항목은 null이라 화면에서 '준비 안 됨'으로 보인다. */
	data class Report(val days: Int, val terms: List<Term>?, val emptyTerms: List<Term>?, val shares: List<Shared>?, val shareTotal: Long?, val inflow: List<Inflow>?,
	                  val stories: List<Story>, val storyTotals: Story?, val excludeTest: Boolean)

	/**
	 * 최근 days일 지표를 모은다. 항목마다 따로 try로 감싸 하나가 실패해도 나머지는 보인다.
	 * 메신저 유입은 SQL로 상위 50개 사이트를 받은 뒤 isMessenger(MESSENGERS 목록, 하위 도메인 포함)에 맞는 것만 남긴다. 검색 엔진은 넣지 않는다.
	 */
	fun report(days: Int, excludeTest: Boolean): Report {
		val since = Kst.today().minusDays(days.toLong() - 1)
		val ex = exclusions.clause()
		val terms = try {
			jdbc.query("SELECT TERM, SUM(HITS) H FROM TB_SEARCH_STAT WHERE STAT_DATE>=? GROUP BY TERM ORDER BY H DESC, TERM LIMIT 20", { r, _ -> Term(r.getString(1), r.getLong(2)) }, Date.valueOf(since))
		} catch (missing: DataAccessException) { null }
		val empty = try {
			jdbc.query("SELECT TERM, SUM(HITS) H FROM TB_SEARCH_STAT WHERE STAT_DATE>=? AND EMPTY_YN='Y' GROUP BY TERM ORDER BY H DESC, TERM LIMIT 20", { r, _ -> Term(r.getString(1), r.getLong(2)) }, Date.valueOf(since))
		} catch (missing: DataAccessException) { null }
		val shareRows = try {
			jdbc.query("SELECT SUBJECT_ID, COUNT(*) C FROM TB_USAGE_EVENT WHERE ACTION_NAME='share' AND VISIT_DATE>=?$ex GROUP BY SUBJECT_ID ORDER BY C DESC LIMIT 20", { r, _ -> r.getString(1) to r.getLong(2) }, Date.valueOf(since))
		} catch (missing: DataAccessException) { null }
		val names = shareRows?.map { it.first }?.filter { !it.startsWith("tool/") }?.takeIf { it.isNotEmpty() }?.let { announcements.names(it) } ?: emptyMap()
		val shares = shareRows?.map { (id, count) -> Shared(id, names[id] ?: TOOLS[id], count) }
		val shareTotal = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_USAGE_EVENT WHERE ACTION_NAME='share' AND VISIT_DATE>=?$ex", Long::class.java, Date.valueOf(since)) } catch (missing: DataAccessException) { null }
		val inflow = try {
			jdbc.query("SELECT REFERRER_HOST, COUNT(DISTINCT SESSION_ID) C FROM TB_PAGE_VIEW WHERE VISIT_DATE>=? AND REFERRER_HOST IS NOT NULL$ex GROUP BY REFERRER_HOST ORDER BY C DESC LIMIT 50",
				{ r, _ -> Inflow(r.getString(1), r.getLong(2)) }, Date.valueOf(since)).filter { isMessenger(it.host) }
		} catch (missing: DataAccessException) { null }
		val stories = stories(since, excludeTest)
		val totals = if (stories.isEmpty()) null else Story("", null, stories.sumOf { it.roots }, stories.sumOf { it.replies }, stories.sumOf { it.writers },
			stories.mapNotNull { it.firstReplyMinutes }.takeIf { it.isNotEmpty() }?.let { it.sorted()[it.size / 2] }, stories.sumOf { it.reports })
		return Report(days, terms, empty, shares, shareTotal, inflow, stories.take(20), totals, excludeTest)
	}

	/** 단지별 이야기: 원글·답글 수, 글쓴이 수, 원글에 첫 답글이 달리기까지 걸린 시간(중앙값, 분), 신고 수. */
	private fun stories(since: java.time.LocalDate, excludeTest: Boolean): List<Story> {
		// 테스트 계정(이메일이 .test로 끝남)이 쓴 글을 뺀다. 중앙값은 정렬한 목록의 가운데 값으로 구한다.
		val testFilter = if (excludeTest) " AND C.CREATE_USER_SEQ NOT IN (SELECT USER_SEQ FROM TB_USER WHERE USER_EMAIL LIKE '%.test')" else ""
		data class Row(val id: String, val seq: Long, val parent: Long?, val user: Long, val at: java.time.LocalDateTime?)
		val rows = try {
			jdbc.query("SELECT C.ANNOUNCEMENT_TYPE, C.HOUSE_MANAGE_NO, C.PBLANC_NO, C.COMMENT_SEQ, C.PARENT_COMMENT_SEQ, C.CREATE_USER_SEQ, C.REG_DT FROM TB_LTTOT_PBLANC_COMMENT C" +
				" WHERE C.DELETE_YN='N' AND C.REG_DT>=?$testFilter", { r, _ ->
				val parent = r.getLong(5).let { if (r.wasNull()) null else it }
				Row("${r.getString(1).lowercase().replace('_', '-')}/${r.getString(2)}/${r.getString(3)}", r.getLong(4), parent, r.getLong(6), r.getTimestamp(7)?.toLocalDateTime())
			}, Timestamp.valueOf(since.atStartOfDay()))
		} catch (missing: DataAccessException) { return emptyList() }
		val reports = try {
			val map = HashMap<Long, Long>()
			jdbc.query("SELECT TARGET_SEQ, COUNT(*) FROM TB_USER_REPORT WHERE TARGET_TYPE='COMMENT' AND REG_DT>=? GROUP BY TARGET_SEQ", { r -> map[r.getLong(1)] = r.getLong(2) }, Timestamp.valueOf(since.atStartOfDay()))
			map
		} catch (missing: DataAccessException) { emptyMap<Long, Long>() }
		val byId = rows.groupBy { it.id }
		val names = announcements.names(byId.keys.toList())
		return byId.map { (id, list) ->
			val roots = list.filter { it.parent == null }
			val bySeq = list.associateBy { it.seq }
			val firstReply = list.filter { it.parent != null }.groupBy { it.parent }.mapNotNull { (parent, replies) ->
				val root = bySeq[parent] ?: return@mapNotNull null
				val first = replies.mapNotNull { it.at }.minOrNull() ?: return@mapNotNull null
				root.at?.let { java.time.Duration.between(it, first).toMinutes().coerceAtLeast(0) }
			}
			Story(id, names[id], roots.size.toLong(), (list.size - roots.size).toLong(), list.map { it.user }.distinct().size.toLong(),
				firstReply.takeIf { it.isNotEmpty() }?.sorted()?.let { it[it.size / 2] }, list.sumOf { reports[it.seq] ?: 0L })
		}.sortedByDescending { it.roots + it.replies }
	}

	// MESSENGERS: 공유 유입으로 볼 사이트. TOOLS: 공고가 아닌 공유 대상(도구 화면)의 표시 이름.
	companion object {
		// 네이버·다음은 검색 유입이 대부분이라 도메인 전체가 아니라 블로그·카페 하위 도메인만 넣는다(검색은 대시보드 '유입 경로'에서 본다).
		val MESSENGERS = listOf("kakao.com", "kakaocdn.net", "band.us", "line.me", "facebook.com", "instagram.com", "t.co", "x.com", "twitter.com", "threads.net", "threads.com",
			"blog.naver.com", "cafe.naver.com", "band.naver.com", "cafe.daum.net")
		/** 메신저·SNS 유입인지. 부분 문자열이 아니라 도메인·하위 도메인으로만 판별한다(search.naver.com·search.daum.net 같은 검색은 빠진다). */
		fun isMessenger(host: String): Boolean = host.lowercase().let { h -> MESSENGERS.any { h == it || h.endsWith(".$it") } }
		val TOOLS = mapOf("tool/score" to "가점 계산기", "tool/match" to "맞춤 청약", "tool/weekly" to "이번 주 청약")
	}
}
