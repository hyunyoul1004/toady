package com.toady.web.app.planner;

/** 사용자에게 그대로 보여 줄 저장 한도 안내(예: 관심 공고 50개). */
public class PlannerLimitException extends RuntimeException {
    public PlannerLimitException(String message) { super(message); }
}
