package com.toady.web.app.planner;

/** 다른 패키지 테스트에서 MailBudget의 패키지 전용 계산을 확인하기 위한 통로. */
public final class MailBudgetAccess {
    private MailBudgetAccess() { }
    public static int signupLimit(MailBudget budget, int effectiveLimit) { return budget.signupLimit(effectiveLimit); }
}
