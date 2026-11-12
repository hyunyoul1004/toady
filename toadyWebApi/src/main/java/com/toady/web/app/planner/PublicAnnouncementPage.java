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
    private final String domain;
    public PublicAnnouncementPage(PlannerSummary summaries,@Value("${toady.web.domain}") String domain) {this.summaries=summaries;this.domain=domain.replaceAll("/+$","");}
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
        String title=s.houseNm()+" | "+label(tab)+" · TOADY";
        String description=s.address()+" · 접수 "+text(s.startDate())+" ~ "+text(s.endDate())+" · 발표 "+text(s.winnerDate());
        if(tab.equals("rate")) description="주택형·순위별 공개 경쟁률 확인 · "+description;
        String image=domain+"/api/v1/public/announcement-images/"+s.id()+".png?tabType="+tab+"&v=1";
        String app=domain+"/?subscriptSeq="+URLEncoder.encode(s.id(),StandardCharsets.UTF_8)+"&tabType="+tab;
        StringBuilder events=new StringBuilder();
        for(var e:s.events())events.append("<li><strong>").append(esc(e.label())).append("</strong> ").append(e.date()).append("</li>");
        String html="""
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='article'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            <meta property='og:image' content='%s'><meta property='og:image:width' content='1200'><meta property='og:image:height' content='630'><meta property='og:image:type' content='image/png'><meta property='og:image:alt' content='%s'>
            <meta name='twitter:card' content='summary_large_image'><meta name='twitter:title' content='%s'><meta name='twitter:description' content='%s'><meta name='twitter:image' content='%s'>
            <style>body{margin:0;background:#f5f7fa;color:#183142;font-family:system-ui,sans-serif;line-height:1.7}main{max-width:780px;margin:24px auto;padding:24px;background:#fff;border-radius:20px}a{color:#087f73}h1{line-height:1.4}li{padding:6px}.cta{display:inline-block;background:#087f73;color:white;padding:12px 20px;border-radius:10px;text-decoration:none}small{color:#617687}img{max-width:100%%;border-radius:12px}</style>
            </head><body><main><a href='%s/'>TOADY · 청약 한곳에서</a><h1>%s</h1><p>%s</p><img src='%s' width='1200' height='630' alt='%s' style='height:auto'><h2>주요 일정</h2><ul>%s</ul><p><a class='cta' href='%s'>%s 확인하기</a></p><hr><small>출처: 청약홈 공개 데이터. 공개 자료가 없는 경쟁률은 제공되지 않을 수 있습니다. 일정·가격·신청 자격은 공식 모집공고를 확인해 주세요.</small></main></body></html>
            """.formatted(esc(title),esc(description),esc(base),esc(title),esc(description),esc(url),esc(image),esc(title),esc(title),esc(description),esc(image),esc(domain),esc(s.houseNm()),esc(description),esc(image),esc(title),events,esc(app),esc(label(tab)));
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(html);
    }
    @GetMapping(value="/api/v1/public/announcement-images/{type}/{house}/{notice}.png",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) throws java.io.IOException {
        if(!valid(type,house,notice))return ResponseEntity.notFound().build();
        try{return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(AnnouncementShareCard.png(summaries.get(type,house,notice),section(tabType)));}
        catch(EntityNotFoundException missing){return ResponseEntity.notFound().build();}
    }
    private static String text(Object value){return value==null?"미제공":value.toString();}
    private static String esc(String value){return HtmlUtils.htmlEscape(value==null?"":value);}
}
