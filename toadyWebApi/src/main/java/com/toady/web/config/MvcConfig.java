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

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		//@formatter:off
		registry
			.addMapping("/webApi/**") //CORS를 적용할 URL패턴을 정의
				.allowedOrigins("*") // 자원 공유를 허락할 Origin을 지정
				//.allowedOriginPatterns("*")
				.allowedMethods("*") // ("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS") 허용할 HTTP method를 지정
			.allowCredentials(false)
			.maxAge(3600); //원하는 시간만큼 pre-flight 리퀘스트를 캐싱
		//@formatter:on
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {

	}

}
