package com.toady.web.app.planner;

/** 다른 패키지 테스트에서 ContentStore의 채우기·다시 읽기를 바로 부르기 위한 통로. */
public final class ContentStoreAccess {
    private ContentStoreAccess() { }
    public static void seedAndRefresh(ContentStore store) { store.seedIfEmpty(); store.refresh(); }
    public static void refresh(ContentStore store) { store.refresh(); }
    public static void resetFaq() { PublicFaqPage.replace(null); }
}
