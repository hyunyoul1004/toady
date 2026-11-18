package com.toady.web.app.planner;

import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

/**
 * 나의 청약(관심 공고) 저장·조회·삭제 API. 화면의 '나의 청약' 메뉴와 공고 상세의 저장 버튼이 부른다.
 * 실제 저장은 PlannerService가 TB_FAVORITE 테이블에 한다.
 */
// @RestController: 이 클래스의 메서드 반환값을 JSON으로 응답하는 컨트롤러로 등록한다.
// @RequestMapping: 아래 모든 주소 앞에 /api/v1/me/favorites가 붙는다.
// @Validated: @PathVariable·@RequestParam에 붙은 @Pattern 같은 검증을 켠다(메서드 파라미터 검증).
// @Secured("ROLE_USER"): 로그인한 회원만 부를 수 있다. 아니면 AccessDeniedException → 403.
@RestController
@RequestMapping("/api/v1/me/favorites")
@Validated
@Secured("ROLE_USER")
public class PlannerController {
    private final PlannerService service;
    public PlannerController(PlannerService service) { this.service=service; }
    /**
     * 저장 요청 본문(JSON). @NotNull·@Size·@Min·@Max는 jakarta 검증 규칙이고, 아래 @Valid가 붙으면 검사한다.
     * checklist는 준비 항목 5개를 비트로 담은 값(0~31), stage는 진행 단계(0~5, 생략하면 기존 단계 유지).
     */
    public record Save(@NotNull @Size(max=2000) String note,@Min(0) @Max(31) int checklist,boolean emailEnabled,@Min(0) @Max(PlannerService.MAX_STAGE) Integer stage) { }
    // @GetMapping: GET /api/v1/me/favorites. 내 관심 공고 목록.
    // @AuthenticationPrincipal: 로그인 정보(JWT로 복원한 사용자)를 파라미터로 바로 받는다.
    @GetMapping public ResData<?> list(@AuthenticationPrincipal CustomUserDetails user) { return ResData.of(service.list(user.getUserSeq()),"SUCCESS","성공"); }
    // @PostMapping: POST /api/v1/me/favorites/{type}/{house}/{notice}. 새로 저장하거나 기존 메모·단계를 고친다.
    // @PathVariable: 주소의 {type} 같은 부분을 변수로 받는다. @Pattern으로 허용 문자를 제한한다.
    // @RequestBody: 요청 본문 JSON을 Save 레코드로 바꾼다. @Valid가 있어 Save 안의 검증 규칙을 검사한다.
    @PostMapping("/{type}/{house}/{notice}") public ResData<?> save(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable @Pattern(regexp="apt|urban|remaining|public-rental|optional") String type,
            @PathVariable @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String house,
            @PathVariable @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String notice,@Valid @RequestBody Save request) {
        service.save(user.getUserSeq(),type,house,notice,request.note(),request.checklist(),request.emailEnabled(),request.stage());
        return ResData.of(null,"SUCCESS","저장했습니다.");
    }
    // @DeleteMapping: DELETE 요청으로 관심 공고 하나를 지운다.
    @DeleteMapping("/{type}/{house}/{notice}") public ResData<?> delete(@AuthenticationPrincipal CustomUserDetails user,@PathVariable String type,@PathVariable String house,@PathVariable String notice) {
        service.delete(user.getUserSeq(),type,house,notice); return ResData.of(null,"SUCCESS","삭제했습니다.");
    }
    // 내 모든 관심 공고의 이메일 알림을 한 번에 끈다.
    @PostMapping("/disable-emails") public ResData<?> disable(@AuthenticationPrincipal CustomUserDetails user) {
        service.disableEmails(user.getUserSeq()); return ResData.of(null,"SUCCESS","모든 관심 공고의 이메일 알림을 해제했습니다.");
    }
}
