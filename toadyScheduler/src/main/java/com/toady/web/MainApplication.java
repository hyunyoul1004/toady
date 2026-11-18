package com.toady.web;

import com.toady.web.config.LocalEnvironment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 청약홈 수집기(toadyScheduler)의 시작점. 화면 없이 8082 포트에서 돈다.
 * 청약홈 공고·경쟁률을 정해진 시각에 받아 DB에 저장하고,
 * 관리 기능을 켜면 관리자 사이트(toadyAdmin)가 내부 API(/internal/admin)로 수집 실행·일정 변경을 요청한다.
 */
// @SpringBootApplication: 이 패키지(com.toady.web) 아래의 @Component·@Service·@Configuration 등을 찾아 빈으로 등록하고,
// 의존성(JPA, Web, Security 등)에 맞는 자동 설정을 켠다.
@SpringBootApplication
// @EnableScheduling: @Scheduled가 붙은 메서드를 cron 시각에 실행하는 타이머를 켠다.
@EnableScheduling
public class MainApplication {

	/** 실행 전에 .env.local의 값을 시스템 속성으로 채운 뒤 Spring을 띄운다(IntelliJ 직접 실행용). */
	public static void main(String[] args) {
		LocalEnvironment.loadMissingProperties();
		SpringApplication.run(MainApplication.class, args);
	}

}
