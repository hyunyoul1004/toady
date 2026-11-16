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
import com.toady.admin.reports.ReportService
import com.toady.admin.audit.AuditService
import com.toady.admin.announcements.OverrideService
import com.toady.admin.members.SanctionService
import com.toady.admin.moderation.RuleService
import com.toady.admin.operations.MailStatusService
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
	private val reports: ReportService,
	private val audit: AuditService,
	private val sanctions: SanctionService,
	private val rules: RuleService,
	private val overrides: OverrideService,
	private val mail: MailStatusService,
	private val memos: com.toady.admin.support.MemberMemoService,
	private val api: com.toady.admin.support.ApiClient,
	private val quality: com.toady.admin.announcements.QualityService,
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
			"ADDED" -> { redirect.addFlashAttribute("notice", "이 브라우저의 방문을 지표에서 뺐습니다(과거 기록 포함)."); audit.record(user.name, "VISITOR_EXCLUDE", "VISITOR", visitorId, after = label) }
			"EXISTS" -> redirect.addFlashAttribute("notice", "이미 제외된 브라우저입니다.")
			"UNAVAILABLE" -> redirect.addFlashAttribute("error", "제외 목록 테이블이 없습니다. database.sql 20번과 관리자 DB 권한(19번)을 적용하세요.")
			else -> redirect.addFlashAttribute("error", "이 브라우저에는 사이트 방문 기록이 없습니다. 먼저 www.toady.co.kr을 한 번 연 뒤 다시 누르세요.")
		}
		return "redirect:/admin/#exclusions"
	}

	@PostMapping("/admin/exclusions/remove")
	fun include(@RequestParam visitorId: String, user: Principal, redirect: RedirectAttributes): String {
		val removed = exclusions.remove(visitorId, user.name)
		if (removed) audit.record(user.name, "VISITOR_INCLUDE", "VISITOR", visitorId)
		flash(redirect, removed, "제외를 풀었습니다. 이 브라우저의 방문을 다시 셉니다.", "제외 목록에 없는 브라우저입니다.")
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
		model.addAttribute("override", overrides.get(detail.row.id))
		model.addAttribute("overrideReady", overrides.available())
		model.addAttribute("history", audit.forTarget("ANNOUNCEMENT", detail.row.id))
		model.addAttribute("official", comments.officialIn(detail.row.id))
		model.addAttribute("locationAddress", quality.confirmedAddresses()[detail.row.id])
		model.addAttribute("needsLocation", !quality.hasParcel(detail.row.address))
		return page(model, user, "announcements", "admin/announcement")
	}

	/** 공고 숨김·정정 안내. 둘 다 비우면 원래대로 돌아간다. 공고 원본은 바꾸지 않는다. */
	@PostMapping("/admin/announcements/{type}/{house}/{pblanc}/override")
	fun saveOverride(@PathVariable type: String, @PathVariable house: String, @PathVariable("pblanc") notice: String,
	                 @RequestParam(defaultValue = "false") hidden: Boolean, @RequestParam(defaultValue = "") message: String,
	                 @RequestParam(defaultValue = "") memo: String, user: Principal, redirect: RedirectAttributes): String {
		val detail = announcements.detail(type, house, notice) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		val id = detail.row.id
		val (ok, before) = overrides.save(id, hidden, message, user.name)
		if (ok) {
			val after = overrides.get(id)
			audit.record(user.name, "ANNOUNCEMENT_OVERRIDE", "ANNOUNCEMENT", id, before?.summary ?: "공개", after?.summary ?: "공개", memo)
		}
		flash(redirect, ok, if (hidden) "공고를 숨겼습니다. 1분 안에 목록·검색·사이트맵에서 빠집니다." else "공고 안내를 저장했습니다. 1분 안에 서비스에 반영됩니다.",
			"저장하지 못했습니다. database.sql 26번 적용과 관리자 계정 권한을 확인하세요.")
		return "redirect:/admin/announcements/$id"
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
		model.addAttribute("dmSuspendedUntil", reports.suspendedUntil(seq)?.takeIf { it.isAfter(Kst.now()) })
		model.addAttribute("reportsReady", reports.available())
		model.addAttribute("sanctions", sanctions.list(seq))
		model.addAttribute("sanctionReady", sanctions.available())
		model.addAttribute("sanctionKinds", SanctionService.KINDS)
		model.addAttribute("sanctionReasons", SanctionService.REASONS)
		model.addAttribute("sanctionDays", SanctionService.DAYS)
		model.addAttribute("history", audit.forTarget("MEMBER", seq))
		model.addAttribute("memos", memos.list(seq)); model.addAttribute("memoReady", memos.available())
		model.addAttribute("apiConfigured", api.configured)
		// 개인정보(이메일 등)를 본 기록. 같은 관리자가 연달아 열어도 매번 남긴다.
		audit.record(user.name, "MEMBER_VIEW", "MEMBER", seq)
		return page(model, user, "members", "admin/member")
	}

	/** 단계별 제재: 경고·이야기 쓰기 정지·닉네임 초기화. */
	@PostMapping("/admin/members/{seq}/sanctions")
	fun addSanction(@PathVariable seq: Long, @RequestParam kind: String, @RequestParam reason: String, @RequestParam(defaultValue = "7") days: Int,
	                @RequestParam(defaultValue = "") memo: String, user: Principal, redirect: RedirectAttributes): String {
		val added = sanctions.add(seq, kind, reason, days, memo, user.name)
		if (added.outcome == SanctionService.Outcome.DONE) {
			val period = if (kind == "STORY_BAN") SanctionService.DAYS[days] else null
			audit.record(user.name, "SANCTION_ADD", "MEMBER", seq, after = listOfNotNull(SanctionService.KINDS[kind], period, SanctionService.REASONS[reason]).joinToString(" · "), note = memo)
			if (kind == "NICKNAME_RESET") audit.record(user.name, "NICKNAME_RESET", "MEMBER", seq, added.previousNickname, added.nickname, memo)
		}
		when (added.outcome) {
			SanctionService.Outcome.DONE -> redirect.addFlashAttribute("notice", when (kind) {
				"WARNING" -> "경고를 남겼습니다. 회원이 다음에 접속하면 안내가 보입니다."
				"STORY_BAN" -> "이야기 쓰기를 정지했습니다. 바로 적용됩니다."
				else -> "닉네임을 '${added.nickname}'(으)로 바꿨습니다. 회원에게 새 닉네임을 정하라는 안내가 보입니다."
			})
			SanctionService.Outcome.INVALID -> redirect.addFlashAttribute("error", "제재 종류·사유·기간을 확인하세요.")
			SanctionService.Outcome.NOT_ACTIVE_MEMBER -> redirect.addFlashAttribute("error", "탈퇴했거나 없는 회원입니다.")
			SanctionService.Outcome.UNAVAILABLE -> redirect.addFlashAttribute("error", "제재 테이블이 없습니다. database.sql 26번과 관리자 계정 권한을 적용하세요.")
		}
		return "redirect:/admin/members/$seq"
	}

	@PostMapping("/admin/members/{seq}/sanctions/{sanctionSeq}/cancel")
	fun cancelSanction(@PathVariable seq: Long, @PathVariable sanctionSeq: Long, @RequestParam(defaultValue = "") memo: String,
	                   user: Principal, redirect: RedirectAttributes): String {
		val ok = sanctions.cancel(seq, sanctionSeq, user.name)
		if (ok) audit.record(user.name, "SANCTION_CANCEL", "MEMBER", seq, after = "제재 #$sanctionSeq 해제", note = memo)
		flash(redirect, ok, "제재를 해제했습니다.", "이미 해제된 제재입니다.")
		return "redirect:/admin/members/$seq"
	}

	@PostMapping("/admin/members/{seq}/lock")
	fun lock(@PathVariable seq: Long, @RequestParam locked: Boolean, @RequestParam(defaultValue = "") next: String,
	         user: Principal, redirect: RedirectAttributes): String {
		val changed = members.setLocked(seq, locked, user.name)
		if (changed) audit.record(user.name, if (locked) "MEMBER_LOCK" else "MEMBER_UNLOCK", "MEMBER", seq)
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
		if (changed > 0) audit.record(user.name, "COMMENT_HIDE", "COMMENT", seq, after = if (changed > 1) "답글 ${changed - 1}개 포함" else null)
		flash(redirect, changed > 0, if (changed > 1) "댓글과 답글 ${changed - 1}개를 숨겼습니다." else "댓글을 숨겼습니다.", "이미 숨긴 댓글입니다.")
		return "redirect:" + safeNext(next, "/admin/comments")
	}

	@PostMapping("/admin/comments/{seq}/restore")
	fun restore(@PathVariable seq: Long, @RequestParam(defaultValue = "") next: String, user: Principal, redirect: RedirectAttributes): String {
		when (comments.restore(seq, user.name)) {
			CommentService.RestoreResult.RESTORED -> { redirect.addFlashAttribute("notice", "댓글을 되살렸습니다."); audit.record(user.name, "COMMENT_RESTORE", "COMMENT", seq) }
			CommentService.RestoreResult.PARENT_HIDDEN -> redirect.addFlashAttribute("error", "원댓글이 숨겨져 있어 답글만 되살릴 수 없습니다. 원댓글부터 되살리세요.")
			CommentService.RestoreResult.NOT_FOUND -> redirect.addFlashAttribute("error", "되살릴 댓글이 없습니다.")
			CommentService.RestoreResult.NOT_ADMIN_HIDDEN -> redirect.addFlashAttribute("error", "작성자가 직접 지운 댓글이거나 숨김 기록이 없어 되살릴 수 없습니다(database.sql 21번 적용 전 숨긴 댓글 포함).")
		}
		return "redirect:" + safeNext(next, "/admin/comments")
	}

	/** 신고 대기열. 오래 기다린 신고부터 보여 준다. */
	@GetMapping("/admin/reports")
	fun reports(@RequestParam(defaultValue = "open") status: String, @RequestParam(defaultValue = "0") page: Int, model: Model, user: Principal): String {
		model.addAttribute("result", reports.search(status, page))
		model.addAttribute("status", status)
		model.addAttribute("ready", reports.available())
		model.addAttribute("reasons", ReportService.REASONS)
		return page(model, user, "reports", "admin/reports")
	}

	@PostMapping("/admin/reports/{seq}/close")
	fun closeReport(@PathVariable seq: Long, @RequestParam outcome: String, @RequestParam(defaultValue = "") note: String,
	                @RequestParam(defaultValue = "false") sameTarget: Boolean, @RequestParam(defaultValue = "") next: String,
	                user: Principal, redirect: RedirectAttributes): String {
		val changed = reports.close(seq, outcome, note, user.name, sameTarget)
		if (changed > 0) audit.record(user.name, "REPORT_CLOSE", "REPORT", seq, after = (if (outcome == "done") "조치 완료" else "문제 없음") + (if (changed > 1) " · 같은 대상 ${changed}건" else ""), note = note)
		flash(redirect, changed > 0, if (changed > 1) "같은 대상의 신고 ${changed}건을 닫았습니다." else "신고를 닫았습니다.", "이미 처리된 신고입니다.")
		return "redirect:" + safeNext(next, "/admin/reports")
	}

	/** 쪽지 보내기 정지. days: 7·30·-1(무기한)·0(해제) */
	@PostMapping("/admin/members/{seq}/dm-suspend")
	fun suspendMessages(@PathVariable seq: Long, @RequestParam days: Int, @RequestParam(defaultValue = "") next: String,
	                    user: Principal, redirect: RedirectAttributes): String {
		val ok = days in setOf(-1, 0, 7, 30) && reports.suspend(seq, days, user.name)
		if (ok) audit.record(user.name, "DM_SUSPEND", "MEMBER", seq, after = when (days) { 0 -> "해제"; -1 -> "무기한"; else -> "${days}일" })
		flash(redirect, ok, when (days) { 0 -> "쪽지 정지를 풀었습니다."; -1 -> "쪽지 보내기를 무기한 정지했습니다."; else -> "쪽지 보내기를 ${days}일 정지했습니다." },
			"쪽지 정지를 저장하지 못했습니다. database.sql 25번 적용과 관리자 계정 권한을 확인하세요.")
		return "redirect:" + safeNext(next, "/admin/members/$seq")
	}

	/** 관리자 작업 이력. */
	@GetMapping("/admin/audit")
	fun auditLog(@RequestParam(required = false) target: String?, @RequestParam(required = false) action: String?, @RequestParam(required = false) q: String?,
	             @RequestParam(defaultValue = "0") page: Int, model: Model, user: Principal): String {
		model.addAttribute("result", audit.search(target, action, q, page))
		model.addAttribute("ready", audit.available())
		model.addAttribute("targets", AuditService.TARGETS); model.addAttribute("actions", AuditService.ACTIONS)
		model.addAttribute("target", target ?: ""); model.addAttribute("action", action ?: ""); model.addAttribute("q", q ?: "")
		return page(model, user, "audit", "admin/audit")
	}

	/** 금칙어 목록·추가와 시험 문장 확인. */
	@GetMapping("/admin/moderation")
	fun moderation(@RequestParam(required = false) test: String?, model: Model, user: Principal): String {
		model.addAttribute("rules", rules.list())
		model.addAttribute("ready", rules.available())
		model.addAttribute("scopes", RuleService.SCOPES); model.addAttribute("ruleActions", RuleService.ACTIONS)
		model.addAttribute("test", test ?: "")
		model.addAttribute("matches", test?.takeIf { it.isNotBlank() }?.let { rules.test(it.take(1000)) })
		return page(model, user, "moderation", "admin/moderation")
	}

	@PostMapping("/admin/moderation")
	fun addRule(@RequestParam pattern: String, @RequestParam(defaultValue = "ALL") scope: String, @RequestParam(defaultValue = "BLOCK") action: String,
	            @RequestParam(defaultValue = "") note: String, user: Principal, redirect: RedirectAttributes): String {
		val (outcome, rule) = rules.add(pattern, scope, action, note, user.name)
		if (outcome == RuleService.Outcome.ADDED && rule != null) audit.record(user.name, "RULE_ADD", "RULE", rule.seq, after = "${rule.pattern} · ${rule.scopeLabel} · ${rule.actionLabel}", note = note)
		when (outcome) {
			RuleService.Outcome.ADDED -> redirect.addFlashAttribute("notice", "금칙어를 추가했습니다. 1분 안에 서비스에 적용됩니다.")
			RuleService.Outcome.INVALID -> redirect.addFlashAttribute("error", "금칙어는 띄어쓰기를 빼고 2~30자여야 합니다. '거래 의심 표시'는 쪽지에만 쓸 수 있습니다.")
			RuleService.Outcome.DUPLICATE -> redirect.addFlashAttribute("error", "같은 범위에 이미 있는 금칙어입니다.")
			RuleService.Outcome.UNAVAILABLE -> redirect.addFlashAttribute("error", "금칙어 테이블이 없습니다. database.sql 26번과 관리자 계정 권한을 적용하세요.")
		}
		return "redirect:/admin/moderation"
	}

	@PostMapping("/admin/moderation/{seq}/toggle")
	fun toggleRule(@PathVariable seq: Long, @RequestParam enabled: Boolean, user: Principal, redirect: RedirectAttributes): String {
		val rule = rules.find(seq)
		val ok = rule != null && rules.setEnabled(seq, enabled, user.name)
		if (ok) audit.record(user.name, "RULE_TOGGLE", "RULE", seq, before = rule!!.pattern, after = if (enabled) "사용" else "사용 안 함")
		flash(redirect, ok, if (enabled) "금칙어를 다시 사용합니다." else "금칙어 사용을 멈췄습니다.", "이미 같은 상태이거나 없는 금칙어입니다.")
		return "redirect:/admin/moderation"
	}

	@PostMapping("/admin/moderation/{seq}/delete")
	fun deleteRule(@PathVariable seq: Long, user: Principal, redirect: RedirectAttributes): String {
		val rule = rules.find(seq)
		val ok = rule != null && rules.delete(seq)
		if (ok) audit.record(user.name, "RULE_DELETE", "RULE", seq, before = "${rule!!.pattern} · ${rule.scopeLabel} · ${rule.actionLabel}")
		flash(redirect, ok, "금칙어를 삭제했습니다.", "없는 금칙어입니다.")
		return "redirect:/admin/moderation"
	}

	/** Gmail 발송 현황(최근 24시간 한도 사용량, 일정 알림 발송 결과). */
	@GetMapping("/admin/mail")
	fun mailStatus(model: Model, user: Principal): String {
		model.addAttribute("report", mail.report())
		return page(model, user, "mail", "admin/mail")
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
		// 청약홈이 공고를 모집공고일보다 먼저 등록해 두는 경우가 있어 종료일은 오늘 이후 7일까지 고를 수 있다(스케줄러와 같은 기준).
		model.addAttribute("latestDate", Kst.today().plusDays(7))
		return page(model, user, "jobs", "admin/jobs")
	}

	@PostMapping("/admin/jobs")
	fun start(@RequestParam kind: JobKind, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate,
	          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate, user: Principal, redirect: RedirectAttributes) =
		schedulerAction(redirect) {
			val id = scheduler.start(kind.name, startDate, endDate, user.name)
			audit.record(user.name, "JOB_START", "JOB", id, after = "${JobKind.label(kind.name)} $startDate~$endDate")
			"수집을 시작했습니다. 작업 ID: $id"
		}

	@PostMapping("/admin/jobs/{id}/cancel")
	fun cancel(@PathVariable id: String, user: Principal, redirect: RedirectAttributes) = schedulerAction(redirect) {
		scheduler.cancel(id).also { audit.record(user.name, "JOB_CANCEL", "JOB", id) }
	}

	@PostMapping("/admin/jobs/{id}/retry")
	fun retry(@PathVariable id: String, user: Principal, redirect: RedirectAttributes) =
		schedulerAction(redirect) {
			val next = scheduler.retry(id, user.name)
			audit.record(user.name, "JOB_RETRY", "JOB", next, before = id)
			"같은 기간으로 새 작업을 시작했습니다. 작업 ID: $next"
		}

	// HTML 체크박스는 해제하면 값이 전송되지 않으므로 미전송을 false로 본다.
	@PostMapping("/admin/schedules/{kind}")
	fun schedule(@PathVariable kind: JobKind, @RequestParam(defaultValue = "false") enabled: Boolean, @RequestParam cron: String,
	             @RequestParam lookbackDays: Int, user: Principal, redirect: RedirectAttributes) =
		schedulerAction(redirect) {
			scheduler.saveSchedule(kind.name, enabled, cron, lookbackDays)
				.also { audit.record(user.name, "SCHEDULE_SAVE", "SCHEDULE", kind.name, after = "${if (enabled) "켬" else "끔"} · $cron · ${lookbackDays}일") }
		}

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
		model.addAttribute("openReports", reports.openCount())
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
