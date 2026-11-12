package com.toady.web.core.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** REST 인증 오류는 JSON으로 응답한다. 브라우저의 HTTP Basic 로그인 창을 요청하지 않는다. */
public final class ApiSecurityError {
    private ApiSecurityError() { }

    public static void unauthorized(HttpServletResponse response) throws IOException {
        write(response, 401, "AUTH_REQUIRED", "로그인이 필요하거나 로그인 정보가 만료되었습니다.");
    }

    public static void forbidden(HttpServletResponse response) throws IOException {
        write(response, 403, "FORBIDDEN", "접근 권한이 없습니다.");
    }

    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // 값은 이 클래스 내부의 상수이며, 사용자 입력을 JSON 문자열에 연결하지 않는다.
        response.getWriter().write("{\"data\":null,\"resultCode\":\"" + code + "\",\"resultMessage\":\"" + message + "\"}");
    }
}
