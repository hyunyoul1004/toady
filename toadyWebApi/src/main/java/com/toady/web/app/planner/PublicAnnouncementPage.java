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
    private final AnnouncementResults results;
    private final PriceGapStore gaps;
    private final com.google.common.cache.Cache<String,byte[]> images=com.google.common.cache.CacheBuilder.newBuilder()
        .maximumWeight(64L*1024*1024).weigher((String key,byte[] value)->value.length).expireAfterWrite(Duration.ofMinutes(10)).build();
    public PublicAnnouncementPage(PlannerSummary summaries,ShareScores shareScores,@Value("${toady.web.domain}") String domain,AnnouncementResults results,PriceGapStore gaps) {this.summaries=summaries;this.shareScores=shareScores;this.domain=domain.replaceAll("/+$","");this.results=results;this.gaps=gaps;}
    /** APT 공고의 공개 결과 카드(1순위 최고 경쟁률·당첨가점). 공개 전이면 비운다. */
    String resultCard(String id) {
        AnnouncementResults.Result result=results.forIds(java.util.List.of(id)).get(id);
        if(result==null || (!result.ratePublished() && !result.scorePublished())) return "";
        StringBuilder out=new StringBuilder("<section class='card'><h2>청약 결과</h2><ul>");
        if(result.ratePublished()) out.append("<li><strong>1순위 최고 경쟁률</strong><span>").append(result.topRate()==null?"미달":PublicHtml.esc(rate(result.topRate())+" : 1")).append("</span></li>");
        if(result.scorePublished()) out.append("<li><strong>공개 당첨가점</strong><span>").append(PublicHtml.esc((result.scoreLow().isEmpty()?"":"최저 "+result.scoreLow()+"점")+(!result.scoreLow().isEmpty() && !result.scoreHigh().isEmpty()?" · ":"")+(result.scoreHigh().isEmpty()?"":"최고 "+result.scoreHigh()+"점"))).append("</span></li>");
        return out.append("</ul><p class='muted' style='margin-top:8px'>주택형·거주지역 전체 중 공개된 값의 최고·최저예요. 주택형별 경쟁률과 내 가점 비교는 앱에서 확인하세요.</p></section>").toString();
    }
    static String rate(double value) { return value>=100?String.format(java.util.Locale.ROOT,"%,d",Math.round(value)):java.math.BigDecimal.valueOf(value).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
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
        String nav="<nav aria-label='현재 위치'><a href='"+PublicHtml.esc(domain)+"/'>TOADY</a>"+area.map(value -> "<span>›</span><a href='"+PublicHtml.esc(domain+"/areas/"+value.slug())+"'>"+PublicHtml.esc(value.label())+" 청약</a>").orElse("")+"</nav>";
        // 미리 계산한 주변 시세 비교(신뢰도 보통 이상)가 있으면 공급금액 아래에 한 줄로 덧붙인다.
        var gap=gaps.forIds(java.util.List.of(s.id())).get(s.id());
        String gapLine=gap==null?"":"<p><strong>"+PublicHtml.esc("주변 "+("new".equals(gap.basis())?"신축 ":"")+("비슷".equals(gap.text())?"시세와 비슷해요":"시세 대비 "+gap.text()))+"</strong></p><p class='muted'>"
            +PublicHtml.esc("전용 "+gap.area()+"㎡ 기준 · "+("DONG".equals(gap.scope())?gap.dong():"같은 시·군·구")+" "+("new".equals(gap.basis())?"준공 10년 이내 ":"")+gap.basisCount()+"건 · 최근 "+gap.months()+"개월 실거래("+gap.computedDate().replace('-','.')+" 계산) · 실제 차익을 보장하지 않는 참고 비교예요.")+"</p>";
        String priceCard=price.isEmpty()?"":"<section class='card'><h2>주택형별 최고 공급금액</h2><p class='price'>"+PublicHtml.esc(price)+"</p>"+gapLine+"<p class='muted'>각 주택형의 최고 금액을 모은 범위이며 최저 분양가가 아닙니다. 층·타입별 금액은 모집공고를 확인해 주세요.</p></section>";
        String areaLink=area.map(value -> "<a class='cta secondary' href='"+PublicHtml.esc(domain+"/areas/"+value.slug())+"'>"+PublicHtml.esc(value.label())+" 생활권 청약 더 보기</a>").orElse("");
        String html="""
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='article'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            <meta property='og:image' content='%s'><meta property='og:image:width' content='1200'><meta property='og:image:height' content='630'><meta property='og:image:type' content='image/png'><meta property='og:image:alt' content='%s'>
            <meta name='twitter:card' content='summary_large_image'><meta name='twitter:title' content='%s'><meta name='twitter:description' content='%s'><meta name='twitter:image' content='%s'>
            <meta name='theme-color' content='#ffffff'>%s</head><body>%s<main>%s
            <section class='card'><h1>%s</h1><p class='muted'>%s</p><img src='%s' width='1200' height='630' alt='%s'></section>
            %s%s<section class='card'><h2>주요 일정</h2><ul>%s</ul></section>
            <section class='card'><a class='cta' data-app href='%s'>%s 자세히 보기</a><a class='cta secondary' data-app href='%s'>이 공고, 내가 넣을 수 있을까? 자격 진단</a>%s<p class='muted' style='margin-top:12px'>경쟁률·당첨가점·위치·동네 실거래가와 내 신청 가능성을 한 화면에서 확인할 수 있어요.</p></section>
            <small>출처: 청약홈 공개 데이터. 공개 자료가 없는 경쟁률은 제공되지 않을 수 있습니다. 일정·가격·신청 자격은 공식 모집공고를 확인해 주세요.</small></main>%s%s</body></html>
            """.formatted(esc(title),esc(description),esc(base),esc(title),esc(description),esc(url),esc(image),esc(title),esc(title),esc(description),esc(image),
                PublicHtml.STYLE,PublicHtml.header(domain,""),nav,esc(s.houseNm()),esc(summary+" · "+s.address()),esc(image),esc(title),priceCard,type.equals("apt")?resultCard(s.id()):"",events,esc(app),esc(label(tab)),esc(domain+"/match"),areaLink,PublicHtml.footer(domain),PublicHtml.SOURCE_SCRIPT);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(html);
    }
    @GetMapping(value="/api/v1/public/announcement-images/{type}/{house}/{notice}.png",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) throws java.io.IOException {
        if(!valid(type,house,notice))return ResponseEntity.notFound().build();
        String section=section(tabType);
        ShareScores.Range scores=section.equals("rate")?shareScores.get(type,house,notice):new ShareScores.Range("", "");
        // 렌더링 비용이 커서 같은 공고·탭·공개 가점의 이미지는 10분간 다시 쓴다(쿼리 값을 바꿔도 같은 키, 가점이 공개되면 새로 그린다).
        String key=type+"/"+house+"/"+notice+"/"+section+"/"+scores.minimum()+"/"+scores.maximum();
        try{return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(images.get(key,()->AnnouncementShareCard.png(summaries.get(type,house,notice),section,scores)));}
        catch(java.util.concurrent.ExecutionException|com.google.common.util.concurrent.UncheckedExecutionException failed){
            if(failed.getCause() instanceof EntityNotFoundException) return ResponseEntity.notFound().build();
            if(failed.getCause() instanceof java.io.IOException io) throw io;
            if(failed.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failed.getCause());
        }
    }
    private static String esc(String value){return PublicHtml.esc(value);}
}
