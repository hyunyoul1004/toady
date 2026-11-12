package com.toady.admin.announcements

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 5개 유형의 공고(청약홈 수집 테이블)를 한 목록으로 찾고, 공고 하나의 운영 정보(조회·관심·댓글·시세 비교·투표)를 모은다.
 * 유형마다 컬럼 이름과 날짜 형식(YYYY-MM-DD / YYYYMMDD)이 달라 유형별 정의(TYPES)로 맞춘다.
 */
@Service
class AnnouncementService(private val jdbc: JdbcTemplate, private val exclusions: com.toady.admin.metrics.ExclusionService) {
	/** 유형별 테이블·컬럼 정의. 공고 ID는 서비스와 같은 '유형/주택관리번호/공고번호'. */
	data class Type(val key: String, val label: String, val detail: String, val model: String, val start: String, val end: String, val address: String,
	                val unitLabel: String, val unitArea: String?, val unitAmount: String)

	data class Row(val id: String, val type: String, val houseNm: String, val address: String?, val start: LocalDate?, val end: LocalDate?, val area: String?, val supply: String?) {
		val typeLabel get() = TYPES[type]?.label ?: type
		val status get() = status(start, end)
		val statusLabel get() = when (status) { "OPEN" -> "접수 중"; "UPCOMING" -> "접수 예정"; "CLOSED" -> "마감"; else -> "일정 없음" }
	}
	data class Page(val items: List<Row>, val total: Long, val page: Int, val size: Int) {
		val pages get() = maxOf(1, ((total + size - 1) / size).toInt())
	}
	data class Unit(val label: String, val area: String?, val households: String?, val amount: String?) {
		/** 청약홈 공급금액은 만원 단위다. 숫자가 아니면 받은 그대로 보여 준다. */
		val amountLabel get() = amount?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
			raw.replace(",", "").toLongOrNull()?.let { "%,d만원".format(it) } ?: raw
		} ?: "—"
	}
	data class Gap(val status: String, val dong: String?, val area: Int?, val supply: Long?, val q1: Long?, val q3: Long?, val basis: String?, val confidence: String?, val computedAt: LocalDateTime?)
	data class Detail(val row: Row, val kind: String?, val noticeDate: String?, val winnerDate: String?, val contract: String?, val moveIn: String?, val url: String?,
	                  val collectedAt: LocalDateTime?, val units: List<Unit>, val views30: Long?, val viewers30: Long?, val favorites: Long?,
	                  val gap: Gap?, val polls: List<Long>?) {
		val appPath get() = "/?subscriptSeq=" + java.net.URLEncoder.encode(row.id, Charsets.UTF_8)
		val pollTotal get() = polls?.sum() ?: 0
		val hasUnitArea get() = units.any { it.area != null }
	}

	/**
	 * status: all · open · upcoming · closed. type: 유형 키 또는 all. 검색어는 단지명·주소 부분 일치.
	 * 유형별 테이블의 문자 정렬 방식(collation)이 서로 달라 UNION으로 묶을 수 없으므로 유형마다 따로 읽어 합친 뒤 정렬·페이지를 나눈다
	 * (관리자 전용이고 전체 공고가 수천 건 규모라 메모리 처리로 충분하다).
	 */
	fun search(query: String?, type: String?, status: String?, page: Int, size: Int = PAGE_SIZE): Page {
		val types = TYPES.values.filter { type == null || type == "all" || it.key == type }
		val keyword = query?.trim()?.takeIf { it.isNotEmpty() }?.take(100)
		val like = keyword?.let { "%" + it.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%" }
		val all = types.flatMap { t ->
			val sql = "SELECT HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, ${t.address} AS ADDR, ${t.start} AS S, ${t.end} AS E, SUBSCRPT_AREA_CODE_NM, TOT_SUPLY_HSHLDCO FROM ${t.detail}" +
				(if (like != null) " WHERE HOUSE_NM LIKE ? OR ${t.address} LIKE ?" else "")
			optional {
				jdbc.query(sql, { r, _ ->
					Row("${t.key}/${r.getString(1)}/${r.getString(2)}", t.key, r.getString(3) ?: "(이름 없음)", r.getString(4), date(r.getString(5)), date(r.getString(6)), r.getString(7), r.getString(8))
				}, *(if (like != null) arrayOf<Any>(like, like) else emptyArray()))
			} ?: emptyList()
		}.filter { row ->
			when (status) {
				"open" -> row.status == "OPEN"; "upcoming" -> row.status == "UPCOMING"; "closed" -> row.status == "CLOSED"; else -> true
			}
		}.sortedWith(compareByDescending<Row> { it.start ?: java.time.LocalDate.MIN }.thenBy { it.houseNm })
		val safePage = page.coerceIn(0, maxOf(0, (all.size - 1) / size))
		return Page(all.drop(safePage * size).take(size), all.size.toLong(), safePage, size)
	}

	fun detail(type: String, house: String, notice: String): Detail? {
		val t = TYPES[type] ?: return null
		if (!KEY.matches(house) || !KEY.matches(notice)) return null
		val base = jdbc.query("""SELECT HOUSE_NM, ${t.address} AS ADDR, ${t.start} AS S, ${t.end} AS E, SUBSCRPT_AREA_CODE_NM, TOT_SUPLY_HSHLDCO, HOUSE_SECD_NM,
			RCRIT_PBLANC_DE, PRZWNER_PRESNATN_DE, CNTRCT_CNCLS_BGNDE, MVN_PREARNGE_YM, PBLANC_URL, REG_DT FROM ${t.detail} WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=?""".trimIndent(), { r, _ ->
			Detail(Row("$type/$house/$notice", type, r.getString("HOUSE_NM") ?: "(이름 없음)", r.getString("ADDR"), date(r.getString("S")), date(r.getString("E")),
				r.getString("SUBSCRPT_AREA_CODE_NM"), r.getString("TOT_SUPLY_HSHLDCO")), r.getString("HOUSE_SECD_NM"), r.getString("RCRIT_PBLANC_DE"),
				r.getString("PRZWNER_PRESNATN_DE"), r.getString("CNTRCT_CNCLS_BGNDE"), r.getString("MVN_PREARNGE_YM"), r.getString("PBLANC_URL")?.takeIf { it.startsWith("http") },
				r.getTimestamp("REG_DT")?.toLocalDateTime(), emptyList(), null, null, null, null, null)
		}, house, notice).firstOrNull() ?: return null
		val id = base.row.id
		return base.copy(
			units = optional { units(t, house, notice) } ?: emptyList(),
			views30 = optional { jdbc.queryForObject("SELECT COUNT(*) FROM TB_USAGE_EVENT WHERE ACTION_NAME='detail_view' AND SUBJECT_ID=? AND VISIT_DATE>=?${exclusions.clause()}", Long::class.java, id, Kst.today().minusDays(29)) },
			viewers30 = optional { jdbc.queryForObject("SELECT COUNT(DISTINCT VISITOR_ID) FROM TB_USAGE_EVENT WHERE ACTION_NAME='detail_view' AND SUBJECT_ID=? AND VISIT_DATE>=?${exclusions.clause()}", Long::class.java, id, Kst.today().minusDays(29)) },
			favorites = optional { jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?", Long::class.java, type, house, notice) },
			gap = optional {
				jdbc.query("SELECT STATUS,DONG,AREA,SUPPLY_MANWON,Q1_MANWON,Q3_MANWON,BASIS,CONFIDENCE,COMPUTED_DT FROM TB_PRICE_GAP WHERE ANNOUNCEMENT_ID=?", { r, _ ->
					Gap(r.getString(1), r.getString(2), num(r.getObject(3))?.toInt(), num(r.getObject(4)), num(r.getObject(5)), num(r.getObject(6)), r.getString(7), r.getString(8), r.getTimestamp(9)?.toLocalDateTime())
				}, id).firstOrNull()
			},
			polls = if (type != "apt") null else optional {
				val counts = LongArray(6)
				jdbc.query("SELECT CHOICE, COUNT(*) FROM TB_RATE_POLL WHERE ANNOUNCEMENT_ID=? GROUP BY CHOICE", { r, _ -> r.getInt(1).takeIf { it in 0..5 }?.let { counts[it] = r.getLong(2) } }, id)
				counts.toList()
			})
	}

	/** 공고 ID → 단지명. 목록·대시보드에서 ID 대신 이름을 보여 줄 때 쓴다. 없는 공고는 빠진다. */
	fun names(ids: Collection<String>): Map<String, String> {
		val byType = ids.mapNotNull { id -> id.split('/').takeIf { it.size == 3 && TYPES.containsKey(it[0]) } }.groupBy { it[0] }
		val out = HashMap<String, String>()
		byType.forEach { (type, keys) ->
			optional {
				keys.chunked(200).forEach { chunk ->
					val marks = chunk.joinToString(",") { "?" }
					jdbc.query("SELECT HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM FROM ${TYPES.getValue(type).detail} WHERE HOUSE_MANAGE_NO IN ($marks)", { r, _ ->
						out["$type/${r.getString(1)}/${r.getString(2)}"] = r.getString(3) ?: ""
					}, *chunk.map { it[1] }.toTypedArray())
				}
			}
		}
		return out
	}

	private fun units(t: Type, house: String, notice: String): List<Unit> =
		jdbc.query("SELECT ${t.unitLabel} AS L, ${t.unitArea ?: "NULL"} AS AR, SUPLY_HSHLDCO AS H, ${t.unitAmount} AS AMT FROM ${t.model} WHERE HOUSE_MANAGE_NO=? AND PBLANC_NO=? ORDER BY MODEL_NO", { r, _ ->
			Unit(r.getString("L") ?: "-", r.getString("AR"), r.getString("H"), r.getString("AMT"))
		}, house, notice)

	private fun <T> optional(block: () -> T): T? = try { block() } catch (missing: DataAccessException) { null }

	companion object {
		const val PAGE_SIZE = 30
		private val KEY = Regex("[A-Za-z0-9_-]{1,40}")
		val TYPES: Map<String, Type> = linkedMapOf(
			"apt" to Type("apt", "APT", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_APT_LTTOT_PBLANC_MDL", "RCEPT_BGNDE", "RCEPT_ENDDE", "HSSPLY_ADRES", "HOUSE_TY", null, "LTTOT_TOP_AMOUNT"),
			"urban" to Type("urban", "오피스텔/도시형", "TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL", "TB_URBTY_OFCTL_LTTOT_PBLANC_MDL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_HSHLDCO", "TP", "EXCLUSE_AR", "SUPLY_AMOUNT"),
			"remaining" to Type("remaining", "무순위·잔여세대", "TB_REMNDR_LTTOT_PBLANC_DETAIL", "TB_REMNDR_LTTOT_PBLANC_MDL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", "HOUSE_TY", null, "LTTOT_TOP_AMOUNT"),
			"public-rental" to Type("public-rental", "공공지원 민간임대", "TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL", "TB_PBL_PVT_RENT_LTTOT_PBLANC_MDL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", "TP", "EXCLUSE_AR", "SUPLY_AMOUNT"),
			"optional" to Type("optional", "임의공급", "TB_OPT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_MDL", "SUBSCRPT_RCEPT_BGNDE", "SUBSCRPT_RCEPT_ENDDE", "HSSPLY_ADRES", "HOUSE_TY", null, "LTTOT_TOP_AMOUNT"))

		/** 'YYYY-MM-DD'·'YYYYMMDD' 모두 받는다. */
		fun date(value: String?): LocalDate? {
			val digits = value?.filter { it.isDigit() } ?: return null
			return if (digits.length < 8) null else runCatching { LocalDate.of(digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt()) }.getOrNull()
		}
		fun status(start: LocalDate?, end: LocalDate?, today: LocalDate = Kst.today()): String = when {
			start == null -> "NONE"
			start.isAfter(today) -> "UPCOMING"
			!(end ?: start).isBefore(today) -> "OPEN"
			else -> "CLOSED"
		}
		private fun num(value: Any?): Long? = (value as? Number)?.toLong()
	}
}
