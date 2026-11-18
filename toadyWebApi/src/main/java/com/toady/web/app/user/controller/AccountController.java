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
 * 흐름: AccountController → AccountService → UserRepository(TB_USER) 등.
 * 닉네임·비밀번호가 바뀌면 새 토큰(accessToken)을 함께 돌려줘 화면이 교체한다.
 */
// @RestController: 메서드 반환값을 JSON으로 응답하는 컨트롤러
@RestController
// @RequestMapping: 이 클래스의 모든 주소 앞에 /api/v1/me/account를 붙인다
@RequestMapping("/api/v1/me/account")
// @Secured: ROLE_USER 권한이 있는(로그인한) 사용자만 부를 수 있다. 아니면 403/401
@Secured("ROLE_USER")
public class AccountController {
    // 요청 본문(JSON)을 받는 record들. @Size: 문자열 길이 상한. 넘으면 @Valid 검증에서 걸린다
    public record NicknameChange(@Size(max = 50) String nickname) { }
    public record PasswordChange(@Size(max = 100) String currentPassword, @Size(max = 100) String newPassword, @Size(max = 100) String newPasswordConfirm) { }
    public record ProfileIconChange(@Size(max = 30) String icon) { }
    public record Withdraw(@Size(max = 100) String password, @Size(max = 50) String phrase) { }

    private final AccountService service;
    // 생성자가 하나뿐이면 @Autowired 없이도 스프링이 이 생성자로 주입한다
    public AccountController(AccountService service) { this.service = service; }

    /** 내 정보 요약(이메일·닉네임·아이콘·가입일 등). */
    // @GetMapping: GET /api/v1/me/account
    // @AuthenticationPrincipal: JwtAuthenticationFilter가 넣어 둔 로그인 사용자(CustomUserDetails)를 바로 받는다
    // CacheControl.noStore(): 개인 정보라 브라우저·중간 캐시에 저장하지 않게 한다
    @GetMapping
    public ResponseEntity<ResData<?>> summary(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.summary(user.getUserSeq()), "SUCCESS", "성공"));
    }

    /** 닉네임 변경. */
    // @Valid: 본문 record의 검증 애너테이션(@Size)을 검사한다. @RequestBody: JSON 본문을 record로 바꿔 받는다
    @PatchMapping("/nickname")
    public ResponseEntity<ResData<?>> nickname(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody NicknameChange request, jakarta.servlet.http.HttpServletRequest http) {
        return respond(service.changeNickname(user.getUserSeq(), request.nickname(), http));
    }

    /** 프로필 아이콘 변경(ProfileIcons에 정의된 이름만 허용). */
    @PatchMapping("/profile-icon")
    public ResponseEntity<ResData<?>> profileIcon(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody ProfileIconChange request) {
        return respond(service.changeProfileIcon(user.getUserSeq(), request.icon()));
    }

    /** 내가 쓴 이야기(최신순 10개씩) */
    @GetMapping("/comments")
    public ResponseEntity<ResData<?>> comments(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "0") int page) { // @RequestParam: ?page=값. 없으면 0
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.myComments(user.getUserSeq(), page), "SUCCESS", "성공"));
    }

    /** 내 이야기에 달린 답글(최신 20개)과 새 답글 수. after: 이 기기에서 마지막으로 본 답글 번호 */
    @GetMapping("/replies")
    public ResponseEntity<ResData<?>> replies(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(required = false) Long after) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ResData.of(service.replies(user.getUserSeq(), after), "SUCCESS", "성공"));
    }

    /** 비밀번호 변경. 현재 비밀번호를 확인한 뒤 바꾼다. */
    @PostMapping("/password")
    public ResponseEntity<ResData<?>> password(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PasswordChange request) {
        return respond(service.changePassword(user.getUserSeq(), request.currentPassword(), request.newPassword(), request.newPasswordConfirm()));
    }

    /** 회원 탈퇴. 비밀번호와 확인 문구를 함께 받는다. */
    @PostMapping("/withdraw")
    public ResponseEntity<ResData<?>> withdraw(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody Withdraw request) {
        return respond(service.withdraw(user.getUserSeq(), request.password(), request.phrase()));
    }

    // 서비스 결과(Outcome)를 응답으로 바꾼다. 실패도 200으로 보내고 code·message로 구분한다. 새 토큰이 있으면 data에 싣는다
    private static ResponseEntity<ResData<?>> respond(AccountService.Outcome outcome) {
        Object data = outcome.accessToken() == null ? null : Map.of("accessToken", outcome.accessToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(data, outcome.code(), outcome.message()));
    }
}
