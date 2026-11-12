package com.toady.web.app.planner;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import java.time.Duration;
import java.time.LocalDate;

/** 공고·생활권 공개 페이지가 같은 문구 규칙과 스타일을 쓰도록 모은다. 검색 봇과 사람이 같은 HTML을 본다. */
final class PublicHtml {
    private PublicHtml() { }
    static String esc(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8"); }
    static String date(LocalDate value) { return value == null ? "미제공" : value.toString(); }
    static String shortDate(LocalDate value) { return value == null ? "미제공" : "%d.%02d".formatted(value.getMonthValue(), value.getDayOfMonth()); }
    /** 만원 단위 금액을 4억 2,000만원 형식으로 표시한다. */
    static String manwon(long amount) {
        long eok = amount / 10000, rest = amount % 10000;
        if (eok == 0) return "%,d만원".formatted(rest);
        return rest == 0 ? "%,d억원".formatted(eok) : "%,d억 %,d만원".formatted(eok, rest);
    }
    static String priceRange(Long min, Long max) {
        if (min == null || max == null) return "";
        return min.equals(max) ? manwon(min) : manwon(min).replace("만원", "만").replace("억원", "억") + " ~ " + manwon(max);
    }
    /** 공개 페이지에서 앱으로 이동할 때 원래 유입처(검색·메신저 도메인)만 넘긴다. 전체 URL·검색어는 넘기지 않는다. */
    static final String SOURCE_SCRIPT = "<script>(function(){try{var h=new URL(document.referrer).hostname;if(!h||/(^|\\.)toady\\.co\\.kr$/.test(h))return;"
        + "document.querySelectorAll('a[data-app]').forEach(function(a){var u=new URL(a.href);u.searchParams.set('src',h);a.href=u.toString();});}catch(e){}})();</script>";
    /** 공개 페이지 공통 문서 틀. 검색 결과 제목·설명·대표 주소를 한곳에서 같은 규칙으로 만든다. */
    static ResponseEntity<String> page(String title, String description, String canonical, String body) {
        String html = """
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='website'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            %s</head><body><main>%s</main>%s</body></html>
            """.formatted(esc(title), esc(description), esc(canonical), esc(title), esc(description), esc(canonical), STYLE, body, SOURCE_SCRIPT);
        return ResponseEntity.ok().header("Content-Type", "text/html;charset=UTF-8").cacheControl(CacheControl.maxAge(Duration.ofMinutes(10))).body(html);
    }
    /** 공개 페이지끼리 서로 이어지도록 하단에 같은 탐색 묶음을 둔다. current는 지금 페이지라 링크에서 뺀다. */
    static String explore(String domain, String current) {
        String[][] links = { {"/areas", "생활권별 청약"}, {"/zupzup", "무순위·줍줍 청약"}, {"/weekly", "이번 주 청약 일정"}, {"/score", "청약 가점 계산기"} };
        StringBuilder out = new StringBuilder("<section class='card'><h2>더 알아보기</h2><div class='chips'>");
        for (String[] link : links) if (!link[0].equals(current))
            out.append("<a class='chip' href='").append(esc(domain + link[0])).append("'>").append(esc(link[1])).append("</a>");
        return out.append("</div></section>").toString();
    }
    static final String STYLE = """
        <style>
        :root{color-scheme:light}*{box-sizing:border-box}body{margin:0;background:#f4f6f9;color:#183142;font-family:system-ui,-apple-system,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;line-height:1.65;word-break:keep-all}
        main{max-width:760px;margin:0 auto;padding:20px 16px 40px}.card{background:#fff;border-radius:18px;padding:22px 20px;margin-bottom:14px;box-shadow:0 1px 2px rgba(16,40,60,.06)}
        nav{display:flex;gap:6px;align-items:center;font-size:13px;color:#617687;margin:4px 2px 14px;flex-wrap:wrap}nav a{color:#0b6e64;text-decoration:none;font-weight:600}
        a{color:#0b6e64}h1{font-size:22px;line-height:1.4;margin:0 0 8px}h2{font-size:16px;margin:0 0 10px}p{margin:0 0 10px}small{color:#617687}
        .chip{display:inline-block;font-size:12px;font-weight:700;color:#0b6e64;background:#e7f4f1;border-radius:999px;padding:3px 10px;margin-bottom:10px;text-decoration:none}
        .price{font-size:20px;font-weight:800;color:#183142}.muted{color:#617687;font-size:13px}
        ul{list-style:none;margin:0;padding:0}li{display:flex;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid #edf1f5}li:last-child{border-bottom:0}
        li strong{font-weight:650}.cta{display:block;text-align:center;background:#0b6e64;color:#fff;padding:14px 18px;border-radius:12px;text-decoration:none;font-weight:700;margin-top:4px}
        .cta.secondary{background:#fff;color:#0b6e64;border:1px solid #b9dcd5;margin-top:8px}img{max-width:100%;height:auto;border-radius:12px;display:block}
        .list a{display:block;text-decoration:none;color:inherit;padding:12px 0;border-bottom:1px solid #edf1f5}.list a:last-child{border-bottom:0}.list strong{display:block;font-size:15px;color:#183142}
        .badge{display:inline-block;font-size:11px;font-weight:700;border-radius:6px;padding:1px 7px;margin-right:6px;background:#eef2f6;color:#4b6175}.badge.open{background:#e7f4f1;color:#0b6e64}.empty{color:#617687;font-size:14px;padding:6px 0}
        .chips{display:flex;flex-wrap:wrap;gap:6px}.chips .chip{margin:0}.stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:14px 0 4px}
        .stats div{background:#f4f8f7;border-radius:12px;padding:10px 12px}.stats strong{display:block;font-size:20px;color:#0b6e64}.stats span{font-size:12px;color:#617687}
        .row{display:flex;gap:8px;flex-wrap:wrap}.row .cta{flex:1;min-width:200px}.when{float:right;font-size:12px;font-weight:700;color:#0b6e64}
        </style>""";
}
