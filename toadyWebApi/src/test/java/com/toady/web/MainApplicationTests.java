package com.toady.web;

import com.toady.web.app.announcement.AnnouncementType;
import com.toady.web.core.security.jwt.JwtProvider;
import com.toady.web.core.security.service.CustomUserDetails;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.MailAuthenticationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.doThrow;

/** Spring 애플리케이션 컨텍스트의 기본 기동을 확인합니다. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:toady;MODE=MariaDB;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class MainApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private EmailUtilService emailUtilService;

    @Test
    void applicationContextLoads() {
    }

    @Test
    void parsesAnnouncementPathTypes() {
        assertEquals(AnnouncementType.PUBLIC_RENTAL, AnnouncementType.fromPath("public-rental"));
        assertThrows(IllegalArgumentException.class, () -> AnnouncementType.fromPath("unknown"));
    }

    @Test
    void createsAndReadsJava21CompatibleJwt() {
        CustomUserDetails user = new CustomUserDetails(
                "member@example.test", "test-password-hash", List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        jdbcTemplate.update("""
                insert into TB_USER (USER_EMAIL, USER_PASSWORD, USER_NIC_NAME, EMAIL_CERTIFY_YN, LOCK_YN, WITHDRAWAL_YN)
                values ('member@example.test', 'test-password-hash', 'member', 'Y', 'N', 'N')
                """);
        user.setUserSeq(jdbcTemplate.queryForObject(
                "select USER_SEQ from TB_USER where USER_EMAIL = 'member@example.test'", Long.class));
        user.setUserNicName("member");
        var authentication = new UsernamePasswordAuthenticationToken(
                user, "", user.getAuthorities()
        );

        String token = jwtProvider.generatorToken(authentication);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);

        var decoded = jwtProvider.getAuthentication(request);
        assertEquals("member@example.test", decoded.getName());
        assertFalse(jwtProvider.getJwtExpiryRenewalCheck(request, decoded));
    }

    @Test
    void registrationReturnsFailureAndRollsBackWhenVerificationEmailCannotBeSent() throws Exception {
        int certifyCountBefore = jdbcTemplate.queryForObject("select count(*) from TB_CERTIFY", Integer.class);
        doThrow(new MailAuthenticationException("SMTP authentication failed"))
                .when(emailUtilService)
                .sendEmail(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );

        mockMvc.perform(post("/webApi/user/regUser")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userEmail": "mail-failure@example.test",
                                  "userPassword": "Passw0rd!",
                                  "userPasswordConfirm": "Passw0rd!",
                                  "userNicName": "mailfailure"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.resultCode").value("ERR-999"))
                .andExpect(jsonPath("$.resultMessage").value(
                        "인증 이메일을 발송하지 못했습니다. 메일 설정을 확인한 후 다시 시도해 주세요."
                ));

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from TB_USER where USER_EMAIL = ?", Integer.class, "mail-failure@example.test"
        ));
        assertEquals(certifyCountBefore, jdbcTemplate.queryForObject("select count(*) from TB_CERTIFY", Integer.class));
    }

    @Test
    void servesAnnouncementAndCommentReadEndpoints() throws Exception {
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_AREA_CODE, RCRIT_PBLANC_DE, RCEPT_BGNDE)
                values (?, ?, ?, ?, ?, ?)
                """, "TEST-HOUSE", "TEST-PBLANC", "테스트 공고", "11", "2026-10-01", "2026-10-02");
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_AREA_CODE, RCRIT_PBLANC_DE, RCEPT_BGNDE)
                values (?, ?, ?, ?, ?, ?)
                """, "TEST-HOUSE-2", "TEST-PBLANC-2", "테스트 공고 늦은 접수", "11", "2026-09-01", "2026-10-04");

        mockMvc.perform(get("/api/v1/apt/announcements")
                        .queryParam("regionCode", "11")
                        .queryParam("keyword", "테스트")
                        .queryParam("startDate", "2026-10-01")
                        .queryParam("endDate", "2026-10-03")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].houseNm").value("테스트 공고"));

        mockMvc.perform(get("/api/v1/apt/announcements")
                        .queryParam("keyword", "테스트 공고")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].houseManageNo").value("TEST-HOUSE-2"))
                .andExpect(jsonPath("$.data.content[0].rceptBgnde").value("2026-10-04"))
                .andExpect(jsonPath("$.data.content[0].publicationDate").doesNotExist());

        mockMvc.perform(get("/api/v1/apt/announcements/TEST-HOUSE/TEST-PBLANC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.announcement.houseNm").value("테스트 공고"))
                .andExpect(jsonPath("$.data.models").isArray());

        mockMvc.perform(get("/api/v1/apt/announcements/TEST-HOUSE/TEST-PBLANC/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void retrievesCompetitionScoresAndSpecialSupplyStatusFromSchedulerTables() throws Exception {
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, SUBSCRPT_RANK_CODE, RESIDE_SECD,
                     HOUSE_TY, SUPLY_HSHLDCO, RESIDE_SENM, REQ_CNT, CMPET_RATE)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "RATE-APT", "RATE-APT", "01", "1", "01", "84A", "10", "서울", "125", "12.5");
        jdbcTemplate.update("""
                insert into TB_URBTY_OFCTL_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, HOUSE_TY, REQ_CNT, CMPET_RATE)
                values (?, ?, ?, ?, ?, ?)
                """, "RATE-URBAN", "RATE-URBAN", "01", "59A", "50", "5.0");
        jdbcTemplate.update("""
                insert into TB_PBL_PVT_RENT_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, SPSPLY_KND_CODE, HOUSE_TY, REQ_CNT, CMPET_RATE)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "RATE-RENTAL", "RATE-RENTAL", "01", "01", "84A", "40", "4.0");
        jdbcTemplate.update("""
                insert into TB_CANC_RESPL_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, HOUSE_TY, NORMAL_REQ_CNT, NORMAL_CMPET_RATE)
                values (?, ?, ?, ?, ?, ?)
                """, "RATE-CANCEL", "RATE-CANCEL", "01", "84A", "25", "2.5");
        jdbcTemplate.update("""
                insert into TB_REMNDR_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY, REMNDR_HSHLD_PBLANC_TYCD, REQ_CNT, CMPET_RATE)
                values (?, ?, ?, ?, ?, ?)
                """, "RATE-REMAIN", "RATE-REMAIN", "84A", "01", "20", "2.0");
        jdbcTemplate.update("""
                insert into TB_OPT_LTTOT_PBLANC_CMPET
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY, REQ_CNT, CMPET_RATE)
                values (?, ?, ?, ?, ?)
                """, "RATE-OPTIONAL", "RATE-OPTIONAL", "84A", "15", "1.5");
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_SCORE
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, RESIDE_SECD, HOUSE_TY,
                     RESIDE_SENM, LWET_SCORE, TOP_SCORE, AVRG_SCORE)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "RATE-APT", "RATE-APT", "01", "01", "84A", "서울", "50", "70", "60");
        jdbcTemplate.update("""
                insert into TB_APT_SPSPLY_REQST_STUS
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_TY, SPSPLY_HSHLDCO, MNYCH_HSHLDCO,
                     NWWDS_NMTW_HSHLDCO, SUBSCRPT_RESULT_NM)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "RATE-APT", "RATE-APT", "84A", "20", "5", "10", "접수");

        assertCompetitionRate("apt", "RATE-APT", "12.5");
        assertCompetitionRate("urban", "RATE-URBAN", "5.0");
        assertCompetitionRate("public-rental", "RATE-RENTAL", "4.0");
        assertCompetitionRate("cancel-resupply", "RATE-CANCEL", null);
        assertCompetitionRate("remaining", "RATE-REMAIN", "2.0");
        assertCompetitionRate("optional", "RATE-OPTIONAL", "1.5");

        mockMvc.perform(get("/api/v1/apt/announcements/RATE-APT/RATE-APT/scores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].houseTy").value("84A"))
                .andExpect(jsonPath("$.data[0].lwetScore").value("50"))
                .andExpect(jsonPath("$.data[0].topScore").value("70"))
                .andExpect(jsonPath("$.data[0].avrgScore").value("60"));

        mockMvc.perform(get("/api/v1/apt/announcements/RATE-APT/RATE-APT/special-supply-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].houseTy").value("84A"))
                .andExpect(jsonPath("$.data[0].spsplyHshldco").value("20"))
                .andExpect(jsonPath("$.data[0].subscrptResultNm").value("접수"));
    }

    @Test
    void suggestsAnnouncementsAcrossAllTypesAndMatchesAddresses() throws Exception {
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, HOUSE_SECD_NM, SUBSCRPT_AREA_CODE_NM,
                     HSSPLY_ADRES, RCEPT_BGNDE, RCEPT_ENDDE)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, "SUGGEST-APT", "SUGGEST-APT", "Suggestion 아파트", "민영", "서울",
                "ADDRESSMATCH 서울특별시", "2026-10-04", "2026-10-06");
        jdbcTemplate.update("""
                insert into TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_RCEPT_BGNDE, SUBSCRPT_RCEPT_ENDDE)
                values (?, ?, ?, ?, ?)
                """, "SUGGEST-URBAN", "SUGGEST-URBAN", "Suggestion 오피스텔", "2026-10-05", "2026-10-05");
        jdbcTemplate.update("""
                insert into TB_REMNDR_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_RCEPT_BGNDE, SUBSCRPT_RCEPT_ENDDE)
                values (?, ?, ?, ?, ?)
                """, "SUGGEST-REMAINING", "SUGGEST-REMAINING", "Suggestion 잔여세대", "2026-10-03", "2026-10-03");
        jdbcTemplate.update("""
                insert into TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_RCEPT_BGNDE, SUBSCRPT_RCEPT_ENDDE)
                values (?, ?, ?, ?, ?)
                """, "SUGGEST-RENTAL", "SUGGEST-RENTAL", "Suggestion 민간임대", "2026-10-04", "2026-10-04");
        jdbcTemplate.update("""
                insert into TB_OPT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, SUBSCRPT_RCEPT_BGNDE, SUBSCRPT_RCEPT_ENDDE)
                values (?, ?, ?, ?, ?)
                """, "SUGGEST-OPTIONAL", "SUGGEST-OPTIONAL", "Suggestion 임의공급", "20261002", "20261002");

        mockMvc.perform(get("/api/v1/announcements/suggestions")
                        .queryParam("keyword", "suggest")
                        .queryParam("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].type").value("urban"))
                .andExpect(jsonPath("$.data[1].type").value("apt"))
                .andExpect(jsonPath("$.data[1].houseManageNo").value("SUGGEST-APT"))
                .andExpect(jsonPath("$.data[1].houseSecdNm").value("민영"))
                .andExpect(jsonPath("$.data[1].subscrptAreaCodeNm").value("서울"))
                .andExpect(jsonPath("$.data[1].rceptBgnde").value("2026-10-04"))
                .andExpect(jsonPath("$.data[2].type").value("public-rental"))
                .andExpect(jsonPath("$.data[3].type").value("remaining"))
                .andExpect(jsonPath("$.data[4].type").value("optional"));

        mockMvc.perform(get("/api/v1/announcements/suggestions")
                        .queryParam("keyword", "ADDRESSMATCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].hssplyAdres").value("ADDRESSMATCH 서울특별시"));

        mockMvc.perform(get("/api/v1/announcements/suggestions")
                        .queryParam("keyword", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void assertCompetitionRate(String type, String announcementId, String rate) throws Exception {
        var result = mockMvc.perform(get(
                        "/api/v1/" + type + "/announcements/" + announcementId + "/" + announcementId
                                + "/competition-rates"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        if (rate != null) {
            result.andExpect(jsonPath("$.data[0].cmpetRate").value(rate));
        } else {
            result.andExpect(jsonPath("$.data[0].normalCmpetRate").value("2.5"));
        }
    }

    @Test
    void searchesAnnouncementsAcrossAllTypesInOneNewestFirstPage() throws Exception {
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, HOUSE_SECD, HOUSE_SECD_NM,
                     HSSPLY_ADRES, HSSPLY_ZIP, SUBSCRPT_AREA_CODE, SUBSCRPT_AREA_CODE_NM,
                     RCRIT_PBLANC_DE, RCEPT_BGNDE, RCEPT_ENDDE,
                     GNRL_RNK1_CRSPAREA_RCPTDE, GNRL_RNK1_CRSPAREA_ENDDE,
                     GNRL_RNK1_ETC_GG_RCPTDE, GNRL_RNK1_ETC_GG_ENDDE,
                     GNRL_RNK1_ETC_AREA_RCPTDE, GNRL_RNK1_ETC_AREA_ENDDE,
                     GNRL_RNK2_CRSPAREA_RCPTDE, GNRL_RNK2_CRSPAREA_ENDDE,
                     GNRL_RNK2_ETC_GG_RCPTDE, GNRL_RNK2_ETC_GG_ENDDE,
                     GNRL_RNK2_ETC_AREA_RCPTDE, GNRL_RNK2_ETC_AREA_ENDDE)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "ALLTYPE-APT", "ALLTYPE-APT", "ALLTYPE 아파트", "05", "민영",
                "서울시 강남구 테스트로 1", "06000", "011", "서울특별시",
                "2026-10-01", "2026-10-04", "2026-10-11",
                "2026-10-05", "2026-10-05", "2026-10-06", "2026-10-06",
                "2026-10-07", "2026-10-07", "2026-10-08", "2026-10-08",
                "2026-10-09", "2026-10-09", "2026-10-10", "2026-10-10");
        jdbcTemplate.update("""
                insert into TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, RCRIT_PBLANC_DE, SUBSCRPT_RCEPT_BGNDE)
                values (?, ?, ?, ?, ?)
                """, "ALLTYPE-URBAN", "ALLTYPE-URBAN", "ALLTYPE 오피스텔", "2026-10-05", "2026-10-05");
        jdbcTemplate.update("""
                insert into TB_REMNDR_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, RCRIT_PBLANC_DE, SUBSCRPT_RCEPT_BGNDE)
                values (?, ?, ?, ?, ?)
                """, "ALLTYPE-REMAINING", "ALLTYPE-REMAINING", "ALLTYPE 잔여세대", "2026-10-03", "2026-10-03");
        jdbcTemplate.update("""
                insert into TB_PBL_PVT_RENT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, RCRIT_PBLANC_DE, SUBSCRPT_RCEPT_BGNDE)
                values (?, ?, ?, ?, ?)
                """, "ALLTYPE-RENTAL", "ALLTYPE-RENTAL", "ALLTYPE 공공지원 민간임대", "2026-10-04", "2026-10-04");
        jdbcTemplate.update("""
                insert into TB_OPT_LTTOT_PBLANC_DETAIL
                    (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, RCRIT_PBLANC_DE, SUBSCRPT_RCEPT_BGNDE)
                values (?, ?, ?, ?, ?)
                """, "ALLTYPE-OPTIONAL", "ALLTYPE-OPTIONAL", "ALLTYPE 임의공급", "20261002", "20261002");

        mockMvc.perform(get("/api/v1/announcements")
                        .queryParam("keyword", "ALLTYPE")
                        .queryParam("page", "0")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[0].type").value("urban"))
                .andExpect(jsonPath("$.data.content[1].type").value("apt"))
                .andExpect(jsonPath("$.data.content[1].houseSecd").value("05"))
                .andExpect(jsonPath("$.data.content[1].houseSecdNm").value("민영"))
                .andExpect(jsonPath("$.data.content[1].hssplyAdres").value("서울시 강남구 테스트로 1"))
                .andExpect(jsonPath("$.data.content[1].hssplyZip").value("06000"))
                .andExpect(jsonPath("$.data.content[1].regionCode").value("011"))
                .andExpect(jsonPath("$.data.content[1].regionName").value("서울특별시"))
                .andExpect(jsonPath("$.data.content[1].rceptBgnde").value("2026-10-04"))
                .andExpect(jsonPath("$.data.content[1].rceptEndde").value("2026-10-11"))
                .andExpect(jsonPath("$.data.content[1].publicationDate").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1CrspareaRcptde").value("2026-10-05"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1CrspareaEndde").value("2026-10-05"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1EtcGgRcptde").value("2026-10-06"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1EtcGgEndde").value("2026-10-06"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1EtcAreaRcptde").value("2026-10-07"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk1EtcAreaEndde").value("2026-10-07"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2CrspareaRcptde").value("2026-10-08"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2CrspareaEndde").value("2026-10-08"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2EtcGgRcptde").value("2026-10-09"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2EtcGgEndde").value("2026-10-09"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2EtcAreaRcptde").value("2026-10-10"))
                .andExpect(jsonPath("$.data.content[1].gnrlRnk2EtcAreaEndde").value("2026-10-10"))
                .andExpect(jsonPath("$.data.content[2].type").value("public-rental"))
                .andExpect(jsonPath("$.data.content[3].type").value("remaining"))
                .andExpect(jsonPath("$.data.content[4].type").value("optional"));

        mockMvc.perform(get("/api/v1/announcements")
                        .queryParam("keyword", "ALLTYPE")
                        .queryParam("types", "apt,urban")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].type").value("urban"))
                .andExpect(jsonPath("$.data.content[1].type").value("apt"));

        mockMvc.perform(get("/api/v1/announcements")
                        .queryParam("keyword", "ALLTYPE")
                        .queryParam("types", "remaining"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.totalElements").value(1))
                        .andExpect(jsonPath("$.data.content[0].type").value("remaining"));

        mockMvc.perform(get("/api/v1/announcements")
                        .queryParam("keyword", "ALLTYPE")
                        .queryParam("startDate", "2026-10-03")
                        .queryParam("endDate", "2026-10-05")
                        .queryParam("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].type").value("urban"))
                .andExpect(jsonPath("$.data.content[1].type").value("apt"))
                .andExpect(jsonPath("$.data.content[2].type").value("public-rental"))
                .andExpect(jsonPath("$.data.content[3].type").value("remaining"));
    }
    @Test
    void returnsDistinctUrbanModelsEvenWithRepeatedOrMissingModelNumbers() throws Exception {
        jdbcTemplate.update("""
                insert into TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM)
                values ('MODEL-REVIEW', 'MODEL-REVIEW', '모델 테스트')
                """);
        jdbcTemplate.update("""
                insert into TB_URBTY_OFCTL_LTTOT_PBLANC_MDL
                    (HOUSE_MANAGE_NO, PBLANC_NO, MODEL_NO, TP)
                values ('MODEL-REVIEW', 'MODEL-REVIEW', '1', 'A'),
                       ('MODEL-REVIEW', 'MODEL-REVIEW', '1', 'B'),
                       ('MODEL-REVIEW', 'MODEL-REVIEW', null, 'C')
                """);
        mockMvc.perform(get("/api/v1/urban/announcements/MODEL-REVIEW/MODEL-REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.models.length()").value(3))
                .andExpect(jsonPath("$.data.models[*].tp", org.hamcrest.Matchers.containsInAnyOrder("A", "B", "C")));
    }

    @Test
    void normalizesDatesAndUsesStableIdsBeforeCandidateLimit() throws Exception {
        jdbcTemplate.update("""
                insert into TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO, PBLANC_NO, HOUSE_NM, RCEPT_BGNDE)
                values ('DATE-A', 'DATE-A', 'DATE-REGRESSION', '2026-11-10'),
                       ('DATE-B', 'DATE-B', 'DATE-REGRESSION', '20261101'),
                       ('DATE-C', 'DATE-C', 'DATE-REGRESSION', '20261110'),
                       ('DATE-D', 'DATE-D', 'DATE-REGRESSION', null)
                """);
        for (int page = 0; page < 4; page++) {
            String expected = new String[]{"DATE-A", "DATE-C", "DATE-B", "DATE-D"}[page];
            mockMvc.perform(get("/api/v1/announcements")
                            .queryParam("keyword", "DATE-REGRESSION").queryParam("types", "apt")
                            .queryParam("size", "1").queryParam("page", String.valueOf(page)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].houseManageNo").value(expected));
        }
        mockMvc.perform(get("/api/v1/apt/announcements")
                        .queryParam("keyword", "DATE-REGRESSION").queryParam("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].houseManageNo").value("DATE-A"));
        mockMvc.perform(get("/api/v1/announcements/suggestions")
                        .queryParam("keyword", "DATE-REGRESSION").queryParam("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].houseManageNo").value("DATE-A"));
    }


    @Test void unifiedListCountsVisibleCommentsAndRepliesByExactAnnouncementType() throws Exception {
        jdbcTemplate.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,RCEPT_BGNDE) VALUES ('COUNT-HOUSE','COUNT-NOTICE','COMMENT_COUNT_TEST','2026-11-02'),('COUNT-EMPTY','COUNT-EMPTY','COMMENT_COUNT_TEST','2026-11-01')");
        jdbcTemplate.update("INSERT INTO TB_URBTY_OFCTL_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,SUBSCRPT_RCEPT_BGNDE) VALUES ('COUNT-HOUSE','COUNT-NOTICE','COMMENT_COUNT_TEST','2026-11-03')");
        for (String type : List.of("APT", "URBAN")) {
            for (int n = 0; n < 3; n++) {
                jdbcTemplate.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,RECOMMEND,OPPOSE,DELETE_YN,CREATE_USER_SEQ,REG_DT,PARENT_COMMENT_SEQ) VALUES (?,'COUNT-HOUSE','COUNT-NOTICE',?,0,0,?,1,CURRENT_TIMESTAMP,?)",
                        type, "count-" + type + "-" + n, "APT".equals(type) && n == 2 ? "Y" : "N", n == 1 ? 999L : null);
            }
        }
        mockMvc.perform(get("/api/v1/announcements").queryParam("types", "apt").queryParam("keyword", "COMMENT_COUNT_TEST").queryParam("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].commentCount").value(2));
        mockMvc.perform(get("/api/v1/announcements").queryParam("types", "apt").queryParam("keyword", "COMMENT_COUNT_TEST").queryParam("page", "1").queryParam("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].commentCount").value(0));
        mockMvc.perform(get("/api/v1/announcements").queryParam("types", "urban").queryParam("keyword", "COMMENT_COUNT_TEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].commentCount").value(3));
        jdbcTemplate.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y' WHERE ANNOUNCEMENT_TYPE='APT' AND HOUSE_MANAGE_NO='COUNT-HOUSE'");
        mockMvc.perform(get("/api/v1/announcements").queryParam("types", "apt").queryParam("keyword", "COMMENT_COUNT_TEST").queryParam("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].commentCount").value(0));
    }
}
