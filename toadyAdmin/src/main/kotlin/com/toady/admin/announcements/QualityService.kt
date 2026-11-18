package com.toady.admin.announcements

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDate

/**
 * 공고 데이터 품질 점검과 위치 확인 대기열.
 * - 위치 확인: 접수 중·예정이거나 최근 공고 중 주소에 지번·도로명 번호가 없어(지구·블록 주소) 지도에 정확히 놓을 수 없는 공고.
 *   운영자가 확인한 주소를 넣어도 앱이 지도 검색으로 다시 검증한 뒤에만 핀을 놓는다(엉뚱한 곳에 확정하지 않게).
 * - 데이터 점검: 주소·접수일 누락, 날짜 형식 불일치(유형별 YYYY-MM-DD / YYYYMMDD), APT 주택형 누락.
 * 읽기: 공고 상세·주택형 테이블. 쓰기: TB_ANNOUNCEMENT_OVERRIDE.LOCATION_ADDRESS(database.sql 27번).
 * 호출: AdminController의 위치 확인(/admin/locations)·데이터 점검(/admin/data-check) 화면.
 */
// @Service: 업무 로직 빈.
@Service
class QualityService(private val jdbc: JdbcTemplate) {
	/** 데이터 점검에서 찾은 문제 하나. 공고 하나에 문제가 여러 개면 여러 줄이 된다. */
	data class Issue(val id: String, val typeLabel: String, val houseNm: String, val address: String?, val start: LocalDate?, val problem: String) {
		val adminPath get() = "/admin/announcements/$id"
	}
	/** 위치 확인 대기 공고. confirmed가 있으면 운영자가 이미 주소를 넣은 것이다. */
	data class Pending(val id: String, val typeLabel: String, val houseNm: String, val address: String?, val start: LocalDate?, val confirmed: String?) {
		val adminPath get() = "/admin/announcements/$id"
	}

	/** 주소에 '동·리·로·길 + 번호'가 있으면 지번·도로명 주소로 본다(앱 resolveLocation의 지번 판정과 같은 기준). */
	fun hasParcel(address: String?): Boolean = address != null && PARCEL.containsMatchIn(address)

	/** 최근 공고(접수 시작이 30일 전 이후 또는 아직 없음) 중 위치 확인이 필요한 것. confirmed는 운영자가 넣은 주소. */
	fun locationQueue(includeConfirmed: Boolean): List<Pending> {
		val confirmed = confirmedAddresses()
		val since = Kst.today().minusDays(30)
		return AnnouncementService.TYPES.values.flatMap { t ->
			try {
				jdbc.query("SELECT HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, ${t.address} AS ADDR, ${t.start} AS S FROM ${t.detail}") { r, _ ->
					val start = AnnouncementService.date(r.getString("S"))
					val id = "${t.key}/${r.getString(1)}/${r.getString(2)}"
					if (start != null && start.isBefore(since)) null
					else Pending(id, t.label, r.getString("HOUSE_NM") ?: "(이름 없음)", r.getString("ADDR"), start, confirmed[id])
				}.filterNotNull()
			} catch (missing: DataAccessException) { emptyList() }
		}.filter { !hasParcel(it.address) && (includeConfirmed || it.confirmed == null) }
			.sortedWith(compareBy<Pending, LocalDate?>(nullsLast()) { it.start }.thenBy { it.id })
	}

	/** 운영자가 확인한 주소 전체(공고 ID → 주소). DB의 유형 값(PUBLIC_RENTAL)을 화면 ID 모양(public-rental)으로 바꾼다. */
	fun confirmedAddresses(): Map<String, String> = try {
		val result = HashMap<String, String>()
		jdbc.query("SELECT ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, LOCATION_ADDRESS FROM TB_ANNOUNCEMENT_OVERRIDE WHERE LOCATION_ADDRESS IS NOT NULL") { r ->
			result["${r.getString(1).lowercase().replace('_', '-')}/${r.getString(2)}/${r.getString(3)}"] = r.getString(4)
		}
		result
	} catch (missing: DataAccessException) { emptyMap() }

