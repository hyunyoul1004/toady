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
 * 서블릿 필터라서 컨트롤러보다 먼저 실행되고, 응답이 끝난 뒤 finally에서 결과를 남긴다.
 * 5xx는 error, 4xx는 warn, 나머지는 info 레벨로 남긴다.
 */
// @Slf4j: log 필드를 만들어 준다
@Slf4j
// @Component: 필터 클래스를 빈으로 등록하면 스프링 부트가 서블릿 필터 체인에 자동으로 넣는다
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    /** OncePerRequestFilter: 한 요청에 한 번만 실행되도록 보장해 주는 필터 부모 클래스. 실제 처리는 이 메서드에 쓴다. */
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
