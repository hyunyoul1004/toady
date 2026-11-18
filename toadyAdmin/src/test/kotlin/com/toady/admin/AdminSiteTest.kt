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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 관리자 사이트: 로그인 보호·CSRF·회원 잠금·댓글 숨김/복구·화면 렌더링·스케줄러 연결 실패 안내. 외부 연결은 쓰지 않는다. */
@SpringBootTest(properties = [
	"spring.datasource.url=jdbc:h2:mem:admin;MODE=MariaDB;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
	"spring.datasource.username=sa", "spring.datasource.password=",
	"spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:admin-schema.sql",
	"toady.admin.username=operator", "toady.admin.password=local-test-password",
	"toady.admin.remember-key=test-remember-key-0123456789abcdef0123456789",
	// 로컬 .env.local의 API 토큰과 상관없이, API 내부 호출이 설정되지 않은 상태로 시험한다.
	"toady.api.token=", "toady.api.base-url=http://127.0.0.1:9",
	// 닫힌 포트: 스케줄러에 연결하지 못했을 때의 안내를 확인한다.
	"toady.scheduler.base-url=http://127.0.0.1:9", "toady.scheduler.token=test-internal-token-0123456789abcdef",
])
@AutoConfigureMockMvc
class AdminSiteTest {
	@Autowired lateinit var mvc: MockMvc
	@Autowired lateinit var jdbc: JdbcTemplate
	@Autowired lateinit var metrics: MetricsService
	@Autowired lateinit var comments: com.toady.admin.comments.CommentService
	@Autowired lateinit var insights: com.toady.admin.insights.InsightService
	private val admin = user("operator").roles("ADMIN")

