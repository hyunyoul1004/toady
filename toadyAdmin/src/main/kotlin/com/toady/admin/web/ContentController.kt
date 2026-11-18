package com.toady.admin.web

import com.toady.admin.Kst
import com.toady.admin.announcements.AnnouncementService
import com.toady.admin.announcements.QualityService
import com.toady.admin.audit.AuditService
import com.toady.admin.comments.CommentService
import com.toady.admin.content.AreaService
import com.toady.admin.content.FaqService
import com.toady.admin.content.NoticeService
import com.toady.admin.content.SettingService
import com.toady.admin.insights.InsightService
import com.toady.admin.jobs.JobKind
import com.toady.admin.jobs.SchedulerClient
import com.toady.admin.members.MemberService
import com.toady.admin.reports.ReportService
import com.toady.admin.support.ApiClient
import com.toady.admin.support.MemberMemoService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.Principal
import java.time.LocalDateTime

/**
 * 관리자 2·3단계 화면: 서비스 설정, 사이트 공지, 자주 묻는 질문, 생활권, 위치 확인, 데이터 점검, 유입·커뮤니티 지표,
 * 운영팀 이야기, 회원 이야기 일괄 숨김, 푸시 발송, 회원 지원(메모·인증 메일·탈퇴 대행). 모든 변경은 작업 이력에 남긴다.
 * 서비스 쪽(API)이 1분마다 DB를 다시 읽으므로, 여기서 저장한 공지·설정 등은 1분 안에 서비스에 보인다.
 * 메일·푸시·탈퇴처럼 API만 할 수 있는 일은 ApiClient로 API 내부 주소에 부탁한다.
 */
