package com.toady.admin.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/**
 * 관리자 사이트(admin.toady.co.kr)와 서비스(www.toady.co.kr)는 도메인이 다르므로,
 * "사이트 열기"·"서비스에서 보기" 링크는 서비스 주소를 앞에 붙인 절대 주소로 만든다.
 */
@ControllerAdvice
class SiteLinks(@Value("\${toady.site.url:https://www.toady.co.kr}") url: String) {
	private val siteUrl = url.trim().trimEnd('/')

	@ModelAttribute("siteUrl") fun siteUrl() = siteUrl
}
