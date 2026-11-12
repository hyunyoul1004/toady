package com.toady.web.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 관리자 전용 세션 인증과 접근 제어. 일반 서비스 회원 계정과 분리된 환경변수 기반 계정을 사용한다. */
@Configuration
public class AdminSecurityConfig {
    // 같은 IP에서 15분 안에 5번 실패하면 15분 동안 로그인 시도를 막는다. 테스트에서 상태를 확인할 수 있게 빈으로 노출한다.
    private final AdminLoginThrottle loginThrottle = new AdminLoginThrottle();

    @Bean
    AdminLoginThrottle adminLoginThrottle() { return loginThrottle; }

    private static OncePerRequestFilter lockedLoginFilter(AdminLoginThrottle throttle) {
        return new OncePerRequestFilter() {
            @Override
            protected boolean shouldNotFilter(jakarta.servlet.http.HttpServletRequest request) {
                return !("POST".equals(request.getMethod())
                        && "/admin/login".equals(request.getRequestURI().substring(request.getContextPath().length())));
            }
            @Override
            protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
                    throws jakarta.servlet.ServletException, java.io.IOException {
                if (throttle.isLocked(AdminLoginThrottle.clientIp(request))) {
                    response.sendRedirect(request.getContextPath() + "/admin/login?locked");
                    return;
                }
                chain.doFilter(request, response);
            }
        };
    }

    /** 기능을 끈 상태에서는 계정을 만들지 않으며, 활성화 시에는 명시적으로 설정한 자격 증명만 허용한다. */
    @Bean
    UserDetailsService adminUsers(@Value("${applyhome.admin.enabled:false}") boolean enabled,
            @Value("${applyhome.admin.username:admin}") String username,
            @Value("${applyhome.admin.password:}") String password) {
        if (!enabled) return new InMemoryUserDetailsManager();
        // BCrypt의 입력 제한은 문자 수가 아니라 UTF-8 바이트 수다.
        if (username.isBlank() || username.length() > 100 || password.length() < 12
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Set SCHEDULER_ADMIN_USERNAME and a SCHEDULER_ADMIN_PASSWORD of at least 12 characters and at most 72 UTF-8 bytes before enabling admin");
        }
        // {bcrypt} 접두사는 Spring Security의 기본 DelegatingPasswordEncoder에 인코딩 방식을 전달한다.
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{bcrypt}" + new BCryptPasswordEncoder().encode(password)).roles("ADMIN").build());
    }
    /** 로그인과 필수 스타일을 제외한 관리자 경로는 ADMIN 권한을 요구하고, 나머지 경로는 차단한다. */
    @Bean
    SecurityFilterChain adminSecurity(HttpSecurity http,
            @Value("${applyhome.admin.enabled:false}") boolean enabled) throws Exception {
        AdminLoginThrottle throttle = loginThrottle;
        if (!enabled) {
            http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/internal/health").permitAll().anyRequest().denyAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/internal/health").permitAll()
                            .requestMatchers("/admin/login", "/admin/style.css", "/error").permitAll()
                            .requestMatchers("/admin/**", "/").hasRole("ADMIN")
                            .anyRequest().denyAll())
                    // 잠긴 IP는 비밀번호 확인 전에 돌려보낸다. 성공하면 해당 IP의 실패 기록을 지운다.
                    .addFilterBefore(lockedLoginFilter(throttle), UsernamePasswordAuthenticationFilter.class)
                    .formLogin(form -> form.loginPage("/admin/login").loginProcessingUrl("/admin/login")
                            .successHandler((request, response, authentication) -> {
                                throttle.succeeded(AdminLoginThrottle.clientIp(request));
                                response.sendRedirect(request.getContextPath() + "/admin/");
                            })
                            .failureHandler((request, response, exception) -> {
                                String ip = AdminLoginThrottle.clientIp(request);
                                throttle.failed(ip);
                                response.sendRedirect(request.getContextPath()
                                        + (throttle.isLocked(ip) ? "/admin/login?locked" : "/admin/login?error"));
                            })
                            .permitAll())
                    .logout(logout -> logout.logoutUrl("/admin/logout").logoutSuccessUrl("/admin/login?logout"));
        }
        // 로그인·로그아웃·수집 실행·설정 변경 모두 CSRF 보호를 유지한다. 템플릿의 POST 폼이 토큰을 전달한다.
        return http.build();
    }
}
