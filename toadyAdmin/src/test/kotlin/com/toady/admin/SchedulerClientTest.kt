package com.toady.admin

import com.sun.net.httpserver.HttpServer
import com.toady.admin.jobs.SchedulerClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress

class SchedulerClientTest {
	private fun server(status: Int, body: String): HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
		createContext("/") { exchange ->
			val bytes = body.toByteArray()
			exchange.responseHeaders.add("Content-Type", "application/json")
			exchange.sendResponseHeaders(status, bytes.size.toLong())
			exchange.responseBody.use { it.write(bytes) }
		}
		start()
	}

	@Test
	fun `scheduler rejection is shown as an error, not as a success notice`() {
		val server = server(400, """{"message":"자동 수집 간격은 10분 이상이어야 합니다."}""")
		try {
			val client = SchedulerClient("http://127.0.0.1:${server.address.port}", "x".repeat(32))
			val failure = assertThrows<SchedulerClient.SchedulerException> { client.saveSchedule("ANNOUNCEMENTS", true, "0 * * * * *", 90) }
			assertEquals("자동 수집 간격은 10분 이상이어야 합니다.", failure.message)
		} finally { server.stop(0) }
	}

	@Test
	fun `successful save returns the scheduler message`() {
		val server = server(200, """{"message":"저장했습니다."}""")
		try {
			val client = SchedulerClient("http://127.0.0.1:${server.address.port}", "x".repeat(32))
			assertEquals("저장했습니다.", client.saveSchedule("ANNOUNCEMENTS", true, "0 0 3 * * *", 90))
		} finally { server.stop(0) }
	}
}
