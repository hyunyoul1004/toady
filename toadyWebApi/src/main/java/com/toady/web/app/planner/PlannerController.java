package com.toady.web.app.planner;

import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/me/favorites")
@Validated
@Secured("ROLE_USER")
public class PlannerController {
    private final PlannerService service;
    public PlannerController(PlannerService service) { this.service=service; }
    public record Save(@NotNull @Size(max=2000) String note,@Min(0) @Max(31) int checklist,boolean emailEnabled,@Min(0) @Max(PlannerService.MAX_STAGE) Integer stage) { }
    @GetMapping public ResData<?> list(@AuthenticationPrincipal CustomUserDetails user) { return ResData.of(service.list(user.getUserSeq()),"SUCCESS","성공"); }
    @PostMapping("/{type}/{house}/{notice}") public ResData<?> save(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable @Pattern(regexp="apt|urban|remaining|public-rental|optional") String type,
            @PathVariable @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String house,
            @PathVariable @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String notice,@Valid @RequestBody Save request) {
        service.save(user.getUserSeq(),type,house,notice,request.note(),request.checklist(),request.emailEnabled(),request.stage());
        return ResData.of(null,"SUCCESS","저장했습니다.");
    }
    @DeleteMapping("/{type}/{house}/{notice}") public ResData<?> delete(@AuthenticationPrincipal CustomUserDetails user,@PathVariable String type,@PathVariable String house,@PathVariable String notice) {
        service.delete(user.getUserSeq(),type,house,notice); return ResData.of(null,"SUCCESS","삭제했습니다.");
    }
    @PostMapping("/disable-emails") public ResData<?> disable(@AuthenticationPrincipal CustomUserDetails user) {
        service.disableEmails(user.getUserSeq()); return ResData.of(null,"SUCCESS","모든 관심 공고의 이메일 알림을 해제했습니다.");
    }
}
