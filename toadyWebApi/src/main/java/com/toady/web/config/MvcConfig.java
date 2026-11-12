package com.toady.web.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * packageName    : com.toady.web.config
 * fileName       : MvcConfig
 * author         : youn
 * date           : 2022/05/19
 * description    : Mvc 관련 설정 java config.
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2022/05/19        youn       최초 생성
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

	// CORS는 SecurityConfig의 허용 출처 목록(toady.web.allowed-origins) 한 곳에서만 관리한다.
	// 예전 "/webApi/** 모든 출처 허용" 설정은 Security CORS가 먼저 처리해 쓰이지 않았고 오해만 낳아 제거했다.

	@Override
	public void addInterceptors(InterceptorRegistry registry) {

	}

}
