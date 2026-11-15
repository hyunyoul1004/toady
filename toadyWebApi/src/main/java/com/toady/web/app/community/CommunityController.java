package com.toady.web.app.community;

import com.toady.web.app.community.Community.Member;
import com.toady.web.app.community.Community.Result;
import com.toady.web.app.user.service.ProfileIcons;
import com.toady.web.core.response.ResData;
import com.toady.web.core.security.service.CustomUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 프로필 카드(공개)와 로그인 회원의 쪽지·차단·신고 API.
 * 규칙 위반·준비 중 같은 결과도 화면에 바로 보여 줄 문구로 돌려준다(내 정보 API와 같은 방식).
 */
@RestController
public class CommunityController {
    public record SendRequest(Long toUserSeq, @Size(max = 2000) String content, @Size(max = 110) String contextId) { }
    public record BlockRequest(Long userSeq) { }
    public record ReportRequest(@Size(max = 10) String targetType, Long targetSeq, @Size(max = 20) String reason, @Size(max = 600) String detail) { }
    public record PolicyRequest(@Size(max = 10) String policy) { }

    private final Community community;
    private final SafetyService safety;
    private final DmService dm;
    private final ProfileIcons icons;

    public CommunityController(Community community, SafetyService safety, DmService dm, ProfileIcons icons) {
        this.community = community; this.safety = safety; this.dm = dm; this.icons = icons;
    }

    /**
     * 프로필 카드: 닉네임·캐릭터·가입한 달·남긴 이야기 수. 이메일 등 개인 정보는 주지 않는다.
     * 로그인했으면 내가 차단했는지와 쪽지를 보낼 수 있는지(이유 포함)도 준다.
     */
    @GetMapping("/api/v1/members/{userSeq}")
    public ResponseEntity<ResData<?>> card(@PathVariable long userSeq, @AuthenticationPrincipal CustomUserDetails viewer) {
        Member member = community.member(userSeq);
        if (member == null) return ok(Result.fail("ERR-404", "회원을 찾을 수 없어요."));
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userSeq", userSeq);
        view.put("withdrawn", member.withdrawn());
        view.put("nickname", member.withdrawn() ? "탈퇴한 회원" : member.nickname());
        view.put("profileIcon", member.withdrawn() ? null : icons.of(userSeq));
        view.put("joinedMonth", member.withdrawn() || member.joinedAt() == null ? null
                : member.joinedAt().getYear() + "-" + String.format("%02d", member.joinedAt().getMonthValue()));
        view.put("stories", member.withdrawn() ? 0 : community.count("SELECT COUNT(*) FROM TB_LTTOT_PBLANC_COMMENT WHERE CREATE_USER_SEQ=? AND DELETE_YN='N'", userSeq));
        view.put("ready", community.ready());
        if (viewer != null) {
            long me = viewer.getUserSeq();
            view.put("me", me == userSeq);
            view.put("blocked", me != userSeq && community.blocked(me, userSeq));
            if (me != userSeq && !member.withdrawn()) view.put("dm", dm.status(me, userSeq));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(view, "SUCCESS", "성공"));
    }

    // ---------------------------------------------------------------- 쪽지

    @Secured("ROLE_USER")
    @GetMapping("/api/v1/me/messages")
    public ResponseEntity<ResData<?>> threads(@AuthenticationPrincipal CustomUserDetails user, @RequestParam(defaultValue = "0") int page) {
        return data(dm.threads(user.getUserSeq(), page));
    }

    @Secured("ROLE_USER")
    @GetMapping("/api/v1/me/messages/unread")
    public ResponseEntity<ResData<?>> unread(@AuthenticationPrincipal CustomUserDetails user) {
        return data(Map.of("unread", dm.unread(user.getUserSeq()), "ready", community.ready()));
    }

    @Secured("ROLE_USER")
    @GetMapping("/api/v1/me/messages/{threadSeq:\\d+}")
    public ResponseEntity<ResData<?>> open(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long threadSeq, @RequestParam(required = false) Long before) {
        return ok(dm.open(user.getUserSeq(), threadSeq, before));
    }

    @Secured("ROLE_USER")
    @PostMapping("/api/v1/me/messages")
    public ResponseEntity<ResData<?>> send(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody SendRequest request) {
        if (request.toUserSeq() == null) return ok(Result.fail("ERR-401", "받는 회원을 찾을 수 없어요."));
        return ok(dm.send(user.getUserSeq(), request.toUserSeq(), request.content(), request.contextId()));
    }

    @Secured("ROLE_USER")
    @DeleteMapping("/api/v1/me/messages/{threadSeq:\\d+}")
    public ResponseEntity<ResData<?>> leave(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long threadSeq) {
        return ok(dm.leave(user.getUserSeq(), threadSeq));
    }

    @Secured("ROLE_USER")
    @GetMapping("/api/v1/me/messages/settings")
    public ResponseEntity<ResData<?>> settings(@AuthenticationPrincipal CustomUserDetails user) {
        SafetyService.Setting setting = safety.setting(user.getUserSeq());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("ready", community.ready());
        view.put("policy", setting.policy());
        view.put("suspendedUntil", setting.suspended(Community.now()) ? setting.suspendedUntil() : null);
        return data(view);
    }

    @Secured("ROLE_USER")
    @PutMapping("/api/v1/me/messages/settings")
    public ResponseEntity<ResData<?>> policy(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody PolicyRequest request) {
        return ok(safety.setPolicy(user.getUserSeq(), request.policy()));
    }

    // ---------------------------------------------------------------- 차단·신고

    @Secured("ROLE_USER")
    @GetMapping("/api/v1/me/blocks")
    public ResponseEntity<ResData<?>> blocks(@AuthenticationPrincipal CustomUserDetails user) {
        return data(Map.of("ready", community.ready(), "items", safety.blocks(user.getUserSeq())));
    }

    @Secured("ROLE_USER")
    @PostMapping("/api/v1/me/blocks")
    public ResponseEntity<ResData<?>> block(@AuthenticationPrincipal CustomUserDetails user, @RequestBody BlockRequest request) {
        if (request.userSeq() == null) return ok(Result.fail("ERR-401", "차단할 회원을 찾을 수 없어요."));
        return ok(safety.block(user.getUserSeq(), request.userSeq()));
    }

    @Secured("ROLE_USER")
    @DeleteMapping("/api/v1/me/blocks/{userSeq}")
    public ResponseEntity<ResData<?>> unblock(@AuthenticationPrincipal CustomUserDetails user, @PathVariable long userSeq) {
        return ok(safety.unblock(user.getUserSeq(), userSeq));
    }

    @Secured("ROLE_USER")
    @PostMapping("/api/v1/me/reports")
    public ResponseEntity<ResData<?>> report(@AuthenticationPrincipal CustomUserDetails user, @Valid @RequestBody ReportRequest request) {
        return ok(safety.report(user.getUserSeq(), request.targetType(), request.targetSeq(), request.reason(), request.detail()));
    }

    private static ResponseEntity<ResData<?>> data(Object data) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(data, "SUCCESS", "성공"));
    }

    private static ResponseEntity<ResData<?>> ok(Result result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ResData.of(result.data(), result.code(), result.message()));
    }
}
