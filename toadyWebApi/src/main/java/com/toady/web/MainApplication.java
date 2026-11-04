package com.toady.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** 애플리케이션을 시작하고 JPA 감사 기능을 활성화하는 진입점입니다. */
@SpringBootApplication
@EnableJpaAuditing
public class MainApplication {

	public static void main(String[] args) {
		SpringApplication.run(MainApplication.class, args);
	}

}
