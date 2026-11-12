package com.toady.web.app.planner;

/** 테스트에서 공개 HTML 캐시를 비우기 위한 접근점(같은 패키지의 PublicHtml이 package-private이라). */
public final class PublicPagesCache {
    private PublicPagesCache() { }
    public static void clear() { PublicHtml.clearCache(); }
    public static void clear(ScoreBenchmarks benchmarks) { benchmarks.invalidate(); }
}
