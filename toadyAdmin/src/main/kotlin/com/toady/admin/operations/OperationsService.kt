package com.toady.admin.operations

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 공고 데이터·주변 시세 비교·웹 푸시 운영 상태. 모두 조회 전용이다.
 * 테이블이 아직 없는 환경에서도 화면이 열리도록 항목별로 실패를 따로 처리한다.
 */
@Service
class OperationsService(private val jdbc: JdbcTemplate) {
	/** 공고 유형별 저장 건수와 마지막으로 새 행이 생긴 시각(REG_DT는 기존 행을 갱신해도 바뀌지 않는다). */
	data class Collection(val label: String, val total: Long?, val lastInserted: LocalDateTime?)
	/** 수집 작업 종류별 마지막 실행 결과. 수집이 멈췄는지는 이 이력으로 판단한다. */
	data class LastRun(val kind: String, val status: String?, val finishedAt: LocalDateTime?, val lastSuccess: LocalDateTime?) {
		val kindLabel get() = com.toady.admin.jobs.JobKind.label(kind)
		/** 마지막 성공이 30시간보다 오래됐으면(매일 자동 수집 기준) 확인이 필요하다. */
		val stale get() = lastSuccess == null || lastSuccess.isBefore(Kst.now().minusHours(30))
	}
	data class GapStatus(val status: String, val count: Long, val latest: LocalDateTime?) {
		val label get() = when (status) {
			"READY" -> "비교 가능"; "NO_LOCATION" -> "위치 미확인"; "NO_TRADES" -> "거래 부족"; "UNAVAILABLE" -> "제공처 실패·재시도"; else -> status
		}
	}
	data class GapRow(val id: String, val status: String, val dong: String?, val area: Int?, val supply: Long?, val confidence: String?, val computedAt: LocalDateTime?)
	data class Push(val total: Long, val members: Long, val withAreas: Long, val successWeek: Long, val failingToday: Long, val lastDigest: LocalDateTime?, val topics: List<Pair<String, Long>>)
	data class Report(val runs: List<LastRun>?, val collections: List<Collection>, val gaps: List<GapStatus>?, val recentGaps: List<GapRow>, val push: Push?)

	fun report(): Report = Report(runs(), collections(), gaps(), recentGaps(), push())

	private fun runs(): List<LastRun>? = try {
		jdbc.query("""
			SELECT J.JOB_KIND,
			  (SELECT X.STATUS FROM TB_SCHEDULER_ADMIN_JOB X WHERE X.JOB_KIND=J.JOB_KIND ORDER BY X.CREATED_AT DESC LIMIT 1) AS LAST_STATUS,
			  MAX(J.FINISHED_AT) AS LAST_FINISHED,
			  MAX(CASE WHEN J.STATUS='SUCCEEDED' THEN J.FINISHED_AT END) AS LAST_SUCCESS
			FROM TB_SCHEDULER_ADMIN_JOB J GROUP BY J.JOB_KIND ORDER BY J.JOB_KIND""".trimIndent()) { r, _ ->
			LastRun(r.getString(1), r.getString(2), r.getTimestamp(3)?.toLocalDateTime(), r.getTimestamp(4)?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { null }

	private fun collections(): List<Collection> = TABLES.map { (label, table) ->
		try {
			jdbc.query("SELECT COUNT(*), MAX(REG_DT) FROM $table") { r, _ -> Collection(label, r.getLong(1), r.getTimestamp(2)?.toLocalDateTime()) }.first()
		} catch (missing: DataAccessException) {
			Collection(label, null, null)
		}
	}

	private fun gaps(): List<GapStatus>? = try {
		jdbc.query("SELECT STATUS, COUNT(*), MAX(COMPUTED_DT) FROM TB_PRICE_GAP GROUP BY STATUS ORDER BY STATUS") { r, _ ->
			GapStatus(r.getString(1), r.getLong(2), r.getTimestamp(3)?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { null }

	private fun recentGaps(): List<GapRow> = try {
		jdbc.query("SELECT ANNOUNCEMENT_ID,STATUS,DONG,AREA,SUPPLY_MANWON,CONFIDENCE,COMPUTED_DT FROM TB_PRICE_GAP ORDER BY COMPUTED_DT DESC LIMIT 20") { r, _ ->
			GapRow(r.getString(1), r.getString(2), r.getString(3), r.getObject(4)?.let { (it as Number).toInt() }, r.getObject(5)?.let { (it as Number).toLong() },
				r.getString(6), r.getTimestamp(7)?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { emptyList() }

	private fun push(): Push? = try {
		val now = Kst.now()
		fun count(sql: String, vararg args: Any) = jdbc.queryForObject(sql, Long::class.java, *args) ?: 0L
		val topics = HashMap<String, Long>()
		jdbc.query("SELECT AREA_SLUGS FROM TB_PUSH_SUBSCRIPTION WHERE AREA_SLUGS <> ''", RowCallbackHandler { r ->
			r.getString(1).split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { topics.merge(it, 1L, Long::plus) }
		})
		Push(
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION"),
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE USER_SEQ IS NOT NULL"),
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE AREA_SLUGS <> ''"),
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE LAST_SUCCESS_DT >= ?", Timestamp.valueOf(now.minusDays(7))),
			// 오늘 발송을 시도했는데 푸시 서비스가 받지 않은 구독(만료·차단 의심)
			count("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE LAST_ATTEMPT_DATE = ? AND (LAST_SUCCESS_DT IS NULL OR LAST_SUCCESS_DT < ?)",
				java.sql.Date.valueOf(now.toLocalDate()), Timestamp.valueOf(now.toLocalDate().atStartOfDay())),
			jdbc.queryForObject("SELECT MAX(LAST_DIGEST_DT) FROM TB_PUSH_SUBSCRIPTION", Timestamp::class.java)?.toLocalDateTime(),
			topics.entries.sortedByDescending { it.value }.take(12).map { it.key to it.value })
	} catch (missing: DataAccessException) { null }

	companion object {
		val TABLES = listOf(
			"APT" to "TB_APT_LTTOT_PBLANC_DETAIL", "오피스텔/도시형" to "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL",
			"무순위·잔여세대" to "TB_REMNDR_LTTOT_PBLANC_DETAIL", "공공지원 민간임대" to "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL",
			"임의공급" to "TB_OPT_LTTOT_PBLANC_DETAIL", "APT 경쟁률" to "TB_APT_LTTOT_PBLANC_CMPET", "APT 당첨가점" to "TB_APT_LTTOT_PBLANC_SCORE")
	}
}
