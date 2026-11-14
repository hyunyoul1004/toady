package com.toady.admin.web

import com.toady.admin.Kst
import com.toady.admin.announcements.AnnouncementService
import com.toady.admin.comments.CommentService
import com.toady.admin.jobs.JobKind
import com.toady.admin.jobs.SchedulerClient
import com.toady.admin.members.MemberService
import com.toady.admin.metrics.ExclusionService
import com.toady.admin.metrics.MetricsService
import com.toady.admin.operations.OperationsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal
import java.time.LocalDate

/**
 * 관리자 화면. 변경 요청(POST) 뒤에는 리다이렉트해 새로고침에 의한 재전송을 막고, 결과는 플래시 메시지로 알린다.
 * 목록의 검색 조건은 주소(쿼리)에 두고, 조치 후에는 next(관리자 내부 경로)로 같은 화면에 돌아온다.
 */
@Controller
class AdminController(
	private val metrics: MetricsService,
	private val exclusions: ExclusionService,
	private val members: MemberService,
	private val comments: CommentService,
	private val announcements: AnnouncementService,
	private val operations: OperationsService,
	private val scheduler: SchedulerClient,
) {
	@GetMapping("/") fun root() = "redirect:/admin/"
	@GetMapping("/admin/login") fun login() = "admin/login"

	@GetMapping("/admin", "/admin/")
	fun dashboard(model: Model, user: Principal): String {
		model.addAttribute("today", metrics.today())
		model.addAttribute("daily", metrics.daily())
		model.addAttribute("report", metrics.report())
		model.addAttribute("topAnnouncements", metrics.topAnnouncements())
		model.addAttribute("topPages", metrics.topPages())
		model.addAttribute("mapTools", metrics.mapTools())
		model.addAttribute("funnel", metrics.funnel())
		model.addAttribute("signups", metrics.recentSignups())
		model.addAttribute("recentComments", comments.search(null, "visible", 0, 6).items)
		model.addAttribute("exclusions", exclusions.list())
		model.addAttribute("exclusionReady", exclusions.available())
		return page(model, user, "dashboard", "admin/dashboard")
	}

	/** 이 브라우저(관리자 화면이 읽은 방문자 ID)를 지표에서 뺀다. 과거 기록에도 적용된다. */
	@PostMapping("/admin/exclusions")
	fun exclude(@RequestParam visitorId: String, @RequestParam(defaultValue = "") label: String, user: Principal, redirect: RedirectAttributes): String {
		when (exclusions.add(visitorId, label, user.name)) {
			"ADDED" -> redirect.addFlashAttribute("notice", "이 브라우저의 방문을 지표에서 뺐습니다(과거 기록 포함).")
			"EXISTS" -> redirect.addFlashAttribute("notice", "이미 제외된 브라우저입니다.")
			"UNAVAILABLE" -> redirect.addFlashAttribute("error", "제외 목록 테이블이 없습니다. database.sql 20번과 관리자 DB 권한(19번)을 적용하세요.")
			else -> redirect.addFlashAttribute("error", "이 브라우저에는 사이트 방문 기록이 없습니다. 먼저 www.toady.co.kr을 한 번 연 뒤 다시 누르세요.")
		}
		return "redirect:/admin/#exclusions"
	}

	@PostMapping("/admin/exclusions/remove")
	fun include(@RequestParam visitorId: String, user: Principal, redirect: RedirectAttributes): String {
		flash(redirect, exclusions.remove(visitorId, user.name), "제외를 풀었습니다. 이 브라우저의 방문을 다시 셉니다.", "제외 목록에 없는 브라우저입니다.")
		return "redirect:/admin/#exclusions"
	}

	@GetMapping("/admin/announcements")
	fun announcements(@RequestParam(required = false) q: String?, @RequestParam(defaultValue = "all") type: String,
	                  @RequestParam(defaultValue = "all") status: String, @RequestParam(defaultValue = "0") page: Int, model: Model, user: Principal): String {
		model.addAttribute("result", announcements.search(q, type, status, page))
		model.addAttribute("types", AnnouncementService.TYPES.values)
		model.addAttribute("q", q ?: ""); model.addAttribute("type", type); model.addAttribute("status", status)
		return page(model, user, "announcements", "admin/announcements")
	}

	// 경로 변수는 화면 모델에도 노출되므로 플래시 메시지(notice)와 겹치지 않는 이름을 쓴다.
	@GetMapping("/admin/announcements/{type}/{house}/{pblanc}")
	fun announcement(@PathVariable type: String, @PathVariable house: String, @PathVariable("pblanc") notice: String,
	                 @RequestParam(defaultValue = "all") state: String, model: Model, user: Principal): String {
		val detail = announcements.detail(type, house, notice) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		model.addAttribute("detail", detail)
		model.addAttribute("comments", comments.search(null, state, 0, 100, announcementId = detail.row.id))
		model.addAttribute("state", state)
		return page(model, user, "announcements", "admin/announcement")
	}

	@GetMapping("/admin/members")
	fun members(@RequestParam(required = false) q: String?, @RequestParam(defaultValue = "all") filter: String,
	            @RequestParam(defaultValue = "0") page: Int, model: Model, user: Principal): String {
		model.addAttribute("result", members.search(q, filter, page))
		model.addAttribute("q", q ?: ""); model.addAttribute("filter", filter)
		return page(model, user, "members", "admin/members")
	}

	@GetMapping("/admin/members/{seq}")
	fun member(@PathVariable seq: Long, model: Model, user: Principal): String {
		val detail = members.detail(seq) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		model.addAttribute("detail", detail)
		model.addAttribute("comments", comments.search(null, "all", 0, 100, authorSeq = seq))
		return page(model, user, "members", "admin/member")
	}

	@PostMapping("/admin/members/{seq}/lock")
	fun lock(@PathVariable seq: Long, @RequestParam locked: Boolean, @RequestParam(defaultValue = "") next: String,
	         user: Principal, redirect: RedirectAttributes): String {
		val changed = members.setLocked(seq, locked, user.name)
		flash(redirect, changed, if (locked) "회원을 잠갔습니다. 다음 로그인부터 막힙니다." else "회원 잠금을 풀었습니다.", "이미 같은 상태이거나 탈퇴한 회원입니다.")
		return "redirect:" + safeNext(next, "/admin/members")
	}

	@GetMapping("/admin/comments")
	fun comments(@RequestParam(required = false) q: String?, @RequestParam(defaultValue = "visible") state: String,
	             @RequestParam(defaultValue = "0") page: Int, model: Model, user: Principal): String {
		model.addAttribute("result", comments.search(q, state, page))
		model.addAttribute("q", q ?: ""); model.addAttribute("state", state)
		return page(model, user, "comments", "admin/comments")
	}

	@PostMapping("/admin/comments/{seq}/hide")
	fun hide(@PathVariable seq: Long, @RequestParam(defaultValue = "") next: String, user: Principal, redirect: RedirectAttributes): String {
		val changed = comments.hide(seq, user.name)
		flash(redirect, changed > 0, if (changed > 1) "댓글과 답글 ${changed - 1}개를 숨겼습니다." else "댓글을 숨겼습니다.", "이미 숨긴 댓글입니다.")
		return "redirect:" + safeNext(next, "/admin/comments")
	}

	@PostMapping("/admin/comments/{seq}/restore")
	fun restore(@PathVariable seq: Long, @RequestParam(defaultValue = "") next: String, user: Principal, redirect: RedirectAttributes): String {
		when (comments.restore(seq, user.name)) {
			CommentService.RestoreResult.RESTORED -> redirect.addFlashAttribute("notice", "댓글을 되살렸습니다.")
			CommentService.RestoreResult.PARENT_HIDDEN -> redirect.addFlashAttribute("error", "원댓글이 숨겨져 있어 답글만 되살릴 수 없습니다. 원댓글부터 되살리세요.")
			CommentService.RestoreResult.NOT_FOUND -> redirect.addFlashAttribute("error", "되살릴 댓글이 없습니다.")
			CommentService.RestoreResult.NOT_ADMIN_HIDDEN -> redirect.addFlashAttribute("error", "작성자가 직접 지운 댓글이거나 숨김 기록이 없어 되살릴 수 없습니다(database.sql 21번 적용 전 숨긴 댓글 포함).")
		}
		return "redirect:" + safeNext(next, "/admin/comments")
	}

	@GetMapping("/admin/operations")
	fun operations(model: Model, user: Principal): String {
		model.addAttribute("report", operations.report())
		return page(model, user, "operations", "admin/operations")
	}

	@GetMapping("/admin/jobs")
	fun jobs(model: Model, user: Principal): String {
		model.addAttribute("kinds", JobKind.entries)
		model.addAttribute("configured", scheduler.configured)
		try {
			model.addAttribute("state", scheduler.state())
		} catch (failure: SchedulerClient.SchedulerException) {
			model.addAttribute("schedulerError", failure.message)
		}
		model.addAttribute("todayDate", Kst.today()); model.addAttribute("defaultStart", Kst.today().minusDays(90))
		return page(model, user, "jobs", "admin/jobs")
	}

	@PostMapping("/admin/jobs")
	fun start(@RequestParam kind: JobKind, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
	          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate, user: Principal, redirect: RedirectAttributes) =
		schedulerAction(redirect) { "수집을 시작했습니다. 작업 ID: " + scheduler.start(kind.name, startDate, endDate, user.name) }

	@PostMapping("/admin/jobs/{id}/cancel")
	fun cancel(@PathVariable id: String, redirect: RedirectAttributes) = schedulerAction(redirect) { scheduler.cancel(id) }

	@PostMapping("/admin/jobs/{id}/retry")
	fun retry(@PathVariable id: String, user: Principal, redirect: RedirectAttributes) =
		schedulerAction(redirect) { "같은 기간으로 새 작업을 시작했습니다. 작업 ID: " + scheduler.retry(id, user.name) }

	// HTML 체크박스는 해제하면 값이 전송되지 않으므로 미전송을 false로 본다.
	@PostMapping("/admin/schedules/{kind}")
	fun schedule(@PathVariable kind: JobKind, @RequestParam(defaultValue = "false") enabled: Boolean, @RequestParam cron: String,
	             @RequestParam lookbackDays: Int, redirect: RedirectAttributes) =
		schedulerAction(redirect) { scheduler.saveSchedule(kind.name, enabled, cron, lookbackDays) }

	private fun schedulerAction(redirect: RedirectAttributes, action: () -> String): String {
		try {
			redirect.addFlashAttribute("notice", action())
		} catch (failure: SchedulerClient.SchedulerException) {
			redirect.addFlashAttribute("error", failure.message)
		}
		return "redirect:/admin/jobs"
	}

	private fun page(model: Model, user: Principal, menu: String, view: String): String {
		model.addAttribute("username", user.name); model.addAttribute("menu", menu)
		return view
	}
	private fun flash(redirect: RedirectAttributes, ok: Boolean, success: String, failure: String) {
		redirect.addFlashAttribute(if (ok) "notice" else "error", if (ok) success else failure)
	}

	companion object {
		/** 조치 후 돌아갈 관리자 내부 경로. 외부 주소(//evil 등)로는 보내지 않는다. */
		// 쿼리에는 Thymeleaf가 인코딩하지 않는 문자(@ : / ? ! * ' ( ) ~ ,)도 올 수 있다(예: 이메일 검색). 역슬래시·공백·줄바꿈은 받지 않는다.
		private val NEXT = Regex("/admin/[A-Za-z0-9/_\\-]*(\\?[A-Za-z0-9%=&_.+\\-@:/?!*'()~,$;]*)?")
		fun safeNext(next: String, fallback: String) = if (next.length <= 300 && NEXT.matches(next) && !next.contains("//")) next else fallback
	}
}

/** 배포 확인용 상태 점검. 앞단 nginx는 /internal을 외부로 내보내지 않는다. */
@RestController
class HealthController(private val jdbc: JdbcTemplate) {
	@GetMapping("/internal/health")
	fun health(): ResponseEntity<Map<String, String>> = try {
		jdbc.queryForObject("SELECT 1", Int::class.java)
		ResponseEntity.ok(mapOf("status" to "UP"))
	} catch (failure: Exception) {
		ResponseEntity.status(503).body(mapOf("status" to "DOWN"))
	}
}
