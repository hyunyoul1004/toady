package com.toady.admin

import com.toady.admin.metrics.MetricsService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 관리자 사이트: 로그인 보호·CSRF·회원 잠금·댓글 숨김/복구·화면 렌더링·스케줄러 연결 실패 안내. 외부 연결은 쓰지 않는다. */
@SpringBootTest(properties = [
	"spring.datasource.url=jdbc:h2:mem:admin;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:admin-schema.sql",
	"toady.admin.username=operator", "toady.admin.password=local-test-password",
	// 닫힌 포트: 스케줄러에 연결하지 못했을 때의 안내를 확인한다.
	"toady.scheduler.base-url=http://127.0.0.1:9", "toady.scheduler.token=test-internal-token-0123456789abcdef",
])
@AutoConfigureMockMvc
class AdminSiteTest {
	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var metrics: MetricsService
	private val admin = user("operator").roles("ADMIN")

	@BeforeEach fun setup() {
		listOf("TB_USER", "TB_LTTOT_PBLANC_COMMENT", "TB_FAVORITE", "TB_PAGE_VIEW", "TB_USAGE_EVENT", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL",
			"TB_APT_LTTOT_PBLANC_MDL", "TB_RATE_POLL", "TB_ANALYTICS_EXCLUDED_VISITOR", "TB_COMMENT_ADMIN_HIDE").forEach { jdbc.update("DELETE FROM $it") }
		val today = Kst.today()
		jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE,TOT_SUPLY_HSHLDCO) VALUES ('H1','P1','역삼 새 단지','서울특별시 강남구 역삼동 1',?,?,'300')",
			today.minusDays(1).toString(), today.plusDays(1).toString())
		// 임의공급은 날짜가 YYYYMMDD 형식이다.
		jdbc.update("INSERT INTO TB_OPT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,SUBSCRPT_RCEPT_BGNDE,SUBSCRPT_RCEPT_ENDDE) VALUES ('O1','Q1','임의 단지','경기도 파주시 1',?,?)",
			today.plusDays(10).toString().replace("-", ""), today.plusDays(10).toString().replace("-", ""))
		jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_MDL VALUES ('H1','P1','01','084.9500A','120','95000')")
		jdbc.update("INSERT INTO TB_FAVORITE (USER_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,NOTE,REG_DT,STAGE) VALUES (1,'apt','H1','P1','SECRET-NOTE-123',CURRENT_TIMESTAMP,2)")
		jdbc.update("INSERT INTO TB_USER (USER_SEQ,USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN,CREATE_DT) VALUES (1,'a@example.test','x','가람','Y',CURRENT_TIMESTAMP),(2,'b@example.test','x','나래_10%','N',CURRENT_TIMESTAMP)")
		jdbc.update("INSERT INTO TB_USER (USER_SEQ,USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN,WITHDRAWAL_YN) VALUES (3,'c@example.test','x','탈퇴회원','Y','Y')")
		jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (COMMENT_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,CREATE_USER_SEQ) VALUES (10,'APT','H1','P1','<script>alert(1)</script> 원댓글',1)")
		jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (COMMENT_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,PARENT_COMMENT_SEQ,CONTENT,CREATE_USER_SEQ) VALUES (11,'PUBLIC_RENTAL','H1','P1',10,'답글',2)")
	}

	@Test fun everyAdminPageRequiresLoginAndPostsRequireCsrf() {
		mvc.perform(get("/admin/members")).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/admin/login"))
		mvc.perform(get("/admin/style.css")).andExpect(status().isOk)
		mvc.perform(post("/admin/members/1/lock").param("locked", "true").with(admin)).andExpect(status().isForbidden)
		mvc.perform(get("/internal/health")).andExpect(status().isOk)
		mvc.perform(get("/api/anything").with(admin)).andExpect(status().isForbidden)
	}

	@Test fun repeatedLoginFailuresLockTheIpForAWhile() {
		repeat(5) { mvc.perform(post("/admin/login").with(csrf()).param("username", "operator").param("password", "wrong-password").with { it.remoteAddr = "203.0.113.9"; it }) }
		mvc.perform(post("/admin/login").with(csrf()).param("username", "operator").param("password", "local-test-password").with { it.remoteAddr = "203.0.113.9"; it })
			.andExpect(redirectedUrl("/admin/login?locked"))
		mvc.perform(post("/admin/login").with(csrf()).param("username", "operator").param("password", "local-test-password").with { it.remoteAddr = "203.0.113.10"; it })
			.andExpect(redirectedUrl("/admin/"))
	}

	@Test fun membersCanBeSearchedLiterallyAndLocked() {
		// '%'·'_'는 글자 그대로 찾는다.
		mvc.perform(get("/admin/members").param("q", "10%").with(admin)).andExpect(status().isOk)
			.andExpect(content().string(org.hamcrest.Matchers.containsString("나래_10%")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("a@example.test"))))
		mvc.perform(post("/admin/members/1/lock").param("locked", "true").param("next", "/admin/members?q=&filter=all&page=0").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/members?q=&filter=all&page=0")).andExpect(flash().attributeExists("notice"))
		assertEquals("Y", jdbc.queryForObject("SELECT LOCK_YN FROM TB_USER WHERE USER_SEQ=1", String::class.java))
		// 탈퇴 회원은 바꾸지 않고, 외부 주소로 돌려보내는 back 값은 무시한다.
		mvc.perform(post("/admin/members/3/lock").param("locked", "true").param("next", "//evil.example").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/members")).andExpect(flash().attributeExists("error"))
		assertEquals("N", jdbc.queryForObject("SELECT LOCK_YN FROM TB_USER WHERE USER_SEQ=3", String::class.java))
		mvc.perform(get("/admin/members").param("filter", "locked").with(admin)).andExpect(content().string(org.hamcrest.Matchers.containsString("a@example.test")))
	}

	@Test fun hidingARootCommentHidesRepliesAndRestoringItBringsThemBack() {
		mvc.perform(get("/admin/comments").with(admin)).andExpect(status().isOk)
			// 댓글 내용은 글자 그대로(이스케이프) 표시한다.
			.andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/announcements/apt/H1/P1")))
			// 공고 ID 대신 단지명을 보여 준다.
			.andExpect(content().string(org.hamcrest.Matchers.containsString("역삼 새 단지")))
		mvc.perform(post("/admin/comments/10/hide").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'", Int::class.java))
		mvc.perform(post("/admin/comments/11/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/comments/10/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		// 원댓글을 되살리면 함께 숨겼던 답글도 같이 돌아온다(답글을 하나씩 찾아 되살리지 않아도 된다).
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'", Int::class.java))
	}
	@Test fun aReplyHiddenOnItsOwnStaysHiddenWhenTheRootIsRestored() {
		// 답글(욕설 등)을 먼저 따로 숨기고, 나중에 원댓글을 숨긴 뒤 원댓글만 되살리면 따로 숨긴 답글은 그대로 숨겨져 있어야 한다.
		mvc.perform(post("/admin/comments/11/hide").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		Thread.sleep(5)
		mvc.perform(post("/admin/comments/10/hide").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		mvc.perform(post("/admin/comments/10/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals("N", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=10", String::class.java))
		assertEquals("Y", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=11", String::class.java))
	}
	@Test fun commentsTheAuthorDeletedAreNeverRestoredByTheAdmin() {
		// 작성자가 직접 지운 댓글(서비스 API가 DELETE_YN='Y'로 바꿈)
		jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET DELETE_YN='Y' WHERE COMMENT_SEQ=10")
		val html = mvc.perform(get("/admin/comments").param("state", "hidden").with(admin)).andReturn().response.contentAsString
		assertTrue(html.contains("작성자 삭제") && !html.contains("/admin/comments/10/restore"), "작성자가 지운 댓글에는 되살리기 버튼이 없다")
		mvc.perform(post("/admin/comments/10/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		assertEquals("Y", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=10", String::class.java))
		// 관리자가 숨긴 답글만 기록되고, 이미 지워진 원댓글은 기록되지 않는다.
		mvc.perform(post("/admin/comments/11/hide").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(listOf(11L), jdbc.queryForList("SELECT COMMENT_SEQ FROM TB_COMMENT_ADMIN_HIDE", Long::class.java))
	}
	@Test fun returnAddressesKeepSearchesWithEmailCharacters() {
		assertEquals("/admin/members?q=a@b.com&page=1", com.toady.admin.web.AdminController.safeNext("/admin/members?q=a@b.com&page=1", "/x"))
		assertEquals("/x", com.toady.admin.web.AdminController.safeNext("//evil.com/admin/", "/x"))
		assertEquals("/x", com.toady.admin.web.AdminController.safeNext("/admin/members?q=a//evil", "/x"))
		assertEquals("/x", com.toady.admin.web.AdminController.safeNext("/admin/members?q=a\\b", "/x"))
	}

	@Test fun dashboardOperationsAndJobsRenderEvenWhenTheSchedulerIsDown() {
		val today = Kst.today()
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('e1','v1','s1','/',?,?,CURRENT_TIMESTAMP),('e2','v1','s2','/',NULL,?,CURRENT_TIMESTAMP)", "www.google.com", today, today.minusDays(8))
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('u1','v1','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP)", today)
		mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("검색 · 구글")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("역삼 새 단지")))  // 많이 본 공고
		// 수집이 멈췄는지는 작업 이력(마지막 성공)으로 판단한다: 어제 성공 → 정상, 사흘 전 성공뿐 → 확인 필요.
		jdbc.update("DELETE FROM TB_SCHEDULER_ADMIN_JOB")
		jdbc.update("INSERT INTO TB_SCHEDULER_ADMIN_JOB (JOB_ID,JOB_KIND,JOB_SOURCE,START_DATE,END_DATE,STATUS,TOTAL_CHUNKS,REQUESTED_BY,MESSAGE,CREATED_AT,FINISHED_AT,HEARTBEAT_AT) VALUES ('j1','ANNOUNCEMENTS','SCHEDULE',?,?,'SUCCEEDED',1,'scheduler','ok',?,?,?),('j2','COMPETITION','SCHEDULE',?,?,'SUCCEEDED',1,'scheduler','ok',?,?,?)",
			today, today, Kst.now().minusHours(20), Kst.now().minusHours(20), Kst.now(), today, today, Kst.now().minusDays(3), Kst.now().minusDays(3), Kst.now())
		// 직전 성공이 있어도 마지막 실행이 실패면 사유와 함께 실패로 보여 준다.
		jdbc.update("INSERT INTO TB_SCHEDULER_ADMIN_JOB (JOB_ID,JOB_KIND,JOB_SOURCE,START_DATE,END_DATE,STATUS,TOTAL_CHUNKS,REQUESTED_BY,MESSAGE,CREATED_AT,FINISHED_AT,HEARTBEAT_AT) VALUES ('j3','SCORES','SCHEDULE',?,?,'SUCCEEDED',1,'scheduler','ok',?,?,?),('j4','SCORES','SCHEDULE',?,?,'FAILED',1,'scheduler','일부 공고만 실패했습니다 TEST-REASON',?,?,?)",
			today, today, Kst.now().minusHours(10), Kst.now().minusHours(10), Kst.now(), today, today, Kst.now().minusHours(2), Kst.now().minusHours(2), Kst.now())
		val html = mvc.perform(get("/admin/operations").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("공고 수집 상태") && html.contains("공고 · 주택형") && html.contains("30시간 넘게 성공 없음") && html.contains("정상"), html.take(200))
		assertTrue(html.contains("실패 · 확인 필요") && html.contains("TEST-REASON") && html.contains("작업 ID j4"), "마지막 실행 실패는 사유와 작업 ID를 보여 준다")
		mvc.perform(get("/admin/jobs").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("스케줄러에 연결하지 못했습니다")))
		mvc.perform(post("/admin/jobs").param("kind", "SCORES").param("startDate", "2020-01-01").param("endDate", "2020-01-02").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/jobs")).andExpect(flash().attribute("error", "스케줄러에 연결하지 못했습니다. 스케줄러가 실행 중인지 확인하세요."))
	}
	@Test fun announcementsAreListedAcrossTypesAndShowOperationalDetail() {
		mvc.perform(get("/admin/announcements").param("status", "open").with(admin)).andExpect(status().isOk)
			.andExpect(content().string(org.hamcrest.Matchers.containsString("역삼 새 단지")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("임의 단지"))))
		mvc.perform(get("/admin/announcements").param("status", "upcoming").param("type", "optional").with(admin))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("임의 단지")))
		jdbc.update("INSERT INTO TB_RATE_POLL VALUES ('apt/H1/P1','v9',3,CURRENT_TIMESTAMP)")
		val html = mvc.perform(get("/admin/announcements/apt/H1/P1").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("95,000만원") && html.contains("1표") && html.contains("원댓글"), "주택형·투표·댓글을 함께 보여 준다")
		assertFalse(html.contains("alert notice"), "공고번호(경로 변수)가 알림 메시지로 보이지 않는다")
		mvc.perform(get("/admin/announcements/apt/NOPE/P1").with(admin)).andExpect(status().isNotFound)
		mvc.perform(get("/admin/announcements/evil/H1/P1").with(admin)).andExpect(status().isNotFound)
	}

	@Test fun memberDetailShowsFavoritesWithoutPrivateNotes() {
		val html = mvc.perform(get("/admin/members/1").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("역삼 새 단지") && html.contains("신청 완료"), "관심 공고 이름과 진행 단계")
		assertTrue(!html.contains("SECRET-NOTE-123"), "회원의 메모는 관리자에게도 보여 주지 않는다")
		mvc.perform(get("/admin/members/999").with(admin)).andExpect(status().isNotFound)
	}

	@Test fun registeredOperatorBrowsersAreRemovedFromVisitorCounts() {
		val today = Kst.today()
		val me = "11111111-1111-4111-8111-111111111111"
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('p1',?,'s1','/',NULL,?,CURRENT_TIMESTAMP),('p2','22222222-2222-4222-8222-222222222222','s2','/',NULL,?,CURRENT_TIMESTAMP)", me, today, today)
		assertEquals(2L, metrics.today().visitors)
		mvc.perform(post("/admin/exclusions").param("visitorId", me).param("label", "맥북 크롬").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(1L, metrics.today().visitors, "등록한 브라우저는 과거 기록까지 빠진다")
		mvc.perform(post("/admin/exclusions").param("visitorId", "not-a-uuid").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/exclusions/remove").param("visitorId", me).with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(2L, metrics.today().visitors)
	}
	@Test fun funnelCountsEachVisitorOnceAndComparesNewVisitorsWhoTurnedOnAlertsEarly() {
		val today = Kst.today()
		val d = { n: Long -> today.minusDays(n) }
		// v1: 20일 전 첫 방문, 첫날 알림 켬, 10일 뒤(10일 전) 다시 옴 → 알림 그룹·재방문
		// v2: 20일 전 첫 방문, 다시 오지 않음 → 비교 그룹·재방문 아님
		// v3: 20일 전 첫 방문, 12일 뒤(8일 전) 다시 와서 그때 알림 켬 → 알림 그룹이 아님(재방문하면서 켬)
		// v4: 50일 전에도 왔던 기존 방문자 → 신규가 아님 / v5: 오늘 첫 방문 → 아직 판단 못 함
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('f1','v1','s1','/',NULL,?,CURRENT_TIMESTAMP),('f2','v1','s2','/',NULL,?,CURRENT_TIMESTAMP),('f3','v2','s3','/',NULL,?,CURRENT_TIMESTAMP)," +
			"('f4','v3','s4','/',NULL,?,CURRENT_TIMESTAMP),('f5','v3','s5','/',NULL,?,CURRENT_TIMESTAMP),('f6','v4','s6','/',NULL,?,CURRENT_TIMESTAMP),('f7','v4','s7','/',NULL,?,CURRENT_TIMESTAMP),('f8','v5','s8','/',NULL,?,CURRENT_TIMESTAMP)",
			d(20), d(10), d(20), d(20), d(8), d(50), d(20), today)
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('g1','v1','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP),('g2','v1','favorite_add','apt/H1/P1',?,CURRENT_TIMESTAMP),('g3','v1','push_enable','tool/push',?,CURRENT_TIMESTAMP)," +
			"('g4','v3','alert_enable','apt/H1/P1',?,CURRENT_TIMESTAMP),('g5','v5','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP)",
			d(20), d(20), d(20), d(8), today)
		val funnel = metrics.funnel(28, today)
		assertEquals(listOf(5L, 2L, 1L, 2L), funnel.steps.map { it.visitors }, "최근 28일 방문 5 → 상세 2 → 저장 1 → 알림 2")
		assertEquals(3L, funnel.returnBase, "신규는 v1·v2·v3 (v4는 기존, v5는 아직)")
		assertEquals(66L, funnel.returnRate, "v1(10일 뒤)·v3(12일 뒤)만 7~13일 안에 다시 왔다")
		assertEquals(1L, funnel.alertBase, "첫 주에 알림 켠 신규는 v1뿐(v3는 재방문하면서 켬)")
		assertEquals(100L, funnel.alertReturnRate)
		assertEquals(2L, funnel.otherBase)
		assertEquals(50L, funnel.otherReturnRate)
		mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("방문 → 알림 흐름")))
	}
}

class MetricsRulesTest {
	@Test fun sourceLabelsUseWholeDomains() {
		assertEquals("검색 · 구글", MetricsService.sourceLabel("www.google.co.kr"))
		assertEquals("네이버 카페", MetricsService.sourceLabel("m.cafe.naver.com"))
		assertEquals("dropbox.com", MetricsService.sourceLabel("dropbox.com"), "x.com 같은 짧은 도메인이 부분 문자열로 잡히지 않는다")
		assertEquals(LocalDate.of(2026, 11, 9), MetricsService.weekStart(LocalDate.of(2026, 11, 15)))
		assertTrue(MetricsService.Week(LocalDate.MIN, LocalDate.MIN, 3, 0, 0, 1, 0, 0, 0, 0).returningRate == 33L)
	}

}
