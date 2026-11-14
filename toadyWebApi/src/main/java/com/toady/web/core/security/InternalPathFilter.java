package com.toady.web.core.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;

/**
 * /internal/**(배포 확인용 상태·스키마)은 서버 안에서 컨테이너로 직접 부를 때만 응답한다.
 * 앞단 nginx를 거친 요청은 X-Real-IP·X-Forwarded-For가 붙으므로, 앞단 설정에서 차단을 빠뜨려도 앱이 404로 막는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalPathFilter extends OncePerRequestFilter {
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    /** 컨트롤러 매핑과 같은 기준(디코딩·정규화한 경로)으로 본다. '/%69nternal/…'처럼 인코딩해 우회하지 못하게 한다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !PATHS.getLookupPathForRequest(request).startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getHeader("X-Real-IP") != null || request.getHeader("X-Forwarded-For") != null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }
}