	/** LOCATION_ADDRESS 컬럼까지 준비됐는지 확인한다(database.sql 27번 적용 여부). */
	fun locationReady(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_ANNOUNCEMENT_OVERRIDE WHERE LOCATION_ADDRESS IS NULL AND 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	/** 운영자가 확인한 주소를 저장한다(빈 값이면 지운다). 지번·도로명 번호가 있는 주소만 받는다. 이전 값을 돌려준다. */
	fun saveLocation(id: String, address: String, admin: String): Result<String?> {
		val key = OverrideService.key(id) ?: return Result.failure(IllegalArgumentException("공고를 찾을 수 없습니다."))
		val text = address.trim().replace(Regex("\\s+"), " ").take(200)
		if (text.isNotEmpty() && !hasParcel(text)) return Result.failure(IllegalArgumentException("지번(예: 동패동 1234-5)이나 도로명 번호(예: 동패로 123)가 있는 주소를 넣어 주세요."))
		val before = confirmedAddresses()[id]
		val now = Timestamp.valueOf(Kst.now())
		val updated = jdbc.update("UPDATE TB_ANNOUNCEMENT_OVERRIDE SET LOCATION_ADDRESS=?, ADMIN_NAME=?, UPDATE_DT=? WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=?",
			text.ifEmpty { null }, admin.take(100), now, key.first, key.second, key.third)
		if (updated == 0 && text.isNotEmpty()) jdbc.update("INSERT INTO TB_ANNOUNCEMENT_OVERRIDE (ANNOUNCEMENT_TYPE, HOUSE_MANAGE_NO, PBLANC_NO, HIDDEN_YN, LOCATION_ADDRESS, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,'N',?,?,?)",
			key.first, key.second, key.third, text, admin.take(100), now)
		// 숨김·안내·위치가 모두 비면 행을 지워 원래대로 둔다.
		jdbc.update("DELETE FROM TB_ANNOUNCEMENT_OVERRIDE WHERE ANNOUNCEMENT_TYPE=? AND HOUSE_MANAGE_NO=? AND PBLANC_NO=? AND HIDDEN_YN='N' AND NOTICE IS NULL AND LOCATION_ADDRESS IS NULL",
			key.first, key.second, key.third)
		return Result.success(before)
	}

	/** 최근 90일·앞으로의 공고에서 데이터 문제를 찾는다. */
	fun issues(): List<Issue> {
		val since = Kst.today().minusDays(90)
		return AnnouncementService.TYPES.values.flatMap { t ->
			val compact = t.key == "public-rental" || t.key == "optional"
			val withModels = t.key == "apt"
			try {
				jdbc.query("SELECT D.HOUSE_MANAGE_NO, D.PBLANC_NO, D.HOUSE_NM, D.${t.address} AS ADDR, D.${t.start} AS S, D.${t.end} AS E, D.RCRIT_PBLANC_DE AS N" +
					(if (withModels) ", (SELECT COUNT(*) FROM ${t.model} M WHERE M.HOUSE_MANAGE_NO=D.HOUSE_MANAGE_NO AND M.PBLANC_NO=D.PBLANC_NO) AS MODELS" else "") +
					" FROM ${t.detail} D") { r, _ ->
					val start = AnnouncementService.date(r.getString("S"))
					val notice = AnnouncementService.date(r.getString("N"))
					if ((start ?: notice)?.isBefore(since) == true) return@query emptyList<Issue>()
					val id = "${t.key}/${r.getString(1)}/${r.getString(2)}"
					val name = r.getString("HOUSE_NM") ?: "(이름 없음)"
					val problems = ArrayList<String>()
					if (r.getString("ADDR").isNullOrBlank()) problems += "주소 없음"
					if (r.getString("S").isNullOrBlank() || r.getString("E").isNullOrBlank()) problems += "접수 일정 없음"
					listOf(r.getString("S"), r.getString("E"), r.getString("N")).filterNotNull().filter { it.isNotBlank() }.forEach { value ->
						val ok = if (compact) value.matches(Regex("\\d{8}")) else value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
						if (!ok && problems.none { it.startsWith("날짜 형식") }) problems += "날짜 형식(${if (compact) "YYYYMMDD" else "YYYY-MM-DD"} 아님): $value"
					}
					if (withModels && r.getLong("MODELS") == 0L) problems += "주택형·분양가 없음"
					problems.map { Issue(id, t.label, name, r.getString("ADDR"), start, it) }
				}.flatten()
			} catch (missing: DataAccessException) { emptyList() }
		}.sortedWith(compareBy<Issue, LocalDate?>(nullsLast()) { it.start }.thenBy { it.id })
	}

	// PARCEL: '한글·숫자 + 동/리/로/길 + 공백 + (산) + 번호(-부번)' 모양. 예: 동패동 1234-5, 동패로 123.
	companion object {
		private val PARCEL = Regex("[가-힣\\d]+(동|리|로|길)\\s+(산\\s*)?\\d+(-\\d+)?")
	}
}