	@BeforeEach fun setup() {
		listOf("TB_USER", "TB_LTTOT_PBLANC_COMMENT", "TB_FAVORITE", "TB_PAGE_VIEW", "TB_USAGE_EVENT", "TB_APT_LTTOT_PBLANC_DETAIL", "TB_OPT_LTTOT_PBLANC_DETAIL",
			"TB_APT_LTTOT_PBLANC_MDL", "TB_RATE_POLL", "TB_ANALYTICS_EXCLUDED_VISITOR", "TB_COMMENT_ADMIN_HIDE",
			"TB_USER_REPORT", "TB_DM_MESSAGE", "TB_DM_THREAD", "TB_DM_SETTING",
			"TB_ADMIN_AUDIT", "TB_USER_SANCTION", "TB_MODERATION_RULE", "TB_ANNOUNCEMENT_OVERRIDE", "TB_MAIL_ATTEMPT", "TB_REMINDER_DELIVERY",
			"TB_SITE_NOTICE", "TB_FAQ_ITEM", "TB_LIVING_AREA", "TB_APP_SETTING", "TB_SEARCH_STAT", "TB_COMMENT_OFFICIAL", "TB_MEMBER_MEMO", "TB_MEMBER_VISIT_DAY").forEach { jdbc.update("DELETE FROM $it") }
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

	@Test fun rememberMeKeepsTheAdminSignedInAfterTheSessionEndsUntilLogout() {
		// 로그인 유지를 고르지 않으면 쿠키를 만들지 않는다.
		val plain = mvc.perform(post("/admin/login").with(csrf()).param("username", "operator").param("password", "local-test-password").with { it.remoteAddr = "198.51.100.20"; it })
			.andExpect(redirectedUrl("/admin/")).andReturn().response
		assertEquals(null, plain.getCookie("TOADY_ADMIN_REMEMBER"))

		val remembered = mvc.perform(post("/admin/login").with(csrf()).param("username", "operator").param("password", "local-test-password").param("remember", "true")
			.with { it.remoteAddr = "198.51.100.21"; it }).andExpect(redirectedUrl("/admin/")).andReturn().response.getCookie("TOADY_ADMIN_REMEMBER")
		assertTrue(remembered != null && remembered.maxAge == 14 * 24 * 60 * 60 && remembered.isHttpOnly, "14일짜리 HttpOnly 쿠키")
		assertEquals("Strict", remembered!!.getAttribute("SameSite"))
		val loginPage = mvc.perform(get("/admin/login")).andReturn().response.contentAsString
		assertTrue(loginPage.contains("name=\"remember\""), "키가 있으면 체크박스를 보여 준다")
		assertTrue(loginPage.contains("이 브라우저에서 14일"), "안내 기간은 설정값(remember-days)을 따른다")
		// 키가 없거나 짧으면 로그인 유지를 끈다(비밀번호로 키를 만들지 않는다).
		assertFalse(com.toady.admin.security.SecurityConfig.rememberEnabled("") || com.toady.admin.security.SecurityConfig.rememberEnabled("short"))
		// 세션 없이 쿠키만으로 다시 로그인된다(세션 30분이 지난 경우와 같다).
		mvc.perform(get("/admin/members").cookie(remembered)).andExpect(status().isOk)
		// 서명이 맞지 않는 쿠키는 받지 않는다. 값을 뒤집으면 '%3D' 같은 인코딩 조각이 깨져 가끔 다른 오류가 나므로, 가운데 영문 한 글자만 바꾼다.
		val value = remembered!!.value
		val at = (value.length / 2 until value.length).first { value[it].isLetterOrDigit() }
		val tampered = value.substring(0, at) + (if (value[at] == 'A') 'B' else 'A') + value.substring(at + 1)
		mvc.perform(get("/admin/members").cookie(jakarta.servlet.http.Cookie("TOADY_ADMIN_REMEMBER", tampered)))
			.andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/admin/login"))
		// 배포·재시작으로 관리자 비밀번호 해시(BCrypt, 매번 새 솔트)가 새로 만들어져도 쿠키는 유효하다.
		val restartedUsers = com.toady.admin.security.SecurityConfig().adminUsers("operator", "local-test-password")
		val restarted = com.toady.admin.security.SecurityConfig().adminRememberMe(restartedUsers, "operator", "local-test-password", "test-remember-key-0123456789abcdef0123456789", 14, false)
		val request = org.springframework.mock.web.MockHttpServletRequest().apply { setCookies(remembered) }
		assertTrue(restarted.autoLogin(request, org.springframework.mock.web.MockHttpServletResponse()) != null, "재시작 뒤에도 로그인 유지")
		// 비밀번호를 바꾸면 기존 쿠키는 풀린다.
		val changed = com.toady.admin.security.SecurityConfig().adminRememberMe(
			com.toady.admin.security.SecurityConfig().adminUsers("operator", "changed-password-123"), "operator", "changed-password-123", "test-remember-key-0123456789abcdef0123456789", 14, false)
		val request2 = org.springframework.mock.web.MockHttpServletRequest().apply { setCookies(remembered) }
		assertEquals(null, changed.autoLogin(request2, org.springframework.mock.web.MockHttpServletResponse()))
		// 로그아웃하면 쿠키를 지운다.
		val cleared = mvc.perform(post("/admin/logout").with(csrf()).cookie(remembered)).andReturn().response.getCookie("TOADY_ADMIN_REMEMBER")
		assertTrue(cleared != null && cleared.maxAge == 0, "로그아웃은 로그인 유지 쿠키도 지운다")
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
	@Test fun reportsShowOnlyTheReportedMessageContextAndCanBeClosedWithASuspension() {
		jdbc.update("INSERT INTO TB_DM_THREAD (THREAD_SEQ,USER_LOW,USER_HIGH,STARTED_BY,LAST_MESSAGE_SEQ,REG_DT) VALUES (5,1,2,2,105,CURRENT_TIMESTAMP)")
		// 신고된 쪽지(103)의 앞 두 통·뒤 두 통만 보이고, 그보다 먼 쪽지(100·106)는 보이지 않는다.
		listOf(100 to "아주 오래된 대화", 101 to "안녕하세요", 102 to "네", 103 to "통장 양도하실래요", 104 to "아니요", 105 to "다시 말씀드리면", 106 to "나중 대화").forEach { (seq, text) ->
			jdbc.update("INSERT INTO TB_DM_MESSAGE (MESSAGE_SEQ,THREAD_SEQ,SENDER_SEQ,CONTENT,REG_DT) VALUES (?,5,?,?,CURRENT_TIMESTAMP)", seq, if (seq % 2 == 1) 2 else 1, text)
		}
		jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ,TARGET_USER_SEQ,TARGET_TYPE,TARGET_SEQ,REASON,STATUS,REG_DT) VALUES (1,2,'MESSAGE',103,'TRADE','OPEN',CURRENT_TIMESTAMP)")
		jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ,TARGET_USER_SEQ,TARGET_TYPE,TARGET_SEQ,REASON,DETAIL,STATUS,REG_DT) VALUES (2,1,'COMMENT',10,'SPAM','<b>광고</b>','OPEN',CURRENT_TIMESTAMP)")
		val html = mvc.perform(get("/admin/reports").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("통장 양도하실래요") && html.contains("안녕하세요") && html.contains("아니요"))
		assertFalse(html.contains("아주 오래된 대화") || html.contains("나중 대화"), "신고와 먼 쪽지는 열람하지 않는다")
		assertTrue(html.contains("&lt;b&gt;광고&lt;/b&gt;"), "신고 설명은 글자 그대로 표시한다")
		assertTrue(html.contains("nav-count"), "메뉴에 대기 신고 수를 보여 준다")

		mvc.perform(post("/admin/members/2/dm-suspend").param("days", "7").param("next", "/admin/reports?status=open&page=0").with(admin).with(csrf()))
			.andExpect(redirectedUrl("/admin/reports?status=open&page=0")).andExpect(flash().attributeExists("notice"))
		assertTrue(jdbc.queryForObject("SELECT SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=2", java.sql.Timestamp::class.java)!!.toLocalDateTime().isAfter(Kst.now().plusDays(6)))
		mvc.perform(post("/admin/members/2/dm-suspend").param("days", "3").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		// 없는 회원·탈퇴 회원에게는 정지 설정을 만들지 않는다.
		mvc.perform(post("/admin/members/999/dm-suspend").param("days", "7").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/members/3/dm-suspend").param("days", "7").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM TB_DM_SETTING WHERE USER_SEQ IN (3, 999)", Int::class.java))
		val report = jdbc.queryForObject("SELECT REPORT_SEQ FROM TB_USER_REPORT WHERE TARGET_TYPE='MESSAGE'", Long::class.java)
		// 알 수 없는 처리 값은 받지 않는다.
		mvc.perform(post("/admin/reports/$report/close").param("outcome", "weird").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		assertEquals("OPEN", jdbc.queryForObject("SELECT STATUS FROM TB_USER_REPORT WHERE REPORT_SEQ=?", String::class.java, report))
		mvc.perform(post("/admin/reports/$report/close").param("outcome", "done").param("note", "쪽지 7일 정지").param("sameTarget", "true").with(admin).with(csrf()))
			.andExpect(flash().attributeExists("notice"))
		assertEquals("DONE", jdbc.queryForObject("SELECT STATUS FROM TB_USER_REPORT WHERE REPORT_SEQ=?", String::class.java, report))
		assertEquals("operator", jdbc.queryForObject("SELECT HANDLED_BY FROM TB_USER_REPORT WHERE REPORT_SEQ=?", String::class.java, report))
		// 회원 화면에서 정지를 풀 수 있다.
		mvc.perform(get("/admin/members/2").with(admin)).andExpect(content().string(org.hamcrest.Matchers.containsString("쪽지 정지 해제")))
		mvc.perform(post("/admin/members/2/dm-suspend").param("days", "0").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(null, jdbc.queryForObject("SELECT SUSPENDED_UNTIL FROM TB_DM_SETTING WHERE USER_SEQ=2", java.sql.Timestamp::class.java))
	}
	@Test fun reportListsSeparateWaitingFromHandledAndExplainDeletedTargets() {
		// 대기 1건(보관 기간이 지나 지워진 쪽지), 처리된 2건(조치 완료·문제 없음)
		jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ,TARGET_USER_SEQ,TARGET_TYPE,TARGET_SEQ,REASON,STATUS,REG_DT) VALUES (1,2,'MESSAGE',777,'ABUSE','OPEN',CURRENT_TIMESTAMP)")
		jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ,TARGET_USER_SEQ,TARGET_TYPE,TARGET_SEQ,REASON,STATUS,ACTION_NOTE,HANDLED_BY,HANDLED_DT,REG_DT) VALUES (2,1,'COMMENT',10,'SPAM','DONE','확인 후 숨김 처리함','operator',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")
		jdbc.update("INSERT INTO TB_USER_REPORT (REPORTER_SEQ,TARGET_USER_SEQ,TARGET_TYPE,TARGET_SEQ,REASON,STATUS,HANDLED_BY,HANDLED_DT,REG_DT) VALUES (1,2,'USER',2,'ETC','DISMISSED','operator',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")

		val waiting = mvc.perform(get("/admin/reports").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(waiting.contains("보관 기간이 지나 지워진 쪽지"), "지워진 쪽지는 그렇게 알린다")
		assertFalse(waiting.contains("확인 후 숨김 처리함"), "대기 목록에는 처리된 신고가 없다")
		assertTrue(waiting.contains("조치 완료") && waiting.contains("문제 없음"), "처리 단추는 대기 신고에만 보인다")

		val handled = mvc.perform(get("/admin/reports").param("status", "closed").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(handled.contains("확인 후 숨김 처리함") && handled.contains("문제 없음"), "처리됨 목록에 처리 메모와 결과가 보인다")
		assertFalse(handled.contains("보관 기간이 지나 지워진 쪽지"))
		assertFalse(handled.contains("name=\"outcome\""), "처리된 신고에는 처리 단추가 없다")
		// 숨긴 원댓글(10)의 신고에는 숨김 사실을 보여 준다.
		mvc.perform(post("/admin/comments/10/hide").with(admin).with(csrf()))
		assertTrue(mvc.perform(get("/admin/reports").param("status", "all").with(admin)).andReturn().response.contentAsString.contains("이미 숨겨졌거나"))

		// 메뉴의 대기 수는 대기 신고만 센다.
		assertTrue(Regex("nav-count[^>]*>1<").containsMatchIn(waiting))
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
		// 관리자는 admin.toady.co.kr에 있으므로 서비스 링크는 www 절대 주소여야 한다.
		assertTrue(html.contains("href=\"https://www.toady.co.kr/?subscriptSeq=apt%2FH1%2FP1\""), "서비스에서 보기는 www 주소로 연다")
		assertTrue(html.contains("href=\"https://www.toady.co.kr/\""), "사이트 열기는 www 주소로 연다")
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
		mvc.perform(post("/admin/exclusions").param("visitorId", "11111111-1111-4111-8111-11111111111").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/exclusions/remove").param("visitorId", me).with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(2L, metrics.today().visitors)
	}

	@Test fun pastedVisitorIdFromTheSiteIsTrimmedAndLowercased() {
		// 관리자 도메인은 사이트(www)의 저장소를 못 읽으므로, 사이트 /my-visitor-id에서 복사한 값을 붙여 넣는다(앞뒤 공백·대문자 섞임).
		val html = mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("www.toady.co.kr/my-visitor-id에서 복사해 붙여 넣으세요") && html.contains("name=\"visitorId\" id=\"this-visitor\""), "붙여 넣기 입력칸과 안내")
		assertTrue(html.contains("https://www.toady.co.kr/my-visitor-id"), "사이트 방문자 ID 화면 링크")
		mvc.perform(post("/admin/exclusions").param("visitorId", "  AAAAAAAA-1111-4111-8111-BBBBBBBBBBBB \n").param("label", "휴대폰 사파리").with(admin).with(csrf()))
			.andExpect(flash().attributeExists("notice"))
		assertEquals("aaaaaaaa-1111-4111-8111-bbbbbbbbbbbb", jdbc.queryForObject("SELECT VISITOR_ID FROM TB_ANALYTICS_EXCLUDED_VISITOR", String::class.java))
		assertEquals("aaaaaaaa-1111-4111-8111-bbbbbbbbbbbb", jdbc.queryForObject("SELECT TARGET_ID FROM TB_ADMIN_AUDIT WHERE ACTION='VISITOR_EXCLUDE'", String::class.java))
		mvc.perform(post("/admin/exclusions").param("visitorId", "aaaaaaaa-1111-4111-8111-bbbbbbbbbbbb").with(admin).with(csrf()))
			.andExpect(flash().attribute("notice", "이미 제외된 브라우저입니다."))
	}
	@Test fun funnelCountsEachVisitorOnceAndComparesNewVisitorsWhoTurnedOnAlertsEarly() {
		val today = Kst.today()
		val d = { n: Long -> today.minusDays(n) }
		// v1: 20일 전 첫 방문, 첫날 알림 켬, 10일 뒤(10일 전) 다시 옴 → 알림 그룹·재방문
		// v2: 20일 전 첫 방문, 다시 오지 않음 → 비교 그룹·재방문 아님
		// v3: 20일 전 첫 방문, 12일 뒤(8일 전) 다시 와서 그때 알림 켬 → 알림 그룹이 아님(재방문하면서 켬)
		// v4: 50일 전에도 왔던 기존 방문자 → 신규가 아님 / v5: 오늘 첫 방문 → 아직 판단 못 함
		// v6: 10일 전 첫 방문, 아직 다시 오지 않음 → 판정 기간(7~13일 뒤)이 3일 남아 분모에서 뺀다(넣으면 재방문율이 낮게 나온다)
		// v7: 13일 전 첫 방문 → 판정 기간이 오늘 끝나 분모에 넣는다
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('f1','v1','s1','/',NULL,?,CURRENT_TIMESTAMP),('f2','v1','s2','/',NULL,?,CURRENT_TIMESTAMP),('f3','v2','s3','/',NULL,?,CURRENT_TIMESTAMP)," +
			"('f4','v3','s4','/',NULL,?,CURRENT_TIMESTAMP),('f5','v3','s5','/',NULL,?,CURRENT_TIMESTAMP),('f6','v4','s6','/',NULL,?,CURRENT_TIMESTAMP),('f7','v4','s7','/',NULL,?,CURRENT_TIMESTAMP),('f8','v5','s8','/',NULL,?,CURRENT_TIMESTAMP)," +
			"('f9','v6','s9','/',NULL,?,CURRENT_TIMESTAMP),('fa','v7','sa','/',NULL,?,CURRENT_TIMESTAMP)",
			d(20), d(10), d(20), d(20), d(8), d(50), d(20), today, d(10), d(13))
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('g1','v1','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP),('g2','v1','favorite_add','apt/H1/P1',?,CURRENT_TIMESTAMP),('g3','v1','push_enable','tool/push',?,CURRENT_TIMESTAMP)," +
			"('g4','v3','alert_enable','apt/H1/P1',?,CURRENT_TIMESTAMP),('g5','v5','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP)",
			d(20), d(20), d(20), d(8), today)
		val funnel = metrics.funnel(28, today)
		assertEquals(listOf(7L, 2L, 1L, 2L), funnel.steps.map { it.visitors }, "최근 28일 방문 7 → 상세 2 → 저장 1 → 알림 2")
		assertEquals(4L, funnel.returnBase, "신규는 v1·v2·v3·v7 (v4는 기존, v5·v6은 판정 기간이 남음)")
		assertEquals(50L, funnel.returnRate, "v1(10일 뒤)·v3(12일 뒤)만 7~13일 안에 다시 왔다")
		assertEquals(1L, funnel.alertBase, "첫 주에 알림 켠 신규는 v1뿐(v3는 재방문하면서 켬)")
		assertEquals(100L, funnel.alertReturnRate)
		assertEquals(3L, funnel.otherBase)
		assertEquals(33L, funnel.otherReturnRate)
		mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("방문 → 알림 흐름")))
	}
	@Test fun retentionMetricsUseThirtyDayAndFavoriteCycleWindows() {
		val today = Kst.today()
		val d = { n: Long -> today.minusDays(n) }
		// n1: 45일 전 첫 방문 → 40일 전 다시 옴(30일 안) / n2: 45일 전 한 번만
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('r1','n1','s1','/',NULL,?,CURRENT_TIMESTAMP),('r2','n1','s2','/',NULL,?,CURRENT_TIMESTAMP),('r3','n2','s3','/',NULL,?,CURRENT_TIMESTAMP)", d(45), d(40), d(45))
		// 회원 1: 50일 전 → 35일 전 다시 옴 / 회원 2: 50일 전 한 번
		jdbc.update("INSERT INTO TB_MEMBER_VISIT_DAY VALUES (1,?),(1,?),(2,?)", d(50), d(35), d(50))
		// n1이 45일 전 관심 저장한 공고의 발표일이 39일 전 → 그 사이(40일 전)에 다시 왔다
		jdbc.update("UPDATE TB_APT_LTTOT_PBLANC_DETAIL SET PRZWNER_PRESNATN_DE=? WHERE HOUSE_MANAGE_NO='H1'", d(39).toString())
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('u1','n1','favorite_add','apt/H1/P1',?,CURRENT_TIMESTAMP)", d(45))
		// 최근 28일: v1 상세 2번·푸시 켬, v2 상세 1번, 저장 1번, 결과 알림 열기 2번, 거절·끄기 각 1번
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('u2','v1','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP),('u3','v1','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP)," +
			"('u4','v1','push_enable','tool/push',?,CURRENT_TIMESTAMP),('u5','v2','detail_view','apt/H1/P1',?,CURRENT_TIMESTAMP),('u6','v2','favorite_add','apt/H1/P1',?,CURRENT_TIMESTAMP)," +
			"('u7','v1','push_open','tool/push-result',?,CURRENT_TIMESTAMP),('u8','v2','push_open','tool/push-result',?,CURRENT_TIMESTAMP),('u9','v3','push_denied','tool/push',?,CURRENT_TIMESTAMP),('u10','v1','push_disable','tool/push',?,CURRENT_TIMESTAMP)",
			d(3), d(2), d(2), d(1), d(1), d(1), today, today, today)
		val r = metrics.retention(today)
		assertEquals(null, r.error)
		assertEquals(2L, r.visitorBase); assertEquals(50L, r.visitorRate)
		assertEquals(2L, r.memberBase); assertEquals(50L, r.memberRate)
		assertEquals(1L, r.cycleBase); assertEquals(100L, r.cycleRate)
		assertEquals(1L, r.subscribeBase, "상세를 2번 이상 본 사람만 분모"); assertEquals(100L, r.subscribeRate)
		assertEquals(33L, r.saveRate)
		assertEquals(listOf("결과 즉시 알림" to 2L), r.opens.map { it.label to it.count })
		assertEquals(1L, r.denied); assertEquals(1L, r.disabled)
		assertEquals("알림 · 결과 즉시 알림", MetricsService.sourceLabel("push.result"))
		mvc.perform(get("/admin/").with(admin)).andExpect(status().isOk).andExpect(content().string(org.hamcrest.Matchers.containsString("재방문 핵심 지표")))
	}
	@Test fun sanctionsAreRecordedAndStoryBanCanBeLiftedEarly() {
		// 회원 상세를 열면 열람 기록이 남는다.
		mvc.perform(get("/admin/members/1").with(admin)).andExpect(status().isOk)
		assertEquals(1L, count("SELECT COUNT(*) FROM TB_ADMIN_AUDIT WHERE ACTION='MEMBER_VIEW' AND TARGET_ID='1'"))
		mvc.perform(post("/admin/members/1/sanctions").with(admin).with(csrf()).param("kind", "STORY_BAN").param("reason", "TRADE").param("days", "7").param("memo", "거래 유도"))
			.andExpect(redirectedUrl("/admin/members/1")).andExpect(flash().attributeExists("notice"))
		// 같은 회원에게 다시 정지하면 앞 정지를 끝내고 새 기간으로 바꾼다(겹쳐 쌓지 않는다).
		mvc.perform(post("/admin/members/1/sanctions").with(admin).with(csrf()).param("kind", "STORY_BAN").param("reason", "SPAM").param("days", "-1"))
		assertEquals(1L, count("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE USER_SEQ=1 AND KIND='STORY_BAN' AND CANCELED_DT IS NULL"))
		val active = count("SELECT SANCTION_SEQ FROM TB_USER_SANCTION WHERE USER_SEQ=1 AND CANCELED_DT IS NULL")
		assertEquals(null, jdbc.queryForObject("SELECT END_DT FROM TB_USER_SANCTION WHERE SANCTION_SEQ=?", java.sql.Timestamp::class.java, active), "무기한")
		val html = mvc.perform(get("/admin/members/1").with(admin)).andReturn().response.contentAsString
		assertTrue(html.contains("이야기 정지 무기한") && html.contains("거래 유도") && html.contains("관리자 작업 이력"))
		mvc.perform(post("/admin/members/1/sanctions/$active/cancel").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_USER_SANCTION WHERE USER_SEQ=1 AND CANCELED_DT IS NULL"))
		assertEquals(3L, count("SELECT COUNT(*) FROM TB_ADMIN_AUDIT WHERE TARGET_TYPE='MEMBER' AND TARGET_ID='1' AND ACTION IN ('SANCTION_ADD','SANCTION_CANCEL')"))
		// 없는 종류·기간은 받지 않는다.
		mvc.perform(post("/admin/members/1/sanctions").with(admin).with(csrf()).param("kind", "BAN_ALL").param("reason", "TRADE")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/members/1/sanctions").with(admin).with(csrf()).param("kind", "STORY_BAN").param("reason", "TRADE").param("days", "999")).andExpect(flash().attributeExists("error"))
		// 탈퇴 회원은 제재하지 않는다.
		mvc.perform(post("/admin/members/3/sanctions").with(admin).with(csrf()).param("kind", "WARNING").param("reason", "ETC")).andExpect(flash().attributeExists("error"))
	}

	@Test fun nicknameResetUsesAFreeDefaultNameWithoutCopyingTheOldOneToTheLog() {
		jdbc.update("INSERT INTO TB_USER (USER_SEQ,USER_EMAIL,USER_PASSWORD,USER_NIC_NAME,EMAIL_CERTIFY_YN) VALUES (4,'d@example.test','x','회원2','Y')")
		mvc.perform(post("/admin/members/2/sanctions").with(admin).with(csrf()).param("kind", "NICKNAME_RESET").param("reason", "NICKNAME"))
			.andExpect(flash().attributeExists("notice"))
		assertEquals("회원2a", jdbc.queryForObject("SELECT USER_NIC_NAME FROM TB_USER WHERE USER_SEQ=2", String::class.java), "이미 있는 닉네임이면 글자를 붙인다")
		assertEquals("이전 닉네임(6자)", jdbc.queryForObject("SELECT BEFORE_VALUE FROM TB_ADMIN_AUDIT WHERE ACTION='NICKNAME_RESET'", String::class.java), "원문 대신 길이만 남긴다")
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_ADMIN_AUDIT WHERE BEFORE_VALUE LIKE '%나래%' OR AFTER_VALUE LIKE '%나래%' OR NOTE LIKE '%나래%'"), "초기화 전 닉네임은 작업 이력 어디에도 없다")
	}

	@Test fun moderationRulesCanBeAddedTestedPausedAndDeleted() {
		mvc.perform(post("/admin/moderation").with(admin).with(csrf()).param("pattern", "통장 매매").param("scope", "ALL").param("action", "BLOCK"))
			.andExpect(flash().attributeExists("notice"))
		// 띄어쓰기만 다른 같은 말은 중복, 한 글자·이야기 표시(FLAG)는 받지 않는다.
		mvc.perform(post("/admin/moderation").with(admin).with(csrf()).param("pattern", "통장매매").param("scope", "ALL")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/moderation").with(admin).with(csrf()).param("pattern", "돈").param("scope", "ALL")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/moderation").with(admin).with(csrf()).param("pattern", "선입금").param("scope", "STORY").param("action", "FLAG")).andExpect(flash().attributeExists("error"))
		val tested = mvc.perform(get("/admin/moderation").param("test", "청약 통장매매 하실 분").with(admin)).andReturn().response.contentAsString
		assertTrue(tested.contains("<strong>통장 매매</strong>"), "띄어쓰기를 무시하고 찾는다")
		val seq = count("SELECT RULE_SEQ FROM TB_MODERATION_RULE")
		mvc.perform(post("/admin/moderation/$seq/toggle").with(admin).with(csrf()).param("enabled", "false")).andExpect(flash().attributeExists("notice"))
		assertEquals("N", jdbc.queryForObject("SELECT USE_YN FROM TB_MODERATION_RULE", String::class.java))
		mvc.perform(post("/admin/moderation/$seq/delete").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_MODERATION_RULE"))
		assertEquals(listOf("RULE_ADD", "RULE_TOGGLE", "RULE_DELETE"), jdbc.queryForList("SELECT ACTION FROM TB_ADMIN_AUDIT ORDER BY AUDIT_SEQ", String::class.java))
	}

	@Test fun announcementsCanBeHiddenWithANoticeAndRestored() {
		mvc.perform(post("/admin/announcements/apt/H1/P1/override").with(admin).with(csrf()).param("hidden", "true").param("message", "청약홈에서 취소된 공고입니다."))
			.andExpect(redirectedUrl("/admin/announcements/apt/H1/P1"))
		assertEquals("Y", jdbc.queryForObject("SELECT HIDDEN_YN FROM TB_ANNOUNCEMENT_OVERRIDE WHERE ANNOUNCEMENT_TYPE='APT' AND HOUSE_MANAGE_NO='H1'", String::class.java))
		val html = mvc.perform(get("/admin/announcements/apt/H1/P1").with(admin)).andReturn().response.contentAsString
		assertTrue(html.contains("서비스에서 숨긴 공고입니다") && html.contains("청약홈에서 취소된 공고입니다."))
		// 숨김을 풀고 안내도 비우면 원래대로(행 삭제).
		mvc.perform(post("/admin/announcements/apt/H1/P1/override").with(admin).with(csrf()).param("message", ""))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_ANNOUNCEMENT_OVERRIDE"))
		assertEquals(listOf("공개", "숨김 · 안내: 청약홈에서 취소된 공고입니다."), jdbc.queryForList("SELECT BEFORE_VALUE FROM TB_ADMIN_AUDIT WHERE ACTION='ANNOUNCEMENT_OVERRIDE' ORDER BY AUDIT_SEQ", String::class.java))
		mvc.perform(post("/admin/announcements/evil/H1/P1/override").with(admin).with(csrf())).andExpect(status().isNotFound)
	}

	@Test fun existingActionsAreWrittenToTheAuditLogAndMailStatusRenders() {
		mvc.perform(post("/admin/members/2/lock").with(admin).with(csrf()).param("locked", "true"))
		mvc.perform(post("/admin/comments/10/hide").with(admin).with(csrf()))
		assertEquals(listOf("MEMBER_LOCK", "COMMENT_HIDE"), jdbc.queryForList("SELECT ACTION FROM TB_ADMIN_AUDIT ORDER BY AUDIT_SEQ", String::class.java))
		val log = mvc.perform(get("/admin/audit").param("target", "COMMENT").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(log.contains(">이야기 10<") && !log.contains(">회원 2<"), "대상으로 거른다")
		val now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)
		jdbc.update("INSERT INTO TB_MAIL_ATTEMPT VALUES ('a',  'AUTH', ?), ('b', 'REMINDER', ?), ('c', 'REMINDER', ?)", now, now, now.minusHours(30))
		jdbc.update("INSERT INTO TB_REMINDER_DELIVERY VALUES (1, ?, 'SENT', ?)", com.toady.admin.Kst.today(), now)
		val mail = mvc.perform(get("/admin/mail").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(mail.contains("2 / 400"), "24시간 안의 발송만 센다")
		// 서비스 설정에서 바꾼 한도·예비분을 메일 현황에도 반영한다.
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.rollingLimit").param("value", "300")).andExpect(flash().attributeExists("notice"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.authReserve").param("value", "50")).andExpect(flash().attributeExists("notice"))
		val changed = mvc.perform(get("/admin/mail").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(changed.contains("2 / 300") && changed.contains("300통 · 50통") && changed.contains("상한 150"), "설정값과 그에 따른 가입 상한(한도의 절반)")
		assertTrue(changed.contains("서비스 설정</a>에서 바꾼 값을 따릅니다"))
	}

	@Test fun mailSettingsRejectAReserveThatIsNotBelowTheLimit() {
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.rollingLimit").param("value", "120")).andExpect(flash().attributeExists("notice"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.authReserve").param("value", "120"))
			.andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("인증 메일 예비분(120통)")))
		assertEquals(1L, count("SELECT COUNT(*) FROM TB_APP_SETTING"), "거부한 값은 저장하지 않는다")
		// 한도를 줄여 기존 예비분(환경변수 기본 100)보다 작거나 같아지는 것도 막는다.
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.rollingLimit").param("value", "100")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.signupLimit").param("value", "130")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "mail.authReserve").param("value", "60")).andExpect(flash().attributeExists("notice"))
		assertEquals("60", jdbc.queryForObject("SELECT SETTING_VALUE FROM TB_APP_SETTING WHERE SETTING_KEY='mail.authReserve'", String::class.java))
	}

	@Test fun resettingASettingWithoutDeletePermissionReturnsAMessageInsteadOf500() {
		jdbc.update("INSERT INTO TB_APP_SETTING VALUES ('dm.dailyMessages','30','operator',CURRENT_TIMESTAMP)")
		// 관리자 DB 계정에 DELETE 권한이 없을 때(database.sql 34번 미적용)처럼 DELETE만 실패하게 한다.
		val noDelete = object : JdbcTemplate(jdbc.dataSource!!) {
			override fun update(sql: String, vararg args: Any?): Int =
				if (sql.startsWith("DELETE")) throw org.springframework.dao.PermissionDeniedDataAccessException("DELETE command denied", java.sql.SQLException("denied")) else super.update(sql, *args)
		}
		val service = com.toady.admin.content.SettingService(noDelete, 400, 100, 0)
		val result = service.save("dm.dailyMessages", "", "operator")
		assertTrue(result.isFailure)
		assertTrue(result.exceptionOrNull()!!.message!!.contains("database.sql 34번"))
		assertEquals("30", jdbc.queryForObject("SELECT SETTING_VALUE FROM TB_APP_SETTING WHERE SETTING_KEY='dm.dailyMessages'", String::class.java))
	}

	@Test fun settingsAreValidatedAndCanReturnToDefaults() {
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "dm.dailyMessages").param("value", "999")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "dm.dailyMessages").param("value", "30")).andExpect(flash().attributeExists("notice"))
		assertEquals("30", jdbc.queryForObject("SELECT SETTING_VALUE FROM TB_APP_SETTING WHERE SETTING_KEY='dm.dailyMessages'", String::class.java))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "dm.requireStory").param("value", "maybe")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "unknown.key").param("value", "1")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "dm.dailyMessages").param("value", ""))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_APP_SETTING"), "비우면 기본값(행 삭제)")
		assertEquals(listOf("기본값", "30"), jdbc.queryForList("SELECT AFTER_VALUE FROM TB_ADMIN_AUDIT WHERE ACTION='SETTING_SAVE' ORDER BY AUDIT_SEQ DESC", String::class.java))
		assertTrue(mvc.perform(get("/admin/settings").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString.contains("하루 쪽지 수"))
	}

	@Test fun noticesOnlyAcceptSafeLinksAndCanBePaused() {
		val start = java.time.LocalDateTime.now().withNano(0).toString(); val end = java.time.LocalDateTime.now().plusDays(1).withNano(0).toString()
		mvc.perform(post("/admin/notices").with(admin).with(csrf()).param("message", "점검 안내").param("link", "javascript:alert(1)").param("start", start).param("end", end))
			.andExpect(flash().attributeExists("error"))
		for (unsafe in listOf("//evil.example", "/\\evil.example", "///evil.example"))
			mvc.perform(post("/admin/notices").with(admin).with(csrf()).param("message", "점검 안내").param("link", unsafe).param("start", start).param("end", end))
				.andExpect(flash().attributeExists("error"))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_SITE_NOTICE"), "다른 사이트로 가는 주소는 사이트 안 경로로 받지 않는다")
		mvc.perform(post("/admin/notices").with(admin).with(csrf()).param("message", "점검 안내").param("link", "/this-week").param("start", end).param("end", start))
			.andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/notices").with(admin).with(csrf()).param("message", "11월 20일 새벽 점검").param("link", "/this-week").param("level", "WARN").param("start", start).param("end", end))
			.andExpect(flash().attributeExists("notice"))
		val seq = count("SELECT NOTICE_SEQ FROM TB_SITE_NOTICE")
		assertTrue(mvc.perform(get("/admin/notices").with(admin)).andReturn().response.contentAsString.contains("노출 중"))
		mvc.perform(post("/admin/notices/$seq/toggle").with(admin).with(csrf()).param("enabled", "false"))
		assertEquals("N", jdbc.queryForObject("SELECT USE_YN FROM TB_SITE_NOTICE", String::class.java))
		mvc.perform(post("/admin/notices/$seq/delete").with(admin).with(csrf()))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_SITE_NOTICE"))
	}

	@Test fun faqAndLivingAreasCanBeEditedWithGuards() {
		mvc.perform(post("/admin/faq").with(admin).with(csrf()).param("id", "Bad ID").param("groupId", "basics").param("groupTitle", "청약 기초").param("question", "질문입니다?").param("answer", "답변 내용입니다. 충분히 길게"))
			.andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/faq").with(admin).with(csrf()).param("id", "test-q").param("groupId", "basics").param("groupTitle", "청약 기초").param("question", "특별공급은 몇 번 받을 수 있나요?").param("answer", "평생 한 번만 받을 수 있어요. [청약홈](https://www.applyhome.co.kr)"))
			.andExpect(flash().attributeExists("notice"))
		mvc.perform(post("/admin/faq/test-q").with(admin).with(csrf()).param("groupId", "basics").param("groupTitle", "청약 기초").param("question", "특별공급은 몇 번 받을 수 있나요?").param("answer", "세대 기준으로 평생 한 번이에요.").param("order", "5"))
		assertEquals("N", jdbc.queryForObject("SELECT USE_YN FROM TB_FAQ_ITEM WHERE ITEM_ID='test-q'", String::class.java), "체크박스를 끄면 숨김")
		// 공백 없는 짧은 검색어에는 시·군이 필요하다(다른 지역 같은 동 이름 오인 방지).
		mvc.perform(post("/admin/areas").with(admin).with(csrf()).param("slug", "godeok").param("label", "고덕").param("aliases", "고덕").param("terms", "고덕").param("description", "평택 고덕 생활권"))
			.andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/areas").with(admin).with(csrf()).param("slug", "godeok").param("label", "고덕").param("aliases", "고덕, 고덕신도시 ,고덕").param("terms", "고덕, 평택시 고덕동").param("scope", "평택").param("description", "평택 고덕 생활권"))
			.andExpect(flash().attributeExists("notice"))
		assertEquals("고덕,고덕신도시", jdbc.queryForObject("SELECT ALIASES FROM TB_LIVING_AREA WHERE SLUG='godeok'", String::class.java), "공백·중복 정리")
		mvc.perform(post("/admin/areas/godeok").with(admin).with(csrf()).param("label", "고덕국제").param("aliases", "고덕").param("terms", "평택시 고덕동").param("description", "평택 고덕 생활권").param("order", "1").param("enabled", "true"))
			.andExpect(flash().attributeExists("notice"))
		assertTrue(mvc.perform(get("/admin/areas").with(admin)).andReturn().response.contentAsString.contains("고덕국제"))
		assertTrue(mvc.perform(get("/admin/faq").with(admin)).andReturn().response.contentAsString.contains("특별공급은 몇 번"))
	}

	@Test fun locationsNeedParcelNumbersAndDataCheckFindsGaps() {
		jdbc.update("INSERT INTO TB_APT_LTTOT_PBLANC_DETAIL (HOUSE_MANAGE_NO,PBLANC_NO,HOUSE_NM,HSSPLY_ADRES,RCEPT_BGNDE,RCEPT_ENDDE) VALUES ('H2','P2','검단 블록 단지','인천광역시 서구 검단신도시 AA13-2블록',?,?)",
			com.toady.admin.Kst.today().plusDays(3).toString(), com.toady.admin.Kst.today().plusDays(4).toString())
		val queue = mvc.perform(get("/admin/locations").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(queue.contains("검단 블록 단지") && !queue.contains("역삼 새 단지"), "지번 있는 주소는 대기열에 없다")
		mvc.perform(post("/admin/locations").with(admin).with(csrf()).param("id", "apt/H2/P2").param("address", "인천 서구 검단신도시")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/locations").with(admin).with(csrf()).param("id", "apt/H2/P2").param("address", "인천광역시 서구 원당동 1234-5")).andExpect(flash().attributeExists("notice"))
		assertEquals("인천광역시 서구 원당동 1234-5", jdbc.queryForObject("SELECT LOCATION_ADDRESS FROM TB_ANNOUNCEMENT_OVERRIDE", String::class.java))
		// 숨김·안내를 비워도 확인 주소는 남는다.
		mvc.perform(post("/admin/announcements/apt/H2/P2/override").with(admin).with(csrf()).param("message", ""))
		assertEquals(1L, count("SELECT COUNT(*) FROM TB_ANNOUNCEMENT_OVERRIDE"))
		val check = mvc.perform(get("/admin/data-check").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(check.contains("검단 블록 단지") && check.contains("주택형·분양가 없음"), "APT 주택형 누락을 찾는다")
		// 스케줄러에 연결하지 못하면 다시 수집은 오류로 안내한다.
		mvc.perform(post("/admin/announcements/apt/H1/P1/recollect").with(admin).with(csrf())).andExpect(flash().attributeExists("error"))
	}

	@Test fun officialStoriesNeedTheOfficialAccountAndCanBePinned() {
		mvc.perform(post("/admin/announcements/apt/H1/P1/official").with(admin).with(csrf()).param("content", "정정 공고 안내입니다.")).andExpect(flash().attributeExists("error"))
		mvc.perform(post("/admin/settings").with(admin).with(csrf()).param("key", "official.userSeq").param("value", "1"))
		mvc.perform(post("/admin/announcements/apt/H1/P1/official").with(admin).with(csrf()).param("content", "정정 공고 안내입니다.").param("pinned", "true"))
			.andExpect(flash().attributeExists("notice"))
		val seq = count("SELECT COMMENT_SEQ FROM TB_COMMENT_OFFICIAL")
		assertEquals("Y", jdbc.queryForObject("SELECT PINNED_YN FROM TB_COMMENT_OFFICIAL", String::class.java))
		assertEquals(1L, count("SELECT CREATE_USER_SEQ FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=$seq"))
		mvc.perform(post("/admin/announcements/apt/H1/P1/official").with(admin).with(csrf()).param("content", "답글로 덧붙여요.").param("parentSeq", "$seq").param("pinned", "true"))
		assertEquals(1L, count("SELECT COUNT(*) FROM TB_COMMENT_OFFICIAL WHERE PINNED_YN='Y'"), "답글은 고정하지 않는다")
		val page = mvc.perform(get("/admin/announcements/apt/H1/P1").with(admin)).andReturn().response.contentAsString
		assertTrue(page.contains("운영팀 · 고정"))
		mvc.perform(post("/admin/comments/$seq/pin").with(admin).with(csrf()).param("pinned", "false")).andExpect(flash().attributeExists("notice"))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_COMMENT_OFFICIAL WHERE PINNED_YN='Y'"))
	}

	@Test fun memberSupportToolsAndBulkHide() {
		mvc.perform(post("/admin/members/2/memo").with(admin).with(csrf()).param("memo", "메일 문의: 인증 메일 미수신")).andExpect(flash().attributeExists("notice"))
		assertTrue(mvc.perform(get("/admin/members/2").with(admin)).andReturn().response.contentAsString.contains("인증 메일 미수신"))
		assertEquals("메모 추가(길이 16자)", jdbc.queryForObject("SELECT AFTER_VALUE FROM TB_ADMIN_AUDIT WHERE ACTION='MEMBER_MEMO'", String::class.java), "작업 이력에는 메모 본문을 복사하지 않는다")
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_ADMIN_AUDIT WHERE AFTER_VALUE LIKE '%미수신%' OR NOTE LIKE '%미수신%'"))
		mvc.perform(post("/admin/members/2/hide-all").with(admin).with(csrf())).andExpect(flash().attributeExists("notice"))
		assertEquals("Y", jdbc.queryForObject("SELECT DELETE_YN FROM TB_LTTOT_PBLANC_COMMENT WHERE COMMENT_SEQ=11", String::class.java))
		// API 토큰이 없으면 탈퇴 대행·인증 메일은 안내만 한다. 확인 문구가 없으면 시도하지 않는다.
		mvc.perform(post("/admin/members/2/withdraw").with(admin).with(csrf()).param("confirm", "")).andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("탈퇴 처리")))
		mvc.perform(post("/admin/members/2/withdraw").with(admin).with(csrf()).param("confirm", "탈퇴 처리")).andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("ADMIN_API_TOKEN")))
		mvc.perform(post("/admin/push").with(admin).with(csrf()).param("title", "알림 제목").param("body", "알림 내용입니다").param("url", "/this-week")).andExpect(flash().attribute("error", org.hamcrest.Matchers.containsString("전체 발송")))
		mvc.perform(post("/admin/push").with(admin).with(csrf()).param("title", "알림 제목").param("body", "알림 내용입니다").param("url", "https://evil.example")).andExpect(flash().attributeExists("error"))
	}

	@Test fun hidingAllOfAMembersStoriesIsAllOrNothing() {
		jdbc.update("INSERT INTO TB_LTTOT_PBLANC_COMMENT (COMMENT_SEQ,ANNOUNCEMENT_TYPE,HOUSE_MANAGE_NO,PBLANC_NO,CONTENT,CREATE_USER_SEQ) VALUES (12,'APT','H1','P1','둘째 글',1)")
		// 둘째 글을 숨길 때 DB 오류가 나게 해, 앞서 숨긴 첫 글(10)과 답글(11)·숨김 기록까지 되돌려지는지 본다.
		jdbc.execute("ALTER TABLE TB_LTTOT_PBLANC_COMMENT ADD CONSTRAINT TEST_FAIL_HIDE CHECK (COMMENT_SEQ<>12 OR DELETE_YN='N')")
		try {
			assertFailsWith<org.springframework.dao.DataAccessException> { comments.hideAllBy(1, "operator") }
		} finally { jdbc.execute("ALTER TABLE TB_LTTOT_PBLANC_COMMENT DROP CONSTRAINT TEST_FAIL_HIDE") }
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'"))
		assertEquals(0L, count("SELECT COUNT(*) FROM TB_COMMENT_ADMIN_HIDE"))
		// 오류가 없으면 원글·답글을 모두 숨기고 건마다 숨김 기록을 남긴다.
		assertEquals(3, comments.hideAllBy(1, "operator"))
		assertEquals(3L, count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE DELETE_YN='Y'"))
		assertEquals(3L, count("SELECT COUNT(*) FROM TB_COMMENT_ADMIN_HIDE WHERE ADMIN_NAME='operator'"))
	}

	@Test fun insightsShowSearchSharesAndStories() {
		jdbc.update("INSERT INTO TB_SEARCH_STAT VALUES (?, '운정', 'N', 5), (?, '고덕', 'Y', 3)", com.toady.admin.Kst.today(), com.toady.admin.Kst.today())
		jdbc.update("INSERT INTO TB_USAGE_EVENT (EVENT_ID,VISITOR_ID,ACTION_NAME,SUBJECT_ID,VISIT_DATE,REG_DT) VALUES ('e1','v1','share','apt/H1/P1',?,CURRENT_TIMESTAMP)", com.toady.admin.Kst.today())
		jdbc.update("UPDATE TB_LTTOT_PBLANC_COMMENT SET REG_DT=CURRENT_TIMESTAMP")
		val html = mvc.perform(get("/admin/insights").with(admin)).andExpect(status().isOk).andReturn().response.contentAsString
		assertTrue(html.contains("운정") && html.contains("고덕") && html.contains("역삼 새 단지"), html.take(200))
	}

	@Test fun messengerInflowSkipsSearchEnginesAndOperatorBrowsers() {
		val today = com.toady.admin.Kst.today()
		val me = "33333333-3333-4333-8333-333333333333"
		jdbc.update("INSERT INTO TB_PAGE_VIEW VALUES ('i1','u1','s1','/','search.naver.com',?,CURRENT_TIMESTAMP),('i2','u2','s2','/','m.search.daum.net',?,CURRENT_TIMESTAMP)," +
			"('i3','u3','s3','/','m.blog.naver.com',?,CURRENT_TIMESTAMP),('i4','u4','s4','/','talk.kakao.com',?,CURRENT_TIMESTAMP),('i5',?,'s5','/','band.us',?,CURRENT_TIMESTAMP)", today, today, today, today, me, today)
		jdbc.update("INSERT INTO TB_USAGE_EVENT VALUES ('j1',?,'share','apt/H1/P1',?,CURRENT_TIMESTAMP),('j2','u4','share','apt/H1/P1',?,CURRENT_TIMESTAMP)", me, today, today)
		jdbc.update("INSERT INTO TB_ANALYTICS_EXCLUDED_VISITOR VALUES (?,'운영자',CURRENT_TIMESTAMP)", me)
		val report = insights.report(30, true)
		assertEquals(setOf("m.blog.naver.com", "talk.kakao.com"), report.inflow!!.map { it.host }.toSet(), "검색 엔진·운영자 방문(band.us)은 빠진다")
		assertEquals(1L, report.shareTotal, "운영자 공유도 빠진다")
	}

	private fun count(sql: String): Long = jdbc.queryForObject(sql, Long::class.java) ?: 0
}

