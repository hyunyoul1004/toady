package com.toady.web.app.operation;

import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 회원에게 보여 줄 운영 안내(경고·이야기 쓰기 정지·닉네임 초기화).
 * 흐름: OperationNoticeController → Sanctions → TB_USER_SANCTION.
 */
// @RestController: JSON 응답 컨트롤러. @RequestMapping: 공통 주소
@RestController
@RequestMapping("/api/v1/me/notices")
// @Secured: 로그인한 회원(ROLE_USER)만 부를 수 있다
@org.springframework.security.access.annotation.Secured("ROLE_USER")
public class OperationNoticeController {
    private final Sanctions sanctions;

    public OperationNoticeController(Sanctions sanctions) { this.sanctions = sanctions; }

    /** 내 안내 목록. 개인 정보라 캐시하지 않는다. */
    // @GetMapping: GET /api/v1/me/notices. @AuthenticationPrincipal: 로그인 사용자 정보를 바로 받는다
    @GetMapping
    public ResponseEntity<ResData<?>> list(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(sanctions.notices(user.getUserSeq()), "SUCCESS", "성공"));
    }

    /** 안내를 확인했다(다시 띄우지 않는다). 이야기 쓰기 정지는 기간 동안 계속 보인다. */
    // @PathVariable: 주소의 {seq} 자리 값을 받는다
    @PostMapping("/{seq}/seen")
    public ResponseEntity<ResData<?>> seen(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long seq) {
        sanctions.markSeen(user.getUserSeq(), seq);
        return ResponseEntity.ok(ResData.of(null, "SUCCESS", "성공"));
    }
}
