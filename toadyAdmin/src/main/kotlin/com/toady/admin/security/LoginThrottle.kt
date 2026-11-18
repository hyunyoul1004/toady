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
 * 규칙: 15분 안에 5번 실패하면 15분 잠근다. 빈으로는 SecurityConfig.loginThrottle()이 등록한다.
 * 맵 갱신은 ConcurrentHashMap.compute로 해서 같은 IP의 동시 요청도 한 번에 하나씩 반영된다.
 */
class LoginThrottle(private val clock: Clock = Clock.systemUTC()) {
	/** IP 하나의 상태: 실패 수, 첫 실패 시각, 잠금 해제 시각, 지금 확인 중인 시도 수. data class라 copy로 일부만 바꾼 새 값을 만든다. */
	private data class Attempts(val failures: Int, val firstFailure: Instant?, val lockedUntil: Instant?, val inFlight: Int) {
		val empty get() = failures == 0 && inFlight == 0
	}

	private val attempts = ConcurrentHashMap<String, Attempts>()

	/** 지금 잠겨 있는지. 실패 처리 직후 어느 안내 화면으로 보낼지 정할 때 쓴다. */
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

	/** 비밀번호가 틀렸을 때. 실패 수를 늘리고 5번째면 잠근다. */
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

	/** 로그인 성공. 실패 기록을 지운다(다른 시도가 진행 중이면 그 수만 남긴다). */
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

	/** 잠금 시간이나 15분 창이 지났으면 실패 기록을 비운 상태로 돌려준다. */
	private fun fresh(value: Attempts?, now: Instant): Attempts {
		if (value == null) return NONE
		val reset = if (value.lockedUntil != null) !now.isBefore(value.lockedUntil)
		else value.firstFailure != null && !now.isBefore(value.firstFailure.plus(WINDOW))
		return if (reset) Attempts(0, null, null, value.inFlight) else value
	}

	/** 기록이 1만 개를 넘으면 오래된·빈 항목부터 지워 메모리가 끝없이 늘지 않게 한다. */
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

		/** 루프백·사설망 주소인지(nginx·도커 네트워크에서 온 요청인지) 확인한다. */
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
