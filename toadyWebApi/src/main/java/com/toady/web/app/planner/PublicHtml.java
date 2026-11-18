package com.toady.web.app.planner;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 서버가 직접 만드는 공개 HTML 페이지(공고·생활권·지역·모음·FAQ)의 공통 도구: 이스케이프, 날짜·금액 문구, 머리말·꼬리말·스타일, 구조화 데이터, 캐시.
 * 이 페이지들은 주로 검색·공유 봇이 본다. 웹 컨테이너 nginx가 봇에게만 /api/v1/public/... HTML을 주고, 사람에게는 같은 주소에서 React 앱을 연다.
 * 그래서 링크·문구는 앱과 같은 서비스로 보이게 맞추고, 앱으로 가는 링크(data-app)에는 유입처를 넘긴다.
 */
final class PublicHtml {
    private PublicHtml() { }
    /** HTML 이스케이프(<, >, &, 따옴표). 사용자·공고 데이터를 HTML에 넣을 때는 반드시 거친다. */
    static String esc(String value) { return HtmlUtils.htmlEscape(value == null ? "" : value, "UTF-8"); }
    static String date(LocalDate value) { return value == null ? "미제공" : value.toString(); }
    /** 올해 날짜는 '12.08', 다른 해는 '2025.12.08'(지난 공고가 올해 일정처럼 읽히지 않게). */
    static String shortDate(LocalDate value) { return shortDate(value, LocalDate.now(KST)); }
    static String shortDate(LocalDate value, LocalDate today) {
        if (value == null) return "미제공";
        String day = "%d.%02d".formatted(value.getMonthValue(), value.getDayOfMonth());
        return value.getYear() == today.getYear() ? day : value.getYear() + "." + "%02d.%02d".formatted(value.getMonthValue(), value.getDayOfMonth());
    }
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ObjectMapper JSON = new ObjectMapper();
    /** 구조화 데이터(JSON-LD) 한 덩어리. <·>·&는 유니코드 이스케이프로 바꿔 </script> 조기 종료와 HTML 해석을 막는다(JSON 구조 문자에는 쓰이지 않는다). */
    static String jsonLd(Map<String, Object> root) {
        try { return "<script type='application/ld+json'>" + JSON.writeValueAsString(root).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026") + "</script>"; }
        catch (JsonProcessingException failed) { throw new IllegalStateException(failed); }
    }
    /** 이름·절대 주소 한 쌍. url이 null이면 링크 없는 현재 위치. */
    record Crumb(String name, String url) { }
    /** 빵부스러기 HTML(마지막은 링크 없이)과 같은 내용의 BreadcrumbList 구조화 데이터. */
    static String breadcrumb(List<Crumb> crumbs) {
        StringBuilder out = new StringBuilder("<nav aria-label='현재 위치'>");
        for (int i = 0; i < crumbs.size(); i++) {
            Crumb crumb = crumbs.get(i);
            if (i > 0) out.append("<span>›</span>");
            if (crumb.url() == null || i == crumbs.size() - 1) out.append("<span>").append(esc(crumb.name())).append("</span>");
            else out.append("<a href='").append(esc(crumb.url())).append("'>").append(esc(crumb.name())).append("</a>");
        }
        return out.append("</nav>").toString();
    }
    static String breadcrumbLd(List<Crumb> crumbs, String currentUrl) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < crumbs.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem"); item.put("position", i + 1); item.put("name", crumbs.get(i).name());
            item.put("item", crumbs.get(i).url() == null ? currentUrl : crumbs.get(i).url());
            items.add(item);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org"); root.put("@type", "BreadcrumbList"); root.put("itemListElement", items);
        return jsonLd(root);
    }
    /** 목록 페이지의 ItemList 구조화 데이터(이름·주소만). */
    static String itemListLd(String name, List<Crumb> items) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("@type", "ListItem"); item.put("position", i + 1); item.put("name", items.get(i).name()); item.put("url", items.get(i).url());
            list.add(item);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org"); root.put("@type", "ItemList"); root.put("name", name);
        root.put("numberOfItems", items.size()); root.put("itemListElement", list);
        return jsonLd(root);
    }
    /** 만원 단위 금액을 4억 2,000만원 형식으로 표시한다. */
    static String manwon(long amount) {
        long eok = amount / 10000, rest = amount % 10000;
        // 서버 기본 로캘과 상관없이 1,000 단위 쉼표를 쓴다.
        if (eok == 0) return String.format(Locale.ROOT, "%,d만원", rest);
        return rest == 0 ? String.format(Locale.ROOT, "%,d억원", eok) : String.format(Locale.ROOT, "%,d억 %,d만원", eok, rest);
    }
    /** '4억 2,000만 ~ 5억 1,000만원'. 둘 중 하나라도 없으면 빈 문자열. */
    static String priceRange(Long min, Long max) {
        if (min == null || max == null) return "";
        return min.equals(max) ? manwon(min) : manwon(min).replace("만원", "만").replace("억원", "억") + " ~ " + manwon(max);
    }
    /**
     * 공개 페이지에서 앱으로 이동할 때 원래 유입처(검색·메신저 도메인)만 넘긴다. 전체 URL·검색어는 넘기지 않는다.
     * 외부 유입 정보가 없으면(카카오톡 등 앱 안 브라우저, TOADY 공개 페이지끼리 이동) 이 페이지 주소에 붙은 꼬리표 src를 대신 넘긴다(예: /weekly?src=kakao.com).
     */
    static final String SOURCE_SCRIPT = "<script>(function(){try{var own=/(^|\\.)toady\\.co\\.kr$/,h='';try{h=new URL(document.referrer).hostname;}catch(e){}"
        + "if(!h||own.test(h)){h=(new URLSearchParams(location.search).get('src')||'').toLowerCase();if(!/^[a-z0-9.-]{3,100}$/.test(h)||h.indexOf('.')<0)h='';}"
        + "if(!h||own.test(h))return;"
        + "document.querySelectorAll('a[data-app]').forEach(function(a){var u=new URL(a.href);u.searchParams.set('src',h);a.href=u.toString();});}catch(e){}})();</script>";
    /**
     * 공개 HTML은 요청마다 공고 쿼리를 여러 번 실행하므로 같은 주소의 응답을 5분간 재사용한다.
     * get(key, loader)는 같은 키를 동시에 한 번만 만들어, 만료 순간 요청이 몰려도 쿼리가 겹치지 않는다.
     */
    private static final Cache<String, ResponseEntity<String>> PAGES = CacheBuilder.newBuilder()
            .maximumSize(600).expireAfterWrite(Duration.ofMinutes(5)).build();
    /** key별로 만든 응답을 5분 재사용한다. 생활권이 바뀌면 clearCache()로 전부, FAQ가 바뀌면 invalidate("faq")로 그 페이지만 비운다. */
    static ResponseEntity<String> cached(String key, Supplier<ResponseEntity<String>> render) {
        return load(PAGES, key, render::get);
    }
    /** Guava 캐시가 감싼 예외를 풀어 다시 던진다(DB 오류가 공통 처리기에서 503으로 바뀌도록). */
    static <K, V> V load(Cache<K, V> cache, K key, Callable<V> loader) {
        try { return cache.get(key, loader); }
        catch (ExecutionException | UncheckedExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(cause);
        }
    }
    /** 페이지 캐시를 모두 비운다. ContentStore가 생활권을 바꿨을 때, 테스트에서 데이터를 바꾼 뒤 부른다. */
    static void clearCache() { PAGES.invalidateAll(); }
    static void invalidate(String key) { PAGES.invalidate(key); }

    /** 공개 JSON 응답: 브라우저·프록시가 maxAge 동안 공용 캐시로 재사용한다. */
    static <T> ResponseEntity<T> publicJson(T body, Duration maxAge) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(maxAge).cachePublic()).body(body);
    }

    /** 공개 페이지 공통 문서 틀. 검색 결과 제목·설명·대표 주소를 한곳에서 같은 규칙으로 만든다. current는 상단 메뉴에서 지금 페이지를 표시할 경로다. */
    static ResponseEntity<String> page(String domain, String current, String title, String description, String canonical, String body) {
        return page(domain, current, title, description, canonical, body, "");
    }
    /** head: 구조화 데이터(JSON-LD)·페이지 전용 스타일처럼 head에 더할 내용. 호출하는 쪽에서 안전하게 만든 문자열만 넘긴다. */
    static ResponseEntity<String> page(String domain, String current, String title, String description, String canonical, String body, String head) {
        String html = """
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='website'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            <meta name='theme-color' content='#ffffff'>%s</head><body>%s<main>%s</main>%s%s</body></html>
            """.formatted(esc(title), esc(description), esc(canonical), esc(title), esc(description), esc(canonical), STYLE + head, header(domain, current), body, footer(domain), SOURCE_SCRIPT);
        return ResponseEntity.ok().header("Content-Type", "text/html;charset=UTF-8").cacheControl(CacheControl.maxAge(Duration.ofMinutes(10))).body(html);
    }
    /** 앱과 같은 로고·메뉴를 위에 두어 검색으로 들어온 사람도 같은 서비스임을 알아보게 한다. 앱 화면(/, /score)으로 가는 링크만 유입처를 넘긴다. */
    static String header(String domain, String current) {
        String[][] links = { {"/", "청약 캘린더", "app"}, {"/match", "맞춤 청약", "app"}, {"/score", "가점 계산기", "app"}, {"/zupzup", "무순위", ""}, {"/weekly", "이번 주", ""} };
        StringBuilder out = new StringBuilder("<header class='site'><div class='site-in'><a class='brand' data-app href='").append(esc(domain + "/"))
            .append("'><img src='").append(esc(domain + "/brand/toady-mark.png")).append("' width='29' height='26' alt=''><span>TOADY</span></a><nav class='menu' aria-label='주요 메뉴'>");
        for (String[] link : links) {
            out.append("<a").append(link[2].isEmpty() ? "" : " data-app").append(link[0].equals(current) ? " aria-current='page'" : "")
                .append(" href='").append(esc(domain + link[0])).append("'>").append(esc(link[1])).append("</a>");
        }
        return out.append("</nav></div></header>").toString();
    }
    /** 서비스 소개·데이터 출처·약관·문의처를 모든 공개 페이지 하단에 같은 형태로 둔다. */
    static String footer(String domain) {
        return "<footer class='site-foot'><div class='site-in'><p><strong>TOADY</strong> · 청약 공고 일정과 분양가·경쟁률·위치·동네 실거래가를 한 화면에서 확인하는 청약 정보 서비스</p>"
            + "<p>데이터 출처: 청약홈·국토교통부 공개 데이터. 일정·가격·신청 자격은 공식 모집공고를 함께 확인해 주세요.</p>"
            + "<p class='foot-links'><a href='" + esc(domain + "/help/faq") + "'>자주 묻는 질문</a><a href='" + esc(domain + "/privacy") + "'>개인정보처리방침</a><a href='" + esc(domain + "/terms") + "'>이용약관</a>"
            + "<a href='mailto:dukkeoba@gmail.com'>문의 dukkeoba@gmail.com</a></p></div></footer>";
    }
    /** 공개 페이지끼리 서로 이어지도록 하단에 같은 탐색 묶음을 둔다. current는 지금 페이지라 링크에서 뺀다. */
    static String explore(String domain, String current) {
        String[][] links = { {"/regions", "지역별 청약"}, {"/areas", "생활권별 청약"}, {"/zupzup", "무순위·줍줍 청약"}, {"/weekly", "이번 주 청약 일정"}, {"/match", "내게 맞는 청약"}, {"/score", "청약 가점 계산기"}, {"/help/faq", "청약 자주 묻는 질문"} };
        StringBuilder out = new StringBuilder("<section class='card'><h2>더 알아보기</h2><div class='chips'>");
        for (String[] link : links) if (!link[0].equals(current))
            out.append("<a class='chip' href='").append(esc(domain + link[0])).append("'>").append(esc(link[1])).append("</a>");
        return out.append("</div></section>").toString();
    }
    /** 앱(MUI 테마)과 같은 색·모서리·글꼴. 외부 글꼴·스크립트 없이 가볍게 둔다. */
    static final String STYLE = """
        <style>
        :root{color-scheme:light}*{box-sizing:border-box}body{margin:0;background:#f8fafc;color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Pretendard','Apple SD Gothic Neo','Malgun Gothic','Noto Sans KR',system-ui,sans-serif;line-height:1.65;word-break:keep-all;-webkit-text-size-adjust:100%}
        a{color:#2563eb}a:focus-visible{outline:2px solid #2563eb;outline-offset:2px;border-radius:6px}
        .site{background:#fff;border-bottom:1px solid #e2e8f0;position:sticky;top:0;z-index:10}.site-in{max-width:760px;margin:0 auto;padding:0 16px}
        .site .site-in{display:flex;align-items:center;gap:4px 16px;flex-wrap:wrap;min-height:56px}
        .brand{display:inline-flex;align-items:center;gap:9px;min-height:44px;color:#0f172a;text-decoration:none;font-weight:800;font-size:18px;letter-spacing:-.01em}.brand img{display:block;height:26px;width:auto}.brand span{font-weight:900;font-size:19px;letter-spacing:-.02em;color:#1e293b}
        .menu{display:flex;gap:2px;overflow-x:auto;white-space:nowrap;scrollbar-width:none;margin-left:auto;-webkit-overflow-scrolling:touch}.menu::-webkit-scrollbar{display:none}
        .menu a{display:inline-flex;align-items:center;min-height:40px;padding:0 12px;border-radius:10px;color:#475569;text-decoration:none;font-size:14px;font-weight:650}
        .menu a:hover{background:#f1f5f9;color:#0f172a}.menu a[aria-current]{background:#eef4ff;color:#1d4ed8}
        @media (max-width:560px){.site .site-in{min-height:0;padding-top:4px}.menu{flex:0 0 calc(100% + 32px);margin:0 -16px;padding:0 12px 6px}.menu a{padding:0 10px}}
        main{max-width:760px;margin:0 auto;padding:16px 16px 32px}.card{background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:22px 20px;margin-bottom:12px}
        main>nav{display:flex;gap:6px;align-items:center;font-size:13px;color:#64748b;margin:4px 2px 12px;flex-wrap:wrap}main>nav a{color:#2563eb;text-decoration:none;font-weight:600}
        h1{font-size:22px;line-height:1.4;margin:0 0 8px;letter-spacing:-.01em}h2{font-size:16px;margin:0 0 10px}p{margin:0 0 10px}small{display:block;color:#64748b;font-size:12px;margin-top:4px}
        .chip{display:inline-block;font-size:12px;font-weight:700;color:#1d4ed8;background:#eef4ff;border-radius:999px;padding:3px 10px;margin-bottom:10px;text-decoration:none}
        .chips{display:flex;flex-wrap:wrap;gap:6px}.chips .chip{margin:0;padding:9px 14px;font-size:13px}.chips .chip:hover{background:#dbe7fe}
        .price{font-size:20px;font-weight:800;color:#0f172a}.muted{color:#64748b;font-size:13px}
        ul{list-style:none;margin:0;padding:0}li{display:flex;justify-content:space-between;gap:12px;padding:10px 0;border-bottom:1px solid #f1f5f9}li:last-child{border-bottom:0}
        li strong{font-weight:650}.cta{display:block;text-align:center;background:#2563eb;color:#fff;padding:14px 18px;border-radius:12px;text-decoration:none;font-weight:700;margin-top:4px}.cta:hover{background:#1d4ed8}
        .cta.secondary{background:#fff;color:#1d4ed8;border:1px solid #bfdbfe;margin-top:8px}.cta.secondary:hover{background:#eef4ff}img{max-width:100%;height:auto;border-radius:12px;display:block}
        .list a{display:block;text-decoration:none;color:inherit;padding:12px 0;border-bottom:1px solid #f1f5f9}.list a:last-child{border-bottom:0}.list strong{display:block;font-size:15px;color:#0f172a}
        .badge{display:inline-block;font-size:11px;font-weight:700;border-radius:6px;padding:1px 7px;margin-right:6px;background:#f1f5f9;color:#475569}.badge.open{background:#eef4ff;color:#1d4ed8}.empty{color:#64748b;font-size:14px;padding:6px 0}
        .stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:14px 0 4px}
        .stats div{background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:10px 12px}.stats strong{display:block;font-size:20px;color:#2563eb}.stats span{font-size:12px;color:#64748b}
        .row{display:flex;gap:8px;flex-wrap:wrap}.row .cta{flex:1;min-width:200px}.when{float:right;font-size:12px;font-weight:700;color:#1d4ed8}
        .site-foot{border-top:1px solid #e2e8f0;background:#fff;color:#64748b;font-size:12px;padding:20px 0 28px}.site-foot p{margin:0 0 6px}.site-foot strong{color:#0f172a}
        .table{overflow-x:auto;margin:0 -4px}table{width:100%;border-collapse:collapse;font-size:13px;min-width:420px}th,td{padding:9px 6px;border-bottom:1px solid #f1f5f9;text-align:right;white-space:nowrap}
        th{font-size:12px;color:#64748b;font-weight:700}th:first-child,td:first-child{text-align:left}tbody tr:last-child td{border-bottom:0}
        .pager{display:flex;justify-content:space-between;gap:8px;margin-top:12px}.pager a{font-weight:700;text-decoration:none}
        .foot-links{display:flex;flex-wrap:wrap;gap:0 14px;margin-top:8px}.foot-links a{display:inline-flex;align-items:center;min-height:40px;color:#475569;font-weight:600;text-decoration:none}.foot-links a:hover{color:#1d4ed8}
        </style>""";
}
