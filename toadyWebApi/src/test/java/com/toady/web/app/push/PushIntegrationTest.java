package com.toady.web.app.push;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toady.web.app.planner.PlannerService;
import com.toady.web.app.planner.PlannerSummary;
import com.toady.web.app.planner.PublicAreaPage;
import com.toady.web.app.user.entity.UserEntity;
import com.toady.web.app.user.repository.UserRepository;
import com.toady.web.core.util.email.EmailUtilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import static com.toady.web.app.push.WebPushCrypto.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:pushaudit;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "toady.security.secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=="
})
@AutoConfigureMockMvc
class PushIntegrationTest {
    record Sent(URI uri, Map<String, String> headers, byte[] body) { }
    static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    static volatile int status = 201;
    static final KeyPair VAPID = keys();
    static KeyPair keys() { try { return ephemeral(); } catch (Exception e) { throw new IllegalStateException(e); } }

    @TestConfiguration static class FakePushService {
        /** 실제 푸시 서비스로 나가지 않도록 전송만 기록한다. */
        @Bean @Primary WebPushSender testSender() {
            String publicKey = b64(uncompressed((ECPublicKey) VAPID.getPublic()));
            byte[] d = ((ECPrivateKey) VAPID.getPrivate()).getS().toByteArray();
            byte[] fixed = new byte[32];
            System.arraycopy(d, Math.max(0, d.length - 32), fixed, 32 - Math.min(32, d.length), Math.min(32, d.length));
            return new WebPushSender(publicKey, b64(fixed), "mailto:push@example.test", (uri, headers, body) -> { SENT.add(new Sent(uri, headers, body)); return status; }, Clock.systemUTC());
        }
    }

    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired PushSubscriptions store; @Autowired WebPushSender sender; @Autowired PlannerService planner; @Autowired PlannerSummary summaries; @Autowired PublicAreaPage areas; @Autowired com.toady.web.app.planner.AnnouncementResults results;
    @MockitoBean EmailUtilService mail;

    KeyPair browser; byte[] auth = new byte[16];

