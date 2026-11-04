package com.toady.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI에 노출할 API 제목과 설명, 버전을 설정합니다. */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI toadyOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Toady API")
                .description("분양 공고 및 커뮤니티 API")
                .version("1.0.0"));
    }
}
