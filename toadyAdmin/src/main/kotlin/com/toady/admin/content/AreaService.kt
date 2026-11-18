package com.toady.admin.content

import com.toady.admin.Kst
import org.springframework.dao.DataAccessException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.LocalDateTime

/**
 * 생활권(database.sql 27번 TB_LIVING_AREA). 처음에는 API가 코드의 기본 생활권으로 채운다.
 * 검색·생활권 공개 페이지·사이트맵·알림 주제가 1분 안에 바뀐다. SLUG는 공개 주소(/areas/{slug})라 만든 뒤 바꾸지 않는다.
 * 목록 값(별칭·검색어·시군)은 쉼표로 나눈다. 짧은 검색어(공백 없음)는 시·군 중 하나가 함께 있어야 인정된다(다른 지역 같은 동 이름 오인 방지).
 * 호출: ContentController의 생활권 화면(/admin/areas).
 */
// @Service: 업무 로직 빈.
@Service
class AreaService(private val jdbc: JdbcTemplate) {
	data class Area(val slug: String, val label: String, val aliases: String, val terms: String, val scope: String, val description: String,
	                val order: Int, val enabled: Boolean, val admin: String, val updatedAt: LocalDateTime?)

	/** 테이블이 있는지 확인한다(WHERE 1=0이라 행은 읽지 않는다). 없으면 화면에 적용 안내를 보여 준다. */
	fun available(): Boolean = try { jdbc.queryForObject("SELECT COUNT(*) FROM TB_LIVING_AREA WHERE 1=0", Long::class.java); true } catch (missing: DataAccessException) { false }

	fun list(): List<Area> = try {
		jdbc.query("SELECT * FROM TB_LIVING_AREA ORDER BY SORT_ORDER, SLUG") { r, _ ->
			Area(r.getString("SLUG"), r.getString("LABEL"), r.getString("ALIASES"), r.getString("TERMS"), r.getString("SCOPE"), r.getString("DESCRIPTION"),
				r.getInt("SORT_ORDER"), r.getString("USE_YN") == "Y", r.getString("ADMIN_NAME"), r.getTimestamp("UPDATE_DT")?.toLocalDateTime())
		}
	} catch (missing: DataAccessException) { emptyList() }

	fun find(slug: String): Area? = list().firstOrNull { it.slug == slug }

	/** 쉼표 목록을 정리한다: 앞뒤 공백·연속 공백 제거, 빈 값·중복 제거. */
	private fun clean(csv: String) = csv.split(',').map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.isNotEmpty() }.distinct().joinToString(",")

	/** 추가·수정 공통 검사. 문제가 있으면 안내 문구, 없으면 null. */
	private fun invalid(label: String, aliases: String, terms: String, scope: String, description: String): String? = when {
		label.trim().length !in 2..30 -> "생활권 이름은 2~30자입니다."
		clean(aliases).isEmpty() -> "검색 별칭을 하나 이상 넣어 주세요(예: 운정, 운정신도시)."
		clean(terms).isEmpty() -> "주소·단지명 검색어를 하나 이상 넣어 주세요(예: 운정, 파주시 동패동)."
		clean(terms).split(',').any { !it.contains(' ') } && clean(scope).isEmpty() -> "공백 없는 짧은 검색어가 있으면 시·군(예: 파주)을 넣어야 다른 지역 같은 이름을 잘못 묶지 않습니다."
		description.trim().length !in 2..100 -> "설명은 2~100자입니다."
		clean(aliases).length > 300 || clean(terms).length > 1000 || clean(scope).length > 200 -> "목록이 너무 깁니다."
		else -> null
	}

	/** 생활권 추가. 성공하면 null, 실패하면 화면에 보일 안내 문구를 돌려준다. */
	fun add(slug: String, label: String, aliases: String, terms: String, scope: String, description: String, order: Int, admin: String): String? {
		if (!SLUG.matches(slug)) return "주소용 이름(slug)은 영문 소문자·숫자·하이픈 2~40자입니다(예: unjeong)."
		invalid(label, aliases, terms, scope, description)?.let { return it }
		if (!available()) return "생활권 테이블이 없습니다. database.sql 27번과 관리자 계정 권한을 적용하세요."
		return try {
			jdbc.update("INSERT INTO TB_LIVING_AREA (SLUG, LABEL, ALIASES, TERMS, SCOPE, DESCRIPTION, SORT_ORDER, USE_YN, ADMIN_NAME, UPDATE_DT) VALUES (?,?,?,?,?,?,?,'Y',?,?)",
				slug, label.trim(), clean(aliases), clean(terms), clean(scope), description.trim(), order, admin.take(100), Timestamp.valueOf(Kst.now()))
			null
		} catch (duplicate: DuplicateKeyException) { "이미 있는 주소용 이름(slug)입니다." }
	}

	/** 생활권 수정(slug는 바꾸지 않는다). 성공하면 null, 실패하면 안내 문구. */
	fun update(slug: String, label: String, aliases: String, terms: String, scope: String, description: String, order: Int, enabled: Boolean, admin: String): String? {
		invalid(label, aliases, terms, scope, description)?.let { return it }
		val changed = try {
			jdbc.update("UPDATE TB_LIVING_AREA SET LABEL=?, ALIASES=?, TERMS=?, SCOPE=?, DESCRIPTION=?, SORT_ORDER=?, USE_YN=?, ADMIN_NAME=?, UPDATE_DT=? WHERE SLUG=?",
				label.trim(), clean(aliases), clean(terms), clean(scope), description.trim(), order, if (enabled) "Y" else "N", admin.take(100), Timestamp.valueOf(Kst.now()), slug)
		} catch (missing: DataAccessException) { 0 }
		return if (changed > 0) null else "없는 생활권입니다."
	}

	companion object { val SLUG = Regex("[a-z0-9-]{2,40}") }
}
