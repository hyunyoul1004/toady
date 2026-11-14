package com.toady.web.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 스케줄러의 HTTP 접근 제어. 화면은 관리자 사이트(toadyAdmin)가 맡으므로 스케줄러는 브라우저 세션·로그인을 두지 않는다.
 * 준비 상태 확인(/internal/health)과, 관리 기능을 켰을 때의 내부 API(/internal/admin/**)만 열고 나머지는 모두 막는다.
 */
@Configuration
public class SchedulerSecurityConfig {

    /** 로그인 계정이 없음을 명시해 Spring Boot가 임시 비밀번호 계정을 만들지 않게 한다. */
    @Bean
    UserDetailsService noUsers() { return new InMemoryUserDetailsManager(); }

    @Bean
    SecurityFilterChain schedulerSecurity(HttpSecurity http,
            @Value("${applyhome.admin.enabled:false}") boolean adminEnabled) throws Exception {
        http.authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers("/internal/health").permitAll();
                    if (adminEnabled) {
                        // 관리자 사이트용 내부 API. 세션이 아니라 공유 토큰(X-Admin-Token)을 AdminInternalApi가 확인한다.
                        authorize.requestMatchers("/internal/admin/**").permitAll()
                                // 내부 API의 잘못된 요청(400 등)이 오류 경로에서 403으로 바뀌지 않게 한다.
                                .requestMatchers("/error").permitAll();
                    }
                    authorize.anyRequest().denyAll();
                })
                // 브라우저 세션 쿠키를 쓰지 않고 토큰 헤더로만 인증하므로 CSRF 대상이 아니다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable);
        return http.build();
    }
}