    @BeforeEach void setup() throws Exception {
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_FAVORITE (USER_SEQ BIGINT,ANNOUNCEMENT_TYPE VARCHAR(20),HOUSE_MANAGE_NO VARCHAR(40),PBLANC_NO VARCHAR(40),NOTE VARCHAR(2000),CHECKLIST INT,EMAIL_ENABLED BOOLEAN,REG_DT TIMESTAMP,PRIMARY KEY(USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_PUSH_SUBSCRIPTION (ENDPOINT_HASH CHAR(64) PRIMARY KEY,ENDPOINT VARCHAR(1000) NOT NULL,P256DH VARCHAR(100) NOT NULL,AUTH VARCHAR(40) NOT NULL,USER_SEQ BIGINT,VISITOR_ID CHAR(36),FAVORITES_ENABLED BOOLEAN DEFAULT TRUE NOT NULL,FAVORITE_IDS TEXT,AREA_SLUGS VARCHAR(200) DEFAULT '' NOT NULL,REG_DT TIMESTAMP NOT NULL,UPDATED_DT TIMESTAMP NOT NULL,LAST_ATTEMPT_DATE DATE,LAST_DIGEST_DT TIMESTAMP,LAST_SUCCESS_DT TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS TB_PUSH_AREA_SEEN (AREA_SLUG VARCHAR(40),ANNOUNCEMENT_ID VARCHAR(110),FIRST_SEEN_DT TIMESTAMP NOT NULL,PRIMARY KEY(AREA_SLUG,ANNOUNCEMENT_ID))");
        for (String table : List.of("TB_PUSH_SUBSCRIPTION", "TB_PUSH_AREA_SEEN", "TB_FAVORITE", "TB_CERTIFY", "TB_USER", "TB_APT_LTTOT_PBLANC_DETAIL")) jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P1','N1','운정 푸시 테스트','경기도 파주시 동패동','2026-11-12','2026-11-13','2026-11-20')");
        SENT.clear(); status = 201;
        browser = ephemeral(); new java.security.SecureRandom().nextBytes(auth);
    }

    String body(String endpoint, Object... extra) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("endpoint", endpoint);
        body.put("keys", Map.of("p256dh", b64(uncompressed((ECPublicKey) browser.getPublic())), "auth", b64(auth)));
        for (int i = 0; i < extra.length; i += 2) body.put((String) extra[i], extra[i + 1]);
        return json.writeValueAsString(body);
    }
    String token(String email) throws Exception {
        users.save(UserEntity.builder().userEmail(email).userNicName("push").userPassword(passwords.encode("Password123!")).emailCertifyYn("Y").lockYn("N").withdrawalYn("N").build());
        String body = mvc.perform(post("/webApi/user/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("userEmail", email, "userPassword", "Password123!"))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("accessToken").asText();
    }
    /** 브라우저가 하듯 개인키로 복호화한 알림 JSON. */
    JsonNode decrypt(byte[] body) throws Exception {
        byte[] salt = Arrays.copyOfRange(body, 0, 16); int id = body[20];
        byte[] asPublic = Arrays.copyOfRange(body, 21, 21 + id), uaPublic = uncompressed((ECPublicKey) browser.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH"); agreement.init(browser.getPrivate()); agreement.doPhase(publicKey(asPublic), true);
        byte[] ikm = hkdf(auth, agreement.generateSecret(), concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0}, uaPublic, asPublic), 32);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(hkdf(salt, ikm, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 16), "AES"),
            new GCMParameterSpec(128, hkdf(salt, ikm, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII), new byte[]{0}), 12)));
        byte[] padded = cipher.doFinal(Arrays.copyOfRange(body, 21 + id, body.length));
        return json.readTree(new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8));
    }
    PushDigest digest(String kst) {
        return new PushDigest(store, sender, jdbc, planner, summaries, areas, users, json, results, Clock.fixed(LocalDateTime.parse(kst).atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul")));
    }

    @Test void subscribeLookupTestSendAndUnsubscribe() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/abc123";
        mvc.perform(get("/api/v1/push/config")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.publicKey").value(b64(uncompressed((ECPublicKey) VAPID.getPublic()))));
        // 임의 주소로 서버가 요청하지 않도록 알려진 푸시 서비스만 허용한다.
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body("https://attacker.example/push"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body("http://fcm.googleapis.com/fcm/send/x"))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "areas", List.of("unjeong", "nowhere"), "favoriteIds", List.of("apt/P1/N1"), "visitorId", UUID.randomUUID())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.subscribed").value(true)).andExpect(jsonPath("$.areas[0]").value("unjeong")).andExpect(jsonPath("$.areas.length()").value(1)).andExpect(jsonPath("$.member").value(false));
        assertEquals("apt/P1/N1", jdbc.queryForObject("SELECT FAVORITE_IDS FROM TB_PUSH_SUBSCRIPTION", String.class));

        mvc.perform(post("/api/v1/push/test").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\",\"announcementId\":\"apt/P1/N1\"}")).andExpect(status().isOk());
        assertEquals(1, SENT.size());
        Sent sent = SENT.get(0);
        assertEquals(endpoint, sent.uri().toString());
        assertEquals("aes128gcm", sent.headers().get("Content-Encoding"));
        assertTrue(sent.headers().get("Authorization").startsWith("vapid t=") && sent.headers().get("Authorization").contains(", k="));
        JsonNode payload = decrypt(sent.body());
        assertEquals("/?subscriptSeq=apt%2FP1%2FN1", payload.path("url").asText());
        assertTrue(payload.path("body").asText().contains("운정 푸시 테스트"), payload.toString());
        assertNotNull(jdbc.queryForObject("SELECT LAST_SUCCESS_DT FROM TB_PUSH_SUBSCRIPTION", java.sql.Timestamp.class));
        mvc.perform(post("/api/v1/push/test").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}")).andExpect(status().isTooManyRequests());

        // 로그인 상태로 다시 저장하면 회원 관심 공고를 쓰고, 기기 목록은 지운다. 로그아웃 상태로 저장하면 회원 연결을 끊는다.
        String bearer = token("push@example.test");
        mvc.perform(put("/api/v1/push/subscription").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favoriteIds", List.of("apt/P1/N1"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(true));
        assertNull(jdbc.queryForObject("SELECT FAVORITE_IDS FROM TB_PUSH_SUBSCRIPTION", String.class));
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favorites", false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.member").value(false)).andExpect(jsonPath("$.favorites").value(false));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION", Integer.class));

        mvc.perform(post("/api/v1/push/subscription/lookup").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}")).andExpect(jsonPath("$.subscribed").value(true));
        mvc.perform(post("/api/v1/push/unsubscribe").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.subscribed").value(false));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION", Integer.class));
    }

    @Test void expiredSubscriptionIsRemovedOnTestSend() throws Exception {
        String endpoint = "https://updates.push.services.mozilla.com/wpush/v2/gone";
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint))).andExpect(status().isOk());
        status = 410;
        mvc.perform(post("/api/v1/push/test").contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}")).andExpect(status().isGone());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION", Integer.class));
    }

    @Test void dailyDigestBundlesTomorrowEventsAndNewAreaAnnouncementsOncePerDay() throws Exception {
        String guest = "https://fcm.googleapis.com/fcm/send/guest", member = "https://web.push.apple.com/member", areaOnly = "https://fcm.googleapis.com/fcm/send/area";
        jdbc.update("INSERT INTO TB_PUSH_SUBSCRIPTION (ENDPOINT_HASH,ENDPOINT,P256DH,AUTH,FAVORITES_ENABLED,FAVORITE_IDS,AREA_SLUGS,REG_DT,UPDATED_DT) VALUES (?,?,?,?,TRUE,'apt/P1/N1,apt/MISSING/X','',?,?)",
            PushSubscriptions.hash(guest), guest, b64(uncompressed((ECPublicKey) browser.getPublic())), b64(auth), java.sql.Timestamp.valueOf("2026-11-01 10:00:00"), java.sql.Timestamp.valueOf("2026-11-01 10:00:00"));
        String bearer = token("digest@example.test");
        mvc.perform(post("/api/v1/me/favorites/apt/P1/N1").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"\",\"checklist\":0,\"emailEnabled\":false}")).andExpect(status().isOk());
        mvc.perform(put("/api/v1/push/subscription").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body(member))).andExpect(status().isOk());
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(areaOnly, "favorites", false, "areas", List.of("unjeong")))).andExpect(status().isOk());
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET REG_DT=?", java.sql.Timestamp.valueOf("2026-11-01 10:00:00"));

        // 11일 저녁: 관심 공고의 내일(12일) 접수 시작을 알린다. 생활권 기록은 처음이라 기존 공고를 새 공고로 보내지 않는다.
        var first = digest("2026-11-11T18:00").run();
        assertEquals(2, first.sent(), first.toString());
        assertEquals(1, first.empty());
        Map<String, JsonNode> payloads = new HashMap<>();
        for (Sent sent : SENT) payloads.put(sent.uri().toString(), decrypt(sent.body()));
        assertEquals("내일 접수 시작 · 운정 푸시 테스트", payloads.get(guest).path("title").asText());
        assertEquals("/?subscriptSeq=apt%2FP1%2FN1", payloads.get(member).path("url").asText());
        assertEquals("toady-digest", payloads.get(guest).path("tag").asText());
        assertEquals("digest", SENT.get(0).headers().get("Topic"));

        // 같은 날 다시 확인해도 중복 발송하지 않는다.
        SENT.clear();
        assertEquals(0, digest("2026-11-11T18:30").run().sent());
        assertTrue(SENT.isEmpty());

        // 다음 날: 관심 공고는 내일(13일) 접수 마감을, 생활권 구독은 새로 올라온 운정 공고를 받는다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P2','N2','운정 새 단지','경기도 파주시 야당동','2026-11-25','2026-11-26','2026-12-03')");
        var second = digest("2026-11-12T18:00").run();
        assertEquals(3, second.sent(), second.toString());
        payloads.clear();
        for (Sent sent : SENT) payloads.put(sent.uri().toString(), decrypt(sent.body()));
        assertEquals("내일 접수 마감 · 운정 푸시 테스트", payloads.get(guest).path("title").asText());
        JsonNode news = payloads.get(areaOnly);
        assertEquals("운정 새 청약 공고", news.path("title").asText(), news.toString());
        assertEquals("/?subscriptSeq=apt%2FP2%2FN2", news.path("url").asText());

        // 브라우저에서 해지된 구독(410)은 발송 시점에 삭제한다.
        SENT.clear(); status = 410;
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('P3','N3','운정 세 번째','경기도 파주시 와동동','2026-11-27','2026-11-28','2026-12-05')");
        var third = digest("2026-11-13T18:00").run();
        assertEquals(1, third.removed(), third.toString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_PUSH_SUBSCRIPTION WHERE ENDPOINT_HASH=?", Integer.class, PushSubscriptions.hash(areaOnly)));
    }

    @Test void zupzupTopicSendsOnlyNewCapitalUnrankedAnnouncements() throws Exception {
        jdbc.update("DELETE FROM TB_REMNDR_LTTOT_PBLANC_DETAIL");
        String endpoint = "https://fcm.googleapis.com/fcm/send/zupzup";
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favorites", false, "areas", List.of("zupzup-capital", "zupzup-nowhere"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.areas.length()").value(1)).andExpect(jsonPath("$.areas[0]").value("zupzup-capital"));
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET REG_DT=?", java.sql.Timestamp.valueOf("2026-11-01 10:00:00"));
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_AREA_CODE_NM,SUBSCRPT_RCEPT_BGNDE,SUBSCRPT_RCEPT_ENDDE) VALUES ('R0','N0','기존 줍줍','서울특별시 강동구 1','서울','2026-11-14','2026-11-14')");
        // 첫 확인은 기준만 남긴다(이미 있던 공고를 새 공고로 보내지 않는다).
        assertEquals(0, digest("2026-11-11T18:00").run().sent());
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_AREA_CODE_NM,SUBSCRPT_RCEPT_BGNDE,SUBSCRPT_RCEPT_ENDDE) VALUES ('R1','N1','서울 새 줍줍','서울특별시 송파구 1','서울','2026-11-16','2026-11-16'),('R2','N2','부산 새 줍줍','부산광역시 해운대구 1','부산','2026-11-16','2026-11-16')");
        SENT.clear();
        assertEquals(1, digest("2026-11-12T18:00").run().sent());
        JsonNode message = decrypt(SENT.get(0).body());
        assertEquals("수도권 줍줍 새 청약 공고", message.path("title").asText(), message.toString());
        assertEquals("/?subscriptSeq=remaining%2FR1%2FN1", message.path("url").asText());
    }

    @Test void favoriteResultsAndSundayWeeklyBriefAreBundled() throws Exception {
        jdbc.update("DELETE FROM TB_APT_LTTOT_PBLANC_CMPET");
        jdbc.update("DELETE FROM TB_APT_LTTOT_PBLANC_SCORE");
        jdbc.update("DELETE FROM TB_REMNDR_LTTOT_PBLANC_DETAIL");
        // 11-13 마감·11-20 발표 공고(P1)를 관심 공고로, 주간 브리핑도 함께 구독한 비회원.
        String endpoint = "https://fcm.googleapis.com/fcm/send/results";
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favoriteIds", List.of("apt/P1/N1"), "areas", List.of("weekly-brief"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.areas[0]").value("weekly-brief"));
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET REG_DT=?", java.sql.Timestamp.valueOf("2026-11-01 10:00:00"));
        // 기능을 켠 첫 실행: 이미 공개돼 있던 결과는 새 소식으로 보내지 않는다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_SCORE (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,RESIDE_SECD,LWET_SCORE,TOP_SCORE) VALUES ('OLD','N0','01','01','40','50')");
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,PRZWNER_PRESNATN_DE) VALUES ('OLD','N0','지난 결과 단지','서울특별시 송파구 9','2026-11-10','2026-11-12','2026-11-19')");
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET FAVORITE_IDS='apt/P1/N1,apt/OLD/N0'");
        assertEquals(0, digest("2026-11-13T18:00").run().sent(), "결과 기록을 시작하는 날에는 기존 결과를 보내지 않는다(P1은 내일 일정도 없다)");
        // 다음 날 P1 경쟁률 발표 → 결과 알림 1건, 경쟁률 탭으로 연결.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_CMPET (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,SUBSCRPT_RANK_CODE,RESIDE_SECD,CMPET_RATE) VALUES ('P1','N1','01','1','01','12.5')");
        SENT.clear();
        assertEquals(1, digest("2026-11-14T18:00").run().sent());
        JsonNode message = decrypt(SENT.get(0).body());
        assertEquals("경쟁률 발표 · 운정 푸시 테스트", message.path("title").asText(), message.toString());
        assertEquals("/?subscriptSeq=apt%2FP1%2FN1&tabType=rate", message.path("url").asText());
        // 일요일(11-15): 다음 주 접수 시작 공고 수를 주간 브리핑으로 보낸다. 같은 결과는 다시 보내지 않는다.
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('W1','N1','다음 주 단지','서울특별시 강동구 1','2026-11-17','2026-11-18')");
        jdbc.update("INSERT INTO TB_REMNDR_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_RCEPT_BGNDE,SUBSCRPT_RCEPT_ENDDE) VALUES ('W2','N2','다음 주 줍줍','서울특별시 강동구 2','2026-11-19','2026-11-19')");
        SENT.clear();
        assertEquals(1, digest("2026-11-15T18:00").run().sent());
        JsonNode weekly = decrypt(SENT.get(0).body());
        assertEquals("이번 주 청약 브리핑", weekly.path("title").asText(), weekly.toString());
        assertTrue(weekly.path("body").asText().contains("접수 시작 2건 · 아파트 1건 · 무순위·줍줍 1건"), weekly.toString());
        assertEquals("/weekly", weekly.path("url").asText());
        // 일요일이 아니면 주간 브리핑을 넣지 않는다.
        SENT.clear();
        assertEquals(0, digest("2026-11-16T18:00").run().sent());
    }

    @Test void lateSubscriberToIdleAreaDoesNotReceiveOldAnnouncementsAsNew() throws Exception {
        // 구독자가 없어도 매 실행 생활권 기록을 갱신한다: 11일 기준 기록 → 12일에 올라온 공고를 12일에 처음 본 것으로 기록.
        assertEquals(0, digest("2026-11-11T18:00").run().sent());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('Q1','N1','운정 조용한 단지','경기도 파주시 야당동 3','2026-11-25','2026-11-26')");
        assertEquals(0, digest("2026-11-12T18:00").run().sent());
        // 15일에 처음 구독한 사람에게는 구독 전에 올라온 공고를 '새 공고'로 보내지 않는다.
        String endpoint = "https://fcm.googleapis.com/fcm/send/late";
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favorites", false, "areas", List.of("unjeong")))).andExpect(status().isOk());
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET REG_DT=?", java.sql.Timestamp.valueOf("2026-11-15 12:00:00"));
        SENT.clear();
        var report = digest("2026-11-15T18:00").run();
        assertEquals(0, report.sent(), report.toString());
        assertTrue(SENT.isEmpty());
    }

    @Test void resultsPublishedDaysBeforeSavingAreNotSentAsNews() throws Exception {
        jdbc.update("DELETE FROM TB_APT_LTTOT_PBLANC_CMPET");
        // 11-13 마감 공고(P1)의 경쟁률이 14일에 공개되고, 그날부터 매 실행 기록된다(구독자 없음).
        assertEquals(0, digest("2026-11-13T18:00").run().sent());
        jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_CMPET (HOUSE_MANAGE_NO,PBLANC_NO,MODEL_NO,SUBSCRPT_RANK_CODE,RESIDE_SECD,CMPET_RATE) VALUES ('P1','N1','01','1','01','9.1')");
        assertEquals(0, digest("2026-11-14T18:00").run().sent());
        // 오래전에 구독했지만 알림을 받은 적 없는 사람이 18일에 P1을 저장해도 나흘 전 결과를 새 소식으로 보내지 않는다.
        String endpoint = "https://fcm.googleapis.com/fcm/send/old-subscriber";
        mvc.perform(put("/api/v1/push/subscription").contentType(MediaType.APPLICATION_JSON).content(body(endpoint, "favoriteIds", List.of("apt/P1/N1")))).andExpect(status().isOk());
        jdbc.update("UPDATE TB_PUSH_SUBSCRIPTION SET REG_DT=?", java.sql.Timestamp.valueOf("2026-10-01 10:00:00"));
        SENT.clear();
        assertEquals(0, digest("2026-11-18T18:00").run().sent());
        assertTrue(SENT.isEmpty());
    }

    @Test void bundledMessageListsBothKindsWithActions() {
        var message = PushMessage.digest(List.of(
            new PushMessage.Line("FAVORITE", "apt/A/1", "가 단지", "접수 마감", null, null),
            new PushMessage.Line("FAVORITE", "apt/B/1", "나 단지", "당첨자 발표", null, null),
            new PushMessage.Line("AREA", "apt/C/1", "다 단지", "새 공고", "dongtan", "동탄"))).orElseThrow();
        assertEquals("TOADY 청약 알림 · 3건", message.title());
        assertEquals("내일 일정 2건 · 가 단지 접수 마감 외 1건\n동탄 새 공고 1건 · 다 단지", message.body());
        assertEquals("/?subscriptSeq=apt%2FA%2F1", message.url());
        assertEquals(List.of("/my", "/areas/dongtan"), message.actions().stream().map(PushMessage.Action::url).toList());
        assertTrue(PushMessage.digest(List.of()).isEmpty());
    }

    @Test void senderStaysOffWithoutMatchingKeys() throws Exception {
        assertFalse(new WebPushSender("", "", "", (u, h, b) -> 201, Clock.systemUTC()).enabled());
        KeyPair other = ephemeral();
        byte[] d = ((ECPrivateKey) other.getPrivate()).getS().toByteArray();
        byte[] fixed = new byte[32]; System.arraycopy(d, Math.max(0, d.length - 32), fixed, 32 - Math.min(32, d.length), Math.min(32, d.length));
        assertFalse(new WebPushSender(b64(uncompressed((ECPublicKey) VAPID.getPublic())), b64(fixed), "", (u, h, b) -> 201, Clock.systemUTC()).enabled());
        assertTrue(WebPushSender.allowedEndpoint("https://fcm.googleapis.com/fcm/send/x"));
        assertTrue(WebPushSender.allowedEndpoint("https://wns2-pn1p.notify.windows.com/w/?token=x"));
        assertFalse(WebPushSender.allowedEndpoint("https://fcm.googleapis.com.attacker.example/x"));
        assertFalse(WebPushSender.allowedEndpoint("https://fcm.googleapis.com:8443/x"));
        assertFalse(WebPushSender.allowedEndpoint("https://user@fcm.googleapis.com/x"));
    }
}
