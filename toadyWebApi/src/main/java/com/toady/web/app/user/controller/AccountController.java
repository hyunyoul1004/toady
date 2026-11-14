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
    public record Withdraw(@Size(max = 100) String password, @Size(max = 50) String phrase) { }

    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<ResData<?>> summary(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.summary(user.getUserSeq()), "SUCCESS", "성공"));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ResData<?>> nickname(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody NicknameChange request) {
        return respond(service.changeNickname(user.getUserSeq(), request.nickname()));
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