class MetricsRulesTest {
	@Test fun sourceLabelsUseWholeDomains() {
		assertEquals("검색 · 구글", MetricsService.sourceLabel("www.google.co.kr"))
		assertEquals("네이버 카페", MetricsService.sourceLabel("m.cafe.naver.com"))
		assertEquals("dropbox.com", MetricsService.sourceLabel("dropbox.com"), "x.com 같은 짧은 도메인이 부분 문자열로 잡히지 않는다")
		assertEquals(LocalDate.of(2026, 11, 9), MetricsService.weekStart(LocalDate.of(2026, 11, 15)))
		assertTrue(MetricsService.Week(LocalDate.MIN, LocalDate.MIN, 3, 0, 0, 1, 0, 0, 0, 0).returningRate == 33L)
	}

	@Test fun messengerHostsExcludeSearchEngines() {
		val messenger = { host: String -> com.toady.admin.insights.InsightService.isMessenger(host) }
		assertTrue(messenger("talk.kakao.com") && messenger("m.blog.naver.com") && messenger("cafe.daum.net") && messenger("band.us"))
		assertFalse(messenger("search.naver.com") || messenger("m.search.naver.com") || messenger("www.naver.com") || messenger("search.daum.net") || messenger("www.google.com"))
		assertFalse(messenger("notkakao.com"), "부분 문자열로 잡지 않는다")
	}

	@Test fun noticeLinksRejectProtocolRelativePaths() {
		val safe = com.toady.admin.content.NoticeService.SAFE_LINK
		assertTrue(safe.matches("/") && safe.matches("/this-week") && safe.matches("/announcements/apt/H1/P1") && safe.matches("https://www.toady.co.kr/faq"))
		assertFalse(safe.matches("//evil.example") || safe.matches("/\\evil.example") || safe.matches("javascript:alert(1)") || safe.matches("http://evil.example"))
	}

	@Test fun visitorIdsAreNormalizedFromPastedText() {
		assertEquals("aaaaaaaa-1111-4111-8111-bbbbbbbbbbbb", com.toady.admin.metrics.ExclusionService.normalize(" AAAAAAAA-1111-4111-8111-BBBBBBBBBBBB\t"))
		assertEquals(null, com.toady.admin.metrics.ExclusionService.normalize("toady.analytics.visitor"))
		assertEquals(null, com.toady.admin.metrics.ExclusionService.normalize("aaaaaaaa-1111-4111-8111-bbbbbbbbbbbb-extra"))
	}


}