// @Controller: 반환한 문자열을 템플릿 이름 또는 "redirect:주소"로 처리하는 화면 컨트롤러. 주소 연결은 AdminController와 같은 방식이다.
@Controller
class ContentController(
	private val settings: SettingService, private val notices: NoticeService, private val faq: FaqService, private val areas: AreaService,
	private val quality: QualityService, private val insights: InsightService, private val announcements: AnnouncementService,
	private val comments: CommentService, private val members: MemberService, private val memos: MemberMemoService, private val api: ApiClient,
	private val scheduler: SchedulerClient, private val reports: ReportService, private val audit: AuditService,
) {
	// ---------------------------------------------------------------- 서비스 설정
	@GetMapping("/admin/settings")
	fun settingsPage(model: Model, user: Principal): String {
		model.addAttribute("items", settings.list()); model.addAttribute("ready", settings.available())
		model.addAttribute("apiConfigured", api.configured)
		return page(model, user, "settings", "admin/settings")
	}

	/** 설정 하나 저장. Result.fold로 성공(이전 값)·실패(오류 문구)를 나눠 처리한다. */
	@PostMapping("/admin/settings")
	fun saveSetting(@RequestParam key: String, @RequestParam(defaultValue = "") value: String, user: Principal, redirect: RedirectAttributes): String {
		settings.save(key, value, user.name).fold(
			{ before -> audit.record(user.name, "SETTING_SAVE", "SETTING", key, before ?: "기본값", value.trim().ifEmpty { "기본값" }); redirect.addFlashAttribute("notice", "설정을 저장했습니다. 1분 안에 서비스에 적용됩니다.") },
			{ redirect.addFlashAttribute("error", it.message ?: "저장하지 못했습니다. database.sql 27번과 관리자 권한을 확인하세요.") })
		return "redirect:/admin/settings"
	}

	// ---------------------------------------------------------------- 사이트 공지
	@GetMapping("/admin/notices")
	fun noticesPage(model: Model, user: Principal): String {
		model.addAttribute("notices", notices.list()); model.addAttribute("ready", notices.available())
		model.addAttribute("defaultStart", Kst.now().withSecond(0).withNano(0)); model.addAttribute("defaultEnd", Kst.now().plusDays(7).withSecond(0).withNano(0))
		return page(model, user, "notices", "admin/notices")
	}

	/** 공지 추가. datetime-local 입력값을 @DateTimeFormat(ISO.DATE_TIME)으로 LocalDateTime으로 받는다. */
	@PostMapping("/admin/notices")
	fun addNotice(@RequestParam message: String, @RequestParam(defaultValue = "") link: String, @RequestParam(defaultValue = "") linkLabel: String,
	              @RequestParam(defaultValue = "ALL") placement: String, @RequestParam(defaultValue = "INFO") level: String,
	              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: LocalDateTime,
	              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: LocalDateTime, user: Principal, redirect: RedirectAttributes): String {
		val error = notices.add(message, link, linkLabel, placement, level, start, end, user.name)
		if (error == null) audit.record(user.name, "NOTICE_ADD", "NOTICE", "-", after = message.trim().take(200))
		flash(redirect, error == null, "공지를 추가했습니다. 기간 안이면 1분 안에 보입니다.", error)
		return "redirect:/admin/notices"
	}

	@PostMapping("/admin/notices/{seq}/toggle")
	fun toggleNotice(@PathVariable seq: Long, @RequestParam enabled: Boolean, user: Principal, redirect: RedirectAttributes): String {
		val ok = notices.setEnabled(seq, enabled, user.name)
		if (ok) audit.record(user.name, "NOTICE_TOGGLE", "NOTICE", seq, after = if (enabled) "노출" else "멈춤")
		flash(redirect, ok, if (enabled) "공지를 다시 보입니다." else "공지를 멈췄습니다.", "없는 공지입니다.")
		return "redirect:/admin/notices"
	}

	@PostMapping("/admin/notices/{seq}/delete")
	fun deleteNotice(@PathVariable seq: Long, user: Principal, redirect: RedirectAttributes): String {
		val before = notices.list().firstOrNull { it.seq == seq }
		val ok = notices.delete(seq)
		if (ok) audit.record(user.name, "NOTICE_DELETE", "NOTICE", seq, before = before?.message)
		flash(redirect, ok, "공지를 삭제했습니다.", "없는 공지입니다.")
		return "redirect:/admin/notices"
	}

	// ---------------------------------------------------------------- 자주 묻는 질문
	@GetMapping("/admin/faq")
	fun faqPage(model: Model, user: Principal): String {
		model.addAttribute("groups", faq.groups()); model.addAttribute("ready", faq.available())
		return page(model, user, "faq", "admin/faq")
	}

	@PostMapping("/admin/faq")
	fun addFaq(@RequestParam id: String, @RequestParam groupId: String, @RequestParam groupTitle: String, @RequestParam question: String,
	           @RequestParam answer: String, @RequestParam(defaultValue = "1000") order: Int, user: Principal, redirect: RedirectAttributes): String {
		val error = faq.add(id.trim(), groupId.trim(), groupTitle, question, answer, order, user.name)
		if (error == null) audit.record(user.name, "FAQ_ADD", "FAQ", id.trim(), after = question.trim().take(200))
		flash(redirect, error == null, "질문을 추가했습니다. 1분 안에 서비스에 보입니다.", error)
		return "redirect:/admin/faq#${id.trim()}"
	}

	/** 질문 수정. 체크박스(enabled)는 해제하면 전송되지 않아 기본값 false로 받는다. */
	@PostMapping("/admin/faq/{id}")
	fun updateFaq(@PathVariable id: String, @RequestParam groupId: String, @RequestParam groupTitle: String, @RequestParam question: String,
	              @RequestParam answer: String, @RequestParam order: Int, @RequestParam(defaultValue = "false") enabled: Boolean,
	              user: Principal, redirect: RedirectAttributes): String {
		val before = faq.find(id)
		val error = faq.update(id, groupId.trim(), groupTitle, question, answer, order, enabled, user.name)
		if (error == null) audit.record(user.name, "FAQ_UPDATE", "FAQ", id, before?.question?.take(200), question.trim().take(200) + if (enabled) "" else " · 숨김")
		flash(redirect, error == null, "질문을 저장했습니다. 1분 안에 서비스에 반영됩니다.", error)
		return "redirect:/admin/faq#$id"
	}

	@PostMapping("/admin/faq/{id}/delete")
	fun deleteFaq(@PathVariable id: String, user: Principal, redirect: RedirectAttributes): String {
		val before = faq.find(id)
		val ok = faq.delete(id)
		if (ok) audit.record(user.name, "FAQ_DELETE", "FAQ", id, before = before?.question?.take(200))
		flash(redirect, ok, "질문을 삭제했습니다.", "없는 질문입니다.")
		return "redirect:/admin/faq"
	}

	// ---------------------------------------------------------------- 생활권
	@GetMapping("/admin/areas")
	fun areasPage(model: Model, user: Principal): String {
		model.addAttribute("areas", areas.list()); model.addAttribute("ready", areas.available())
		return page(model, user, "areas", "admin/areas")
	}

	@PostMapping("/admin/areas")
	fun addArea(@RequestParam slug: String, @RequestParam label: String, @RequestParam aliases: String, @RequestParam terms: String,
	            @RequestParam(defaultValue = "") scope: String, @RequestParam description: String, @RequestParam(defaultValue = "1000") order: Int,
	            user: Principal, redirect: RedirectAttributes): String {
		val error = areas.add(slug.trim(), label, aliases, terms, scope, description, order, user.name)
		if (error == null) audit.record(user.name, "AREA_ADD", "AREA", slug.trim(), after = "$label · $terms")
		flash(redirect, error == null, "생활권을 추가했습니다. 1분 안에 검색·생활권 페이지·사이트맵에 반영됩니다.", error)
		return "redirect:/admin/areas"
	}

	/** 생활권 수정. slug(공개 주소)는 바꾸지 않는다. */
	@PostMapping("/admin/areas/{slug}")
	fun updateArea(@PathVariable slug: String, @RequestParam label: String, @RequestParam aliases: String, @RequestParam terms: String,
	               @RequestParam(defaultValue = "") scope: String, @RequestParam description: String, @RequestParam order: Int,
	               @RequestParam(defaultValue = "false") enabled: Boolean, user: Principal, redirect: RedirectAttributes): String {
		val before = areas.find(slug)
		val error = areas.update(slug, label, aliases, terms, scope, description, order, enabled, user.name)
		if (error == null) audit.record(user.name, "AREA_UPDATE", "AREA", slug, before?.let { "${it.label} · ${it.terms}" }, "$label · $terms" + if (enabled) "" else " · 숨김")
		flash(redirect, error == null, "생활권을 저장했습니다. 1분 안에 반영됩니다.", error)
		return "redirect:/admin/areas"
	}

	// ---------------------------------------------------------------- 위치 확인 · 데이터 점검 · 다시 수집
	/** 위치 확인 대기열. all=true면 이미 주소를 넣은 공고도 함께 보여 준다. */
	@GetMapping("/admin/locations")
	fun locations(@RequestParam(defaultValue = "false") all: Boolean, model: Model, user: Principal): String {
		model.addAttribute("items", quality.locationQueue(all)); model.addAttribute("all", all); model.addAttribute("ready", quality.locationReady())
		return page(model, user, "locations", "admin/locations")
	}

	/** 운영자가 확인한 주소 저장. 실제 지도 핀은 앱이 지도 검색으로 다시 확인한 뒤에만 놓는다. */
	@PostMapping("/admin/locations")
	fun saveLocation(@RequestParam id: String, @RequestParam(defaultValue = "") address: String, @RequestParam(defaultValue = "") next: String,
	                 user: Principal, redirect: RedirectAttributes): String {
		quality.saveLocation(id, address, user.name).fold(
			{ before -> audit.record(user.name, "LOCATION_SAVE", "ANNOUNCEMENT", id, before, address.trim().ifEmpty { "(지움)" })
				redirect.addFlashAttribute("notice", if (address.isBlank()) "확인 주소를 지웠습니다." else "주소를 저장했습니다. 앱이 지도 검색으로 확인되면 1분 안에 핀을 놓습니다.") },
			{ redirect.addFlashAttribute("error", it.message ?: "저장하지 못했습니다(database.sql 27번 확인).") })
		return "redirect:" + AdminController.safeNext(next, "/admin/locations")
	}

	@GetMapping("/admin/data-check")
	fun dataCheck(model: Model, user: Principal): String {
		model.addAttribute("issues", quality.issues())
		return page(model, user, "data-check", "admin/data-check")
	}

	/** 공고 하나를 다시 수집한다: 그 공고의 모집공고일 하루를 전체 수집(공고·주택형·경쟁률)으로 다시 받는다(같은 날 다른 공고도 함께 갱신). */
	@PostMapping("/admin/announcements/{type}/{house}/{pblanc}/recollect")
	fun recollect(@PathVariable type: String, @PathVariable house: String, @PathVariable("pblanc") pblanc: String, user: Principal, redirect: RedirectAttributes): String {
		val detail = announcements.detail(type, house, pblanc) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		val day = AnnouncementService.date(detail.noticeDate)
		if (day == null) {
			redirect.addFlashAttribute("error", "모집공고일이 없어 다시 수집할 날짜를 정할 수 없습니다. 수집 작업 화면에서 기간을 지정하세요.")
		} else try {
			val id = scheduler.start(JobKind.ALL.name, day, day, user.name)
			audit.record(user.name, "RECOLLECT", "ANNOUNCEMENT", detail.row.id, after = "모집공고일 $day 전체 수집 · 작업 $id")
			redirect.addFlashAttribute("notice", "모집공고일($day) 하루를 다시 수집하기 시작했습니다. 작업 ID: $id")
		} catch (failure: SchedulerClient.SchedulerException) { redirect.addFlashAttribute("error", failure.message) }
		return "redirect:/admin/announcements/${detail.row.id}"
	}

	// ---------------------------------------------------------------- 운영팀 이야기 · 일괄 숨김
	/**
	 * 운영팀 이야기 쓰기. 설정에 넣은 운영팀 회원 번호로 글을 쓰며, 그 계정이 정상인지·테이블이 있는지 먼저 확인한다.
	 * when의 각 줄은 위에서부터 먼저 맞는 조건 하나만 실행된다.
	 */
	@PostMapping("/admin/announcements/{type}/{house}/{pblanc}/official")
	fun writeOfficial(@PathVariable type: String, @PathVariable house: String, @PathVariable("pblanc") pblanc: String, @RequestParam content: String,
	                  @RequestParam(required = false) parentSeq: Long?, @RequestParam(defaultValue = "false") pinned: Boolean,
	                  user: Principal, redirect: RedirectAttributes): String {
		val detail = announcements.detail(type, house, pblanc) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
		val official = settings.value(SettingService.OFFICIAL_USER_SEQ)?.toLongOrNull()
		val text = content.trim()
		when {
			official == null -> redirect.addFlashAttribute("error", "운영팀 계정이 없습니다. 서비스에서 운영팀용 계정을 가입하고 '서비스 설정'에 회원 번호를 넣으세요.")
			members.search(null, null, 0, 1, official).items.firstOrNull()?.let { !it.withdrawn && !it.locked } != true -> redirect.addFlashAttribute("error", "운영팀 계정(#$official)이 없거나 잠긴·탈퇴한 계정입니다.")
			text.length !in 2..1000 -> redirect.addFlashAttribute("error", "운영팀 이야기는 2~1000자로 써 주세요.")
			!comments.officialReady() -> redirect.addFlashAttribute("error", "운영팀 이야기 테이블이 없습니다. database.sql 27번과 관리자 권한(이야기 INSERT 포함)을 적용하세요.")
			else -> try {
				val seq = comments.writeOfficial(detail.row.id, official, text, parentSeq, pinned, user.name)
				audit.record(user.name, "OFFICIAL_WRITE", "ANNOUNCEMENT", detail.row.id, after = (if (parentSeq != null) "답글 #$parentSeq · " else if (pinned) "고정 · " else "") + "#$seq ${text.take(150)}")
				redirect.addFlashAttribute("notice", "운영팀 이야기를 올렸습니다.")
			} catch (failure: IllegalArgumentException) { redirect.addFlashAttribute("error", failure.message)
			} catch (failure: org.springframework.dao.DataAccessException) { redirect.addFlashAttribute("error", "저장하지 못했습니다. 관리자 계정의 이야기 INSERT 권한(database.sql 27번)을 확인하세요.") }
		}
		return "redirect:/admin/announcements/${detail.row.id}"
	}

	/** 운영팀 글 고정·해제. */
	@PostMapping("/admin/comments/{seq}/pin")
	fun pin(@PathVariable seq: Long, @RequestParam pinned: Boolean, @RequestParam(defaultValue = "") next: String, user: Principal, redirect: RedirectAttributes): String {
		val ok = comments.setPinned(seq, pinned)
		if (ok) audit.record(user.name, "OFFICIAL_PIN", "COMMENT", seq, after = if (pinned) "고정" else "고정 해제")
		flash(redirect, ok, if (pinned) "이야기 맨 위에 고정했습니다." else "고정을 풀었습니다.", "운영팀 글이 아니거나 이미 같은 상태입니다.")
		return "redirect:" + AdminController.safeNext(next, "/admin/comments")
	}

	/** 한 회원의 보이는 이야기를 모두 숨긴다(최대 1000개). */
	@PostMapping("/admin/members/{seq}/hide-all")
	fun hideAll(@PathVariable seq: Long, @RequestParam(defaultValue = "") memo: String, user: Principal, redirect: RedirectAttributes): String {
		val count = comments.hideAllBy(seq, user.name)
		if (count > 0) audit.record(user.name, "COMMENT_HIDE_ALL", "MEMBER", seq, after = "${count}개 숨김", note = memo)
		flash(redirect, count > 0, "이 회원의 이야기 ${count}개(답글 포함)를 숨겼습니다.", "숨길 이야기가 없습니다.")
		return "redirect:/admin/members/$seq"
	}

	// ---------------------------------------------------------------- 유입·커뮤니티 지표
	/** 유입·커뮤니티 지표. 기간은 7~90일로 제한한다. */
	@GetMapping("/admin/insights")
	fun insightsPage(@RequestParam(defaultValue = "30") days: Int, @RequestParam(defaultValue = "true") excludeTest: Boolean, model: Model, user: Principal): String {
		val period = days.coerceIn(7, 90)
		model.addAttribute("report", insights.report(period, excludeTest)); model.addAttribute("days", period)
		return page(model, user, "insights", "admin/insights")
	}

	// ---------------------------------------------------------------- 푸시 발송
	@GetMapping("/admin/push")
	fun pushPage(model: Model, user: Principal): String {
		model.addAttribute("apiConfigured", api.configured)
		model.addAttribute("recent", audit.search("PUSH", null, null, 0, 10).items)
		return page(model, user, "push", "admin/push")
	}

	/** 시험 발송(testUserSeq) 또는 전체 발송. 전체 발송은 '전체 발송' 확인 문구를 입력해야 하고, API가 하루 두 번으로 제한한다. */
	@PostMapping("/admin/push")
	fun sendPush(@RequestParam title: String, @RequestParam body: String, @RequestParam(defaultValue = "/") url: String,
	             @RequestParam(required = false) testUserSeq: Long?, @RequestParam(defaultValue = "") confirm: String,
	             user: Principal, redirect: RedirectAttributes): String {
		val path = url.trim().ifEmpty { "/" }
		if (!path.matches(Regex("/[A-Za-z0-9/_?=&%.\\-]*"))) { redirect.addFlashAttribute("error", "이동할 주소는 /로 시작하는 사이트 안 경로만 쓸 수 있습니다."); return "redirect:/admin/push" }
		if (title.trim().length !in 2..40 || body.trim().length !in 2..120) { redirect.addFlashAttribute("error", "제목은 2~40자, 내용은 2~120자로 써 주세요."); return "redirect:/admin/push" }
		if (testUserSeq == null && confirm.trim() != "전체 발송") { redirect.addFlashAttribute("error", "전체 발송은 확인 칸에 '전체 발송'을 그대로 입력해야 보냅니다."); return "redirect:/admin/push" }
		val reply = api.push(title.trim(), body.trim(), path, testUserSeq)
		if (reply.ok) {
			val summary = "대상 ${reply.body["targets"]} · 전달 ${reply.body["sent"]} · 만료 정리 ${reply.body["removed"]} · 실패 ${reply.body["failed"]}"
			audit.record(user.name, if (testUserSeq == null) "PUSH_BROADCAST" else "PUSH_TEST", "PUSH", testUserSeq ?: "all", after = "${title.trim()} · $summary")
			redirect.addFlashAttribute("notice", (if (testUserSeq == null) "전체 발송했습니다. " else "시험 발송했습니다. ") + summary)
		} else redirect.addFlashAttribute("error", reply.message)
		return "redirect:/admin/push"
	}

	// ---------------------------------------------------------------- 회원 지원
	@PostMapping("/admin/members/{seq}/memo")
	fun addMemo(@PathVariable seq: Long, @RequestParam memo: String, user: Principal, redirect: RedirectAttributes): String {
		val ok = memos.add(seq, memo, user.name)
		if (ok) audit.record(user.name, "MEMBER_MEMO", "MEMBER", seq, after = memo.trim().take(200))
		flash(redirect, ok, "메모를 남겼습니다.", "메모를 저장하지 못했습니다(2자 이상, database.sql 27번 확인).")
		return "redirect:/admin/members/$seq"
	}

	/** 인증 메일 다시 보내기(API가 메일을 보낸다). */
	@PostMapping("/admin/members/{seq}/resend-verification")
	fun resend(@PathVariable seq: Long, user: Principal, redirect: RedirectAttributes): String {
		val reply = api.resendVerification(seq)
		if (reply.ok) audit.record(user.name, "MEMBER_RESEND", "MEMBER", seq, after = reply.message)
		flash(redirect, reply.ok, reply.message, reply.message)
		return "redirect:/admin/members/$seq"
	}

	/** 회원 요청에 따른 탈퇴 대행. 실수를 막으려고 '탈퇴 처리' 문구를 직접 입력해야 한다. 실제 처리는 API가 한다. */
	@PostMapping("/admin/members/{seq}/withdraw")
	fun withdraw(@PathVariable seq: Long, @RequestParam(defaultValue = "") confirm: String, @RequestParam(defaultValue = "") memo: String,
	             user: Principal, redirect: RedirectAttributes): String {
		if (confirm.trim() != "탈퇴 처리") { redirect.addFlashAttribute("error", "확인 칸에 '탈퇴 처리'를 그대로 입력해야 합니다."); return "redirect:/admin/members/$seq" }
		val reply = api.withdraw(seq)
		if (reply.ok) audit.record(user.name, "MEMBER_WITHDRAW", "MEMBER", seq, note = memo)
		flash(redirect, reply.ok, "회원 요청에 따라 탈퇴 처리했습니다.", reply.message)
		return "redirect:/admin/members/$seq"
	}

	/** 공통 화면 값(관리자 이름·메뉴·대기 신고 수)을 넣는다. AdminController.page와 같다. */
	private fun page(model: Model, user: Principal, menu: String, view: String): String {
		model.addAttribute("username", user.name); model.addAttribute("menu", menu)
		model.addAttribute("openReports", reports.openCount())
		return view
	}
	private fun flash(redirect: RedirectAttributes, ok: Boolean, success: String, failure: String?) {
		redirect.addFlashAttribute(if (ok) "notice" else "error", if (ok) success else (failure ?: "처리하지 못했습니다."))
	}
}
