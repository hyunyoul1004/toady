package com.toady.web.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * API 요청의 메서드, 경로, 응답 상태, 처리 시간을 기록합니다.
 * 쿼리 문자열과 헤더, 요청 본문은 개인정보 및 인증정보 보호를 위해 기록하지 않습니다.
 */
@Slf4j
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            log.error(
                    "HTTP {} {} 처리 중 예외: {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    exception.getClass().getSimpleName()
            );
            throw exception;
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            int status = response.getStatus();
            if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                log.error("HTTP {} {} -> {} ({} ms)",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
                log.warn("HTTP {} {} -> {} ({} ms)",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            } else {
                log.info("HTTP {} {} -> {} ({} ms)",
                        request.getMethod(), request.getRequestURI(), status, elapsedMs);
            }
        }
    }

    /** 문서 UI 및 문서 리소스 요청은 요청 로그 대상에서 제외합니다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/webjars/");
    }
}
