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
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.UrlPathHelper

/**
 * 관리자 전용 세션 인증. 서비스 회원 계정과 분리된 환경변수 계정 하나만 허용한다.
 * 로그인·회원 잠금·댓글 숨김·수집 실행 등 모든 POST는 CSRF 토큰을 요구한다.
 */
@Configuration
class SecurityConfig {
	private val throttle = LoginThrottle()

	@Bean
	fun loginThrottle(): LoginThrottle = throttle

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

	@Bean
	fun adminSecurity(http: HttpSecurity): SecurityFilterChain {
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

	private fun finishAttempt(request: HttpServletRequest): String {
		val ip = request.getAttribute(ATTEMPT) as? String
		request.removeAttribute(ATTEMPT)
		return ip ?: LoginThrottle.clientIp(request)
	}

	companion object {
		private val ATTEMPT = SecurityConfig::class.java.name + ".loginAttempt"
	}
}
