package com.toady.admin.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices
import java.util.HexFormat
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

/**
 * 관리자 전용 세션 인증. 서비스 회원 계정과 분리된 환경변수 계정 하나만 허용한다.
 * 로그인·회원 잠금·댓글 숨김·수집 실행 등 모든 POST는 CSRF 토큰을 요구한다.
 * 관리자 IP 제한은 이 앱이 아니라 앞단 nginx(conf.d/admin-allow.inc, 저장소에 두지 않음)가 맡는다.
 * 여기서는 그 뒤의 두 번째 벽으로 로그인·로그인 실패 잠금(LoginThrottle)·로그인 유지 쿠키를 설정한다.
 */
// @Configuration: 안의 @Bean 메서드가 돌려주는 객체를 스프링 빈으로 등록하는 설정 클래스.
@Configuration
class SecurityConfig {
	private val throttle = LoginThrottle()

	// @Bean: 반환 객체를 빈으로 등록한다. 필터와 처리기가 같은 객체를 쓰도록 필드 하나를 빈으로도 내보낸다.
	@Bean
	fun loginThrottle(): LoginThrottle = throttle

	/**
	 * 관리자 계정 하나를 메모리에 만든다. 환경변수의 비밀번호를 시작할 때 BCrypt로 해시해 두고,
	 * 조건(12자 이상 등)이 맞지 않으면 check가 예외를 던져 앱이 뜨지 않는다.
	 */
	@Bean
	fun adminUsers(@Value("\${toady.admin.username:admin}") username: String,
	               @Value("\${toady.admin.password:}") password: String): UserDetailsService {
		// BCrypt 입력 제한은 문자 수가 아니라 UTF-8 바이트 수다.
		check(username.isNotBlank() && username.length <= 100 && password.length >= 12 && password.toByteArray().size <= 72) {
			"Set ADMIN_USERNAME and an ADMIN_PASSWORD of at least 12 characters and at most 72 UTF-8 bytes"
		}
		return InMemoryUserDetailsManager(User.withUsername(username)
			.password("{bcrypt}" + BCryptPasswordEncoder().encode(password)).roles("ADMIN").build())
	}

