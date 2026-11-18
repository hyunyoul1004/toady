package com.toady.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * TOADY 관리자 사이트. 서비스 지표·회원·댓글·공고/알림 운영 화면과 수집 작업 제어를 한곳에 모은다.
 * 수집 실행기는 스케줄러 프로세스에 있으므로 실행·중지·일정 변경은 스케줄러 내부 API로 요청한다.
 * 8083 포트에서 돌고, 앞단 nginx가 https://admin.toady.co.kr 로 관리자 IP에서 온 요청만 넘겨준다.
 * 화면은 Thymeleaf(templates/admin), DB는 JPA 없이 JdbcTemplate로 SQL을 직접 쓴다.
 */
// @SpringBootApplication: com.toady.admin 아래의 @Service·@Controller·@Configuration 등을 찾아 빈으로 등록하고 자동 설정을 켠다.
// Kotlin에서는 내용이 없는 클래스도 이렇게 선언만 해 두면 된다.
@SpringBootApplication
class ToadyAdminApplication

/** 시작점. runApplication은 SpringApplication.run의 Kotlin용 짧은 표현이다. */
fun main(args: Array<String>) {
	runApplication<ToadyAdminApplication>(*args)
}

/** 목록 한 페이지. 회원·댓글·공고 목록이 같은 모양(pages는 화면 페이지 이동용)을 쓴다. */
data class Page<T>(val items: List<T>, val total: Long, val page: Int, val size: Int) {
	// get()을 붙인 속성이라 읽을 때마다 계산한다. 0건이어도 화면에는 1페이지로 보인다.
	val pages get() = maxOf(1, ((total + size - 1) / size).toInt())
}

/** 검색어를 부분 일치 LIKE 값으로 바꾼다. %·_·\ 는 글자 그대로 찾도록 이스케이프한다. */
fun likeContains(keyword: String): String = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/** 서버 OS 시간대와 무관하게 화면·집계 날짜를 한국시간으로 맞춘다. object는 인스턴스가 하나뿐인 Kotlin 싱글턴이다. */
object Kst {
	val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
	fun today(): LocalDate = LocalDate.now(ZONE)
	fun now(): LocalDateTime = LocalDateTime.now(ZONE)
}
