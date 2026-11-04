package com.toady.web.core.security.jwt.filter;

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

/** Bearer JWT를 검증해 요청 인증을 설정하고, 만료 임박 토큰은 응답 헤더로 갱신합니다. */
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
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                SecurityContextHolder.getContext().setAuthentication(authentication);
                if (jwtProvider.getJwtExpiryRenewalCheck(request, authentication)) {
                    response.setHeader("Authorization", "Bearer " + jwtProvider.generatorToken(authentication));
                } else {
                    response.setHeader("Authorization", header);
                }
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
