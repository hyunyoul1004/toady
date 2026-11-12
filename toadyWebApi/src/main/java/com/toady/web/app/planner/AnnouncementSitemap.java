package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.time.Duration;
import java.util.Map;

/** 공고 URL을 유형별로 나누고 페이지당 5천 건으로 제한한다. 공고 데이터만 공개한다. */
@RestController
public class AnnouncementSitemap {
    private static final Map<String,String> TABLES=Map.of("apt","TB_APT_LTTOT_PBLANC_DETAIL","urban","TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL","remaining","TB_REMNDR_LTTOT_PBLANC_DETAIL","public-rental","TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL","optional","TB_OPT_LTTOT_PBLANC_DETAIL");
    private final JdbcTemplate jdbc; private final String domain;
    public AnnouncementSitemap(JdbcTemplate jdbc,@Value("${toady.web.domain}") String domain) { this.jdbc=jdbc;this.domain=domain.replaceAll("/+$",""); }
    @GetMapping(value="/api/v1/public/sitemap.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> index() {
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><sitemapindex xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        for(var entry:TABLES.entrySet()) {
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+entry.getValue(),Long.class);
            for(long page=0;page<(count+4999)/5000;page++) xml.append("<sitemap><loc>").append(esc(domain+"/sitemaps/"+entry.getKey()+"/"+page+".xml")).append("</loc></sitemap>");
        }
        xml.append("<sitemap><loc>").append(esc(domain+"/sitemaps/areas.xml")).append("</loc></sitemap>");
        xml.append("</sitemapindex>");return response(xml.toString());
    }
    @GetMapping(value="/api/v1/public/sitemaps/{type}/{page}.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> entries(@PathVariable String type,@PathVariable int page) {
        if(!TABLES.containsKey(type) || page<0 || page>10000) return ResponseEntity.notFound().build();
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        // 테이블명은 고정 허용 목록에서만 가져온다. 숫자 페이지도 바인딩한다.
        jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO FROM "+TABLES.get(type)+" ORDER BY HOUSE_MANAGE_NO,PBLANC_NO LIMIT 5000 OFFSET ?",(org.springframework.jdbc.core.RowCallbackHandler)r -> {
            String house=r.getString(1),notice=r.getString(2);
            if(house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}")) xml.append("<url><loc>").append(esc(domain+"/announcements/"+type+"/"+house+"/"+notice)).append("</loc></url>");
        },(long)page*5000);
        xml.append("</urlset>");return response(xml.toString());
    }
    /** 생활권·모음 공개 페이지. 생활권 목록·각 생활권, 무순위·이번 주, 가점 계산기를 포함한다. */
    @GetMapping(value="/api/v1/public/sitemaps/areas.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> areas() {
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        xml.append("<url><loc>").append(esc(domain+"/areas")).append("</loc></url>");
        for(var area:LivingAreas.ALL) xml.append("<url><loc>").append(esc(domain+"/areas/"+area.slug())).append("</loc></url>");
        // 공고 데이터로 자동 생성되는 모음 페이지와 가점 계산기
        for(String path:java.util.List.of("/zupzup","/weekly","/score")) xml.append("<url><loc>").append(esc(domain+path)).append("</loc></url>");
        xml.append("</urlset>");return response(xml.toString());
    }
    private static String esc(String value) { return HtmlUtils.htmlEscape(value); }
    private static ResponseEntity<String> response(String xml) { return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.maxAge(Duration.ofHours(1))).body(xml); }
}
