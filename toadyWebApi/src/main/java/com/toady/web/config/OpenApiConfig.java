package com.toady.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI(/swagger-ui)와 /v3/api-docs에 노출할 API 제목·설명·버전을 정한다.
 * springdoc 라이브러리가 이 OpenAPI 빈을 읽어 문서 화면을 만든다.
 */
// @Configuration: 이 클래스 안의 @Bean 메서드들이 스프링 빈을 만드는 설정 클래스라는 표시
@Configuration
public class OpenApiConfig {
    // @Bean: 메서드가 돌려준 객체를 스프링이 빈으로 등록해 관리한다
    @Bean
    public OpenAPI toadyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Toady API")
                .description("분양 공고 및 커뮤니티 API")
                .version("1.0.0"));
    }
}
