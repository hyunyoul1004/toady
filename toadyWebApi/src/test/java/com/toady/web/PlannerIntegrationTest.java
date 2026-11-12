package com.toady.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.app.planner.*;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:planneraudit;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    "toady.planner.mail-limit=4", "toady.planner.auth-reserve=1"
})
@AutoConfigureMockMvc
class PlannerIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired MailBudget budget; @Autowired PlannerService planner;
    @MockitoBean EmailUtilService mail;
    @Autowired com.toady.web.app.planner.ScoreBenchmarks benchmarks;
    @BeforeEach void setup() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_FAVORITE (USER_SEQ BIGINT,ANNOUNCEMENT_TYPE VARCHAR(20),HOUSE_MANAGE_NO VARCHAR(40),PBLANC_NO VARCHAR(40),NOTE VARCHAR(2000),CHECKLIST INT,EMAIL_ENABLED BOOLEAN,REG_DT TIMESTAMP,PRIMARY KEY(USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MAIL_BUDGET (ID INT PRIMARY KEY)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_MAIL_ATTEMPT (ID CHAR(36) PRIMARY KEY,MAIL_KIND VARCHAR(10),REG_DT TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_REMINDER_DELIVERY (USER_SEQ BIGINT,SEND_DATE DATE,STATUS VARCHAR(12),UPDATED_AT TIMESTAMP,PRIMARY KEY(USER_SEQ,SEND_DATE))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_CALENDAR_FEED (USER_SEQ BIGINT PRIMARY KEY,FEED_TOKEN CHAR(43) NOT NULL UNIQUE,REG_DT TIMESTAMP NOT NULL,LAST_READ_DT TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_RATE_POLL (ANNOUNCEMENT_ID VARCHAR(110) NOT NULL,VISITOR_ID CHAR(36) NOT NULL,CHOICE TINYINT NOT NULL,REG_DT TIMESTAMP NOT NULL,PRIMARY KEY(ANNOUNCEMENT_ID,VISITOR_ID))");
        for(String table:List.of("TB_RATE_POLL","TB_APT_LTTOT_PBLANC_CMPET","TB_REMNDR_LTTOT_PBLANC_DETAIL","TB_CALENDAR_FEED","TB_FAVORITE","TB_MAIL_ATTEMPT","TB_MAIL_BUDGET","TB_REMINDER_DELIVERY","TB_CERTIFY","TB_USER","TB_APT_LTTOT_PBLANC_SCORE","TB_APT_LTTOT_PBLANC_DETAIL")) jdbc.update("DELETE FROM "+table);
        jdbc.update("INSERT INTO TB_MAIL_BUDGET VALUES (1)");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P1','N1','<운정 테스트>','경기도 파주시 동패동','2026-11-12','2026-11-13','2026-11-20')");
        org.mockito.Mockito.reset(mail);
        com.toady.web.app.planner.PublicPagesCache.clear();
        com.toady.web.app.planner.PublicPagesCache.clear(benchmarks);
    }
    UserEntity user(String email) {
        return users.save(UserEntity.builder().userEmail(email).userNicName(email.substring(0,email.indexOf('@'))).userPassword(passwords.encode("Password123!"))
                .emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
    }
    String token(String email) throws Exception {
        String body=mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userEmail",email,"userPassword","Password123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+json.readTree(body).path("data").path("accessToken").asText();
    }
    static String feedToken(String body) { var matcher=java.util.regex.Pattern.compile("/calendar/([A-Za-z0-9_-]{43})\\.ics").matcher(body); assertTrue(matcher.find(),body); return matcher.group(1); }
    @Test void calendarFeedIsPrivateRotatableAndStopsForWithdrawnAccounts() throws Exception {
        UserEntity owner=user("feed@example.test");String auth=token("feed@example.test");
        mvc.perform(get("/api/v1/me/calendar-feed")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/calendar-feed").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.subscribed").value(false));
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization",auth).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"비공개 메모\",\"checklist\":0,\"emailEnabled\":false}")).andExpect(status().isOk());
        String first=feedToken(mvc.perform(post("/api/v1/me/calendar-feed").header("Authorization",auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.webcalUrl").value(org.hamcrest.Matchers.startsWith("webcal://")))
                .andExpect(jsonPath("$.data.googleUrl").value(org.hamcrest.Matchers.startsWith("https://calendar.google.com/calendar/r?cid=webcal%3A%2F%2F")))
                .andReturn().getResponse().getContentAsString());
        String ics=mvc.perform(get("/api/v1/public/calendar/"+first+".ics")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type",org.hamcrest.Matchers.startsWith("text/calendar")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n ","");
        assertTrue(ics.contains("SUMMARY:<운정 테스트> · "),ics);
        assertFalse(ics.contains("비공개 메모"),"메모는 캘린더로 내보내지 않는다");
        assertNotNull(jdbc.queryForObject("SELECT LAST_READ_DT FROM TB_CALENDAR_FEED",java.sql.Timestamp.class));
        String second=feedToken(mvc.perform(post("/api/v1/me/calendar-feed").header("Authorization",auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertNotEquals(first,second);
        mvc.perform(get("/api/v1/public/calendar/"+first+".ics")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/calendar/"+second+".ics")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/calendar/short.ics")).andExpect(status().isNotFound());
        owner.setWithdrawalYn("Y");users.save(owner);
        mvc.perform(get("/api/v1/public/calendar/"+second+".ics")).andExpect(status().isNotFound());
        owner.setWithdrawalYn("N");users.save(owner);
        mvc.perform(delete("/api/v1/me/calendar-feed").header("Authorization",auth)).andExpect(status().isOk()).andExpect(jsonPath("$.data.subscribed").value(false));
        mvc.perform(get("/api/v1/public/calendar/"+second+".ics")).andExpect(status().isNotFound());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM TB_CALENDAR_FEED",Integer.class));
    }
    @Test void livingAreaAndAnnouncementPagesShareTheSameAreaRule() throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P2','N2','운정 예정 단지','경기도 파주시 야당동 10',?,?,?)",
                today.plusDays(5).toString(),today.plusDays(7).toString(),today.plusDays(14).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P3','N3','강남 단지','서울특별시 강남구 역삼동 1',?,?,?)",
                today.plusDays(5).toString(),today.plusDays(7).toString(),today.plusDays(14).toString());
        String area=mvc.perform(get("/api/v1/public/areas/unjeong")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(area.contains("운정 청약 일정"));
        assertTrue(area.contains("/announcements/apt/P2/N2"),"주소의 동 이름으로 생활권 공고를 모은다");
        assertFalse(area.contains("/announcements/apt/P3/N3"));
        assertTrue(area.contains("/?area=unjeong&amp;month="+today.plusDays(5).toString().substring(0,7)),"앱 이동 시 가장 이른 접수월을 넘긴다");
        mvc.perform(get("/api/v1/public/areas/unknown")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/areas")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/areas/dongtan")));
        String detail=mvc.perform(get("/api/v1/public/announcements/apt/P2/N2")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(detail.contains("/areas/unjeong") && detail.contains("운정 생활권"),detail);
        assertTrue(detail.contains("data-app"),"공개 페이지에서 앱으로 갈 때 유입처를 넘길 링크를 표시한다");
        mvc.perform(get("/api/v1/public/sitemaps/areas.xml")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/areas/gwanggyo")));
    }
    static String html(org.springframework.test.web.servlet.ResultActions result) throws Exception { return result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8); }
    @Test void areaCalendarIsPublicContainsOnlyThatAreaAndHasNoAlarms() throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('C1','N1','운정 구독 단지','경기도 파주시 와동동 1',?,?,?)",
                today.plusDays(3).toString(),today.plusDays(4).toString(),today.plusDays(10).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('C2','N2','동탄 단지','경기도 화성시 청계동 1',?,?,?)",
                today.plusDays(3).toString(),today.plusDays(4).toString(),today.plusDays(10).toString());
        String ics=html(mvc.perform(get("/api/v1/public/areas/unjeong/calendar.ics")).andExpect(status().isOk())
                .andExpect(header().string("Content-Type",org.hamcrest.Matchers.startsWith("text/calendar")))).replace("\r\n ","");
        assertTrue(ics.contains("X-WR-CALNAME:TOADY 운정 청약") && ics.contains("SUMMARY:운정 구독 단지"),ics);
        assertFalse(ics.contains("동탄 단지"),"다른 생활권 공고는 넣지 않는다");
        assertFalse(ics.contains("BEGIN:VALARM"),"여러 공고가 들어오는 공개 구독에는 알림을 붙이지 않는다");
        mvc.perform(get("/api/v1/public/areas/unknown/calendar.ics")).andExpect(status().isNotFound());
        String page=html(mvc.perform(get("/api/v1/public/areas/unjeong")).andExpect(status().isOk()));
        assertTrue(page.contains("webcal://") && page.contains("/calendar/areas/unjeong.ics") && page.contains("calendar.google.com"),"생활권 페이지에 구독 버튼을 둔다");
    }
    @Test void scoreBenchmarksGroupPublishedScoresByAnnouncementAndArea() throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE) VALUES ('S1','N1','운정 가점 단지','경기도 파주시 동패동 1',?)",today.minusMonths(2).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE) VALUES ('S2','N2','오래된 단지','경기도 파주시 동패동 2',?)",today.minusYears(4).toString().replace("-",""));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_SCORE (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,RESIDE_SECD,LWET_SCORE,TOP_SCORE) VALUES ('S1','N1','01','01','52','69'),('S1','N1','02','01','47','74'),('S1','N1','03','02','-','0'),('S2','N2','01','01','30','40')");
        String body=html(mvc.perform(get("/api/v1/public/score-benchmarks")).andExpect(status().isOk()));
        var tree=json.readTree(body);
        var unjeong=java.util.stream.StreamSupport.stream(tree.path("areas").spliterator(),false).filter(a->a.path("slug").asText().equals("unjeong")).findFirst().orElseThrow();
        assertEquals(1,unjeong.path("items").size(),"3년보다 오래된 공고는 빼고 공고별로 한 줄로 모은다");
        assertEquals(47.0,unjeong.path("items").get(0).path("low").asDouble());
        assertEquals(74.0,unjeong.path("items").get(0).path("high").asDouble());
        assertEquals("apt/S1/N1",tree.path("recent").get(0).path("id").asText());
        var found=json.readTree(html(mvc.perform(get("/api/v1/public/score-benchmarks/search").param("sido","경기").param("district","파주시")).andExpect(status().isOk())));
        assertEquals("경기 파주시",found.path("scope").asText());
        assertEquals(0,found.path("years").asInt(),"최근 3년 1건뿐이라 전체 기간으로 넓힌다");
        assertEquals(2,found.path("total").asInt());
        var regions=json.readTree(html(mvc.perform(get("/api/v1/public/score-benchmarks/regions")).andExpect(status().isOk())));
        assertEquals("경기",regions.get(0).path("sido").asText());
        assertEquals("파주시",regions.get(0).path("districts").get(0).path("name").asText());
    }
    @Test void unrankedAndWeeklyPagesAreGeneratedFromAnnouncements() throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_RCEPT_BGNDE,SUBSCRPT_RCEPT_ENDDE) VALUES ('R1','N1','<줍줍 단지>','서울특별시 강동구 1',?,?)",today.toString(),today.plusDays(1).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('W1','N1','이번주 발표 단지','서울특별시 송파구 1',?,?,?)",
                today.minusDays(20).toString(),today.minusDays(18).toString(),today.toString());
        String unranked=html(mvc.perform(get("/api/v1/public/zupzup")).andExpect(status().isOk()));
        assertTrue(unranked.contains("무순위·줍줍 청약 일정") && unranked.contains("/announcements/remaining/R1/N1"),unranked);
        assertTrue(unranked.contains("&lt;줍줍 단지&gt;") && !unranked.contains("<줍줍 단지>"),"단지명은 이스케이프한다");
        assertFalse(unranked.contains("이번주 발표 단지"),"무순위 페이지에는 일반 아파트 공고를 넣지 않는다");
        assertTrue(unranked.contains("/?focus=unranked"));
        String weekly=html(mvc.perform(get("/api/v1/public/weekly")).andExpect(status().isOk()));
        assertTrue(weekly.contains("이번 주 청약 일정") && weekly.contains("/announcements/remaining/R1/N1"),weekly);
        assertTrue(weekly.contains("/announcements/apt/W1/N1?tabType=rate"),"이번 주 발표 공고는 경쟁률 탭으로 연결한다");
        // 이번 주에 마감된 공고가 12건을 넘어도 접수 건수를 자르지 않는다(생활권 페이지의 최근 마감 12건 제한과 별개).
        java.time.LocalDate monday=today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        for(int i=0;i<15;i++) jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES (?,?,?,?,?,?)",
                "K"+i,"N1","주간 단지 "+i,"서울특별시 강서구 "+i,monday.minusDays(60).toString(),monday.toString());
        com.toady.web.app.planner.PublicPagesCache.clear();
        String many=html(mvc.perform(get("/api/v1/public/weekly")).andExpect(status().isOk()));
        for(int i=0;i<15;i++) assertTrue(many.contains("/announcements/apt/K"+i+"/N1"),"60일 전에 시작해 이번 주까지 접수하는 공고도 모두 넣는다: K"+i);
        String sitemap=html(mvc.perform(get("/api/v1/public/sitemaps/areas.xml")).andExpect(status().isOk()));
        assertTrue(sitemap.contains("/zupzup") && sitemap.contains("/weekly") && sitemap.contains("/score"));
    }
    @Test void ratePollAcceptsOneChangeableVoteUntilCloseAndRevealsActualBucket() throws Exception {
        java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('V1','N1','투표 단지','서울특별시 송파구 1',?,?)",today.toString(),today.plusDays(2).toString());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('V2','N2','마감 단지','서울특별시 송파구 2',?,?)",today.minusDays(9).toString(),today.minusDays(7).toString());
        String a=UUID.randomUUID().toString(),b=UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/polls/rate/apt/V1/N1").param("visitorId",a)).andExpect(status().isOk()).andExpect(jsonPath("$.open").value(true)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/polls/rate/apt/V1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\""+a+"\",\"choice\":2}")).andExpect(status().isOk()).andExpect(jsonPath("$.myChoice").value(2));
        mvc.perform(post("/api/v1/polls/rate/apt/V1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\""+a+"\",\"choice\":4}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.counts[4]").value(1)).andExpect(jsonPath("$.counts[2]").value(0));
        mvc.perform(post("/api/v1/polls/rate/apt/V1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\""+b+"\",\"choice\":6}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/polls/rate/apt/V1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\""+b+"\",\"choice\":5}")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/polls/rate/apt/V1/N1")).andExpect(status().isOk()).andExpect(jsonPath("$.myChoice").doesNotExist());
        mvc.perform(post("/api/v1/polls/rate/apt/V2/N2").contentType(MediaType.APPLICATION_JSON).content("{\"visitorId\":\""+a+"\",\"choice\":1}")).andExpect(status().isConflict());
        // 경쟁률이 공개되면 투표를 닫고 실제 구간을 알려 준다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_CMPET (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,SUBSCRPT_RANK_CODE,RESIDE_SECD,CMPET_RATE) VALUES ('V1','N1','01','1','01','45.2'),('V1','N1','02','1','01','(△3)'),('V1','N1','01','2','01','300')");
        mvc.perform(get("/api/v1/polls/rate/apt/V1/N1").param("visitorId",a)).andExpect(status().isOk()).andExpect(jsonPath("$.open").value(false))
                .andExpect(jsonPath("$.actualRate").value(45.2)).andExpect(jsonPath("$.actualChoice").value(4)).andExpect(jsonPath("$.myChoice").value(4));
        mvc.perform(get("/api/v1/polls/rate/apt/NOPE/N1")).andExpect(status().isNotFound());
    }
    @Test void favoritesArePrivateValidatedAndUseAuthoritativeSummary() throws Exception {
        user("owner@example.test");user("other@example.test");String owner=token("owner@example.test"),other=token("other@example.test");
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"비공개 조사\",\"checklist\":5,\"emailEnabled\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/favorites").header("Authorization",owner)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].summary.houseNm").value("<운정 테스트>"));
        mvc.perform(get("/api/v1/me/favorites").header("Authorization",other)).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(delete("/api/v1/me/favorites/apt/P1/N1").header("Authorization",other)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
        long ownerId=users.findByUserEmail("owner@example.test").getUserSeq();
        planner.save(ownerId,"apt","P1","N1","비공개 조사",5,true); // 동일 값 재저장도 새 INSERT가 아니다.
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization",owner).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\",\"checklist\":32}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/favorites/disable-emails").header("Authorization",owner)).andExpect(status().isOk());
        assertFalse(jdbc.queryForObject("SELECT EMAIL_ENABLED FROM TB_FAVORITE",Boolean.class));
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isUnauthorized());
    }

    @Test void publicRequestsAndAuthenticationErrorsNeverChallengeTheBrowserForBasicCredentials() throws Exception {
        mvc.perform(get("/api/v1/planner/settings").accept(MediaType.ALL))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/v1/announcements").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
        mvc.perform(get("/api/v1/me/favorites").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resultCode").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/v1/planner/settings").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(jsonPath("$.resultCode").value("AUTH_REQUIRED"));
        mvc.perform(get("/api/v1/planner/settings").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("WWW-Authenticate"));
    }
    @Test void livingAreaSearchFindsAddressWithoutDistrictWideFalseMatchAndHtmlIsEscaped() throws Exception {
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE) VALUES ('P2','N2','파주 다른 공고','경기도 파주시 문산읍','2026-11-12')");
        mvc.perform(get("/api/v1/announcements/suggestions").param("keyword","운정역")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(get("/api/v1/announcements").param("keyword","운정")).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        String html=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/v1/public/sitemaps/apt/0.xml")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/sitemap.xml")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/planner/summaries").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[\"apt/P1/N1\"]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].houseNm").value("<운정 테스트>"));
        mvc.perform(post("/api/v1/planner/summaries").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[null]}"))
            .andExpect(status().isBadRequest());
        assertTrue(html.contains("&lt;운정 테스트&gt;"));assertFalse(html.contains("<운정 테스트>"));assertTrue(html.contains("rel='canonical'"));
    }
    @Test void allPublicAccountEndpointsRejectInvalidInputWithoutBrowserChallenge() throws Exception {
        for(String path:List.of("/webApi/user/regUser","/webApi/user/login","/webApi/certify/certify-check","/webApi/certify/resend-account-verification","/webApi/certify/find-password-request-certify","/webApi/certify/certify-confrim-and-password-change")) {
            mvc.perform(post(path).accept(MediaType.TEXT_HTML,MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError()).andExpect(header().doesNotExist("WWW-Authenticate"));
        }
    }
    @Test void shareMetadataAndPngDependOnAnnouncementAndSelectedTab() throws Exception {
        String detail=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String rate=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(detail.contains("청약 일정·분양 정보"));assertTrue(rate.contains("청약 경쟁률"));
        assertTrue(rate.contains("?tabType=rate"));assertTrue(rate.contains("twitter:card"));assertTrue(rate.contains("summary_large_image"));
        assertTrue(rate.contains("announcement-images/apt/P1/N1.png?tabType=rate&amp;v=2"));
        assertFalse(rate.contains("<운정 테스트>"));
        byte[] image=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var decoded=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(image));
        assertEquals(1200,decoded.getWidth());assertEquals(630,decoded.getHeight());
        mvc.perform(get("/api/v1/public/announcement-images/apt/missing/missing.png")).andExpect(status().isNotFound());
        String unknown=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","<script>oops</script>")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(unknown.contains("oops"));
    }
    @Test void sharedCompetitionMetadataAndImageIncludeOnlyPublishedScoreRange() throws Exception {
        byte[] before=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andReturn().getResponse().getContentAsByteArray();
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_SCORE (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,RESIDE_SECD,LWET_SCORE,TOP_SCORE,AVRG_SCORE) VALUES ('P1','N1','01','01','56','74','65')");
        String html=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("청약 경쟁률·당첨가점"));
        assertTrue(html.contains("최저 56점 / 최고 74점"));
        assertTrue(html.contains("공개 주택형·지역 전체 기준"));
        byte[] after=mvc.perform(get("/api/v1/public/announcement-images/apt/P1/N1.png").param("tabType","rate")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertFalse(java.util.Arrays.equals(before,after));
        String detail=mvc.perform(get("/api/v1/public/announcements/apt/P1/N1")).andReturn().getResponse().getContentAsString();
        assertFalse(detail.contains("최저 56점"));
    }
    @Test void rollingQuotaIsAtomicAndReservesAuthenticationCapacity() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<6;i++) tasks.add(()->budget.reserve(true));
            int accepted=0;for(Future<Boolean> result:pool.invokeAll(tasks)) if(result.get()) accepted++;
            assertEquals(3,accepted);assertTrue(budget.reserve(false));assertFalse(budget.reserve(false));
            jdbc.update("UPDATE TB_MAIL_ATTEMPT SET REG_DT=?",java.sql.Timestamp.valueOf(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(25)));
            assertTrue(budget.reserve(false));
        } finally {pool.shutdownNow();}
    }
    @Test void missingAnnouncementCannotBeSaved() throws Exception {
        var account=user("missing@example.test");
        assertThrows(jakarta.persistence.EntityNotFoundException.class,()->planner.save(account.getUserSeq(),"apt","missing","missing","",0,false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM TB_FAVORITE",Integer.class));
    }
    @Test void reminderBundlesEventsAndDoesNotSendTwiceOrAfterOptOut() throws Exception {
        var account=user("reminder@example.test");
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET RCEPT_BGNDE=?,RCEPT_ENDDE=?",today.toString(),today.plusDays(1).toString());
        planner.save(account.getUserSeq(),"apt","P1","N1","개인 메모는 이메일에 넣지 않음",3,true);
        var reminder=new PlannerReminder(jdbc,planner,users,mail,"https://www.toady.co.kr");
        reminder.send();reminder.send();
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(1)).sendReminder(org.mockito.ArgumentMatchers.eq("reminder@example.test"),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.argThat(body -> body.contains("접수 시작") && body.contains("접수 마감") && !body.contains("개인 메모")));
        assertEquals("SENT",jdbc.queryForObject("SELECT STATUS FROM TB_REMINDER_DELIVERY",String.class));
        jdbc.update("DELETE FROM TB_REMINDER_DELIVERY");planner.disableEmails(account.getUserSeq());reminder.send();
        org.mockito.Mockito.verifyNoMoreInteractions(mail);
    }
    @Test void reminderQuotaDefersBeforeSendAndAmbiguousSmtpFailureDoesNotRetry() throws Exception {
        var account=user("quota@example.test");
        var today=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET RCEPT_BGNDE=?",today.toString());
        planner.save(account.getUserSeq(),"apt","P1","N1","",0,true);
        var reminder=new PlannerReminder(jdbc,planner,users,mail,"https://www.toady.co.kr");
        org.mockito.Mockito.doThrow(new MailQuotaException()).when(mail).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        reminder.send();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM TB_REMINDER_DELIVERY",Integer.class));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("unknown")).when(mail).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
        reminder.send();reminder.send();
        assertEquals("UNKNOWN",jdbc.queryForObject("SELECT STATUS FROM TB_REMINDER_DELIVERY",String.class));
        org.mockito.Mockito.verify(mail,org.mockito.Mockito.times(2)).sendReminder(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());
    }

}
