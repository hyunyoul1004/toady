package com.toady.web.app.planner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 공고 URL을 유형별로 나누고 페이지당 5천 건으로 제한한다. 공고 데이터만 공개한다.
 * 사이트맵에는 접수 마감(없으면 모집공고일)이 최근 recent-days 이내이거나 아직 오지 않은 공고만 넣는다.
 * 몇 년 지난 공고 수천 건을 함께 알리면 검색엔진이 비슷한 페이지로 보고 대부분 색인을 미룬다.
 * 지난 공고 페이지는 그대로 열려 있어 링크로는 들어올 수 있다.
 */
@RestController
public class AnnouncementSitemap {
    private static final Map<String,String> TABLES=Map.of("apt","TB_APT_LTTOT_PBLANC_DETAIL","urban","TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL","remaining","TB_REMNDR_LTTOT_PBLANC_DETAIL","public-rental","TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL","optional","TB_OPT_LTTOT_PBLANC_DETAIL");
    /** 유형별 접수 마감 컬럼. APT는 RCEPT_ENDDE, 나머지는 SUBSCRPT_RCEPT_ENDDE. 날짜 형식(YYYY-MM-DD·YYYYMMDD)은 '-'를 빼 맞춘다. */
    private static final Map<String,String> END_COLUMNS=Map.of("apt","RCEPT_ENDDE","urban","SUBSCRPT_RCEPT_ENDDE","remaining","SUBSCRPT_RCEPT_ENDDE","public-rental","SUBSCRPT_RCEPT_ENDDE","optional","SUBSCRPT_RCEPT_ENDDE");
    private static final DateTimeFormatter DAY=DateTimeFormatter.BASIC_ISO_DATE;
    private final JdbcTemplate jdbc; private final String domain; private final int recentDays; private final Clock clock;
    /** 운영자가 숨긴 공고는 알리지 않는다(database.sql 26번). 단위 테스트에서 직접 만들면 없을 수 있다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    com.toady.web.app.operation.AnnouncementOverrides overrides;
    @org.springframework.beans.factory.annotation.Autowired
    public AnnouncementSitemap(JdbcTemplate jdbc,@Value("${toady.web.domain}") String domain,@Value("${toady.sitemap.recent-days:365}") int recentDays) {
        this(jdbc,domain,recentDays,Clock.system(ZoneId.of("Asia/Seoul")));
    }
    AnnouncementSitemap(JdbcTemplate jdbc,String domain,int recentDays,Clock clock) {
        this.jdbc=jdbc;this.domain=domain.replaceAll("/+$","");this.recentDays=Math.max(30,recentDays);this.clock=clock;
    }
    /** 기준일(YYYYMMDD 문자열 비교) 이후로 끝나는 공고만 고르는 조건. 컬럼명은 고정 목록에서만 온다. */
    private static String recent(String type) {
        return " WHERE REPLACE(COALESCE(NULLIF("+END_COLUMNS.get(type)+",''),RCRIT_PBLANC_DE),'-','') >= ?";
    }
    private String since() { return LocalDate.now(clock).minusDays(recentDays).format(DAY); }
    @GetMapping(value="/api/v1/public/sitemap.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> index() {
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><sitemapindex xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        for(var entry:TABLES.entrySet()) {
            long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+entry.getValue()+recent(entry.getKey()),Long.class,since());
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
        jdbc.query("SELECT HOUSE_MANAGE_NO,PBLANC_NO FROM "+TABLES.get(type)+recent(type)+" ORDER BY HOUSE_MANAGE_NO,PBLANC_NO LIMIT 5000 OFFSET ?",(RowCallbackHandler)r -> {
            String house=r.getString(1),notice=r.getString(2);
            if(overrides!=null && overrides.hidden(type.toUpperCase().replace('-','_'),house,notice)) return;
            if(house.matches("[a-zA-Z0-9_-]{1,40}") && notice.matches("[a-zA-Z0-9_-]{1,40}")) xml.append("<url><loc>").append(esc(domain+"/announcements/"+type+"/"+house+"/"+notice)).append("</loc></url>");
        },since(),(long)page*5000);
        xml.append("</urlset>");return response(xml.toString());
    }
    /** 홈과 생활권·모음 공개 페이지. 홈, 생활권 목록·각 생활권, 무순위·이번 주, 가점 계산기, 맞춤 청약, 자주 묻는 질문을 포함한다. */
    @GetMapping(value="/api/v1/public/sitemaps/areas.xml",produces="application/xml;charset=UTF-8")
    public ResponseEntity<String> areas() {
        StringBuilder xml=new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><urlset xmlns='http://www.sitemaps.org/schemas/sitemap/0.9'>");
        // 홈(청약 캘린더)을 맨 앞에 둔다. /sitemap.xml이 이 색인을 가리키므로 홈도 여기서 알려야 한다.
        xml.append("<url><loc>").append(esc(domain+"/")).append("</loc></url>");
        xml.append("<url><loc>").append(esc(domain+"/areas")).append("</loc></url>");
        for(var area:LivingAreas.all()) xml.append("<url><loc>").append(esc(domain+"/areas/"+area.slug())).append("</loc></url>");
        // 공고 데이터로 자동 생성되는 모음 페이지와 가점 계산기. /match/zupzup은 /zupzup과 같은 내용이라 공개 주소(/zupzup) 하나만 알린다.
        for(String path:List.of("/zupzup","/weekly","/score","/match","/help/faq")) xml.append("<url><loc>").append(esc(domain+path)).append("</loc></url>");
        xml.append("</urlset>");return response(xml.toString());
    }
    private static String esc(String value) { return HtmlUtils.htmlEscape(value); }
    private static ResponseEntity<String> response(String xml) { return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1))).body(xml); }
}
