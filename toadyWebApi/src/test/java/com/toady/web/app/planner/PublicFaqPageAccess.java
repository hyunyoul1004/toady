package com.toady.web.app.planner;

/** 다른 패키지 테스트에서 FAQ 질문 수를 확인한다. */
public final class PublicFaqPageAccess {
    private PublicFaqPageAccess() { }
    public static int count() { return PublicFaqPage.GROUPS.stream().mapToInt(group -> group.items().size()).sum(); }
}
