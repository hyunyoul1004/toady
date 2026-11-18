package com.toady.admin.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * 관리자 → API 내부 주소(/internal/admin). 같은 docker-compose 네트워크에서만 부를 수 있고, API와 같은 토큰(ADMIN_API_TOKEN, 32자 이상)을 보낸다.
 * 메일·푸시 발송 키는 API에만 있으므로 발송은 API가 한다.
 * 호출: AdminController·ContentController(푸시 발송, 인증 메일 재발송, 회원 탈퇴 처리).
 */
// @Component: 빈으로 등록한다. 생성자의 @Value("\${...}")로 application.yml의 toady.api.* 값을 받는다
// (Kotlin 문자열에서 $는 변수 표시라 \$로 막아 둔다).
@Component
class ApiClient(@Value("\${toady.api.base-url:http://toadyWebApi:8080}") baseUrl: String, @Value("\${toady.api.token:}") private val token: String) {
	/** API 응답 요약. ok가 false면 message를 화면에 그대로 보여 준다. */
	data class Reply(val ok: Boolean, val message: String, val body: Map<*, *> = emptyMap<String, Any>())

	private val client = RestClient.builder().baseUrl(baseUrl.trimEnd('/'))
		.requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(3000); setReadTimeout(60000) })
		.defaultHeader("X-Admin-Token", token).build()

	/** 토큰이 32자 이상 설정됐는지. 없으면 요청을 보내지 않고 안내 문구만 돌려준다. */
	val configured get() = token.trim().length >= 32

	/** 웹 푸시 발송 요청. testUserSeq를 주면 그 회원에게만 시험 발송한다. */
	fun push(title: String, body: String, url: String, testUserSeq: Long?): Reply =
		post("/internal/admin/push", mapOf("title" to title, "body" to body, "url" to url, "testUserSeq" to testUserSeq))
	fun resendVerification(userSeq: Long): Reply = post("/internal/admin/members/{seq}/resend-verification", null, userSeq)
	fun withdraw(userSeq: Long): Reply = post("/internal/admin/members/{seq}/withdraw", null, userSeq)

	/**
	 * POST 요청 공통 처리. 오류 상태도 예외 대신 Reply로 돌려주려고 onStatus에서 아무것도 하지 않는다.
	 * 본문 없는 404는 API 쪽 토큰 검사에서 막힌 경우로 본다.
	 */
	private fun post(path: String, body: Any?, vararg vars: Any): Reply {
		if (!configured) return Reply(false, "API 내부 토큰(ADMIN_API_TOKEN)이 설정되지 않았습니다. 관리자와 API의 .env.prod에 같은 값(32자 이상)을 넣으세요.")
		return try {
			val spec = client.post().uri(path, *vars).contentType(MediaType.APPLICATION_JSON)
			val entity = (if (body != null) spec.body(body) else spec).retrieve().onStatus({ it.isError }) { _, _ -> }.toEntity(Map::class.java)
			val map = entity.body ?: emptyMap<String, Any>()
			val status = entity.statusCode.value()
			when {
				status == 404 && map.isEmpty() -> Reply(false, "API가 요청을 거절했습니다. 토큰이 API와 같은지 확인하세요.")
				status in 200..299 -> Reply(true, map["message"]?.toString() ?: "처리했습니다.", map)
				else -> Reply(false, map["message"]?.toString() ?: "API 오류(HTTP $status)")
			}
		} catch (failure: RestClientException) {
			Reply(false, "API에 연결하지 못했습니다: ${failure.mostSpecificCause.message ?: failure.javaClass.simpleName}")
		}
	}
}
