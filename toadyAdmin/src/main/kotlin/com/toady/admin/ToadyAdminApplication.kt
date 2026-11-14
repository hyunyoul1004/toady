package com.toady.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * TOADY 관리자 사이트. 서비스 지표·회원·댓글·공고/알림 운영 화면과 수집 작업 제어를 한곳에 모은다.
 * 수집 실행기는 스케줄러 프로세스에 있으므로 실행·중지·일정 변경은 스케줄러 내부 API로 요청한다.
 */
@SpringBootApplication
class ToadyAdminApplication

fun main(args: Array<String>) {
	runApplication<ToadyAdminApplication>(*args)
}

/** 목록 한 페이지. 회원·댓글·공고 목록이 같은 모양(pages는 화면 페이지 이동용)을 쓴다. */
data class Page<T>(val items: List<T>, val total: Long, val page: Int, val size: Int) {
	val pages get() = maxOf(1, ((total + size - 1) / size).toInt())
}

/** 검색어를 부분 일치 LIKE 값으로 바꾼다. %·_·\ 는 글자 그대로 찾도록 이스케이프한다. */
fun likeContains(keyword: String): String = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

/** 서버 OS 시간대와 무관하게 화면·집계 날짜를 한국시간으로 맞춘다. */
object Kst {
	val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
	fun today(): LocalDate = LocalDate.now(ZONE)
	fun now(): LocalDateTime = LocalDateTime.now(ZONE)
}
