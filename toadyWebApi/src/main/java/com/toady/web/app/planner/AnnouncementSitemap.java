package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * 공고 URL을 유형별로 나누고 페이지당 5천 건으로 제한한다. 공고 데이터만 공개한다.
 * 사이트맵에는 접수 마감(없으면 모집공고일)이 최근 recent-days 이내이거나 아직 오지 않은 공고만 넣는다(PublicAnnouncementIndex와 같은 기준).
 * 몇 년 지난 공고 수천 건을 함께 알리면 검색엔진이 비슷한 페이지로 보고 대부분 색인을 미룬다.
 * 지난 공고 페이지는 그대로 열려 있어 링크로는 들어올 수 있다.
 * lastmod는 공고 수집·경쟁률·당첨가점이 처음 들어온 날 중 가장 늦은 날이고, 모음·지역 페이지는 담긴 공고 중 가장 늦은 날이다.
 */
@RestController
public class AnnouncementSitemap {
    static final int PAGE_SIZE = 5000;
    private final PublicAnnouncementIndex announcements; private final String domain;
    public AnnouncementSitemap(PublicAnnouncementIndex announcements, @Value("${toady.web.domain}") String domain) {
        this.announcements = announcements; this.domain = domain.replaceAll("/+$", "");
    }
    private List<PublicAnnouncementIndex.Entry> ofType(String type) {
        return announcements.recent().stream().filter(entry -> entry.type().equals(type)).toList();
    }
    @GetMapping(value="/api/v1/public/sitemap.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> index() {
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><sitemapindex xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        for(String type:PublicAnnouncementIndex.SOURCES.keySet()) {
            List<PublicAnnouncementIndex.Entry> entries=ofType(type);
            for(int page=0;page*PAGE_SIZE<entries.size();page++)
                xml.append("<sitemap><loc>").append(esc(domain+"/sitemaps/"+type+"/"+page+".xml")).append("</loc>")
                    .append(lastmod(latest(entries.subList(page*PAGE_SIZE,Math.min(entries.size(),(page+1)*PAGE_SIZE))))).append("</sitemap>");
        }
        LocalDate all=latest(announcements.recent());
        xml.append("<sitemap><loc>").append(esc(domain+"/sitemaps/areas.xml")).append("</loc>").append(lastmod(all)).append("</sitemap>");
        xml.append("<sitemap><loc>").append(esc(domain+"/sitemaps/regions.xml")).append("</loc>").append(lastmod(all)).append("</sitemap>");
        xml.append("</sitemapindex>");return response(xml.toString());
    }
    @GetMapping(value="/api/v1/public/sitemaps/{type}/{page}.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> entries(@PathVariable String type,@PathVariable int page) {
        if(!PublicAnnouncementIndex.SOURCES.containsKey(type) || page<0 || page>10000) return ResponseEntity.notFound().build();
        List<PublicAnnouncementIndex.Entry> entries=ofType(type);
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        for(var entry:entries.subList(Math.min(entries.size(),page*PAGE_SIZE),Math.min(entries.size(),(page+1)*PAGE_SIZE)))
            xml.append("<url><loc>").append(esc(domain+entry.path())).append("</loc>").append(lastmod(entry.updated())).append("</url>");
        xml.append("</urlset>");return response(xml.toString());
    }
    /** 홈과 생활권·모음 공개 페이지. 홈, 생활권 목록·각 생활권, 무순위·이번 주, 가점 계산기, 맞춤 청약, 자주 묻는 질문을 포함한다. */
    @GetMapping(value="/api/v1/public/sitemaps/areas.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> areas() {
        List<PublicAnnouncementIndex.Entry> all=announcements.recent();
        LocalDate latest=latest(all);
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        // 홈(청약 캘린더)을 맨 앞에 둔다. /sitemap.xml이 이 색인을 가리키므로 홈도 여기서 알려야 한다.
        url(xml,"/",latest);
        url(xml,"/areas",latest);
        for(var area:LivingAreas.all()) url(xml,"/areas/"+area.slug(),latest(all.stream().filter(entry -> LivingAreas.match(entry.address(),entry.houseNm()).filter(found -> found.slug().equals(area.slug())).isPresent()).toList()));
        // 공고 데이터로 자동 생성되는 모음 페이지. /match/zupzup은 /zupzup과 같은 내용이라 공개 주소(/zupzup) 하나만 알린다.
        url(xml,"/zupzup",latest(all.stream().filter(entry -> PublicCollectionPages.UNRANKED_TYPES.contains(entry.type())).toList()));
        url(xml,"/weekly",latest);
        // 계산기·안내 페이지는 공고 데이터와 무관하게 바뀌므로 lastmod를 붙이지 않는다.
        for(String path:List.of("/score","/match","/help/faq")) url(xml,path,null);
        xml.append("</urlset>");return response(xml.toString());
    }
    /** 지역별 청약(시·도, 시·군·구) 공개 페이지. 최근 공고가 있는 지역만 알린다. */
    @GetMapping(value="/api/v1/public/sitemaps/regions.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> regions() {
        List<PublicAnnouncementIndex.Entry> all=announcements.recent();
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        url(xml,"/regions",latest(all));
        Map<String,List<PublicAnnouncementIndex.Entry>> byPath=new LinkedHashMap<>();
        for(var entry:all) {
            if(entry.region()==null) continue;
            byPath.computeIfAbsent(entry.region().sido().path(),key -> new ArrayList<>()).add(entry);
            if(entry.region().district()!=null) byPath.computeIfAbsent(entry.region().path(),key -> new ArrayList<>()).add(entry);
        }
        byPath.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> url(xml,e.getKey(),latest(e.getValue())));
        xml.append("</urlset>");return response(xml.toString());
    }
    private void url(StringBuilder xml,String path,LocalDate lastmod) {
        xml.append("<url><loc>").append(esc(domain+path)).append("</loc>").append(lastmod(lastmod)).append("</url>");
    }
    static LocalDate latest(List<PublicAnnouncementIndex.Entry> entries) {
        return entries.stream().map(PublicAnnouncementIndex.Entry::updated).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }
    private static String lastmod(LocalDate day) { return day==null?"":"<lastmod>"+day+"</lastmod>"; }
    private static String esc(String value) { return HtmlUtils.htmlEscape(value); }
    private static ResponseEntity<String> response(String xml) { return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(xml); }
}
