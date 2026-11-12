package com.toady.web.app.planner;

import org.springframework.web.util.HtmlUtils;
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
        </style>""";
}