	/**
	 * 로그인 유지(자동 로그인). 세션은 30분 동안 아무 요청이 없으면 끝나고, 로그인 유지를 고른 브라우저는
	 * 서명된 쿠키로 다시 로그인된다. 쿠키에는 비밀번호가 아닌 서명만 담기며, 비밀번호나 서명 키를 바꾸면 모든 쿠키가 무효가 된다.
	 * 서명 키(ADMIN_REMEMBER_KEY, 32자 이상 무작위 값)는 비밀번호와 무관해야 한다. 비밀번호로 키를 만들면 쿠키 하나만 유출돼도
	 * 비밀번호를 오프라인으로 대입해 볼 수 있다. 키가 없으면 로그인 유지를 끈다(화면의 체크박스도 숨기고, 이번 실행에만 쓰는 무작위 키를 둔다).
	 * 기본 구현은 서명에 저장된 비밀번호 해시를 넣는데, BCrypt 해시는 서버가 켜질 때마다 새 솔트로 바뀌어 배포할 때마다 쿠키가 풀린다.
	 * 그래서 해시 대신 HMAC(키, 비밀번호) 지문을 넣는다(재시작해도 유지, 비밀번호나 키를 바꾸면 무효).
	 */
	@Bean
	fun adminRememberMe(users: UserDetailsService,
	                    @Value("\${toady.admin.username:admin}") username: String,
	                    @Value("\${toady.admin.password:}") password: String,
	                    @Value("\${toady.admin.remember-key:}") rememberKey: String,
	                    @Value("\${toady.admin.remember-days:14}") rememberDays: Int,
	                    @Value("\${server.servlet.session.cookie.secure:false}") secure: Boolean): TokenBasedRememberMeServices {
		val key = if (rememberEnabled(rememberKey)) rememberKey.trim()
			else HexFormat.of().formatHex(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
		val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply { init(javax.crypto.spec.SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
		val fingerprint = HexFormat.of().formatHex(mac.doFinal("toady-admin-password:$username:$password".toByteArray()))
		return StableRememberMeServices(key, users, fingerprint).apply {
			// 세션 쿠키처럼 다른 사이트에서 온 요청에는 싣지 않는다.
			setCookieCustomizer { it.setAttribute("SameSite", "Strict") }
			setParameter(REMEMBER_PARAMETER)
			setCookieName(REMEMBER_COOKIE)
			setTokenValiditySeconds(rememberDays.coerceIn(1, 30) * 24 * 60 * 60)
			setUseSecureCookie(secure)
		}
	}

	/**
	 * 경로별 접근 규칙과 로그인·로그아웃·보안 헤더 설정. 규칙은 위에서부터 먼저 맞는 것이 적용된다.
	 * CSRF는 따로 끄지 않았으므로 기본값대로 모든 POST에 토큰이 필요하다(템플릿 form이 자동으로 넣는다).
	 */
	@Bean
	fun adminSecurity(http: HttpSecurity, rememberMe: TokenBasedRememberMeServices): SecurityFilterChain {
		http.authorizeHttpRequests {
			it.requestMatchers("/internal/health").permitAll()
				.requestMatchers("/admin/login", "/admin/style.css", "/error").permitAll()
				.requestMatchers("/admin/**", "/").hasRole("ADMIN")
				.anyRequest().denyAll()
		}
			// 잠긴 IP는 비밀번호 확인 전에 돌려보낸다. 성공하면 해당 IP의 실패 기록을 지운다.
			.addFilterBefore(lockedLoginFilter(), UsernamePasswordAuthenticationFilter::class.java)
			.formLogin { form ->
				form.loginPage("/admin/login").loginProcessingUrl("/admin/login")
					.successHandler { request, response, _ ->
						throttle.succeeded(finishAttempt(request))
						response.sendRedirect(request.contextPath + "/admin/")
					}
					.failureHandler { request, response, _ ->
						val ip = finishAttempt(request)
						throttle.failed(ip)
						response.sendRedirect(request.contextPath + if (throttle.isLocked(ip)) "/admin/login?locked" else "/admin/login?error")
					}
					.permitAll()
			}
			.rememberMe { it.rememberMeServices(rememberMe) }
			// 로그아웃하면 로그인 유지 쿠키도 지운다(rememberMe 설정이 로그아웃 처리기를 함께 등록한다).
			.logout { it.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout") }
			// 앞단 nginx(HTTPS) 뒤에서 http://내부주소 로 보내지 않도록 로그인 이동 주소를 상대 경로로 둔다.
			.exceptionHandling { it.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/admin/login").apply { setFavorRelativeUris(true) }) }
			.headers { headers ->
				// 관리자 화면은 다른 사이트에 끼워 넣지 못하게 하고, 외부 스크립트를 쓰지 않는다.
				headers.frameOptions { it.deny() }
				headers.contentSecurityPolicy { it.policyDirectives("default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'") }
			}
		return http.build()
	}

	/**
	 * 로그인 POST 앞에 끼우는 필터. 잠긴 IP면 비밀번호 확인 없이 돌려보내고, 아니면 '진행 중 시도'로 표시해 둔다.
	 * object : OncePerRequestFilter()는 이름 없는 클래스를 바로 만드는 Kotlin 문법이다(한 요청에 한 번만 실행되는 필터).
	 */
	private fun lockedLoginFilter() = object : OncePerRequestFilter() {
		// 디코딩·정규화된 경로로 비교해 /admin/%6Cogin 같은 인코딩 우회를 막는다.
		override fun shouldNotFilter(request: HttpServletRequest) =
			!(request.method == "POST" && UrlPathHelper().getPathWithinApplication(request) == "/admin/login")

		override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
			val ip = LoginThrottle.clientIp(request)
			if (!throttle.tryBegin(ip)) {
				response.sendRedirect(request.contextPath + "/admin/login?locked")
				return
			}
			request.setAttribute(ATTEMPT, ip)
			try {
				chain.doFilter(request, response)
			} finally {
				// 처리기가 결과를 기록하지 못한 경우(예외 등) 진행 중 시도만 반환한다.
				if (request.getAttribute(ATTEMPT) != null) {
					request.removeAttribute(ATTEMPT)
					throttle.abandoned(ip)
				}
			}
		}
	}

	/** 필터가 요청에 붙여 둔 IP를 꺼내고 표시를 지운다(finally에서 abandoned로 한 번 더 세지 않게). */
	private fun finishAttempt(request: HttpServletRequest): String {
		val ip = request.getAttribute(ATTEMPT) as? String
		request.removeAttribute(ATTEMPT)
		return ip ?: LoginThrottle.clientIp(request)
	}

	// companion object: 클래스 이름으로 바로 부르는 정적 멤버 모음(Java의 static과 비슷하다).
	companion object {
		private val ATTEMPT = SecurityConfig::class.java.name + ".loginAttempt"
		const val REMEMBER_PARAMETER = "remember"
		const val REMEMBER_COOKIE = "TOADY_ADMIN_REMEMBER"
		/** 로그인 유지는 충분히 긴 서명 키가 설정됐을 때만 켠다. */
		fun rememberEnabled(rememberKey: String?) = (rememberKey?.trim()?.length ?: 0) >= 32
	}
}

/** 로그인 유지 서명에 저장된 비밀번호 해시 대신 고정된 비밀번호 지문을 쓴다(위 adminRememberMe 설명 참고). */
class StableRememberMeServices(key: String, users: UserDetailsService, private val passwordFingerprint: String) :
	TokenBasedRememberMeServices(key, users, RememberMeTokenAlgorithm.SHA256) {
	override fun makeTokenSignature(tokenExpiryTime: Long, username: String, password: String?, algorithm: RememberMeTokenAlgorithm): String =
		super.makeTokenSignature(tokenExpiryTime, username, passwordFingerprint, algorithm)
}
