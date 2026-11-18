package com.toady.admin.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 관리자 사이트(admin.toady.co.kr)와 서비스(www.toady.co.kr)는 도메인이 다르므로,
 * "사이트 열기"·"서비스에서 보기" 링크는 서비스 주소를 앞에 붙인 절대 주소로 만든다.
 */
// @ControllerAdvice: 모든 컨트롤러에 공통으로 적용되는 설정을 모아 두는 빈. 여기서는 모든 화면에 같은 값을 넣는 데 쓴다.
@ControllerAdvice
class SiteLinks(@Value("\${toady.site.url:https://www.toady.co.kr}") url: String,
                @Value("\${toady.admin.remember-key:}") rememberKey: String,
                @Value("\${toady.admin.remember-days:14}") rememberDays: Int) {
	private val siteUrl = url.trim().trimEnd('/')
	private val rememberEnabled = com.toady.admin.security.SecurityConfig.rememberEnabled(rememberKey)
	// SecurityConfig와 같은 범위(1~30일)로 맞춰, 화면 안내와 실제 쿠키 유효기간이 어긋나지 않게 한다.
	private val rememberDays = rememberDays.coerceIn(1, 30)

	// @ModelAttribute("이름"): 모든 화면(Thymeleaf)의 모델에 이 반환값을 그 이름으로 넣는다. 템플릿에서 ${siteUrl}로 쓴다.
	@ModelAttribute("siteUrl") fun siteUrl() = siteUrl
	/** 로그인 화면의 '로그인 유지' 체크박스는 서명 키(ADMIN_REMEMBER_KEY)가 있을 때만 보인다. */
	@ModelAttribute("rememberEnabled") fun rememberEnabled() = rememberEnabled
	/** 로그인 화면에 보여 줄 '로그인 유지' 기간(일). ADMIN_REMEMBER_DAYS 설정을 따른다. */
	@ModelAttribute("rememberDays") fun rememberDays() = rememberDays
}
