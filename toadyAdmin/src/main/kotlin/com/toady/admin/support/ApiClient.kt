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
 */
@Component
class ApiClient(@Value("\${toady.api.base-url:http://toadyWebApi:8080}") baseUrl: String, @Value("\${toady.api.token:}") private val token: String) {
	data class Reply(val ok: Boolean, val message: String, val body: Map<*, *> = emptyMap<String, Any>())

	private val client = RestClient.builder().baseUrl(baseUrl.trimEnd('/'))
		.requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(3000); setReadTimeout(60000) })
		.defaultHeader("X-Admin-Token", token).build()

	val configured get() = token.trim().length >= 32

	fun push(title: String, body: String, url: String, testUserSeq: Long?): Reply =
		post("/internal/admin/push", mapOf("title" to title, "body" to body, "url" to url, "testUserSeq" to testUserSeq))
	fun resendVerification(userSeq: Long): Reply = post("/internal/admin/members/{seq}/resend-verification", null, userSeq)
	fun withdraw(userSeq: Long): Reply = post("/internal/admin/members/{seq}/withdraw", null, userSeq)

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
