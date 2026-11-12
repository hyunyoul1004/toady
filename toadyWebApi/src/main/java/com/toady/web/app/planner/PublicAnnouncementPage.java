package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 검색·공유 봇에 URL별 HTML과 PNG를 제공한다. 회원 정보와 브라우저 실행에 의존하지 않는다. */
@RestController
public class PublicAnnouncementPage {
    private final PlannerSummary summaries;
    private final ShareScores shareScores;
    private final String domain;
    public PublicAnnouncementPage(PlannerSummary summaries,ShareScores shareScores,@Value("${toady.web.domain}") String domain) {this.summaries=summaries;this.shareScores=shareScores;this.domain=domain.replaceAll("/+$","");}
    static String section(String value) {return java.util.Set.of("detail","rate","map","comment").contains(value)?value:"detail";}
    static String label(String value) {return switch(value){case "rate" -> "청약 경쟁률";case "map" -> "위치·입지";case "comment" -> "청약 이야기";default -> "청약 일정·분양 정보";};}
    private static boolean valid(String type,String house,String notice) {return type.matches("apt|urban|remaining|public-rental|optional") && house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}");}
    @GetMapping(value="/api/v1/public/announcements/{type}/{house}/{notice}",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> page(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) {
        if(!valid(type,house,notice)) return ResponseEntity.notFound().build();
        PlannerSummary.Summary s;
        try{s=summaries.get(type,house,notice);}catch(EntityNotFoundException missing){return ResponseEntity.status(404).contentType(MediaType.TEXT_HTML).body("<!doctype html><html lang='ko'><meta charset='UTF-8'><title>공고 없음 · TOADY</title><h1>공고를 찾을 수 없습니다.</h1></html>");}
        String tab=section(tabType),base=domain+"/announcements/"+s.id();
        String url=base+(tab.equals("detail")?"":"?tabType="+tab);
        var scores = tab.equals("rate") ? shareScores.get(type,house,notice) : new ShareScores.Range("", "");
        var area = LivingAreas.match(s.address(), s.houseNm());
        String price = PublicHtml.priceRange(s.minPriceManwon(), s.maxPriceManwon());
        String topic = switch(tab){case "rate" -> scores.available()?"청약 경쟁률·당첨가점":"청약 경쟁률";case "map" -> "위치·입지";case "comment" -> "청약 이야기";default -> "분양가·청약 일정";};
        String title=s.houseNm()+" "+topic+" · TOADY";
        // 검색 결과에서 잘리지 않도록 핵심(생활권·금액·일정)을 앞에 둔다.
        StringBuilder summary=new StringBuilder();
        area.ifPresent(value -> summary.append(value.label()).append(" 생활권 · "));
        if(!price.isEmpty()) summary.append("주택형별 최고 공급금액 ").append(price).append(" · ");
        summary.append("접수 ").append(PublicHtml.shortDate(s.startDate())).append("~").append(PublicHtml.shortDate(s.endDate()))
            .append(" · 발표 ").append(PublicHtml.shortDate(s.winnerDate()));
        String description=(tab.equals("rate")?(scores.available()?scores.text()+" · 공개 주택형·지역 전체 기준 · ":"주택형·순위별 공개 경쟁률 · "):"")+summary+" · "+s.address();
        String image=domain+"/api/v1/public/announcement-images/"+s.id()+".png?tabType="+tab+"&v=2";
        String app=domain+"/?subscriptSeq="+URLEncoder.encode(s.id(),StandardCharsets.UTF_8)+"&tabType="+tab;
        StringBuilder events=new StringBuilder();
        for(var e:s.events())events.append("<li><strong>").append(PublicHtml.esc(e.label())).append("</strong><span>").append(e.date()).append("</span></li>");
        String nav="<nav><a href='"+PublicHtml.esc(domain)+"/'>TOADY</a>"+area.map(value -> "<span>›</span><a href='"+PublicHtml.esc(domain+"/areas/"+value.slug())+"'>"+PublicHtml.esc(value.label())+" 청약</a>").orElse("")+"</nav>";
        String priceCard=price.isEmpty()?"":"<section class='card'><h2>주택형별 최고 공급금액</h2><p class='price'>"+PublicHtml.esc(price)+"</p><p class='muted'>각 주택형의 최고 금액을 모은 범위이며 최저 분양가가 아닙니다. 층·타입별 금액은 모집공고를 확인해 주세요.</p></section>";
        String areaLink=area.map(value -> "<a class='cta secondary' href='"+PublicHtml.esc(domain+"/areas/"+value.slug())+"'>"+PublicHtml.esc(value.label())+" 생활권 청약 더 보기</a>").orElse("");
        String html="""
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='article'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            <meta property='og:image' content='%s'><meta property='og:image:width' content='1200'><meta property='og:image:height' content='630'><meta property='og:image:type' content='image/png'><meta property='og:image:alt' content='%s'>
            <meta name='twitter:card' content='summary_large_image'><meta name='twitter:title' content='%s'><meta name='twitter:description' content='%s'><meta name='twitter:image' content='%s'>
            %s</head><body><main>%s
            <section class='card'><h1>%s</h1><p class='muted'>%s</p><img src='%s' width='1200' height='630' alt='%s'></section>
            %s<section class='card'><h2>주요 일정</h2><ul>%s</ul></section>
            <section class='card'><a class='cta' data-app href='%s'>%s 자세히 보기</a>%s<p class='muted' style='margin-top:12px'>경쟁률·당첨가점·위치·동네 실거래가를 한 화면에서 확인할 수 있어요.</p></section>
            <small>출처: 청약홈 공개 데이터. 공개 자료가 없는 경쟁률은 제공되지 않을 수 있습니다. 일정·가격·신청 자격은 공식 모집공고를 확인해 주세요.</small></main>%s</body></html>
            """.formatted(esc(title),esc(description),esc(base),esc(title),esc(description),esc(url),esc(image),esc(title),esc(title),esc(description),esc(image),
                PublicHtml.STYLE,nav,esc(s.houseNm()),esc(summary+" · "+s.address()),esc(image),esc(title),priceCard,events,esc(app),esc(label(tab)),areaLink,PublicHtml.SOURCE_SCRIPT);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(html);
    }
    @GetMapping(value="/api/v1/public/announcement-images/{type}/{house}/{notice}.png",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) throws java.io.IOException {
        if(!valid(type,house,notice))return ResponseEntity.notFound().build();
        try{return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(AnnouncementShareCard.png(summaries.get(type,house,notice),section(tabType),section(tabType).equals("rate")?shareScores.get(type,house,notice):new ShareScores.Range("", "")));}
        catch(EntityNotFoundException missing){return ResponseEntity.notFound().build();}
    }
    private static String esc(String value){return PublicHtml.esc(value);}
}
