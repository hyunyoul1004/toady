package com.toady.admin.security

import jakarta.servlet.http.HttpServletRequest
import java.net.InetAddress
import java.net.UnknownHostException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 관리자 로그인 실패를 접속 IP별로 세고, 짧은 기간 안에 반복 실패하면 일정 시간 로그인 시도를 막는다.
 * 비밀번호 확인 중인 시도(inFlight)도 함께 세어 동시에 보낸 요청으로 상한을 넘지 못하게 한다.
 * 단일 인스턴스 메모리 상태라 재시작하면 초기화된다(관리자 계정 하나를 지키는 용도로 충분하다).
 */
class LoginThrottle(private val clock: Clock = Clock.systemUTC()) {
	private data class Attempts(val failures: Int, val firstFailure: Instant?, val lockedUntil: Instant?, val inFlight: Int) {
		val empty get() = failures == 0 && inFlight == 0
	}

	private val attempts = ConcurrentHashMap<String, Attempts>()

	fun isLocked(ip: String): Boolean = attempts[ip]?.lockedUntil?.let { clock.instant().isBefore(it) } ?: false

	/** 비밀번호 확인 전에 호출한다. true를 받은 호출은 failed·succeeded·abandoned 중 하나로 끝내야 한다. */
	fun tryBegin(ip: String): Boolean {
		val now = clock.instant()
		makeRoom(now)
		var allowed = false
		attempts.compute(ip) { _, stored ->
			val current = fresh(stored, now)
			if (current.lockedUntil != null || current.failures + current.inFlight >= MAX_FAILURES) {
				if (current.empty) null else current
			} else {
				allowed = true
				current.copy(lockedUntil = null, inFlight = current.inFlight + 1)
			}
		}
		return allowed
	}

	fun failed(ip: String) {
		val now = clock.instant()
		makeRoom(now)
		attempts.compute(ip) { _, stored ->
			val current = fresh(stored, now)
			val failures = current.failures + 1
			Attempts(failures, if (current.failures == 0) now else current.firstFailure,
				if (failures >= MAX_FAILURES) now.plus(LOCK) else null, maxOf(0, current.inFlight - 1))
		}
	}

	fun succeeded(ip: String) {
		attempts.computeIfPresent(ip) { _, current ->
			val inFlight = maxOf(0, current.inFlight - 1)
			if (inFlight == 0) null else Attempts(0, null, null, inFlight)
		}
	}

	/** 인증 결과 없이 끝난 시도(예외 등)는 실패로 세지 않고 진행 중 수만 줄인다. */
	fun abandoned(ip: String) {
		val now = clock.instant()
		attempts.computeIfPresent(ip) { _, stored ->
			val current = fresh(stored, now)
			current.copy(inFlight = maxOf(0, current.inFlight - 1)).takeUnless { it.empty }
		}
	}

	private fun fresh(value: Attempts?, now: Instant): Attempts {
		if (value == null) return NONE
		val reset = if (value.lockedUntil != null) !now.isBefore(value.lockedUntil)
		else value.firstFailure != null && !now.isBefore(value.firstFailure.plus(WINDOW))
		return if (reset) Attempts(0, null, null, value.inFlight) else value
	}

	private fun makeRoom(now: Instant) {
		if (attempts.size < MAX_TRACKED) return
		attempts.replaceAll { _, value -> fresh(value, now) }
		attempts.values.removeIf { it.empty }
		if (attempts.size >= MAX_TRACKED) attempts.values.removeIf { it.inFlight == 0 }
	}

	companion object {
		const val MAX_FAILURES = 5
		val WINDOW: Duration = Duration.ofMinutes(15)
		val LOCK: Duration = Duration.ofMinutes(15)
		private const val MAX_TRACKED = 10_000
		private val NONE = Attempts(0, null, null, 0)
		private val IP_LITERAL = Regex("[0-9A-Fa-f:.]{2,45}")

		/**
		 * 실제 접속 IP. 앞단 nginx(같은 서버·도커 네트워크)를 거친 요청에서만 X-Real-IP를 믿는다.
		 * 외부에서 직접 들어온 요청이 헤더를 꾸며 다른 IP로 위장하지 못하게 하기 위해서다.
		 */
		fun clientIp(request: HttpServletRequest): String {
			val remote = request.remoteAddr
			val forwarded = request.getHeader("X-Real-IP")?.trim()
			return if (forwarded != null && IP_LITERAL.matches(forwarded) && isInternal(remote)) forwarded else remote
		}

		fun isInternal(address: String?): Boolean {
			if (address == null || !IP_LITERAL.matches(address)) return false
			return try {
				val parsed = InetAddress.getByName(address)
				if (parsed.isLoopbackAddress || parsed.isSiteLocalAddress) return true
				// IPv6 고유 로컬 주소(fc00::/7)는 isSiteLocalAddress가 판별하지 않는다.
				val bytes = parsed.address
				bytes.size == 16 && (bytes[0].toInt() and 0xFE) == 0xFC
			} catch (invalid: UnknownHostException) {
				false
			}
		}
	}
}
