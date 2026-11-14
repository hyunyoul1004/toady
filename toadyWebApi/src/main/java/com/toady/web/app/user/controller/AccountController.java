package com.toady.web.app.user.controller;

import com.toady.web.app.user.service.AccountService;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 로그인한 회원의 '내 정보' API. 화면: /account.
 * 입력 규칙 오류도 400이 아니라 화면에 바로 보여 줄 문구로 돌려준다(가입 화면과 같은 방식).
 */
@RestController
@RequestMapping("/api/v1/me/account")
@Secured("ROLE_USER")
public class AccountController {
    public record NicknameChange(@Size(max = 50) String nickname) { }
    public record PasswordChange(@Size(max = 100) String currentPassword, @Size(max = 100) String newPassword, @Size(max = 100) String newPasswordConfirm) { }
    public record ProfileIconChange(@Size(max = 30) String icon) { }
    public record Withdraw(@Size(max = 100) String password, @Size(max = 50) String phrase) { }

    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ResData<?>> summary(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.summary(user.getUserSeq()), "SUCCESS", "성공"));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ResData<?>> nickname(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody NicknameChange request, jakarta.servlet.http.HttpServletRequest http) {
        return respond(service.changeNickname(user.getUserSeq(), request.nickname(), http));
    }

    @PatchMapping("/profile-icon")
    public ResponseEntity<ResData<?>> profileIcon(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody ProfileIconChange request) {
        return respond(service.changeProfileIcon(user.getUserSeq(), request.icon()));
    }

    /** 내가 쓴 이야기(최신순 10개씩) */
    @GetMapping("/comments")
    public ResponseEntity<ResData<?>> comments(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.myComments(user.getUserSeq(), page), "SUCCESS", "성공"));
    }

    /** 내 이야기에 달린 답글(최신 20개)과 새 답글 수. after: 이 기기에서 마지막으로 본 답글 번호 */
    @GetMapping("/replies")
    public ResponseEntity<ResData<?>> replies(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(required = false) Long after) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.replies(user.getUserSeq(), after), "SUCCESS", "성공"));
    }

    @PostMapping("/password")
    public ResponseEntity<ResData<?>> password(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PasswordChange request) {
        return respond(service.changePassword(user.getUserSeq(), request.currentPassword(), request.newPassword(), request.newPasswordConfirm()));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<ResData<?>> withdraw(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody Withdraw request) {
        return respond(service.withdraw(user.getUserSeq(), request.password(), request.phrase()));
    }

    private static ResponseEntity<ResData<?>> respond(AccountService.Outcome outcome) {
        Object data = outcome.accessToken() == null ? null : Map.of("accessToken", outcome.accessToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(data, outcome.code(), outcome.message()));
    }
}
