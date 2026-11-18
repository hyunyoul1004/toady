package com.toady.web.app.planner;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * 공고 상세 공개 페이지(서버 HTML)와 공유 이미지(PNG). 검색·공유 봇에 URL별 HTML과 PNG를 제공한다.
 * 사람은 /announcements/{type}/{house}/{notice}에서 React 앱을 보고, 봇만 웹 컨테이너 nginx를 거쳐 이 HTML을 받는다.
 * 회원 정보와 브라우저 실행에 의존하지 않는다. 요약은 PlannerSummary, 결과는 AnnouncementResults, 시세 비교는 PriceGapStore에서 읽는다.
 */
// @RestController: 완성된 HTML·PNG를 응답 본문으로 돌려준다.
@RestController
public class PublicAnnouncementPage {
    private final PlannerSummary summaries;
    private final ShareScores shareScores;
    private final String domain;
    private final AnnouncementResults results;
    private final PriceGapStore gaps;
    // @Autowired(required=false): 숨김 기능 빈이 있을 때만 넣는다(없으면 null, 숨김 확인을 건너뜀).
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.toady.web.app.operation.AnnouncementOverrides overrides;
    /** 지역 판정·같은 지역 다른 공고(사이트맵과 같은 목록). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    PublicAnnouncementIndex index;
    private final Cache<String,byte[]> images=CacheBuilder.newBuilder()
        .maximumWeight(64L*1024*1024).weigher((String key,byte[] value)->value.length).expireAfterWrite(Duration.ofMinutes(10)).build();
    public PublicAnnouncementPage(PlannerSummary summaries,ShareScores shareScores,@Value("${toady.web.domain}") String domain,AnnouncementResults results,PriceGapStore gaps) {this.summaries=summaries;this.shareScores=shareScores;this.domain=domain.replaceAll("/+$","");this.results=results;this.gaps=gaps;}
    /**
     * APT 공고의 공개 결과: 1순위 최고 경쟁률·공개 당첨가점 요약과 주택형별 1순위 경쟁률·당첨가점 표. 공개 전이면 비운다.
     * 검색으로 결과를 찾는 사람이 표를 바로 보도록 서버 HTML에 모두 넣는다. result: 페이지에서 이미 조회한 공개 결과(다시 조회하지 않는다).
     */
    String resultCard(String id,AnnouncementResults.Result result) {
        if(result==null || (!result.ratePublished() && !result.scorePublished())) return "";
        StringBuilder out=new StringBuilder("<section class='card' id='result'><h2>청약 결과</h2><ul>");
        if(result.ratePublished()) out.append("<li><strong>1순위 최고 경쟁률</strong><span>").append(result.topRate()==null?"미달":PublicHtml.esc(rate(result.topRate())+" : 1")).append("</span></li>");
        if(result.scorePublished()) out.append("<li><strong>공개 당첨가점</strong><span>").append(PublicHtml.esc(scoreText(result.scoreLow(),result.scoreHigh()))).append("</span></li>");
        out.append("</ul>");
        String[] parts=id.split("/");
        // 주택형별 표는 덧붙이는 정보라, 조회에 실패하면 표만 빼고 요약은 그대로 보여 준다.
        List<AnnouncementResults.HouseTypeResult> types;
        try { types=results.houseTypes(parts[1],parts[2]); } catch(RuntimeException unavailable) { types=List.of(); }
        if(!types.isEmpty()) {
            out.append("<h2 style='margin-top:16px'>주택형별 1순위 경쟁률·당첨가점</h2><div class='table'><table><thead><tr><th scope='col'>주택형</th><th scope='col'>공급</th><th scope='col'>1순위 접수</th><th scope='col'>최고 경쟁률</th><th scope='col'>당첨가점</th></tr></thead><tbody>");
            for(var type:types) {
                out.append("<tr><th scope='row'>").append(PublicHtml.esc(type.houseType())).append("</th><td>").append(type.supply().isEmpty()?"-":PublicHtml.esc(type.supply()+"세대"))
                    .append("</td><td>").append(type.requests()==null?"-":PublicHtml.esc(String.format(Locale.ROOT,"%,d건",type.requests())))
                    .append("</td><td>").append(!type.rated()?"-":type.topRate()==null?"미달":PublicHtml.esc(rate(type.topRate())+" : 1"))
                    .append("</td><td>").append(type.scoreLow().isEmpty()&&type.scoreHigh().isEmpty()?"-":PublicHtml.esc(scoreRange(type.scoreLow(),type.scoreHigh()))).append("</td></tr>");
            }
            out.append("</tbody></table></div>");
        }
        return out.append("<p class='muted' style='margin-top:8px'>청약홈이 공개한 1순위 거주지역(해당·기타지역) 중 최고 경쟁률과 주택형별 최저~최고 당첨가점이에요. 내 가점 비교는 앱에서 확인하세요.</p></section>").toString();
    }
    static String scoreText(String low,String high) {
        return (low.isEmpty()?"":"최저 "+low+"점")+(!low.isEmpty() && !high.isEmpty()?" · ":"")+(high.isEmpty()?"":"최고 "+high+"점");
    }
    static String scoreRange(String low,String high) {
        if(low.isEmpty()) return high+"점";
        if(high.isEmpty() || low.equals(high)) return low+"점";
        return low+"~"+high+"점";
    }
    static String rate(double value) { return value>=100?String.format(Locale.ROOT,"%,d",Math.round(value)):BigDecimal.valueOf(value).setScale(2,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    static String section(String value) {return Set.of("detail","rate","map","comment").contains(value)?value:"detail";}
    static String label(String value) {return switch(value){case "rate" -> "청약 경쟁률";case "map" -> "위치·입지";case "comment" -> "청약 이야기";default -> "청약 일정·분양 정보";};}
    private static boolean valid(String type,String house,String notice) {return type.matches("apt|urban|remaining|public-rental|optional") && house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}");}
    // @GetMapping: 공고 상세 HTML. @PathVariable로 공고 키를, @RequestParam(defaultValue="detail")로 탭(detail·rate·map·comment)을 받는다.
    // 제목·설명·og:image(카카오톡 미리보기)·구조화 데이터를 탭과 결과 공개 여부에 맞게 만든다. 숨긴 공고는 410(사라짐).
    @GetMapping(value="/api/v1/public/announcements/{type}/{house}/{notice}",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> page(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) {
        if(!valid(type,house,notice)) return ResponseEntity.notFound().build();
        // 운영자가 숨긴 공고(잘못 수집·취소)는 검색엔진에 알리지 않는다. 사람은 앱에서 안내와 함께 볼 수 있다.
        if(overrides!=null && overrides.hidden(type.toUpperCase(Locale.ROOT).replace('-','_'),house,notice)) {
            return ResponseEntity.status(410).contentType(MediaType.TEXT_HTML).body("<!doctype html><html lang='ko'><meta charset='UTF-8'><meta name='robots' content='noindex'><title>공고 안내 · TOADY</title><h1>운영 안내로 숨긴 공고입니다.</h1></html>");
        }
        PlannerSummary.Summary s;
        try{s=summaries.get(type,house,notice);}catch(EntityNotFoundException missing){return ResponseEntity.status(404).contentType(MediaType.TEXT_HTML).body("<!doctype html><html lang='ko'><meta charset='UTF-8'><title>공고 없음 · TOADY</title><h1>공고를 찾을 수 없습니다.</h1></html>");}
        String tab=section(tabType),base=domain+"/announcements/"+s.id();
        String url=base+(tab.equals("detail")?"":"?tabType="+tab);
        var scores = tab.equals("rate") ? shareScores.get(type,house,notice) : new ShareScores.Range("", "");
        var area = LivingAreas.match(s.address(), s.houseNm());
        // 지역은 공개 목록과 같은 기준(주소 + 청약홈 공급지역명)으로 정하고, 목록에 없는 지난 공고는 주소만으로 정한다.
        // 공개 목록 조회에 실패해도 공고 페이지는 주소만으로 지역을 정해 그대로 보여 준다.
        java.util.Optional<PublicAnnouncementIndex.Entry> indexed;
        try { indexed = index==null ? java.util.Optional.empty() : index.find(s.id()); } catch(RuntimeException unavailable) { indexed = java.util.Optional.empty(); }
        Regions.Region region = indexed.map(PublicAnnouncementIndex.Entry::region).orElseGet(() -> Regions.of(s.address()).orElse(null));
        AnnouncementResults.Result published = type.equals("apt") ? publishedResult(s.id()) : null;
        boolean resultOut = published!=null && (published.ratePublished() || published.scorePublished());
        String price = PublicHtml.priceRange(s.minPriceManwon(), s.maxPriceManwon());
        // 경쟁률·당첨가점이 공개되면 '단지명 경쟁률', '단지명 당첨가점' 검색에 맞게 제목을 결과 중심으로 바꾼다.
        String resultTopic = resultOut ? (published.scorePublished() ? "청약 경쟁률·당첨가점 결과" : "청약 경쟁률 결과") : "";
        String topic = switch(tab){
            case "rate" -> resultOut ? resultTopic : scores.available()?"청약 경쟁률·당첨가점":"청약 경쟁률";
            case "map" -> "위치·입지"; case "comment" -> "청약 이야기";
            default -> resultOut ? resultTopic : "분양가·청약 일정";};
        String title=s.houseNm()+" "+topic+" · TOADY";
        // 검색 결과에서 잘리지 않도록 핵심(생활권·금액·일정)을 앞에 둔다.
        StringBuilder summary=new StringBuilder();
        area.ifPresent(value -> summary.append(value.label()).append(" 생활권 · "));
        if(!price.isEmpty()) summary.append("주택형별 최고 공급금액 ").append(price).append(" · ");
        summary.append("접수 ").append(PublicHtml.shortDate(s.startDate())).append("~").append(PublicHtml.shortDate(s.endDate()))
            .append(" · 발표 ").append(PublicHtml.shortDate(s.winnerDate()));
        String lead=!tab.equals("rate")?resultLead(published):scores.available()?scores.text()+" · 공개 주택형·지역 전체 기준 · ":resultOut?resultLead(published):"주택형·순위별 공개 경쟁률 · ";
        String description=lead+summary+" · "+s.address();
        String image=domain+"/api/v1/public/announcement-images/"+s.id()+".png?tabType="+tab+"&v=2";
        // 앱으로 가는 링크도 공고의 대표 주소를 쓴다(사람에게는 같은 주소에서 앱 상세 화면이 열린다).
        String app=url;
        StringBuilder events=new StringBuilder();
        for(var e:s.events())events.append("<li><strong>").append(PublicHtml.esc(e.label())).append("</strong><span>").append(e.date()).append("</span></li>");
        List<PublicHtml.Crumb> crumbs=new java.util.ArrayList<>();
        crumbs.add(new PublicHtml.Crumb("TOADY",domain+"/"));
        if(region!=null) {
            crumbs.add(new PublicHtml.Crumb("지역별 청약",domain+"/regions"));
            crumbs.add(new PublicHtml.Crumb(region.sido().name(),domain+region.sido().path()));
            if(region.district()!=null) crumbs.add(new PublicHtml.Crumb(region.district(),domain+region.path()));
        } else area.ifPresent(value -> crumbs.add(new PublicHtml.Crumb(value.label()+" 청약",domain+"/areas/"+value.slug())));
        crumbs.add(new PublicHtml.Crumb(s.houseNm(),null));
        String nav=PublicHtml.breadcrumb(crumbs);
        // 미리 계산한 주변 시세 비교(신뢰도 보통 이상)가 있으면 공급금액 아래에 한 줄로 덧붙인다.
        var gap=gaps.forIds(List.of(s.id())).get(s.id());
        String gapLine=gap==null?"":"<p><strong>"+PublicHtml.esc("주변 "+("new".equals(gap.basis())?"신축 ":"")+("비슷".equals(gap.text())?"시세와 비슷해요":"시세 대비 "+gap.text()))+"</strong></p><p class='muted'>"
            +PublicHtml.esc("전용 "+gap.area()+"㎡ 기준 · "+("DONG".equals(gap.scope())?gap.dong():"같은 시·군·구")+" "+("new".equals(gap.basis())?"준공 10년 이내 ":"")+gap.basisCount()+"건 · 최근 "+gap.months()+"개월 실거래("+gap.computedDate().replace('-','.')+" 계산) · 실제 차익을 보장하지 않는 참고 비교예요.")+"</p>";
        String priceCard=price.isEmpty()?"":"<section class='card'><h2>주택형별 최고 공급금액</h2><p class='price'>"+PublicHtml.esc(price)+"</p>"+gapLine+"<p class='muted'>각 주택형의 최고 금액을 모은 범위이며 최저 분양가가 아닙니다. 층·타입별 금액은 모집공고를 확인해 주세요.</p></section>";
        String areaLink=area.map(value -> "<a class='cta secondary' href='"+PublicHtml.esc(domain+"/areas/"+value.slug())+"'>"+PublicHtml.esc(value.label())+" 생활권 청약 더 보기</a>").orElse("");
        String head=PublicHtml.breadcrumbLd(crumbs,base);
        String html="""
            <!doctype html><html lang='ko'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
            <title>%s</title><meta name='description' content='%s'><link rel='canonical' href='%s'>
            <meta property='og:type' content='article'><meta property='og:locale' content='ko_KR'><meta property='og:site_name' content='TOADY'>
            <meta property='og:title' content='%s'><meta property='og:description' content='%s'><meta property='og:url' content='%s'>
            <meta property='og:image' content='%s'><meta property='og:image:width' content='1200'><meta property='og:image:height' content='630'><meta property='og:image:type' content='image/png'><meta property='og:image:alt' content='%s'>
            <meta name='twitter:card' content='summary_large_image'><meta name='twitter:title' content='%s'><meta name='twitter:description' content='%s'><meta name='twitter:image' content='%s'>
            <meta name='theme-color' content='#ffffff'>%s%s</head><body>%s<main>%s
            <section class='card'><h1>%s</h1><p class='muted'>%s</p><img src='%s' width='1200' height='630' alt='%s'></section>
            %s%s<section class='card'><h2>주요 일정</h2><ul>%s</ul></section>
            <section class='card'><a class='cta' data-app href='%s'>%s 자세히 보기</a><a class='cta secondary' data-app href='%s'>이 공고, 내가 넣을 수 있을까? 자격 진단</a>%s<p class='muted' style='margin-top:12px'>경쟁률·당첨가점·위치·동네 실거래가와 내 신청 가능성을 한 화면에서 확인할 수 있어요.</p></section>
            %s
            <small>출처: 청약홈 공개 데이터. 공개 자료가 없는 경쟁률은 제공되지 않을 수 있습니다. 일정·가격·신청 자격은 공식 모집공고를 확인해 주세요.</small></main>%s%s</body></html>
            """.formatted(esc(title),esc(description),esc(base),esc(title),esc(description),esc(url),esc(image),esc(title),esc(title),esc(description),esc(image),
                PublicHtml.STYLE,head,PublicHtml.header(domain,""),nav,esc(s.houseNm()),esc(summary+" · "+s.address()),esc(image),esc(title),priceCard,resultOut?resultCard(s.id(),published):"",events,esc(app),esc(label(tab)),esc(domain+"/match"),areaLink,
                nearby(s.id(),region),PublicHtml.footer(domain),PublicHtml.SOURCE_SCRIPT);
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(html);
    }
    static final int NEARBY = 8;
    /**
     * 같은 시·군·구(없으면 시·도)의 다른 공고: 접수 중·예정 먼저, 그다음 최근 공고. 지역 공개 페이지로 이어지는 링크도 둔다.
     * 지역 페이지와 같은 범위(Entry.listed)만 쓴다. 180일보다 먼 접수 예정 공고는 맨 앞에 오지 않게 빼고,
     * 그 지역에 목록에 보이는 공고가 하나도 없으면(시·군·구 페이지가 404) '전체 보기' 링크를 두지 않는다.
     */
    String nearby(String id,Regions.Region region) {
        if(region==null || index==null) return "";
        java.time.LocalDate today;
        List<PublicAnnouncementIndex.Entry> inRegion;
        try {
            today=index.today();
            inRegion=index.recent().stream().filter(entry -> entry.listed(today) && entry.region()!=null && entry.region().sido().equals(region.sido())
                && (region.district()==null || region.district().equals(entry.region().district()))).toList();
        } catch(RuntimeException unavailable) { return ""; }
        List<PublicAnnouncementIndex.Entry> same=inRegion.stream().filter(entry -> !entry.id().equals(id)).toList();
        String label=region.district()==null?region.sido().name():region.district();
        StringBuilder out=new StringBuilder("<section class='card list'><h2>같은 ").append(region.district()==null?"시·도":"시·군·구").append("의 다른 공고 <span class='muted'>")
            .append(esc(label)).append("</span></h2>");
        var picked=PublicAnnouncementIndex.ordered(same,today).stream().limit(NEARBY).toList();
        if(picked.isEmpty()) out.append("<p class='empty'>최근 1년 안에 이 지역의 다른 공고가 없어요.</p>");
        for(var entry:picked) out.append(PublicRegionPages.item(domain,entry,today));
        if(inRegion.isEmpty() && region.district()!=null) return out.append("</section>").toString();
        return out.append("<a class='cta secondary' href='").append(esc(domain+region.path())).append("'>").append(esc(region.label())).append(" 청약 전체 보기</a></section>").toString();
    }
    // @GetMapping: 공유 카드 PNG. produces로 image/png를 알리고, 그린 결과는 메모리(최대 64MB)에 10분 둔다.
    @GetMapping(value="/api/v1/public/announcement-images/{type}/{house}/{notice}.png",produces=MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String type,@PathVariable String house,@PathVariable String notice,@RequestParam(defaultValue="detail") String tabType) throws IOException {
        if(!valid(type,house,notice))return ResponseEntity.notFound().build();
        // 숨긴 공고는 공유 카드도 내주지 않는다(공고 페이지와 같이 410). 캐시보다 먼저 확인해 숨긴 직후부터 막는다.
        if(overrides!=null && overrides.hidden(type.toUpperCase(Locale.ROOT).replace('-','_'),house,notice)) return ResponseEntity.status(410).build();
        String section=section(tabType);
        ShareScores.Range scores=section.equals("rate")?shareScores.get(type,house,notice):new ShareScores.Range("", "");
        // 렌더링 비용이 커서 같은 공고·탭·공개 가점의 이미지는 10분간 다시 쓴다(쿼리 값을 바꿔도 같은 키, 가점이 공개되면 새로 그린다).
        String key=type+"/"+house+"/"+notice+"/"+section+"/"+scores.minimum()+"/"+scores.maximum();
        try{return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(images.get(key,()->AnnouncementShareCard.png(summaries.get(type,house,notice),section,scores)));}
        catch(ExecutionException|UncheckedExecutionException failed){
            if(failed.getCause() instanceof EntityNotFoundException) return ResponseEntity.notFound().build();
            if(failed.getCause() instanceof IOException io) throw io;
            if(failed.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(failed.getCause());
        }
    }
    private static String esc(String value){return PublicHtml.esc(value);}

    /** 공개된 결과(조회 실패·APT 외 공고는 null). */
    AnnouncementResults.Result publishedResult(String id) {
        if(results==null || !id.startsWith("apt/")) return null;
        try { return results.forIds(List.of(id)).get(id); } catch(RuntimeException unavailable) { return null; }
    }
    /** 결과가 공개된 APT 공고면 설명 앞에 '1순위 최고 12.4 대 1 · 당첨가점 최저 54점 · 최고 69점 · '을 둔다(결과를 찾는 검색에 맞게). */
    static String resultLead(AnnouncementResults.Result result) {
        if(result==null) return "";
        StringBuilder out=new StringBuilder();
        if(result.ratePublished()) out.append(result.topRate()==null?"1순위 미달 · ":"1순위 최고 "+com.toady.web.app.push.PushMessage.rate(result.topRate())+" 대 1 · ");
        if(result.scorePublished()) out.append("당첨가점 ").append(scoreText(result.scoreLow(),result.scoreHigh())).append(" · ");
        return out.toString();
    }
}
