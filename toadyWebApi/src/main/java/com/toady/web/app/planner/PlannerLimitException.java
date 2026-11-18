package com.toady.web.app.planner;

/**
 * 사용자에게 그대로 보여 줄 저장 한도 안내(예: 관심 공고 50개).
 * PlannerService가 던지고, PlannerExceptionHandler가 400 + 'LIMIT' 코드로 바꿔 메시지를 화면에 그대로 보낸다.
 * RuntimeException이라 throws 선언 없이 던질 수 있다.
 */
public class PlannerLimitException extends RuntimeException {
    public PlannerLimitException(String message) { super(message); }
}
