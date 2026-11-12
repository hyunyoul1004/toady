package com.toady.web.admin;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 유입 도메인은 부분 문자열이 아니라 도메인·하위 도메인 단위로 분류한다. */
class AdminMetricsSourceTest {
    @ParameterizedTest
    @CsvSource({
            "www.google.com, 검색 · 구글", "www.google.co.kr, 검색 · 구글", "google.com, 검색 · 구글",
            "google.example.com, google.example.com", "mygoogle.com, mygoogle.com",
            "m.search.naver.com, 검색 · 네이버", "cafe.naver.com, 네이버 카페", "m.blog.naver.com, 네이버 블로그",
            "notnaver.com, notnaver.com", "cafe.daum.net, 다음 카페", "search.daum.net, 검색 · 다음",
            "www.bing.com, 검색 · 빙", "talk.kakao.com, 카카오톡·카카오",
            "x.com, SNS", "t.co, SNS", "l.instagram.com, SNS", "lm.facebook.com, SNS", "www.threads.net, SNS",
            "dropbox.com, dropbox.com", "www.dropbox.com, www.dropbox.com", "netflix.com, netflix.com",
            "box.co, box.co", "WWW.GOOGLE.COM, 검색 · 구글"
    })
    void classifiesByExactDomainOrSubdomain(String host, String label) {
        assertEquals(label, AdminMetrics.sourceLabel(host));
    }
}
