package com.toady.web.core.security.jwt.filter;

import com.toady.web.core.security.ApiSecurityError;
import com.toady.web.core.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bearer JWT를 검증해 요청 인증을 설정하고, 만료 임박 토큰은 응답 헤더로 갱신합니다.
 * @Component가 아니라 SecurityConfig의 @Bean으로 만들어 보안 체인 안(UsernamePasswordAuthenticationFilter 앞)에 넣는다.
 * 토큰이 없으면 손님으로 그냥 통과시키고, 토큰이 있는데 잘못됐으면 401로 끝낸다.
 */
// @RequiredArgsConstructor: final 필드 jwtProvider를 받는 생성자를 만든다
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;

    /**
     * Bearer 토큰을 검증하고 인증 정보를 요청 동안 설정합니다.
     * 만료가 임박하면 갱신 토큰을 응답 헤더에 넣고, 완료 후 보안 컨텍스트를 비웁니다.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Authentication authentication = jwtProvider.getAuthentication(request);
                if (authentication == null) {
                    ApiSecurityError.unauthorized(response);
                    return;
                }
                // SecurityContextHolder: 현재 요청 스레드에 로그인 사용자를 담아 두는 곳. 컨트롤러의 @AuthenticationPrincipal 등이 여기서 꺼낸다
                SecurityContextHolder.getContext().setAuthentication(authentication);
                // 토큰은 갱신할 때만 응답에 싣는다. 받은 토큰을 매번 되돌려 주면 공개 캐시(Cache-Control: public) 응답에도
                // 토큰이 실려, 앞단에 공유 캐시가 붙는 순간 다른 방문자에게 전달될 수 있다.
                if (jwtProvider.getJwtExpiryRenewalCheck(request, authentication)) {
                    response.setHeader("Authorization", "Bearer " + jwtProvider.renewToken(request, authentication));
                    // 새 토큰이 담긴 응답은 어떤 캐시에도 저장하지 않는다(컨트롤러가 정한 캐시 정책보다 우선).
                    response.setHeader("Cache-Control", "no-store");
                }
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                ApiSecurityError.unauthorized(response);
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 톰캣은 스레드를 재사용하므로 요청이 끝나면 비워서 다음 요청에 이전 사용자가 남지 않게 한다
            SecurityContextHolder.clearContext();
        }
    }
}
