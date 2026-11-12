package com.toady.admin.jobs

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 스케줄러 내부 API(/internal/admin) 호출. 수집 실행기와 자동 일정 타이머는 스케줄러 프로세스에 있다.
 * 토큰은 스케줄러와 공유하는 SCHEDULER_INTERNAL_TOKEN이며, 실패하면 화면에 보여 줄 문장으로 바꿔 던진다.
 */
@Component
class SchedulerClient(@Value("\${toady.scheduler.base-url}") baseUrl: String,
                      @Value("\${toady.scheduler.token:}") private val token: String) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Job(val id: String, val kind: String, val source: String, val startDate: LocalDate, val endDate: LocalDate, val status: String,
	               val totalChunks: Int, val completedChunks: Int, val cancelRequested: Boolean, val requestedBy: String, val message: String,
	               val createdAt: LocalDateTime?, val startedAt: LocalDateTime?, val finishedAt: LocalDateTime?, val statusLabel: String? = null) {
		val active get() = status in setOf("QUEUED", "RUNNING", "WAITING")
		val kindLabel get() = JobKind.label(kind)
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class Schedule(val kind: String, val enabled: Boolean, val cronExpression: String, val lookbackDays: Int, val nextRun: LocalDateTime?) {
		val kindLabel get() = JobKind.label(kind)
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
	data class State(val today: LocalDate, val active: Job?, val jobs: List<Job>, val schedules: List<Schedule>)
	class SchedulerException(message: String) : RuntimeException(message)

	private val client = RestClient.builder().baseUrl(baseUrl.trimEnd('/'))
		.requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(3000); setReadTimeout(10000) })
		.defaultHeader("X-Admin-Token", token).build()

	val configured get() = token.length >= 32

	fun state(): State = call { client.get().uri("/internal/admin/state").retrieve().body(State::class.java)!! }
	fun start(kind: String, startDate: LocalDate, endDate: LocalDate, requestedBy: String): String =
		message(post("/internal/admin/jobs", mapOf("kind" to kind, "startDate" to startDate.toString(), "endDate" to endDate.toString(), "requestedBy" to requestedBy)), "id")
	fun cancel(id: String): String = message(post("/internal/admin/jobs/{id}/cancel", null, id), "message")
	fun retry(id: String, requestedBy: String): String = message(post("/internal/admin/jobs/{id}/retry", mapOf("requestedBy" to requestedBy), id), "id")
	fun saveSchedule(kind: String, enabled: Boolean, cron: String, lookbackDays: Int): String =
		message(post("/internal/admin/schedules/{kind}", mapOf("enabled" to enabled, "cron" to cron, "lookbackDays" to lookbackDays), kind), "message")

	private fun post(path: String, body: Any?, vararg vars: Any): Map<*, *> = call {
		val spec = client.post().uri(path, *vars).contentType(MediaType.APPLICATION_JSON)
		(if (body != null) spec.body(body) else spec).retrieve()
			// 4xx는 스케줄러가 준 사유(기간 오류·진행 중 작업 등)를 그대로 보여 준다.
			.onStatus(HttpStatusCode::is4xxClientError) { _, _ -> }
			.body(Map::class.java) ?: emptyMap<String, Any>()
	}
	private fun message(response: Map<*, *>, key: String): String =
		response[key]?.toString() ?: throw SchedulerException(response["message"]?.toString() ?: "스케줄러가 요청을 처리하지 못했습니다.")

	private fun <T> call(block: () -> T): T {
		if (!configured) throw SchedulerException("스케줄러 연결 토큰(SCHEDULER_INTERNAL_TOKEN)이 설정되지 않았습니다.")
		return try {
			block()
		} catch (failure: RestClientResponseException) {
			log.warn("스케줄러 내부 API 오류: status={}", failure.statusCode.value())
			throw SchedulerException(when {
				// 토큰 검사(AdminInternalApi)가 거절하면 사유 본문이 오고, 스케줄러 관리 기능이 꺼져 있으면 보안 설정이 본문 없이 막는다.
				failure.statusCode.value() == 403 && failure.responseBodyAsString.contains("허용되지 않은 요청") ->
					"스케줄러 연결 토큰이 맞지 않습니다. 두 서비스의 SCHEDULER_INTERNAL_TOKEN이 같은지, 바꾼 뒤 컨테이너를 다시 만들었는지 확인하세요."
				failure.statusCode.value() == 403 || failure.statusCode.value() == 404 ->
					"스케줄러 관리 기능이 꺼져 있습니다. 스케줄러의 SCHEDULER_ADMIN_ENABLED=true와 토큰을 설정한 뒤 스케줄러 컨테이너를 다시 만드세요."
				else -> "스케줄러가 요청을 처리하지 못했습니다(${failure.statusCode.value()})."
			})
		} catch (failure: RestClientException) {
			log.warn("스케줄러 내부 API 연결 실패: {}", failure.javaClass.simpleName)
			throw SchedulerException("스케줄러에 연결하지 못했습니다. 스케줄러가 실행 중인지 확인하세요.")
		}
	}

	companion object { private val log = LoggerFactory.getLogger(SchedulerClient::class.java) }
}

/** 스케줄러 JobKind와 같은 이름·설명. */
enum class JobKind(val label: String, val description: String) {
	ALL("전체 수집", "공고·주택형을 수집한 뒤 경쟁률·가점·특별공급을 수집합니다."),
	ANNOUNCEMENTS("공고 · 주택형", "5개 공고 유형의 상세 정보와 주택형을 갱신합니다."),
	COMPETITION("경쟁률 · 가점 · 특별공급", "전체 8개 결과 API를 수집합니다."),
	SCORES("APT 당첨 가점", "과거 가점 복원에 필요한 API만 수집합니다.");
	companion object { fun label(name: String) = entries.firstOrNull { it.name == name }?.label ?: name }
}
