package com.toady.web.config;

import com.toady.web.core.security.ApiSecurityError;
import com.toady.web.core.security.jwt.JwtProvider;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.security.jwt.filter.JwtAuthenticationFilter;
import com.toady.web.core.security.jwt.impl.JwtProviderImpl;
import com.toady.web.core.security.service.SimpleUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 스프링 시큐리티 설정. 모든 HTTP 요청은 여기서 만든 필터 체인을 먼저 지난다.
 * - 세션을 쓰지 않는다(STATELESS). 로그인 상태는 매 요청의 Authorization: Bearer JWT로 판단한다.
 * - JwtAuthenticationFilter가 토큰을 읽어 SecurityContext에 로그인 사용자를 넣는다.
 * - 비밀번호는 BCrypt로 해시해 TB_USER 테이블에 저장한다.
 * - CORS 허용 Origin은 toady.web.allowed-origins 설정 값에서 읽는다.
 */
// @Configuration: @Bean 메서드로 빈을 만드는 설정 클래스
@Configuration
// @EnableWebSecurity: 스프링 시큐리티 웹 보안을 켠다. 아래 SecurityFilterChain 빈이 실제 규칙이 된다
@EnableWebSecurity
// @EnableMethodSecurity: 메서드에 @Secured·@PreAuthorize를 붙여 권한을 검사할 수 있게 한다
@EnableMethodSecurity(securedEnabled = true)
// @RequiredArgsConstructor(Lombok): final 필드를 받는 생성자를 만들어 준다. 스프링은 이 생성자로 의존성을 주입한다
@RequiredArgsConstructor
public class SecurityConfig {
    private final SimpleUserDetailsService userDetailsService;

    /** JWT 발급·검증 객체. 토큰 안의 사용자를 확인할 때 UserRepository로 DB를 조회한다. */
    @Bean
    public JwtProvider jwtProvider(UserRepository userRepository) {
        return new JwtProviderImpl(userRepository);
    }

    /** 요청마다 Authorization 헤더의 JWT를 읽어 인증 정보를 채우는 필터. */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtProvider jwtProvider) {
        return new JwtAuthenticationFilter(jwtProvider);
    }

    /**
     * 보안 규칙 본체. JWT 인증 필터를 기본 사용자명·암호 인증 필터보다 먼저 실행한다.
     * /api/v1/me/** 만 로그인이 필요하고 나머지는 열어 둔다.
     * 그 밖의 권한 확인(작성자 본인인지 등)은 각 서비스에서 직접 한다.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // 일반 회원은 Bearer JWT로 인증한다. Basic challenge는 브라우저 자체 로그인 창을 띄운다.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                ApiSecurityError.unauthorized(response))
                        .accessDeniedHandler((request, response, exception) ->
                                ApiSecurityError.forbidden(response)))
                // 쿠키 세션이 아니라 헤더 토큰으로 인증하므로 CSRF 토큰 검사는 끈다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/me/**").authenticated()
                        .requestMatchers("/webApi/user/login", "/webApi/user/regUser",
                                "/webApi/certify/**", "/api/v1/**", "/swagger-ui/**",
                                "/v3/api-docs/**").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 비밀번호 해시 방식. BCrypt는 같은 비밀번호라도 매번 다른 해시를 만들고 matches()로 비교한다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 설정된 쉼표 구분 Origin에 대해 인증 정보 포함 CORS를 허용한다.
     * 값은 CORS_ALLOWED_ORIGINS 환경변수로 바꾸며, 기본값은 로컬 개발 화면 주소다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            // @Value: application.yml 등에서 ${...} 키의 값을 읽어 주입한다
            @Value("${toady.web.allowed-origins}") String allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        // 로그인 요청(Authorization 헤더)은 사전 요청이 필요하므로 결과를 1시간 기억하게 한다(기본은 브라우저마다 5초~).
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
