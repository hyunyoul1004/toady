package com.toady.web.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;

/**
 * 관리자 화면과 실행·중지·스케줄 변경 요청을 처리한다.
 * 변경 요청 뒤에는 리다이렉트해 새로고침에 의한 POST 재전송을 방지한다.
 */
@Controller
@ConditionalOnProperty(name = "applyhome.admin.enabled", havingValue = "true")
public class AdminController {
    private final AdminJobService jobs;
    private final AdminJobRepository repository;
    private final AdminScheduleManager schedules;
    public AdminController(AdminJobService jobs, AdminJobRepository repository, AdminScheduleManager schedules) {
        this.jobs = jobs; this.repository = repository; this.schedules = schedules;
    }
    @GetMapping("/") String root() { return "redirect:/admin/"; }
    @GetMapping("/admin/login") String login() { return "admin/login"; }
    /** 조회용 모델만 구성한다. 실제 수집은 페이지 요청과 분리된 백그라운드 실행기에서 동작한다. */
    // 프록시가 /admin을 /admin/로 정규화해도 같은 화면을 반환한다.
    @GetMapping({"/admin", "/admin/"}) String index(Model model, Principal user) {
        model.addAttribute("kinds", JobKind.values());
        model.addAttribute("jobs", repository.recent());
        model.addAttribute("active", repository.active().orElse(null));
        model.addAttribute("schedules", schedules.list());
        model.addAttribute("today", AdminJobService.today());
        model.addAttribute("defaultStart", AdminJobService.today().minusDays(90));
        model.addAttribute("username", user.getName());
        return "admin/index";
    }
    /** 로그인한 관리자 이름을 요청자 이력으로 남기고, 접수한 작업 ID를 화면에 알린다. */
    @PostMapping("/admin/jobs") String start(@RequestParam JobKind kind,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Principal user, RedirectAttributes redirect) {
        try {
            String id = jobs.submit(kind, startDate, endDate, user.getName(), "MANUAL");
            redirect.addFlashAttribute("notice", "수집을 시작했습니다. 작업 ID: " + id);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/";
    }
    /** 현재 구간의 저장을 강제로 취소하지 않고 다음 구간으로 넘어가기 전 중지하도록 요청한다. */
    @PostMapping("/admin/jobs/{id}/cancel") String cancel(@PathVariable String id, RedirectAttributes redirect) {
        redirect.addFlashAttribute("notice", repository.cancel(id)
                ? "중지를 요청했습니다. 진행 중인 월의 수집이 끝나면 멈춥니다." : "이미 종료되었거나 존재하지 않는 작업입니다.");
        return "redirect:/admin/";
    }
    /** 기존 실패·완료 이력은 보존하고 같은 종류·기간의 새 작업을 생성한다. 중간 재개와는 다르다. */
    @PostMapping("/admin/jobs/{id}/retry") String retry(@PathVariable String id, Principal user, RedirectAttributes redirect) {
        repository.find(id).ifPresentOrElse(job -> {
            if (job.isActive()) { redirect.addFlashAttribute("error", "진행 중인 작업은 다시 실행할 수 없습니다."); return; }
            try {
                jobs.submit(job.kind(), job.startDate(), job.endDate(), user.getName(), "MANUAL");
                redirect.addFlashAttribute("notice", "같은 기간으로 새 작업을 시작했습니다.");
            } catch (IllegalArgumentException | IllegalStateException exception) {
                redirect.addFlashAttribute("error", exception.getMessage());
            }
        }, () -> redirect.addFlashAttribute("error", "작업을 찾을 수 없습니다."));
        return "redirect:/admin/";
    }
    // HTML 체크박스는 해제 시 파라미터가 전송되지 않으므로 미전송 값을 false로 해석한다.
    @PostMapping("/admin/schedules/{kind}") String schedule(@PathVariable JobKind kind,
            @RequestParam(defaultValue = "false") boolean enabled, @RequestParam String cron,
            @RequestParam int lookbackDays, RedirectAttributes redirect) {
        try {
            schedules.save(kind, enabled, cron, lookbackDays);
            redirect.addFlashAttribute("notice", "자동 스케줄을 저장했습니다. 다른 인스턴스에는 최대 30초 뒤 반영됩니다.");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/";
    }
}
