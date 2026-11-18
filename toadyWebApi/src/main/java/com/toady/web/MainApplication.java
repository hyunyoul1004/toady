package com.toady.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import com.toady.web.config.SecurityConfig;

/**
 * TOADY 웹 API 서버의 시작점이다.
 * 이 패키지(com.toady.web) 아래의 컨트롤러·서비스·리포지토리·설정 클래스를 모두 찾아 스프링 빈으로 등록한다.
 * 설정 값은 resources/config/application.yml에서 읽는다.
 */
// @Import: SecurityConfig를 명시적으로 함께 불러온다. (같은 패키지 아래라 스캔으로도 잡히지만 보안 설정임을 드러낸다)
@Import(SecurityConfig.class)
// @SpringBootApplication: 자동 설정(@EnableAutoConfiguration) + 컴포넌트 스캔(@ComponentScan) + 설정 클래스(@Configuration)를 한 번에 켠다
@SpringBootApplication
// @EnableJpaAuditing: 엔티티의 @CreatedDate·@LastModifiedDate 같은 감사 필드를 저장할 때 자동으로 채운다.
// 지금은 그런 필드를 쓰는 엔티티가 없다(시간은 @CreationTimestamp나 DB 기본값으로 채운다)
@EnableJpaAuditing
public class MainApplication {

	/** 내장 톰캣을 띄우고 스프링 컨테이너를 시작한다. */
	public static void main(String[] args) {
		SpringApplication.run(MainApplication.class, args);
	}

}
