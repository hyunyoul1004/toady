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
	private val admin = user("operator").roles("ADMIN")

	@BeforeEach fun setup() {
		listOf("TB_USER", "TB_LTTOT_PBLANC_COMMENT", "TB_FAVORITE", "TB_PAGE_VIEW", "TB_USAGE_EVENT").forEach { jdbc.update("DELETE FROM $it") }
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
		mvc.perform(post("/admin/members/1/lock").param("locked", "true").param("back", "q=&filter=all&page=0").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/members?q=&filter=all&page=0")).andExpect(flash().attributeExists("notice"))
		assertEquals("Y", jdbc.queryForObject("SELECT LOCK_YN FROM TB_USER WHERE USER_SEQ=1", String::class.java))
		// 탈퇴 회원은 바꾸지 않고, 외부 주소로 돌려보내는 back 값은 무시한다.
		mvc.perform(post("/admin/members/3/lock").param("locked", "true").param("back", "//evil.example").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/members")).andExpect(flash().attributeExists("error"))
		assertEquals("N", jdbc.queryForObject("SELECT LOCK_YN FROM TB_USER WHERE USER_SEQ=3", String::class.java))
		mvc.perform(get("/admin/members").param("filter", "locked").with(admin)).andExpect(content().string(org.hamcrest.Matchers.containsString("a@example.test")))
	}

	@Test fun hidingARootCommentHidesRepliesAndReplyCannotBeRestoredAlone() {
		mvc.perform(get("/admin/comments").with(admin)).andExpect(status().isOk)
			// 댓글 내용은 글자 그대로(이스케이프) 표시한다.
			.andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("public-rental/H1/P1")))
		mvc.perform(post("/admin/comments/10/hide").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'", Int::class.java))
		mvc.perform(post("/admin/comments/11/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/comments/10/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		mvc.perform(post("/admin/comments/11/restore").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'", Int::class.java))
	}

	@Test fun dashboardOperationsAndJobsRenderEvenWhenTheSchedulerIsDown() {
		val today = Kst.today()
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('e1','v1','s1','/',?,?,CURRENT_TIMESTAMP),('e2','v1','s2','/',NULL,?,CURRENT_TIMESTAMP)", "www.google.com", today, today.minusDays(8))
		mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("검색 · 구글")))
		// 수집이 멈췄는지는 작업 이력(마지막 성공)으로 판단한다: 어제 성공 → 정상, 사흘 전 성공뿐 → 확인 필요.
		jdbc.update("DELETE FROM TB_SCHEDULER_ADMIN_JOB")
		jdbc.update("INSERT INTO TB_SCHEDULER_ADMIN_JOB (JOB_ID,JOB_KIND,JOB_SOURCE,START_DATE,END_DATE,STATUS,TOTAL_CHUNKS,REQUESTED_BY,MESSAGE,CREATED_AT,FINISHED_AT,HEARTBEAT_AT) VALUES ('j1','ANNOUNCEMENTS','SCHEDULE',?,?,'SUCCEEDED',1,'scheduler','ok',?,?,?),('j2','COMPETITION','SCHEDULE',?,?,'SUCCEEDED',1,'scheduler','ok',?,?,?)",
			today, today, Kst.now().minusHours(20), Kst.now().minusHours(20), Kst.now(), today, today, Kst.now().minusDays(3), Kst.now().minusDays(3), Kst.now())
		val html = mvc.perform(get("/admin/operations").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("공고 수집 상태") && html.contains("공고 · 주택형") && html.contains("확인 필요") && html.contains("정상"), html.take(200))
		mvc.perform(get("/admin/jobs").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("스케줄러에 연결하지 못했습니다")))
		mvc.perform(post("/admin/jobs").param("kind", "SCORES").param("startDate", "2020-01-01").param("endDate", "2020-01-02").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/jobs")).andExpect(flash().attribute("error", "스케줄러에 연결하지 못했습니다. 스케줄러가 실행 중인지 확인하세요."))
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
